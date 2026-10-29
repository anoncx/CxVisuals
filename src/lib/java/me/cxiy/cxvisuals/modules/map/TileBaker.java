package me.cxiy.cxvisuals.modules.map;

import java.util.Arrays;
import me.cxiy.cxvisuals.modules.map.Relief.Terrain;

public final class TileBaker {
  public interface Source {
    Region region(int layer, int regionX, int regionZ);

    int[] thumbnail(int regionX, int regionZ);

    default int average(int regionX, int regionZ) {
      int[] thumb = thumbnail(regionX, regionZ);
      return thumb == null ? 0 : TileBaker.average(thumb);
    }

    default boolean occupied(int level, int x, int z) {
      return true;
    }
  }

  public static final int PIXELS = TileKey.EDGE * TileKey.EDGE;
  public static final int WIDE_LEVEL = Region.SHIFT;

  private static final int SAMPLES = 4;

  private static final int MARGIN = 8;
  private static final int WIDE = TileKey.EDGE + MARGIN * 2;
  private static final int RADIUS = 4;

  private final Source source;
  private final float[] cover = new float[WIDE * WIDE];
  private final float[] blur = new float[WIDE * WIDE];
  private Region cached;
  private int cachedLayer = -1;
  private int cachedX = Integer.MIN_VALUE;
  private int cachedZ = Integer.MIN_VALUE;

  public TileBaker(Source source) {
    this.source = source;
  }

  public static final int HEIGHT_OFFSET = 64;
  public static final int HEIGHT_MAX = 511;
  public static final int WATER_BIT = 1 << 25;

  public static final int DEPTH_SHIFT = 26;
  public static final int DEPTH_MAX = 63;
  public static final int GROUND_BIT = 1 << 25;
  public static final int GROUND_WET = 1 << 26;

  public void details(long key, int[] ids, int[] grounds) {
    Arrays.fill(ids, 0);
    Arrays.fill(grounds, 0);
    int level = TileKey.level(key);
    if (level > TileKey.REGION_LEVEL) {
      return;
    }
    int step = 1 << level;
    int layer = TileKey.layer(key);
    int originX = TileKey.x(key) * TileKey.span(level);
    int originZ = TileKey.z(key) * TileKey.span(level);
    Region region = source.region(layer, originX >> Region.SHIFT, originZ >> Region.SHIFT);
    if (region == null) {
      return;
    }
    int baseX = originX & Region.MASK;
    int baseZ = originZ & Region.MASK;
    for (int pz = 0; pz < TileKey.EDGE; pz++) {
      for (int px = 0; px < TileKey.EDGE; px++) {
        int best = -1;
        int top = Integer.MIN_VALUE;
        for (int dz = 0; dz < step; dz++) {
          for (int dx = 0; dx < step; dx++) {
            int lx = baseX + px * step + dx;
            int lz = baseZ + pz * step + dz;
            if (region.seen(lx, lz)) {
              int at = Region.index(lx, lz);
              if (region.height[at] > top) {
                top = region.height[at];
                best = at;
              }
            }
          }
        }
        if (best >= 0) {
          int block = Math.min(region.block[best], BlockPalette.TABLE - 1);
          int height = Math.max(0, Math.min(HEIGHT_MAX, top + HEIGHT_OFFSET));
          int water = region.water[best] != 0 ? WATER_BIT : 0;
          int thick = Math.min(DEPTH_MAX, region.depth[best] & DEPTH_MAX);
          int at = (pz << TileKey.EDGE_SHIFT) | px;
          ids[at] = block | (height << 16) | water | (thick << DEPTH_SHIFT);
          if (thick > 0 && region.ground[best] != BlockPalette.NONE) {
            int under = Math.min(region.ground[best], BlockPalette.TABLE - 1);
            int floor = Math.max(0, Math.min(HEIGHT_MAX, region.floor[best] + HEIGHT_OFFSET));
            grounds[at] =
                under
                    | (floor << 16)
                    | GROUND_BIT
                    | ((region.depth[best] & 0x80) != 0 ? GROUND_WET : 0);
          }
        }
      }
    }
  }

  public boolean bake(long key, Terrain terrain, int[] out) {
    cachedLayer = -1;
    int level = TileKey.level(key);
    boolean any = level <= TileKey.REGION_LEVEL ? columns(key, terrain, out) : thumbnails(key, out);
    if (any) {
      bleed(out);
      soften(key, out);
    }
    return any;
  }

  private void soften(long key, int[] out) {
    int level = TileKey.level(key);
    int layer = TileKey.layer(key);
    int step = 1 << level;
    int originX = TileKey.x(key) * TileKey.span(level);
    int originZ = TileKey.z(key) * TileKey.span(level);
    for (int z = 0; z < WIDE; z++) {
      for (int x = 0; x < WIDE; x++) {
        int px = x - MARGIN;
        int pz = z - MARGIN;
        float value;
        if (px >= 0 && pz >= 0 && px < TileKey.EDGE && pz < TileKey.EDGE) {
          value = (out[(pz << TileKey.EDGE_SHIFT) | px] >>> 24) / 255.0F;
        } else {
          value =
              covered(
                  layer,
                  level,
                  originX + px * step + (step >> 1),
                  originZ + pz * step + (step >> 1));
        }
        cover[z * WIDE + x] = value;
      }
    }
    box(cover, blur, 1);
    box(blur, cover, WIDE);
    box(cover, blur, 1);
    box(blur, cover, WIDE);
    for (int pz = 0; pz < TileKey.EDGE; pz++) {
      for (int px = 0; px < TileKey.EDGE; px++) {
        int at = (pz << TileKey.EDGE_SHIFT) | px;
        float soft = cover[(pz + MARGIN) * WIDE + px + MARGIN];
        int alpha = Math.max(0, Math.min(255, Math.round(soft * 255.0F)));
        out[at] = (alpha << 24) | (out[at] & 0xFFFFFF);
      }
    }
  }

  private float covered(int layer, int level, int wx, int wz) {
    if (level <= TileKey.REGION_LEVEL) {
      Region region = region(layer, wx >> Region.SHIFT, wz >> Region.SHIFT);
      return region != null && region.seen(wx & Region.MASK, wz & Region.MASK) ? 1.0F : 0.0F;
    }
    if (level > WIDE_LEVEL) {
      return (source.average(wx >> Region.SHIFT, wz >> Region.SHIFT) >>> 24) / 255.0F;
    }
    int[] thumb = source.thumbnail(wx >> Region.SHIFT, wz >> Region.SHIFT);
    if (thumb == null) {
      return 0.0F;
    }
    int shift = Region.SHIFT - TileKey.EDGE_SHIFT;
    int tx = (wx & Region.MASK) >> shift;
    int tz = (wz & Region.MASK) >> shift;
    return (thumb[(tz << TileKey.EDGE_SHIFT) | tx] >>> 24) / 255.0F;
  }

  private static void box(float[] from, float[] to, int stride) {
    int across = stride == 1 ? WIDE : 1;
    float norm = 1.0F / (RADIUS * 2 + 1);
    for (int line = 0; line < WIDE; line++) {
      int base = line * across;
      float sum = 0.0F;
      for (int i = -RADIUS; i <= RADIUS; i++) {
        sum += from[base + clampIndex(i) * stride];
      }
      for (int i = 0; i < WIDE; i++) {
        to[base + i * stride] = sum * norm;
        sum +=
            from[base + clampIndex(i + RADIUS + 1) * stride]
                - from[base + clampIndex(i - RADIUS) * stride];
      }
    }
  }

  private static int clampIndex(int i) {
    return i < 0 ? 0 : Math.min(i, WIDE - 1);
  }

  static void bleed(int[] out) {
    int edge = TileKey.EDGE;
    for (int pass = 0; pass < 2; pass++) {
      for (int at = 0; at < PIXELS; at++) {
        if (out[at] != 0) {
          continue;
        }
        int x = at & (edge - 1);
        int fill =
            x > 0 && (out[at - 1] & 0xFFFFFF) != 0
                ? out[at - 1]
                : x < edge - 1 && (out[at + 1] & 0xFFFFFF) != 0
                    ? out[at + 1]
                    : at >= edge && (out[at - edge] & 0xFFFFFF) != 0
                        ? out[at - edge]
                        : at < PIXELS - edge ? out[at + edge] : 0;
        out[at] = fill & 0xFFFFFF;
      }
    }
  }

  public boolean thumbnail(Region region, Terrain terrain, int[] out) {
    cachedLayer = -1;
    return columns(
        TileKey.of(TileKey.SURFACE, TileKey.REGION_LEVEL, region.x, region.z), terrain, out);
  }

  private boolean columns(long key, Terrain terrain, int[] out) {
    int layer = TileKey.layer(key);
    int step = 1 << TileKey.level(key);
    int samples = step * step;
    int originX = TileKey.x(key) * TileKey.span(TileKey.level(key));
    int originZ = TileKey.z(key) * TileKey.span(TileKey.level(key));
    boolean any = false;
    for (int pz = 0; pz < TileKey.EDGE; pz++) {
      for (int px = 0; px < TileKey.EDGE; px++) {
        int r = 0;
        int g = 0;
        int b = 0;
        int seen = 0;
        int baseX = originX + px * step;
        int baseZ = originZ + pz * step;
        for (int dz = 0; dz < step; dz++) {
          for (int dx = 0; dx < step; dx++) {
            int rgb = shaded(layer, terrain, baseX + dx, baseZ + dz);
            if (rgb >= 0) {
              r += (rgb >> 16) & 0xFF;
              g += (rgb >> 8) & 0xFF;
              b += rgb & 0xFF;
              seen++;
            }
          }
        }
        int at = (pz << TileKey.EDGE_SHIFT) | px;
        if (seen == 0) {
          out[at] = 0;
          continue;
        }
        any = true;
        int rgb = ((r / seen) << 16) | ((g / seen) << 8) | (b / seen);
        out[at] = Relief.pixel(rgb, seen * 255 / samples);
      }
    }
    return any;
  }

  private int shaded(int layer, Terrain terrain, int wx, int wz) {
    Region region = region(layer, wx >> Region.SHIFT, wz >> Region.SHIFT);
    if (region == null) {
      return -1;
    }
    int lx = wx & Region.MASK;
    int lz = wz & Region.MASK;
    if (!region.seen(lx, lz)) {
      return -1;
    }
    int at = Region.index(lx, lz);
    int h = region.height[at];
    int water = region.water[at] & 0xFF;
    int west = h;
    int north = h;
    int shore = water;
    Region westRegion = lx > 0 ? region : region(layer, (wx - 1) >> Region.SHIFT, region.z);
    if (westRegion != null) {
      int wl = (wx - 1) & Region.MASK;
      if (westRegion.seen(wl, lz)) {
        int i = Region.index(wl, lz);
        west = westRegion.height[i];
        shore = Math.min(shore, westRegion.water[i] & 0xFF);
      }
    }
    Region northRegion = lz > 0 ? region : region(layer, region.x, (wz - 1) >> Region.SHIFT);
    if (northRegion != null) {
      int nl = (wz - 1) & Region.MASK;
      if (northRegion.seen(lx, nl)) {
        int i = Region.index(lx, nl);
        north = northRegion.height[i];
        shore = Math.min(shore, northRegion.water[i] & 0xFF);
      }
    }
    float light = Relief.light(terrain, h, west, north, water, shore);
    if (terrain != Terrain.FLAT && water == 0) {
      light *= Relief.grain(wx, wz);
    }
    return Relief.rgb(region.color[at], light);
  }

  private Region region(int layer, int rx, int rz) {
    if (layer != cachedLayer || rx != cachedX || rz != cachedZ) {
      cachedLayer = layer;
      cachedX = rx;
      cachedZ = rz;
      cached = source.region(layer, rx, rz);
    }
    return cached;
  }

  public static int average(int[] thumb) {
    long a = 0L;
    long r = 0L;
    long g = 0L;
    long b = 0L;
    for (int p : thumb) {
      int alpha = p >>> 24;
      a += alpha;
      r += (long) (p & 0xFF) * alpha;
      g += (long) ((p >> 8) & 0xFF) * alpha;
      b += (long) ((p >> 16) & 0xFF) * alpha;
    }
    if (a == 0L) {
      return 0;
    }
    return ((int) (a / thumb.length) << 24)
        | ((int) (b / a) << 16)
        | ((int) (g / a) << 8)
        | (int) (r / a);
  }

  private boolean wide(long key, int[] out) {
    int level = TileKey.level(key);
    int reach = 1 << (level - WIDE_LEVEL);
    int taken = Math.min(reach, SAMPLES);
    int stride = reach / taken;
    int offset = stride >> 1;
    int regions = 1 << (level - TileKey.REGION_LEVEL);
    int firstX = TileKey.x(key) * regions;
    int firstZ = TileKey.z(key) * regions;
    int area = taken * taken;
    boolean any = false;
    for (int pz = 0; pz < TileKey.EDGE; pz++) {
      for (int px = 0; px < TileKey.EDGE; px++) {
        int a = 0;
        int r = 0;
        int g = 0;
        int b = 0;
        for (int dz = 0; dz < taken; dz++) {
          int rz = firstZ + pz * reach + dz * stride + offset;
          for (int dx = 0; dx < taken; dx++) {
            int p = source.average(firstX + px * reach + dx * stride + offset, rz);
            int alpha = p >>> 24;
            a += alpha;
            r += (p & 0xFF) * alpha;
            g += ((p >> 8) & 0xFF) * alpha;
            b += ((p >> 16) & 0xFF) * alpha;
          }
        }
        int at = (pz << TileKey.EDGE_SHIFT) | px;
        if (a == 0) {
          out[at] = 0;
          continue;
        }
        any = true;
        out[at] = ((a / area) << 24) | ((b / a) << 16) | ((g / a) << 8) | (r / a);
      }
    }
    return any;
  }

  private boolean thumbnails(long key, int[] out) {
    int level = TileKey.level(key);
    if (!source.occupied(level, TileKey.x(key), TileKey.z(key))) {
      return false;
    }
    if (level > WIDE_LEVEL) {
      return wide(key, out);
    }
    int regions = 1 << (level - TileKey.REGION_LEVEL);
    int share = TileKey.EDGE / regions;
    int box = regions;
    int taken = Math.min(box, SAMPLES);
    int stride = box / taken;
    int offset = stride >> 1;
    int area = taken * taken;
    int firstX = TileKey.x(key) * regions;
    int firstZ = TileKey.z(key) * regions;
    boolean any = false;
    for (int rz = 0; rz < regions; rz++) {
      for (int rx = 0; rx < regions; rx++) {
        int[] thumb = source.thumbnail(firstX + rx, firstZ + rz);
        int offsetX = rx * share;
        int offsetZ = rz * share;
        for (int pz = 0; pz < share; pz++) {
          for (int px = 0; px < share; px++) {
            int at = ((offsetZ + pz) << TileKey.EDGE_SHIFT) | (offsetX + px);
            if (thumb == null) {
              out[at] = 0;
              continue;
            }
            int a = 0;
            int r = 0;
            int g = 0;
            int b = 0;
            for (int dz = 0; dz < taken; dz++) {
              int row = (pz * box + dz * stride + offset) << TileKey.EDGE_SHIFT;
              for (int dx = 0; dx < taken; dx++) {
                int p = thumb[row | (px * box + dx * stride + offset)];
                int alpha = p >>> 24;
                a += alpha;
                r += (p & 0xFF) * alpha;
                g += ((p >> 8) & 0xFF) * alpha;
                b += ((p >> 16) & 0xFF) * alpha;
              }
            }
            if (a == 0) {
              out[at] = 0;
              continue;
            }
            any = true;
            out[at] = ((a / area) << 24) | ((b / a) << 16) | ((g / a) << 8) | (r / a);
          }
        }
      }
    }
    return any;
  }
}
