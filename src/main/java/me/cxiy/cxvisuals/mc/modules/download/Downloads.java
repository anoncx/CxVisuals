package me.cxiy.cxvisuals.mc.modules.download;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.modules.download.DownloadModule;
import me.cxiy.cxvisuals.modules.download.Recording;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;

public final class Downloads {
  private static final int NAME_LIMIT = 48;
  private static final int CHEST = 27;
  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm");

  private static DownloadModule module;
  private static Recording recording;
  private static KeyMapping recordKey;
  private static WorldWriter writer;
  private static BlockPos clicked;
  private static final Map<BlockPos, ListTag> ITEMS = new HashMap<>();
  private static Area area;

  private record Area(int minX, int minZ, int maxX, int maxZ) {
    boolean contains(ChunkPos pos) {
      return pos.x() >= minX && pos.x() <= maxX && pos.z() >= minZ && pos.z() <= maxZ;
    }
  }

  private Downloads() {}

  public static DownloadModule create(McKeyBinder keys) {
    module = new DownloadModule(keys);
    recording = module.recording;
    recordKey = keys.mapping(DownloadModule.RECORD_KEY);
    recording.actions(
        new Recording.Actions() {
          @Override
          public void snapshot() {
            Downloads.snapshot(null);
          }

          @Override
          public void start() {
            Downloads.start();
          }

          @Override
          public void finish() {
            Downloads.finish();
          }

          @Override
          public void discard() {
            Downloads.discard();
          }
        });
    ClientChunkEvents.CHUNK_LOAD.register((level, chunk) -> capture(level, chunk));
    ClientChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> capture(level, chunk));
    return module;
  }

  public static DownloadModule module() {
    return module;
  }

  public static void clicked(BlockPos pos) {
    clicked = pos.immutable();
  }

  public static boolean area(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ) {
    if (!module.live() || writer != null) {
      return false;
    }
    snapshot(new Area(minChunkX, minChunkZ, maxChunkX, maxChunkZ));
    return true;
  }

  public static void tick(Minecraft mc) {
    if (module == null) {
      return;
    }
    while (recordKey.consumeClick()) {
      if (recording.recording()) {
        finish();
      } else {
        start();
      }
    }
    if (writer != null) {
      recording.chunks(writer.written());
      recording.bytes(writer.bytes());
    }
    if (writer != null && recording.recording() && (mc.level == null || !module.live())) {
      close(mc);
      return;
    }
    if (recording.recording() && module.containers.get()) {
      watchContainer(mc);
    }
  }

  private static void start() {
    Minecraft mc = Minecraft.getInstance();
    if (!module.live() || mc.level == null || writer != null) {
      return;
    }
    if (!open(mc)) {
      return;
    }
    area = null;
    recording.state(Recording.State.RECORDING);
    everything(mc.level);
  }

  private static void snapshot(Area only) {
    Minecraft mc = Minecraft.getInstance();
    if (!module.live() || mc.level == null || writer != null) {
      return;
    }
    if (!open(mc)) {
      return;
    }
    area = only;
    recording.state(Recording.State.RECORDING);
    everything(mc.level);
    close(mc);
  }

  private static void finish() {
    Minecraft mc = Minecraft.getInstance();
    if (writer == null) {
      return;
    }
    if (mc.level != null) {
      everything(mc.level);
    }
    close(mc);
  }

  private static void discard() {
    if (writer == null) {
      return;
    }
    WorldWriter doomed = writer;
    writer = null;
    ITEMS.clear();
    recording.state(Recording.State.IDLE);
    recording.chunks(0);
    recording.bytes(0L);
    doomed.discard(() -> {});
  }

  private static boolean open(Minecraft mc) {
    Path saves = mc.getLevelSource().getBaseDir();
    String title = Lang.format("{0} {1}", title(mc), LocalDateTime.now().format(STAMP));
    String base = clean(title);
    Path folder = saves.resolve(base);
    for (int i = 2; Files.exists(folder); i++) {
      folder = saves.resolve(base + " (" + i + ")");
    }
    writer = new WorldWriter(folder, title, CxVisualsClient.LOG::warn);
    ITEMS.clear();
    recording.world(title);
    recording.notice("");
    recording.chunks(0);
    recording.bytes(0L);
    return true;
  }

  private static void close(Minecraft mc) {
    WorldWriter done = writer;
    writer = null;
    area = null;
    if (done == null) {
      return;
    }
    recording.state(Recording.State.SAVING);
    BlockPos spawn = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
    ResourceKey<Level> dimension = mc.level != null ? mc.level.dimension() : Level.OVERWORLD;
    long seed = done.name().hashCode();
    done.finish(
        spawn,
        dimension,
        module.mode.get() == DownloadModule.Mode.CREATIVE,
        module.outside.get() == DownloadModule.Outside.EMPTY,
        seed,
        ok ->
            mc.execute(
                () -> {
                  recording.chunks(done.written());
                  recording.bytes(done.bytes());
                  recording.state(ok ? Recording.State.SAVED : Recording.State.FAILED);
                  recording.notice(ok ? "" : Lang.tr("Nothing was loaded to save."));
                }));
    ITEMS.clear();
  }

  private static void everything(ClientLevel level) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) {
      return;
    }
    int radius = mc.options.getEffectiveRenderDistance() + 1;
    int centreX = mc.player.getBlockX() >> 4;
    int centreZ = mc.player.getBlockZ() >> 4;
    for (int dz = -radius; dz <= radius; dz++) {
      for (int dx = -radius; dx <= radius; dx++) {
        LevelChunk chunk =
            level.getChunkSource().getChunk(centreX + dx, centreZ + dz, ChunkStatus.FULL, false);
        if (chunk != null) {
          capture(level, chunk);
        }
      }
    }
  }

  private static void capture(ClientLevel level, LevelChunk chunk) {
    WorldWriter target = writer;
    if (target == null || !recording.recording() || chunk.isEmpty()) {
      return;
    }
    if (area != null && !area.contains(chunk.getPos())) {
      return;
    }
    boolean entities = module.entities.get();
    target.submit(ChunkCopy.take(level, chunk, ITEMS, entities), entities);
  }

  private static void watchContainer(Minecraft mc) {
    if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)
        || clicked == null
        || mc.level == null) {
      return;
    }
    BlockEntity block = mc.level.getBlockEntity(clicked);
    if (!(block instanceof Container)) {
      return;
    }
    AbstractContainerMenu menu = screen.getMenu();
    RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, mc.level.registryAccess());
    ListTag first = new ListTag();
    ListTag second = new ListTag();
    int index = 0;
    for (Slot slot : menu.slots) {
      if (slot.container instanceof Inventory) {
        continue;
      }
      ItemStack stack = slot.getItem();
      int at = index++;
      if (stack.isEmpty()) {
        continue;
      }
      ListTag into = at < CHEST || !doubled(mc.level.getBlockState(clicked)) ? first : second;
      int place = into == second ? at - CHEST : at;
      into.add(
          ItemStackWithSlot.CODEC
              .encodeStart(ops, new ItemStackWithSlot(place, stack))
              .getOrThrow());
    }
    BlockState state = mc.level.getBlockState(clicked);
    if (doubled(state)) {
      BlockPos partner = clicked.relative(ChestBlock.getConnectedDirection(state));
      boolean clickedFirst = state.getValue(ChestBlock.TYPE) == ChestType.RIGHT;
      ITEMS.put(clicked, clickedFirst ? first : second);
      ITEMS.put(partner, clickedFirst ? second : first);
    } else {
      ITEMS.put(clicked, first);
    }
  }

  private static boolean doubled(BlockState state) {
    return state.getBlock() instanceof ChestBlock
        && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE;
  }

  private static String title(Minecraft mc) {
    if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
      return mc.getSingleplayerServer().getWorldData().getLevelName();
    }
    ServerData server = mc.getCurrentServer();
    if (server == null) {
      return Lang.tr("Download");
    }
    return server.name == null || server.name.isBlank() ? server.ip : server.name;
  }

  private static String clean(String name) {
    StringBuilder out = new StringBuilder(Math.min(name.length(), NAME_LIMIT));
    for (int i = 0; i < name.length() && out.length() < NAME_LIMIT; i++) {
      char c = name.charAt(i);
      boolean bad = c < 0x20 || "\\/:*?\"<>|.".indexOf(c) >= 0;
      out.append(bad ? '_' : c);
    }
    String result = out.toString().strip();
    return result.isEmpty() ? "Download" : result;
  }
}
