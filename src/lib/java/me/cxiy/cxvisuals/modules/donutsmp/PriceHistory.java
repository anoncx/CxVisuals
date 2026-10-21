package me.cxiy.cxvisuals.modules.donutsmp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class PriceHistory {
  public static final int COMMUNITY = 0;
  public static final int AUCTION = 1;
  public static final int SEEN = 2;
  public static final int KINDS = 3;
  public static final long RETAIN = 7L * 24L * 3600L;

  private static final int MAGIC = 0x43585048;
  private static final int VERSION = 2;
  private static final long EPOCH = 1_577_836_800L;
  private static final int MAX_POINTS = 4096;
  private static final int FIRST_CAPACITY = 8;
  private static final float EPSILON = 1.0e-4F;

  private final Map<String, Track[]> tracks = new HashMap<>(2048);
  private boolean dirty;
  private int revision;

  private static final class Track {
    int[] at = new int[FIRST_CAPACITY];
    float[] value = new float[FIRST_CAPACITY];
    int size;

    void add(int time, float price) {
      if (size == at.length) {
        at = Arrays.copyOf(at, size * 2);
        value = Arrays.copyOf(value, size * 2);
      }
      at[size] = time;
      value[size] = price;
      size++;
    }

    void trim(int oldest) {
      int drop = 0;
      while (drop < size - 1 && at[drop + 1] <= oldest) {
        drop++;
      }
      if (size - drop > MAX_POINTS) {
        drop = size - MAX_POINTS;
      }
      if (drop > 0) {
        System.arraycopy(at, drop, at, 0, size - drop);
        System.arraycopy(value, drop, value, 0, size - drop);
        size -= drop;
      }
    }

    float valueAt(int time) {
      if (size == 0 || time < at[0]) {
        return Float.NaN;
      }
      int low = 0;
      int high = size - 1;
      while (low < high) {
        int mid = (low + high + 1) >>> 1;
        if (at[mid] <= time) {
          low = mid;
        } else {
          high = mid - 1;
        }
      }
      return value[low];
    }
  }

  public static long now() {
    return System.currentTimeMillis() / 1000L;
  }

  private static int stamp(long seconds) {
    return (int) (seconds - EPOCH);
  }

  public synchronized void record(String id, int kind, long seconds, float price) {
    Track[] pair = tracks.computeIfAbsent(id, key -> new Track[KINDS]);
    Track track = pair[kind];
    if (track == null) {
      track = new Track();
      pair[kind] = track;
    }
    if (track.size > 0
        && Math.abs(track.value[track.size - 1] - price) <= EPSILON * Math.max(1.0F, price)) {
      return;
    }
    track.add(stamp(seconds), price);
    dirty = true;
    revision++;
  }

  public synchronized void trim(long seconds) {
    int oldest = stamp(seconds - RETAIN);
    for (Track[] pair : tracks.values()) {
      for (Track track : pair) {
        if (track != null) {
          track.trim(oldest);
        }
      }
    }
  }

  public synchronized void reset() {
    tracks.clear();
    dirty = true;
    revision++;
  }

  public synchronized int revision() {
    return revision;
  }

  public synchronized boolean dirty() {
    return dirty;
  }

  public synchronized int size() {
    return tracks.size();
  }

  public synchronized List<String> ids() {
    return new ArrayList<>(tracks.keySet());
  }

  public synchronized boolean has(String id, int kind) {
    Track[] pair = tracks.get(id);
    return pair != null && pair[kind] != null && pair[kind].size > 0;
  }

  public synchronized float latest(String id, int kind) {
    Track[] pair = tracks.get(id);
    Track track = pair == null ? null : pair[kind];
    return track == null || track.size == 0 ? Float.NaN : track.value[track.size - 1];
  }

  public synchronized long since(String id, int kind) {
    Track[] pair = tracks.get(id);
    Track track = pair == null ? null : pair[kind];
    return track == null || track.size == 0 ? 0L : track.at[0] + EPOCH;
  }

  public synchronized float change(String id, int kind, long window, long seconds) {
    Track[] pair = tracks.get(id);
    Track track = pair == null ? null : pair[kind];
    if (track == null || track.size < 2) {
      return Float.NaN;
    }
    float base = track.valueAt(stamp(seconds - window));
    if (Float.isNaN(base)) {
      base = track.value[0];
    }
    float last = track.value[track.size - 1];
    return base <= 0.0F ? Float.NaN : (last - base) / base;
  }

  public synchronized void sample(String id, int kind, long from, long to, float[] out) {
    Track[] pair = tracks.get(id);
    Track track = pair == null ? null : pair[kind];
    int n = out.length;
    for (int i = 0; i < n; i++) {
      if (track == null) {
        out[i] = Float.NaN;
        continue;
      }
      long time = n == 1 ? to : from + (to - from) * i / (n - 1);
      out[i] = track.valueAt(stamp(time));
    }
  }

  public synchronized float[] extent(String id, int kind, long from, long to) {
    Track[] pair = tracks.get(id);
    Track track = pair == null ? null : pair[kind];
    if (track == null || track.size == 0) {
      return null;
    }
    float low = Float.MAX_VALUE;
    float high = -Float.MAX_VALUE;
    double sum = 0.0;
    double weight = 0.0;
    int start = stamp(from);
    int end = stamp(to);
    float opening = track.valueAt(start);
    int cursor = start;
    float holding = Float.isNaN(opening) ? track.value[0] : opening;
    if (Float.isNaN(opening)) {
      cursor = Math.max(start, track.at[0]);
    }
    low = Math.min(low, holding);
    high = Math.max(high, holding);
    for (int i = 0; i < track.size; i++) {
      if (track.at[i] <= cursor || track.at[i] > end) {
        continue;
      }
      double span = track.at[i] - cursor;
      sum += holding * span;
      weight += span;
      cursor = track.at[i];
      holding = track.value[i];
      low = Math.min(low, holding);
      high = Math.max(high, holding);
    }
    double tail = Math.max(0, end - cursor);
    sum += holding * tail;
    weight += tail;
    float mean = weight <= 0.0 ? holding : (float) (sum / weight);
    return new float[] {low, high, mean};
  }

  public void save(Path file) throws IOException {
    Path temp = file.resolveSibling(file.getFileName() + ".tmp");
    synchronized (this) {
      Files.createDirectories(file.getParent());
      try (DataOutputStream out =
          new DataOutputStream(
              new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temp))))) {
        out.writeInt(MAGIC);
        out.writeInt(VERSION);
        int count = 0;
        for (Track[] pair : tracks.values()) {
          for (Track track : pair) {
            count += track != null && track.size > 0 ? 1 : 0;
          }
        }
        out.writeInt(count);
        for (Map.Entry<String, Track[]> entry : tracks.entrySet()) {
          for (int kind = 0; kind < KINDS; kind++) {
            Track track = entry.getValue()[kind];
            if (track == null || track.size == 0) {
              continue;
            }
            out.writeUTF(entry.getKey());
            out.writeByte(kind);
            out.writeInt(track.size);
            for (int i = 0; i < track.size; i++) {
              out.writeInt(track.at[i]);
              out.writeFloat(track.value[i]);
            }
          }
        }
      }
      dirty = false;
    }
    Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
  }

  public void load(Path file) throws IOException {
    if (!Files.isRegularFile(file)) {
      return;
    }
    try (DataInputStream in =
        new DataInputStream(
            new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
      if (in.readInt() != MAGIC || in.readInt() != VERSION) {
        return;
      }
      int count = in.readInt();
      Map<String, Track[]> loaded = new HashMap<>(Math.max(16, count));
      for (int n = 0; n < count; n++) {
        String id = in.readUTF();
        int kind = in.readUnsignedByte();
        int size = in.readInt();
        if (size < 0 || size > MAX_POINTS * 4) {
          throw new IOException("corrupt history");
        }
        Track track = new Track();
        for (int i = 0; i < size; i++) {
          track.add(in.readInt(), in.readFloat());
        }
        if (kind < KINDS) {
          loaded.computeIfAbsent(id, key -> new Track[KINDS])[kind] = track;
        }
      }
      synchronized (this) {
        tracks.clear();
        tracks.putAll(loaded);
        dirty = false;
        revision++;
      }
    }
  }
}
