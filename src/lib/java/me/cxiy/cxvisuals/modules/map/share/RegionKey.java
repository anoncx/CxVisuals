package me.cxiy.cxvisuals.modules.map.share;

import me.cxiy.cxvisuals.modules.map.Region;

record RegionKey(String dimension, int x, int z) {
  private static final int CHUNK_SHIFT = Region.SHIFT - 4;

  static RegionKey ofChunk(String dimension, int chunkX, int chunkZ) {
    return new RegionKey(dimension, chunkX >> CHUNK_SHIFT, chunkZ >> CHUNK_SHIFT);
  }

  static long chunkKey(int chunkX, int chunkZ) {
    return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
  }

  long distanceSquared(int chunkX, int chunkZ) {
    long dx = ((long) x << CHUNK_SHIFT) + Region.CHUNKS / 2 - chunkX;
    long dz = ((long) z << CHUNK_SHIFT) + Region.CHUNKS / 2 - chunkZ;
    return dx * dx + dz * dz;
  }
}
