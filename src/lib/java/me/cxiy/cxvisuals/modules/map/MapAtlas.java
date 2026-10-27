package me.cxiy.cxvisuals.modules.map;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class MapAtlas {
  public interface Baker {
    void bake(long key, int generation);
  }

  public interface Uploader {
    void upload(int slot, int[] pixels, int[] ids);
  }

  public static final int GRID = 16;
  public static final int SLOTS = GRID * GRID;
  public static final int SIZE = GRID * TileKey.EDGE;
  public static final int EMPTY = 0;
  public static final int MISSING = -1;

  private static final int IN_FLIGHT = 6;
  private static final long REBAKE_NANOS = 350_000_000L;
  private static final int POOL = 24;

  private record Done(long key, int generation, int[] pixels, int[] ids) {}

  private final Map<Long, Integer> slots = new HashMap<>(SLOTS * 2);
  private final long[] keys = new long[SLOTS];
  private final long[] used = new long[SLOTS];
  private final long[] bakedAt = new long[SLOTS];
  private final boolean[] stale = new boolean[SLOTS];
  private final boolean[] taken = new boolean[SLOTS];
  private final Set<Long> flight = new HashSet<>();
  private final Set<Long> dirtyEmpty = new HashSet<>();
  private final ConcurrentLinkedQueue<Done> done = new ConcurrentLinkedQueue<>();
  private final ConcurrentLinkedQueue<int[]> pool = new ConcurrentLinkedQueue<>();
  private Baker baker = (key, generation) -> {};
  private volatile int generation;
  private long frame = 1L;
  private int revision;

  public void baker(Baker next) {
    baker = next;
  }

  public int generation() {
    return generation;
  }

  public int revision() {
    return revision;
  }

  public void frame() {
    frame++;
  }

  public int slot(long key) {
    Integer slot = slots.get(key);
    if (slot == null) {
      request(key);
      return MISSING;
    }
    int index = slot;
    if (index == EMPTY) {
      if (dirtyEmpty.remove(key)) {
        request(key);
      }
      return EMPTY;
    }
    used[index] = frame;
    if (stale[index] && System.nanoTime() - bakedAt[index] > REBAKE_NANOS && request(key)) {
      stale[index] = false;
    }
    return index;
  }

  public void invalidate(long key) {
    Integer slot = slots.get(key);
    if (slot == null) {
      return;
    }
    if (slot == EMPTY) {
      dirtyEmpty.add(key);
    } else {
      stale[slot] = true;
    }
  }

  public void clear() {
    generation++;
    slots.clear();
    flight.clear();
    dirtyEmpty.clear();
    done.clear();
    for (int i = 1; i < SLOTS; i++) {
      taken[i] = false;
      used[i] = 0L;
      stale[i] = false;
    }
    revision++;
  }

  public int[] buffer() {
    int[] reused = pool.poll();
    return reused != null ? reused : new int[TileBaker.PIXELS];
  }

  public void finish(long key, int forGeneration, int[] pixels, int[] ids) {
    done.add(new Done(key, forGeneration, pixels, ids));
  }

  public int drain(Uploader uploader, int max) {
    int uploaded = 0;
    Done next;
    while (uploaded < max && (next = done.poll()) != null) {
      if (next.generation != generation) {
        recycle(next.pixels);
        recycle(next.ids);
        continue;
      }
      flight.remove(next.key);
      Integer existing = slots.get(next.key);
      if (next.pixels == null) {
        if (existing != null && existing != EMPTY) {
          taken[existing] = false;
          used[existing] = 0L;
        }
        slots.put(next.key, EMPTY);
        continue;
      }
      int slot = existing != null && existing != EMPTY ? existing : evict();
      if (slot == MISSING) {
        recycle(next.pixels);
        recycle(next.ids);
        continue;
      }
      keys[slot] = next.key;
      taken[slot] = true;
      used[slot] = frame;
      bakedAt[slot] = System.nanoTime();
      slots.put(next.key, slot);
      uploader.upload(slot, next.pixels, next.ids);
      recycle(next.pixels);
      recycle(next.ids);
      uploaded++;
      revision++;
    }
    return uploaded;
  }

  public boolean busy() {
    return !flight.isEmpty() || !done.isEmpty();
  }

  public static float u(int slot) {
    return (slot % GRID) / (float) GRID;
  }

  public static float v(int slot) {
    return (slot / GRID) / (float) GRID;
  }

  private boolean request(long key) {
    if (flight.size() >= IN_FLIGHT || !flight.add(key)) {
      return false;
    }
    baker.bake(key, generation);
    return true;
  }

  private int evict() {
    int best = MISSING;
    long oldest = Long.MAX_VALUE;
    for (int i = 1; i < SLOTS; i++) {
      if (!taken[i]) {
        return i;
      }
      if (used[i] < oldest && used[i] < frame) {
        oldest = used[i];
        best = i;
      }
    }
    if (best != MISSING) {
      slots.remove(keys[best]);
      stale[best] = false;
    }
    return best;
  }

  private void recycle(int[] pixels) {
    if (pixels != null && pool.size() < POOL) {
      pool.add(pixels);
    }
  }
}
