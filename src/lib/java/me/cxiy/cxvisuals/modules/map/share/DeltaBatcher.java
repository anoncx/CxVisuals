package me.cxiy.cxvisuals.modules.map.share;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

public final class DeltaBatcher {
  public record Batch(String dimension, int regionX, int regionZ, List<long[]> chunks) {}

  private final LongSupplier clock;
  private final long minGapMillis;
  private final int maxChunks;
  private final Map<RegionKey, Map<Long, long[]>> dirty = new HashMap<>();
  private int pending;
  private long lastTake = Long.MIN_VALUE / 4;

  public DeltaBatcher(LongSupplier clock, long minGapMillis, int maxChunks) {
    this.clock = clock;
    this.minGapMillis = minGapMillis;
    this.maxChunks = maxChunks;
  }

  public synchronized void mark(String dimension, int chunkX, int chunkZ, long stamp) {
    Map<Long, long[]> region =
        dirty.computeIfAbsent(
            RegionKey.ofChunk(dimension, chunkX, chunkZ), key -> new LinkedHashMap<>());
    long chunkKey = RegionKey.chunkKey(chunkX, chunkZ);
    long[] have = region.get(chunkKey);
    if (have == null) {
      region.put(chunkKey, new long[] {chunkX, chunkZ, stamp});
      pending++;
    } else if (stamp > have[2]) {
      have[2] = stamp;
    }
  }

  public synchronized int pending() {
    return pending;
  }

  public synchronized List<Batch> take(int playerChunkX, int playerChunkZ) {
    long now = clock.getAsLong();
    if (pending == 0 || now - lastTake < minGapMillis) {
      return List.of();
    }
    List<RegionKey> order = new ArrayList<>(dirty.keySet());
    order.sort(Comparator.comparingLong(key -> key.distanceSquared(playerChunkX, playerChunkZ)));
    List<Batch> out = new ArrayList<>();
    int budget = maxChunks;
    for (RegionKey key : order) {
      if (budget <= 0) {
        break;
      }
      Map<Long, long[]> region = dirty.get(key);
      List<long[]> chunks = new ArrayList<>(Math.min(budget, region.size()));
      Iterator<long[]> each = region.values().iterator();
      while (each.hasNext() && budget > 0) {
        chunks.add(each.next());
        each.remove();
        budget--;
        pending--;
      }
      if (region.isEmpty()) {
        dirty.remove(key);
      }
      out.add(new Batch(key.dimension(), key.x(), key.z(), chunks));
    }
    lastTake = now;
    return out;
  }

  public synchronized void requeue(List<Batch> batches) {
    for (Batch batch : batches) {
      for (long[] chunk : batch.chunks()) {
        mark(batch.dimension(), (int) chunk[0], (int) chunk[1], chunk[2]);
      }
    }
  }
}
