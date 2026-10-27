package me.cxiy.cxvisuals.mc.modules.map;

import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import me.cxiy.cxvisuals.modules.map.BlockPalette;
import me.cxiy.cxvisuals.modules.map.MapWorld;
import me.cxiy.cxvisuals.modules.map.Region;
import me.cxiy.cxvisuals.modules.map.TileKey;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;

final class Cartographer {
  private static final long BUDGET_NANOS = 1_600_000L;
  private static final int REFRESH_TICKS = 10;
  private static final int CAVE_RADIUS = 5;
  private static final int CAVE_SHIFT = 3;
  private static final int SETTLE_TICKS = 16;
  private static final int SKIP_LIMIT = 48;
  private static final int WATER_LIMIT = 96;
  private static final int RUN_LIMIT = 24;
  private static final int GAP_LIMIT = 48;
  private static final int CAVE_SEARCH = 20;
  private static final int CEILING_START = 118;
  private static final int CEILING_SEARCH = 110;
  private static final float WATER_MIN = 0.42F;
  private static final float WATER_STEP = 0.06F;
  private static final float WATER_MAX = 0.9F;
  private static final float ROCK = 0.34F;
  private static final int VOID = 0x0B0B0D;

  private final MapWorld world;
  private final Swatches swatches = new Swatches();
  private final LongLinkedOpenHashSet surface = new LongLinkedOpenHashSet();
  private final LongLinkedOpenHashSet caves = new LongLinkedOpenHashSet();
  private final LongOpenHashSet dirty = new LongOpenHashSet();
  private final LongOpenHashSet waiting = new LongOpenHashSet();
  private final Region.Column column = new Region.Column();
  private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
  private int ticks;
  private int caveBand = Integer.MIN_VALUE;
  private boolean underground;
  private int undergroundFor;

  Cartographer(MapWorld world) {
    this.world = world;
  }

  boolean sprites() {
    BlockPalette palette = world.palette();
    boolean moved = swatches.reloaded();
    if (moved) {
      swatches.of(net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
      palette.forgetSprites();
      swatches.reset();
    }
    swatches.resolve(palette);
    return swatches.takeLate() || moved;
  }

  boolean underground() {
    return underground;
  }

  void loaded(LevelChunk chunk) {
    surface.add(chunk.getPos().pack());
  }

  void changed(BlockPos pos) {
    dirty.add(ChunkPos.pack(pos));
  }

  void reset() {
    surface.clear();
    caves.clear();
    dirty.clear();
    waiting.clear();
    caveBand = Integer.MIN_VALUE;
    underground = false;
    undergroundFor = 0;
  }

  void everything(ClientLevel level, LocalPlayer player, int radius) {
    int centreX = player.getBlockX() >> 4;
    int centreZ = player.getBlockZ() >> 4;
    for (int ring = 0; ring <= radius; ring++) {
      for (int dz = -ring; dz <= ring; dz++) {
        for (int dx = -ring; dx <= ring; dx++) {
          int x = centreX + dx;
          int z = centreZ + dz;
          if (Math.max(Math.abs(dx), Math.abs(dz)) == ring
              && level.getChunkSource().getChunk(x, z, ChunkStatus.FULL, false) != null) {
            surface.add(ChunkPos.pack(x, z));
          }
        }
      }
    }
  }

  void tick(ClientLevel level, LocalPlayer player, boolean cavesWanted, long now) {
    if (++ticks % REFRESH_TICKS == 0) {
      LongIterator moved = dirty.iterator();
      while (moved.hasNext()) {
        long chunk = moved.nextLong();
        surface.add(chunk);
        if (underground) {
          caves.add(chunk);
        }
      }
      dirty.clear();
      surface.addAll(waiting);
      waiting.clear();
    }
    watchCaves(level, player, cavesWanted);
    long start = System.nanoTime();
    while (System.nanoTime() - start < BUDGET_NANOS) {
      if (!caves.isEmpty()) {
        scan(level, caves.removeFirstLong(), TileKey.CAVE, now);
      } else if (!surface.isEmpty()) {
        scan(level, surface.removeFirstLong(), TileKey.SURFACE, now);
      } else {
        break;
      }
    }
  }

  private void watchCaves(ClientLevel level, LocalPlayer player, boolean wanted) {
    BlockPos eye = BlockPos.containing(player.getEyePosition());
    boolean below =
        wanted
            && (level.dimensionType().hasCeiling()
                || (level.getBrightness(LightLayer.SKY, eye) == 0
                    && level.getHeight(Heightmap.Types.WORLD_SURFACE, eye.getX(), eye.getZ())
                        > eye.getY() + 2));
    undergroundFor =
        below ? Math.min(undergroundFor + 1, SETTLE_TICKS) : Math.max(undergroundFor - 2, 0);
    boolean next = undergroundFor >= SETTLE_TICKS || (underground && undergroundFor > 0);
    if (!next) {
      if (underground) {
        underground = false;
        caveBand = Integer.MIN_VALUE;
        caves.clear();
        world.clearCaves();
      }
      return;
    }
    underground = true;
    int band = player.getBlockY() >> CAVE_SHIFT;
    int chunkX = player.getBlockX() >> 4;
    int chunkZ = player.getBlockZ() >> 4;
    if (band != caveBand) {
      caveBand = band;
      caves.clear();
      world.clearCaves();
      for (int ring = 0; ring <= CAVE_RADIUS; ring++) {
        for (int dz = -ring; dz <= ring; dz++) {
          for (int dx = -ring; dx <= ring; dx++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) == ring) {
              caves.add(ChunkPos.pack(chunkX + dx, chunkZ + dz));
            }
          }
        }
      }
      return;
    }
    if (ticks % REFRESH_TICKS == 0) {
      for (int dz = -CAVE_RADIUS; dz <= CAVE_RADIUS; dz++) {
        for (int dx = -CAVE_RADIUS; dx <= CAVE_RADIUS; dx++) {
          if (!world.seen(TileKey.CAVE, chunkX + dx, chunkZ + dz)) {
            caves.add(ChunkPos.pack(chunkX + dx, chunkZ + dz));
          }
        }
      }
    }
  }

  private void scan(ClientLevel level, long packed, int layer, long now) {
    int chunkX = ChunkPos.getX(packed);
    int chunkZ = ChunkPos.getZ(packed);
    LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    if (chunk == null) {
      return;
    }
    Region region = world.writable(layer, chunkX >> 5, chunkZ >> 5);
    if (region == null) {
      if (layer == TileKey.SURFACE) {
        waiting.add(packed);
      }
      return;
    }
    boolean ceiling = level.dimensionType().hasCeiling();
    int minY = level.getMinY();
    int start =
        layer == TileKey.CAVE
            ? (caveBand << CAVE_SHIFT) + (1 << CAVE_SHIFT)
            : ceiling ? Math.min(CEILING_START, level.getMaxY()) : 0;
    for (int lz = 0; lz < 16; lz++) {
      for (int lx = 0; lx < 16; lx++) {
        int x = (chunkX << 4) + lx;
        int z = (chunkZ << 4) + lz;
        int i = (lz << 4) | lx;
        if (layer == TileKey.CAVE || ceiling) {
          int limit = layer == TileKey.CAVE ? CAVE_SEARCH : CEILING_SEARCH;
          column(level, chunk, x, z, start, minY, limit, i, true);
        } else {
          int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, lx, lz);
          column(level, chunk, x, z, top, minY, 0, i, false);
        }
      }
    }
    region.write(chunkX & 31, chunkZ & 31, column, now);
    world.written(layer, chunkX, chunkZ);
  }

  private void column(
      ClientLevel level,
      LevelChunk chunk,
      int x,
      int z,
      int start,
      int minY,
      int search,
      int i,
      boolean buried) {
    column.depth[i] = 0;
    column.floor[i] = 0;
    column.ground[i] = (char) BlockPalette.NONE;
    int y = start;
    if (buried) {
      int air = -1;
      for (int step = 0; step < search && y > minY; step++, y--) {
        if (chunk.getBlockState(cursor.set(x, y, z)).isAir()) {
          air = y;
          break;
        }
      }
      if (air < 0) {
        BlockState rock = chunk.getBlockState(cursor.set(x, start, z));
        Swatches.Swatch swatch = swatches.of(rock);
        column.color[i] = darken(swatches.color(swatch, rock, level, cursor), ROCK);
        column.block[i] = (char) swatches.block(rock, null, world.palette());
        column.height[i] = (short) start;
        column.water[i] = 0;
        return;
      }
      y = air;
    }
    BlockState state = null;
    Swatches.Swatch swatch = null;
    for (int step = 0; y >= minY; step++, y--) {
      state = chunk.getBlockState(cursor.set(x, y, z));
      if (state.isAir()) {
        continue;
      }
      swatch = swatches.of(state);
      if (!swatch.skip() || step > SKIP_LIMIT + search) {
        break;
      }
    }
    if (state == null || swatch == null || y < minY) {
      column.color[i] = VOID;
      column.block[i] = (char) BlockPalette.NONE;
      column.height[i] = (short) minY;
      column.water[i] = 0;
      return;
    }
    int surfaceY = y;
    if (!state.getFluidState().is(FluidTags.WATER)) {
      column.color[i] = swatches.color(swatch, state, level, cursor);
      BlockState below = chunk.getBlockState(cursor.set(x, surfaceY - 1, z));
      column.block[i] = (char) swatches.block(state, below, world.palette());
      column.height[i] = (short) surfaceY;
      column.water[i] = 0;
      overhang(chunk, x, z, surfaceY, minY, i);
      return;
    }
    int fluidColor = swatches.color(swatch, state, level, cursor);
    int depth = 0;
    BlockState floor = state;
    while (depth < WATER_LIMIT && y > minY) {
      y--;
      depth++;
      floor = chunk.getBlockState(cursor.set(x, y, z));
      if (floor.getFluidState().isEmpty()) {
        break;
      }
    }
    Swatches.Swatch floorSwatch = swatches.of(floor);
    int floorColor = swatches.color(floorSwatch, floor, level, cursor);
    float cover = Math.min(WATER_MAX, WATER_MIN + depth * WATER_STEP);
    column.color[i] = Swatches.mix(floorColor, fluidColor, cover);
    column.block[i] = (char) swatches.block(state, null, world.palette());
    column.height[i] = (short) surfaceY;
    column.water[i] = (byte) Math.min(255, depth);
  }

  private void overhang(LevelChunk chunk, int x, int z, int top, int minY, int i) {
    int y = top - 1;
    int run = 1;
    while (run < RUN_LIMIT && y > minY && !open(chunk.getBlockState(cursor.set(x, y, z)))) {
      run++;
      y--;
    }
    if (run >= RUN_LIMIT || y <= minY) {
      return;
    }
    for (int gap = 0; gap < GAP_LIMIT && y > minY; gap++, y--) {
      BlockState state = chunk.getBlockState(cursor.set(x, y, z));
      if (!open(state)) {
        BlockState below = chunk.getBlockState(cursor.set(x, y - 1, z));
        column.depth[i] = (byte) run;
        column.floor[i] = (short) y;
        column.ground[i] = (char) swatches.block(state, below, world.palette());
        return;
      }
    }
  }

  private boolean open(BlockState state) {
    return state.isAir() || (state.getFluidState().isEmpty() && swatches.of(state).skip());
  }

  private static int darken(int rgb, float amount) {
    return Swatches.mix(rgb, 0x000000, 1.0F - amount);
  }
}
