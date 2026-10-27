package me.cxiy.cxvisuals.modules.map;

public final class TileKey {
  public static final int EDGE = 128;
  public static final int EDGE_SHIFT = 7;
  public static final int MAX_LEVEL = 6;
  public static final int REGION_LEVEL = Region.SHIFT - EDGE_SHIFT;
  public static final int SURFACE = 0;
  public static final int CAVE = 1;

  private static final int BITS = 29;
  private static final long COORD = (1L << BITS) - 1L;

  private TileKey() {}

  public static long of(int layer, int level, int x, int z) {
    return ((long) layer << 62) | ((long) level << 58) | ((x & COORD) << BITS) | (z & COORD);
  }

  public static int layer(long key) {
    return (int) (key >>> 62);
  }

  public static int level(long key) {
    return (int) ((key >>> 58) & 0xF);
  }

  public static int x(long key) {
    return (int) ((key << (64 - BITS * 2)) >> (64 - BITS));
  }

  public static int z(long key) {
    return (int) ((key << (64 - BITS)) >> (64 - BITS));
  }

  public static int span(int level) {
    return EDGE << level;
  }

  public static long parent(long key) {
    int level = level(key);
    return of(layer(key), level + 1, x(key) >> 1, z(key) >> 1);
  }
}
