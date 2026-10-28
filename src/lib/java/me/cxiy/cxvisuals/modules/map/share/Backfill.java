package me.cxiy.cxvisuals.modules.map.share;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import me.cxiy.cxvisuals.modules.map.MapShare;
import me.cxiy.cxvisuals.modules.map.Region;

public final class Backfill {
  private static final long MIX_X = 0x9E3779B97F4A7C15L;
  private static final long MIX_Z = 0xC2B2AE3D27D4EB4FL;
  private static final long MIX_STAMP = 0x165667B19E3779F9L;

  private Backfill() {}

  public static List<Long> order(Collection<Long> regions, int nearChunkX, int nearChunkZ) {
    List<Long> out = new ArrayList<>(regions);
    out.sort(Comparator.comparingLong(key -> distance(key, nearChunkX, nearChunkZ)));
    return out;
  }

  public static long stampHash(List<MapShare.Chunk> chunks) {
    if (chunks.isEmpty()) {
      return 0L;
    }
    long sum = 0L;
    for (MapShare.Chunk chunk : chunks) {
      sum += mix(chunk.x() * MIX_X ^ chunk.z() * MIX_Z ^ chunk.stamp() * MIX_STAMP);
    }
    return sum == 0L ? 1L : sum;
  }

  public static boolean needed(List<MapShare.Chunk> chunks, long uploadedHash) {
    return !chunks.isEmpty() && stampHash(chunks) != uploadedHash;
  }

  private static long distance(long key, int chunkX, int chunkZ) {
    long dx = ((long) Region.keyX(key) << 5) + Region.CHUNKS / 2 - chunkX;
    long dz = ((long) Region.keyZ(key) << 5) + Region.CHUNKS / 2 - chunkZ;
    return dx * dx + dz * dz;
  }

  private static long mix(long value) {
    long z = value + MIX_X;
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    return z ^ (z >>> 31);
  }
}
