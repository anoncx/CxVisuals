package me.cxiy.cxvisuals.modules.map;

import java.util.LinkedHashMap;
import java.util.Map;

final class ThumbCache {
  static final int NONE = Integer.MIN_VALUE;
  static final int DISK = -1;

  private record Entry(int revision, int[] pixels) {}

  private final Map<Long, Entry> entries;

  ThumbCache(int limit) {
    this.entries =
        new LinkedHashMap<>(256, 0.75F, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<Long, Entry> eldest) {
            return size() > limit;
          }
        };
  }

  synchronized int[] get(long key) {
    Entry entry = entries.get(key);
    return entry == null ? null : entry.pixels();
  }

  synchronized int revision(long key) {
    Entry entry = entries.get(key);
    return entry == null ? NONE : entry.revision();
  }

  synchronized boolean put(long key, int revision, int[] pixels) {
    Entry have = entries.get(key);
    if (have != null && revision < have.revision()) {
      return false;
    }
    entries.put(key, new Entry(revision, pixels));
    return true;
  }

  synchronized void remove(long key) {
    entries.remove(key);
  }

  synchronized void clear() {
    entries.clear();
  }
}
