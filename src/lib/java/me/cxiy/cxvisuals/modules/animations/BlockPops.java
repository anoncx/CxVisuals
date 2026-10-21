package me.cxiy.cxvisuals.modules.animations;

import java.util.Arrays;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.modules.animations.AnimationsModule.Source;

public final class BlockPops {
  public enum Kind {
    PLACE,
    BREAK
  }

  public static final int CAPACITY = 48;

  private static final float PLACE_FROM = 0.15F;
  private static final float PLACE_LIFT = 0.35F;
  private static final float BREAK_SINK = 0.25F;

  private static final class Entry {
    long pos;
    Kind kind;
    Object state;
    float phase;
  }

  private final Entry[] entries = new Entry[CAPACITY];
  private final Entry[] finished = new Entry[CAPACITY];
  private volatile long[] hidden = new long[0];
  private int count;
  private int finishedCount;

  public BlockPops() {
    for (int i = 0; i < CAPACITY; i++) {
      entries[i] = new Entry();
      finished[i] = new Entry();
    }
  }

  public boolean start(long pos, Kind kind, Object state, Source filter, boolean self) {
    if (filter == Source.SELF && !self) {
      return false;
    }
    for (int i = 0; i < count; i++) {
      if (entries[i].pos == pos) {
        if (entries[i].kind == kind) {
          entries[i].phase = 0.0F;
          entries[i].state = state;
          return true;
        }
        remove(i);
        return false;
      }
    }
    if (count >= CAPACITY) {
      return false;
    }
    Entry entry = entries[count++];
    entry.pos = pos;
    entry.kind = kind;
    entry.state = state;
    entry.phase = 0.0F;
    if (kind == Kind.PLACE) {
      publish();
    }
    return true;
  }

  public void update(float dt, float seconds) {
    finishedCount = 0;
    boolean expired = false;
    for (int i = 0; i < count; i++) {
      Entry entry = entries[i];
      entry.phase =
          seconds > 0.0F && dt >= 0.0F ? Math.min(1.0F, entry.phase + dt / seconds) : 1.0F;
      if (entry.phase >= 1.0F) {
        Entry done = finished[finishedCount++];
        done.pos = entry.pos;
        done.kind = entry.kind;
        done.state = entry.state;
        entries[i] = entries[count - 1];
        count--;
        i--;
        expired = true;
      }
    }
    if (expired) {
      publish();
    }
  }

  public void clear() {
    count = 0;
    finishedCount = 0;
    hidden = new long[0];
  }

  public boolean hidden(long pos) {
    long[] packed = hidden;
    int low = 0;
    int high = packed.length - 1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      long value = packed[mid];
      if (value < pos) {
        low = mid + 1;
      } else if (value > pos) {
        high = mid - 1;
      } else {
        return true;
      }
    }
    return false;
  }

  public int hiddenCount() {
    return hidden.length;
  }

  public int count() {
    return count;
  }

  public long pos(int index) {
    return entries[index].pos;
  }

  public Kind kind(int index) {
    return entries[index].kind;
  }

  public Object state(int index) {
    return entries[index].state;
  }

  public float scale(int index) {
    Entry entry = entries[index];
    return entry.kind == Kind.PLACE
        ? PLACE_FROM + (1.0F - PLACE_FROM) * (float) Easing.spring(entry.phase)
        : 1.0F - (float) Easing.smootherstep(entry.phase);
  }

  public float spin(int index) {
    Entry entry = entries[index];
    return entry.kind == Kind.PLACE
        ? (float) Easing.snappy(entry.phase) - 1.0F
        : (float) Easing.snappy(entry.phase);
  }

  public float lift(int index) {
    Entry entry = entries[index];
    float eased = (float) Easing.snappy(entry.phase);
    return entry.kind == Kind.PLACE ? (eased - 1.0F) * PLACE_LIFT : -eased * BREAK_SINK;
  }

  public int finishedCount() {
    return finishedCount;
  }

  public long finishedPos(int index) {
    return finished[index].pos;
  }

  public Kind finishedKind(int index) {
    return finished[index].kind;
  }

  private void remove(int index) {
    entries[index] = entries[count - 1];
    count--;
    publish();
  }

  private void publish() {
    long[] packed = new long[count];
    int placed = 0;
    for (int i = 0; i < count; i++) {
      if (entries[i].kind == Kind.PLACE) {
        packed[placed++] = entries[i].pos;
      }
    }
    Arrays.sort(packed, 0, placed);
    hidden = placed == packed.length ? packed : Arrays.copyOf(packed, placed);
  }
}
