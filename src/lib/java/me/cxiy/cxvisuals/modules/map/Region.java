package me.cxiy.cxvisuals.modules.map;

public final class Region {
  public static final int SHIFT = 9;
  public static final int SIZE = 1 << SHIFT;
  public static final int MASK = SIZE - 1;
  public static final int CHUNKS = SIZE >> 4;
  public static final int COLUMNS = SIZE * SIZE;
  public static final int CHUNK_COLUMNS = 256;

  public final int x;
  public final int z;
  final int[] color;
  final short[] height;
  final byte[] water;
  final char[] block;
  final long[] stamps;
  private volatile int revision;
  private int saved;
  private int explored;

  public Region(int x, int z) {
    this(
        x,
        z,
        new int[COLUMNS],
        new short[COLUMNS],
        new byte[COLUMNS],
        new char[COLUMNS],
        new long[CHUNKS * CHUNKS]);
  }

  Region(int x, int z, int[] color, short[] height, byte[] water, char[] block, long[] stamps) {
    this.x = x;
    this.z = z;
    this.color = color;
    this.height = height;
    this.water = water;
    this.block = block;
    this.stamps = stamps;
    for (long stamp : stamps) {
      if (stamp != 0L) {
        explored++;
      }
    }
  }

  public static long key(int x, int z) {
    return ((long) x << 32) | (z & 0xFFFFFFFFL);
  }

  public static int keyX(long key) {
    return (int) (key >> 32);
  }

  public static int keyZ(long key) {
    return (int) key;
  }

  public long key() {
    return key(x, z);
  }

  public static int index(int localX, int localZ) {
    return (localZ << SHIFT) | localX;
  }

  public int revision() {
    return revision;
  }

  public boolean dirty() {
    return revision != saved;
  }

  public void saved(int at) {
    saved = at;
  }

  public int explored() {
    return explored;
  }

  public boolean empty() {
    return explored == 0;
  }

  public long stamp(int chunkX, int chunkZ) {
    return stamps[(chunkZ << 5) | chunkX];
  }

  public boolean seen(int localX, int localZ) {
    return stamps[((localZ >> 4) << 5) | (localX >> 4)] != 0L;
  }

  public int color(int localX, int localZ) {
    return color[index(localX, localZ)];
  }

  public int height(int localX, int localZ) {
    return height[index(localX, localZ)];
  }

  public int block(int localX, int localZ) {
    return block[index(localX, localZ)];
  }

  public int water(int localX, int localZ) {
    return water[index(localX, localZ)] & 0xFF;
  }

  public void write(int chunkX, int chunkZ, Column column, long stamp) {
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    for (int row = 0; row < 16; row++) {
      int to = index(baseX, baseZ + row);
      int from = row << 4;
      System.arraycopy(column.color, from, color, to, 16);
      System.arraycopy(column.height, from, height, to, 16);
      System.arraycopy(column.water, from, water, to, 16);
      System.arraycopy(column.block, from, block, to, 16);
    }
    int slot = (chunkZ << 5) | chunkX;
    if (stamps[slot] == 0L) {
      explored++;
    }
    stamps[slot] = Math.max(1L, stamp);
    revision++;
  }

  public void read(int chunkX, int chunkZ, Column out) {
    int baseX = chunkX << 4;
    int baseZ = chunkZ << 4;
    for (int row = 0; row < 16; row++) {
      int from = index(baseX, baseZ + row);
      int to = row << 4;
      System.arraycopy(color, from, out.color, to, 16);
      System.arraycopy(height, from, out.height, to, 16);
      System.arraycopy(water, from, out.water, to, 16);
      System.arraycopy(block, from, out.block, to, 16);
    }
  }

  public Region copy() {
    return new Region(
        x, z, color.clone(), height.clone(), water.clone(), block.clone(), stamps.clone());
  }

  public static final class Column {
    public final int[] color = new int[CHUNK_COLUMNS];
    public final short[] height = new short[CHUNK_COLUMNS];
    public final byte[] water = new byte[CHUNK_COLUMNS];
    public final char[] block = new char[CHUNK_COLUMNS];
  }
}
