package me.cxiy.cxvisuals.modules.map.share;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.LongSupplier;
import me.cxiy.cxvisuals.modules.map.MapShare;

public final class RemoteQueue {
  public static final int SLICE = 32;
  public static final int CAP = 65_536;

  private record Slice(String dimension, List<MapShare.Chunk> chunks) {}

  private record Entry(RegionKey region, Long key, long stamp) {}

  private final Map<RegionKey, Map<Long, MapShare.Chunk>> regions = new LinkedHashMap<>();
  private int size;
  private boolean focused;
  private int focusX;
  private int focusZ;

  public synchronized void focus(int chunkX, int chunkZ) {
    focused = true;
    focusX = chunkX;
    focusZ = chunkZ;
  }

  public synchronized int size() {
    return size;
  }

  public synchronized void offer(String dimension, List<MapShare.Chunk> chunks) {
    for (MapShare.Chunk chunk : chunks) {
      Map<Long, MapShare.Chunk> region =
          regions.computeIfAbsent(
              RegionKey.ofChunk(dimension, chunk.x(), chunk.z()), key -> new LinkedHashMap<>());
      long chunkKey = RegionKey.chunkKey(chunk.x(), chunk.z());
      MapShare.Chunk have = region.get(chunkKey);
      if (have == null) {
        size++;
      }
      if (have == null || chunk.stamp() > have.stamp()) {
        region.put(chunkKey, chunk);
      }
    }
    if (size > CAP) {
      trim();
    }
  }

  public int drain(
      long budgetNanos, LongSupplier nanos, BiConsumer<String, List<MapShare.Chunk>> sink) {
    long start = nanos.getAsLong();
    int applied = 0;
    while (true) {
      Slice slice = next();
      if (slice == null) {
        return applied;
      }
      sink.accept(slice.dimension(), slice.chunks());
      applied += slice.chunks().size();
      if (nanos.getAsLong() - start >= budgetNanos) {
        return applied;
      }
    }
  }

  private synchronized Slice next() {
    RegionKey pick = null;
    for (RegionKey key : regions.keySet()) {
      if (pick == null) {
        pick = key;
        if (!focused) {
          break;
        }
      } else if (key.distanceSquared(focusX, focusZ) < pick.distanceSquared(focusX, focusZ)) {
        pick = key;
      }
    }
    if (pick == null) {
      return null;
    }
    Map<Long, MapShare.Chunk> region = regions.get(pick);
    List<MapShare.Chunk> chunks = new ArrayList<>(Math.min(SLICE, region.size()));
    Iterator<MapShare.Chunk> each = region.values().iterator();
    while (each.hasNext() && chunks.size() < SLICE) {
      chunks.add(each.next());
      each.remove();
      size--;
    }
    if (region.isEmpty()) {
      regions.remove(pick);
    }
    return new Slice(pick.dimension(), chunks);
  }

  private void trim() {
    List<Entry> all = new ArrayList<>(size);
    for (Map.Entry<RegionKey, Map<Long, MapShare.Chunk>> region : regions.entrySet()) {
      for (Map.Entry<Long, MapShare.Chunk> chunk : region.getValue().entrySet()) {
        all.add(new Entry(region.getKey(), chunk.getKey(), chunk.getValue().stamp()));
      }
    }
    all.sort(Comparator.comparingLong(Entry::stamp));
    int excess = size - CAP;
    for (int i = 0; i < excess; i++) {
      Entry drop = all.get(i);
      Map<Long, MapShare.Chunk> region = regions.get(drop.region());
      region.remove(drop.key());
      if (region.isEmpty()) {
        regions.remove(drop.region());
      }
    }
    size = CAP;
  }
}
