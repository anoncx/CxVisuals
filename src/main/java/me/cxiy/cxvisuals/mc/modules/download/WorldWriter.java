package me.cxiy.cxvisuals.mc.modules.download;

import com.mojang.serialization.Lifecycle;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.PrimaryLevelData;

final class WorldWriter {
  private static final String OVERWORLD = "minecraft:overworld";
  private static final String NETHER = "minecraft:the_nether";
  private static final String END = "minecraft:the_end";
  private static final String VOID = "minecraft:the_void";
  private static final int MEASURE_EVERY = 32;

  private record Storages(RegionFileStorage chunks, RegionFileStorage entities) {}

  private final Path folder;
  private final String name;
  private final Consumer<String> warn;
  private final ExecutorService worker;
  private final Map<ResourceKey<Level>, Storages> storages = new HashMap<>();
  private volatile int written;
  private volatile long bytes;
  private volatile boolean failed;

  WorldWriter(Path folder, String name, Consumer<String> warn) {
    this.folder = folder;
    this.name = name;
    this.warn = warn;
    this.worker =
        Executors.newSingleThreadExecutor(
            run -> {
              Thread thread = new Thread(run, "CxVisuals World Download");
              thread.setDaemon(true);
              thread.setPriority(Thread.MIN_PRIORITY + 1);
              return thread;
            });
  }

  Path folder() {
    return folder;
  }

  String name() {
    return name;
  }

  int written() {
    return written;
  }

  long bytes() {
    return bytes;
  }

  void submit(ChunkCopy copy, boolean withEntities) {
    worker.execute(
        () -> {
          try {
            Storages target = storages(copy.dimension());
            target.chunks().write(copy.pos(), copy.chunkTag());
            if (withEntities) {
              target.entities().write(copy.pos(), copy.entityTag());
            }
            if (++written % MEASURE_EVERY == 1) {
              bytes = measure();
            }
          } catch (IOException | RuntimeException e) {
            failed = true;
            warn.accept("Couldn't save a chunk: " + e.getMessage());
          }
        });
  }

  void finish(
      BlockPos spawn,
      ResourceKey<Level> spawnDimension,
      boolean creative,
      boolean empty,
      long seed,
      Consumer<Boolean> done) {
    worker.execute(
        () -> {
          boolean ok = !failed;
          try {
            close();
            writeLevel(spawn, spawnDimension, creative);
            if (empty) {
              writeVoid(seed);
            }
            bytes = measure();
          } catch (IOException | RuntimeException e) {
            ok = false;
            warn.accept("Couldn't finish the world: " + e.getMessage());
          }
          done.accept(ok && written > 0);
        });
    worker.shutdown();
  }

  void discard(Runnable done) {
    worker.execute(
        () -> {
          try {
            close();
            delete(folder);
          } catch (IOException | RuntimeException e) {
            warn.accept("Couldn't remove the download: " + e.getMessage());
          }
          done.run();
        });
    worker.shutdown();
  }

  private Storages storages(ResourceKey<Level> dimension) {
    Storages known = storages.get(dimension);
    if (known == null) {
      Path base = DimensionType.getStorageFolder(dimension, folder);
      known =
          new Storages(
              new RegionFileStorage(
                  new RegionStorageInfo(name, dimension, "chunk"), base.resolve("region"), true),
              new RegionFileStorage(
                  new RegionStorageInfo(name, dimension, "entities"),
                  base.resolve("entities"),
                  true));
      storages.put(dimension, known);
    }
    return known;
  }

  private void close() throws IOException {
    for (Storages target : storages.values()) {
      target.chunks().close();
      target.entities().close();
    }
    storages.clear();
  }

  private void writeLevel(BlockPos spawn, ResourceKey<Level> dimension, boolean creative)
      throws IOException {
    LevelSettings settings =
        new LevelSettings(
            name,
            creative ? GameType.CREATIVE : GameType.SURVIVAL,
            new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false),
            true,
            WorldDataConfiguration.DEFAULT);
    PrimaryLevelData data =
        new PrimaryLevelData(
            settings, PrimaryLevelData.SpecialWorldProperty.NONE, Lifecycle.stable());
    data.setSpawn(new LevelData.RespawnData(GlobalPos.of(dimension, spawn), 0.0F, 0.0F));
    CompoundTag root = new CompoundTag();
    root.put("Data", data.createTag(null));
    Files.createDirectories(folder);
    NbtIo.writeCompressed(root, folder.resolve("level.dat"));
  }

  private void writeVoid(long seed) throws IOException {
    CompoundTag dimensions = new CompoundTag();
    CompoundTag settings = new CompoundTag();
    settings.put("layers", new ListTag());
    settings.putString("biome", VOID);
    settings.put("lakes", ByteTag.valueOf(false));
    settings.put("features", ByteTag.valueOf(false));
    CompoundTag generator = new CompoundTag();
    generator.putString("type", "minecraft:flat");
    generator.put("settings", settings);
    CompoundTag stem = new CompoundTag();
    stem.putString("type", OVERWORLD);
    stem.put("generator", generator);
    dimensions.put(OVERWORLD, stem);
    dimensions.put(NETHER, vanilla(NETHER, "minecraft:nether", "minecraft:multi_noise"));
    dimensions.put(END, vanilla(END, "minecraft:end", "minecraft:the_end"));
    CompoundTag data = new CompoundTag();
    data.putLong("seed", seed);
    data.putBoolean("generate_structures", false);
    data.putBoolean("bonus_chest", false);
    data.put("dimensions", dimensions);
    CompoundTag root = NbtUtils.addCurrentDataVersion(new CompoundTag());
    root.put("data", data);
    Path file = folder.resolve("data").resolve("minecraft").resolve("world_gen_settings.dat");
    Files.createDirectories(file.getParent());
    NbtIo.writeCompressed(root, file);
  }

  private static CompoundTag vanilla(String type, String noise, String biomes) {
    CompoundTag source = new CompoundTag();
    source.putString("type", biomes);
    if (biomes.equals("minecraft:multi_noise")) {
      source.putString("preset", noise);
    }
    CompoundTag generator = new CompoundTag();
    generator.putString("type", "minecraft:noise");
    generator.putString("settings", noise);
    generator.put("biome_source", source);
    CompoundTag stem = new CompoundTag();
    stem.putString("type", type);
    stem.put("generator", generator);
    return stem;
  }

  private long measure() {
    if (!Files.isDirectory(folder)) {
      return 0L;
    }
    try (Stream<Path> files = Files.walk(folder)) {
      return files.filter(Files::isRegularFile).mapToLong(WorldWriter::size).sum();
    } catch (IOException | RuntimeException e) {
      return bytes;
    }
  }

  private static long size(Path file) {
    try {
      return Files.size(file);
    } catch (IOException e) {
      return 0L;
    }
  }

  private static void delete(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (Stream<Path> files = Files.walk(root)) {
      for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
        Files.deleteIfExists(file);
      }
    }
  }
}
