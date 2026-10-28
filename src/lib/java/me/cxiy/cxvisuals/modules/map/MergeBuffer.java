package me.cxiy.cxvisuals.modules.map;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MergeBuffer {
  private final Map<Path, Map<Long, MapShare.Chunk>> waiting = new LinkedHashMap<>();
  private boolean scheduled;

  synchronized boolean add(Path folder, List<MapShare.Chunk> chunks) {
    Map<Long, MapShare.Chunk> into = waiting.computeIfAbsent(folder, key -> new HashMap<>());
    for (MapShare.Chunk chunk : chunks) {
      long key = ((long) chunk.x() << 32) | (chunk.z() & 0xFFFFFFFFL);
      MapShare.Chunk have = into.get(key);
      if (have == null || chunk.stamp() > have.stamp()) {
        into.put(key, chunk);
      }
    }
    boolean first = !scheduled;
    scheduled = true;
    return first;
  }

  synchronized Map<Path, List<MapShare.Chunk>> take() {
    Map<Path, List<MapShare.Chunk>> out = new LinkedHashMap<>();
    for (Map.Entry<Path, Map<Long, MapShare.Chunk>> entry : waiting.entrySet()) {
      out.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
    }
    waiting.clear();
    scheduled = false;
    return out;
  }
}
