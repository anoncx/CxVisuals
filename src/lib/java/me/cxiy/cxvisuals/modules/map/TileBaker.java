package me.cxiy.cxvisuals.modules.map;

import me.cxiy.cxvisuals.modules.map.Relief.Terrain;

public final class TileBaker {
  public interface Source {
    Region region(int layer, int regionX, int regionZ);

    int[] thumbnail(int regionX, int regionZ);

    default int sprite(int block) {
      return 0;
    }
  }

  public static final int PIXELS = TileKey.EDGE * TileKey.EDGE;

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

  public void sprites(long key, int[] ids) {
    java.util.Arrays.fill(ids, 0);
    if (TileKey.level(key) != 0) {
      return;
    }
    int layer = TileKey.layer(key);
    int originX = TileKey.x(key) * TileKey.EDGE;
    int originZ = TileKey.z(key) * TileKey.EDGE;
    Region region = source.region(layer, originX >> Region.SHIFT, originZ >> Region.SHIFT);
    if (region == null) {
      return;
    }
    int baseX = originX & Region.MASK;
    int baseZ = originZ & Region.MASK;
    for (int pz = 0; pz < TileKey.EDGE; pz++) {
      for (int px = 0; px < TileKey.EDGE; px++) {
        int lx = baseX + px;
        int lz = baseZ + pz;
        if (region.seen(lx, lz)) {
          ids[(pz << TileKey.EDGE_SHIFT) | px] = source.sprite(region.block[Region.index(lx, lz)]);
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

  private boolean thumbnails(long key, int[] out) {
    int level = TileKey.level(key);
    int regions = 1 << (level - TileKey.REGION_LEVEL);
    int share = TileKey.EDGE / regions;
    int box = regions;
    int area = box * box;
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
            for (int dz = 0; dz < box; dz++) {
              int row = (pz * box + dz) << TileKey.EDGE_SHIFT;
              for (int dx = 0; dx < box; dx++) {
                int p = thumb[row | (px * box + dx)];
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
