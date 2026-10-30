package me.cxiy.cxvisuals.mc.modules.map.forecast;

import me.cxiy.cxvisuals.modules.map.Forecast;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

public final class ForecastWorld implements Forecast {
  public enum Kind {
    OVERWORLD,
    NETHER,
    END
  }

  private static final int CELL = 4;
  private static final int COLUMN_CELL = 8;
  private static final int COARSE_STEP = 8;
  private static final int COLUMN_LEVEL_LIMIT = 3;
  private static final int NETHER_TOP = 110;
  private static final int NETHER_LAVA = 32;
  private static final int END_TOP = 110;
  private static final int NONE = -1;
  private static final int PENDING = Integer.MIN_VALUE;

  private final Kind kind;
  private final RandomState random;
  private final NoiseBasedChunkGenerator generator;
  private final BiomeSource biomes;
  private final LevelHeightAccessor accessor;
  private final int seaLevel;
  private final ThreadLocal<Sampling> local = ThreadLocal.withInitial(this::sampling);

  private ForecastWorld(
      Kind kind,
      RandomState random,
      NoiseBasedChunkGenerator generator,
      LevelHeightAccessor accessor,
      int seaLevel) {
    this.kind = kind;
    this.random = random;
    this.generator = generator;
    this.biomes = generator.getBiomeSource();
    this.accessor = accessor;
    this.seaLevel = seaLevel;
  }

  public static ForecastWorld create(HolderLookup.Provider provider, long seed, Kind kind) {
    var stems = WorldPresets.createNormalWorldDimensions(provider).dimensions();
    var key =
        switch (kind) {
          case OVERWORLD -> LevelStem.OVERWORLD;
          case NETHER -> LevelStem.NETHER;
          case END -> LevelStem.END;
        };
    LevelStem stem = stems.get(key);
    if (stem == null || !(stem.generator() instanceof NoiseBasedChunkGenerator noise)) {
      throw new IllegalStateException("no noise generator for " + kind);
    }
    NoiseGeneratorSettings settings = noise.generatorSettings().value();
    RandomState state = RandomState.create(settings, provider.lookupOrThrow(Registries.NOISE), seed);
    LevelHeightAccessor accessor =
        LevelHeightAccessor.create(settings.noiseSettings().minY(), settings.noiseSettings().height());
    return new ForecastWorld(kind, state, noise, accessor, settings.seaLevel());
  }

  public static Kind kind(String dimension) {
    return switch (dimension) {
      case "minecraft:overworld" -> Kind.OVERWORLD;
      case "minecraft:the_nether" -> Kind.NETHER;
      case "minecraft:the_end" -> Kind.END;
      default -> null;
    };
  }

  public static long seed(String text) {
    String value = text.strip();
    try {
      return Long.parseLong(value);
    } catch (NumberFormatException e) {
      return value.hashCode();
    }
  }

  private Sampling sampling() {
    return new Sampling(random.router().preliminarySurfaceLevel(), random.sampler());
  }

  @Override
  public boolean grid(
      int originX, int originZ, int step, int[] rgb, short[] height, byte[] water) {
    int level = Integer.numberOfTrailingZeros(step);
    if (kind != Kind.OVERWORLD && level > COLUMN_LEVEL_LIMIT) {
      return false;
    }
    Sampling sampling = local.get();
    int base = Math.max(step, kind == Kind.OVERWORLD ? CELL : COLUMN_CELL);
    int cell = step >= COARSE_STEP ? base * 2 : base;
    int half = step >> 1;
    int minCellX = Math.floorDiv(originX - step, cell) - 1;
    int minCellZ = Math.floorDiv(originZ - step, cell) - 1;
    int span = (GRID * step) / cell + 4;
    sampling.prepare(span * span);
    boolean smooth = cell > step;
    int flatten = Math.max(1, step / CELL);
    boolean any = false;
    for (int gz = 0; gz < GRID; gz++) {
      int wz = originZ + (gz - 1) * step + half;
      int cellZ = Math.floorDiv(wz, cell) - minCellZ;
      float v = (wz - (cellZ + minCellZ) * cell) / (float) cell - 0.5F;
      int nearZ = v < 0.0F ? -1 : 1;
      float weightZ = Math.abs(v);
      for (int gx = 0; gx < GRID; gx++) {
        int wx = originX + (gx - 1) * step + half;
        int cellX = Math.floorDiv(wx, cell) - minCellX;
        int slot = cellZ * span + cellX;
        ensure(sampling, minCellX, minCellZ, span, cell, cellX, cellZ);
        int color = sampling.colors[slot];
        int h = sampling.heights[slot];
        if (smooth && color >= 0) {
          float u = (wx - (cellX + minCellX) * cell) / (float) cell - 0.5F;
          int nearX = u < 0.0F ? -1 : 1;
          float weightX = Math.abs(u);
          ensure(sampling, minCellX, minCellZ, span, cell, cellX + nearX, cellZ);
          ensure(sampling, minCellX, minCellZ, span, cell, cellX, cellZ + nearZ);
          ensure(sampling, minCellX, minCellZ, span, cell, cellX + nearX, cellZ + nearZ);
          int a = slot;
          int b = slot + nearX;
          int c = slot + nearZ * span;
          int d = c + nearX;
          if (sampling.colors[b] >= 0 && sampling.colors[c] >= 0 && sampling.colors[d] >= 0) {
            float top = sampling.heights[a] + (sampling.heights[b] - sampling.heights[a]) * weightX;
            float bottom = sampling.heights[c] + (sampling.heights[d] - sampling.heights[c]) * weightX;
            h = Math.round(top + (bottom - top) * weightZ);
          }
        }
        if (flatten > 1 && sampling.waters[slot] == 0) {
          h = seaLevel + (h - seaLevel) / flatten;
        }
        int at = gz * GRID + gx;
        rgb[at] = color;
        height[at] = (short) h;
        water[at] = sampling.waters[slot];
        any |= color >= 0;
      }
    }
    return any;
  }

  private void ensure(
      Sampling sampling, int minCellX, int minCellZ, int span, int cell, int cellX, int cellZ) {
    int slot = cellZ * span + cellX;
    if (sampling.colors[slot] == PENDING) {
      int bx = (cellX + minCellX) * cell + (cell >> 1);
      int bz = (cellZ + minCellZ) * cell + (cell >> 1);
      sample(sampling, bx, bz, slot);
    }
  }

  private void sample(Sampling sampling, int x, int z, int slot) {
    switch (kind) {
      case OVERWORLD -> overworld(sampling, x, z, slot);
      case NETHER -> nether(sampling, x, z, slot);
      case END -> end(sampling, x, z, slot);
    }
  }

  private void overworld(Sampling sampling, int x, int z, int slot) {
    int minY = accessor.getMinY();
    int maxY = accessor.getMaxY();
    int h = Mth.clamp(Mth.floor(sampling.surface.compute(new DensityFunction.SinglePointContext(x, 0, z))), minY, maxY);
    Holder<Biome> biome = biomes.getNoiseBiome(x >> 2, Math.max(h, seaLevel) >> 2, z >> 2, sampling.climate);
    int depth = seaLevel - h;
    if (depth > 0) {
      sampling.put(slot, Palette.sea(biome, depth), seaLevel, Math.min(255, depth));
    } else {
      sampling.put(slot, Palette.land(biome, x, z, h), h, 0);
    }
  }

  private void nether(Sampling sampling, int x, int z, int slot) {
    NoiseColumn column = generator.getBaseColumn(x, z, accessor, random);
    int top = Math.min(NETHER_TOP, accessor.getMaxY());
    int floor = NETHER_LAVA;
    for (int y = top; y > NETHER_LAVA; y--) {
      BlockState state = column.getBlock(y);
      if (!state.isAir() && column.getBlock(y + 1).isAir()) {
        floor = y;
        break;
      }
    }
    BlockState state = column.getBlock(floor);
    Holder<Biome> biome = biomes.getNoiseBiome(x >> 2, floor >> 2, z >> 2, sampling.climate);
    if (floor <= NETHER_LAVA || state.is(Blocks.LAVA)) {
      sampling.put(slot, Palette.LAVA, NETHER_LAVA, 0);
    } else {
      sampling.put(slot, Palette.nether(biome, state), floor, 0);
    }
  }

  private void end(Sampling sampling, int x, int z, int slot) {
    NoiseColumn column = generator.getBaseColumn(x, z, accessor, random);
    int top = Math.min(END_TOP, accessor.getMaxY());
    for (int y = top; y > accessor.getMinY(); y--) {
      if (!column.getBlock(y).isAir()) {
        Holder<Biome> biome = biomes.getNoiseBiome(x >> 2, y >> 2, z >> 2, sampling.climate);
        sampling.put(slot, Palette.end(biome, y), y, 0);
        return;
      }
    }
    sampling.put(slot, NONE, 0, 0);
  }

  private static final class Sampling {
    final DensityFunction surface;
    final Climate.Sampler climate;
    int[] colors = new int[0];
    short[] heights = new short[0];
    byte[] waters = new byte[0];

    Sampling(DensityFunction surface, Climate.Sampler climate) {
      this.surface = surface;
      this.climate = climate;
    }

    void prepare(int size) {
      if (colors.length < size) {
        colors = new int[size];
        heights = new short[size];
        waters = new byte[size];
      }
      java.util.Arrays.fill(colors, 0, size, PENDING);
    }

    void put(int slot, int color, int height, int water) {
      colors[slot] = color;
      heights[slot] = (short) height;
      waters[slot] = (byte) water;
    }
  }
}
