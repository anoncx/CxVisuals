package me.cxiy.cxvisuals.modules.animations;

import java.util.Arrays;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.modules.animations.AnimationsModule.BlockBreak;
import me.cxiy.cxvisuals.modules.animations.AnimationsModule.BlockPlace;
import me.cxiy.cxvisuals.modules.animations.AnimationsModule.Source;

public final class BlockPops {
  public enum Kind {
    PLACE,
    BREAK
  }

  public record Box(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    public static final Box FULL = new Box(0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F);
  }

  public static final int CAPACITY = 48;
  public static final float SETTLE = 0.15F;

  private static final float MIN_SCALE = 0.02F;
  private static final float INSET = 0.998F;
  private static final float HALF_TURN = (float) Math.PI;
  private static final float PLACE_FROM = 0.15F;
  private static final float PLACE_LIFT = 0.35F;
  private static final float DROP_FALL = 0.55F;
  private static final float DROP_HEIGHT = 1.0F;
  private static final float DROP_FROM = 0.4F;
  private static final float DROP_APPEAR = 0.25F;
  private static final float DROP_STRETCH = 0.14F;
  private static final float DROP_SQUASH = 0.3F;
  private static final float GROW_FROM = 0.55F;
  private static final float JELLY_FROM = 0.3F;
  private static final float JELLY_APPEAR = 0.3F;
  private static final float JELLY_SQUASH = 0.5F;
  private static final float JELLY_WAVES = 3.0F;
  private static final float TUMBLE_PITCH = 0.75F;
  private static final float TUMBLE_ROLL = 0.35F;
  private static final float TUMBLE_LIFT = 0.2F;
  private static final float BREAK_SINK = 0.25F;
  private static final float SINK_SPREAD = 0.15F;
  private static final float BURST_SWELL = 0.3F;
  private static final float BURST_GROW = 0.2F;
  private static final float FLING_RISE = 0.9F;
  private static final float FLING_TILT = 0.9F;

  private static final class Entry {
    long pos;
    Kind kind;
    Object state;
    Object attachment;
    float phase;
    float settle;
    float minX;
    float minY;
    float minZ;
    float maxX;
    float maxY;
    float maxZ;

    void fill(Kind kind, Object state, Box box) {
      this.kind = kind;
      this.state = state;
      attachment = null;
      phase = 0.0F;
      settle = 0.0F;
      minX = box.minX();
      minY = box.minY();
      minZ = box.minZ();
      maxX = box.maxX();
      maxY = box.maxY();
      maxZ = box.maxZ();
    }
  }

  private final Entry[] entries = new Entry[CAPACITY];
  private final Entry[] finished = new Entry[CAPACITY];
  private volatile long[] hidden = new long[0];
  private BlockPlace place = BlockPlace.POP;
  private BlockBreak broken = BlockBreak.SHRINK;
  private int count;
  private int finishedCount;

  public BlockPops() {
    for (int i = 0; i < CAPACITY; i++) {
      entries[i] = new Entry();
      finished[i] = new Entry();
    }
  }

  public void styles(BlockPlace place, BlockBreak broken) {
    this.place = place;
    this.broken = broken;
  }

  public boolean start(long pos, Kind kind, Object state, Source filter, boolean self) {
    return start(pos, kind, state, filter, self, Box.FULL);
  }

  public boolean start(long pos, Kind kind, Object state, Source filter, boolean self, Box box) {
    int index = find(pos);
    if (index >= 0 && entries[index].settle > 0.0F) {
      drop(index);
      index = -1;
    }
    if (filter == Source.SELF && !self) {
      if (index >= 0 && entries[index].kind != kind) {
        remove(index);
      }
      return false;
    }
    if (index >= 0) {
      Entry entry = entries[index];
      if (entry.kind == Kind.PLACE && kind == Kind.BREAK) {
        remove(index);
        return false;
      }
      boolean became = entry.kind != kind;
      entry.fill(kind, state, box);
      if (became) {
        publish();
      }
      return true;
    }
    if (count >= CAPACITY) {
      return false;
    }
    Entry entry = entries[count++];
    entry.pos = pos;
    entry.fill(kind, state, box);
    if (kind == Kind.PLACE) {
      publish();
    }
    return true;
  }

  public void attach(long pos, Object attachment) {
    int index = find(pos);
    if (index >= 0) {
      entries[index].attachment = attachment;
    }
  }

  public void update(float dt, float seconds) {
    finishedCount = 0;
    boolean expired = false;
    for (int i = 0; i < count; i++) {
      Entry entry = entries[i];
      if (entry.settle > 0.0F) {
        entry.settle -= Math.max(dt, 0.0F);
        if (entry.settle <= 0.0F) {
          drop(i--);
        }
        continue;
      }
      entry.phase =
          seconds > 0.0F && dt >= 0.0F ? Math.min(1.0F, entry.phase + dt / seconds) : 1.0F;
      if (entry.phase < 1.0F) {
        continue;
      }
      Entry done = finished[finishedCount++];
      done.pos = entry.pos;
      done.kind = entry.kind;
      done.state = entry.state;
      if (entry.kind == Kind.PLACE) {
        entry.settle = SETTLE;
        expired = true;
      } else {
        drop(i--);
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

  public int indexOf(long pos) {
    return find(pos);
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

  public Object attachment(int index) {
    return entries[index].attachment;
  }

  public boolean settling(int index) {
    return entries[index].settle > 0.0F;
  }

  public void pose(int index, float spin, BlockPose out) {
    Entry entry = entries[index];
    float centerX = (entry.minX + entry.maxX) * 0.5F;
    float centerY = (entry.minY + entry.maxY) * 0.5F;
    float centerZ = (entry.minZ + entry.maxZ) * 0.5F;
    if (entry.settle > 0.0F) {
      out.rest(centerX, centerY, centerZ);
      out.scale(INSET);
      return;
    }
    float t = entry.phase;
    float snap = (float) Easing.snappy(t);
    if (entry.kind == Kind.PLACE) {
      switch (place) {
        case POP -> {
          out.rest(centerX, centerY, centerZ);
          out.scale(PLACE_FROM + (1.0F - PLACE_FROM) * (float) Easing.spring(t));
          out.moveY = (snap - 1.0F) * PLACE_LIFT;
        }
        case DROP -> {
          out.rest(centerX, entry.minY, centerZ);
          float fall = Math.min(t / DROP_FALL, 1.0F);
          out.moveY = DROP_HEIGHT * (1.0F - fall * fall);
          if (t < DROP_FALL) {
            out.stretch(1.0F + DROP_STRETCH * 4.0F * fall * (1.0F - fall));
          } else {
            float land = (t - DROP_FALL) / (1.0F - DROP_FALL);
            float fade = (1.0F - land) * (1.0F - land);
            out.stretch(1.0F - DROP_SQUASH * (float) Math.sin(land * 2.0F * HALF_TURN) * fade);
          }
          out.scale(
              DROP_FROM + (1.0F - DROP_FROM) * (float) Easing.smoothstep(t / DROP_APPEAR));
        }
        case GROW -> {
          out.rest(centerX, entry.minY, centerZ);
          float width = GROW_FROM + (1.0F - GROW_FROM) * snap;
          out.scaleX = width;
          out.scaleY = (float) Easing.overshoot(t);
          out.scaleZ = width;
        }
        case JELLY -> {
          out.rest(centerX, entry.minY, centerZ);
          float left = 1.0F - t;
          out.stretch(
              1.0F - JELLY_SQUASH * left * left * (float) Math.cos(JELLY_WAVES * HALF_TURN * t));
          out.scale(
              JELLY_FROM
                  + (1.0F - JELLY_FROM) * (float) Easing.snappy(t / JELLY_APPEAR));
        }
        case TUMBLE -> {
          out.rest(centerX, centerY, centerZ);
          out.scale(PLACE_FROM + (1.0F - PLACE_FROM) * snap);
          out.pitch = (snap - 1.0F) * HALF_TURN * TUMBLE_PITCH;
          out.roll = (1.0F - snap) * HALF_TURN * TUMBLE_ROLL;
          out.moveY = (snap - 1.0F) * TUMBLE_LIFT;
        }
      }
      out.yaw = (snap - 1.0F) * spin;
    } else {
      switch (broken) {
        case SINK -> {
          out.rest(centerX, entry.minY, centerZ);
          float spread = 1.0F + SINK_SPREAD * (float) Math.sin(HALF_TURN * t);
          out.scaleX = spread;
          out.scaleY = 1.0F - (float) Easing.smootherstep(t);
          out.scaleZ = spread;
        }
        case BURST -> {
          out.rest(centerX, centerY, centerZ);
          if (t < BURST_SWELL) {
            out.scale(1.0F + BURST_GROW * (float) Easing.smoothstep(t / BURST_SWELL));
          } else {
            float collapse = (t - BURST_SWELL) / (1.0F - BURST_SWELL);
            out.scale((1.0F + BURST_GROW) * (1.0F - collapse * collapse * collapse));
          }
        }
        case FLING -> {
          out.rest(centerX, centerY, centerZ);
          out.scale(1.0F - t * t);
          out.moveY = snap * FLING_RISE;
          out.pitch = t * HALF_TURN * FLING_TILT;
        }
        default -> {
          out.rest(centerX, centerY, centerZ);
          out.scale(1.0F - (float) Easing.smootherstep(t));
          out.moveY = -snap * BREAK_SINK;
        }
      }
      out.yaw = snap * spin;
    }
    out.floor(MIN_SCALE);
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

  private int find(long pos) {
    for (int i = 0; i < count; i++) {
      if (entries[i].pos == pos) {
        return i;
      }
    }
    return -1;
  }

  private void drop(int index) {
    Entry last = entries[count - 1];
    entries[count - 1] = entries[index];
    entries[index] = last;
    count--;
  }

  private void remove(int index) {
    drop(index);
    publish();
  }

  private void publish() {
    long[] packed = new long[count];
    int placed = 0;
    for (int i = 0; i < count; i++) {
      if (entries[i].kind == Kind.PLACE && entries[i].settle <= 0.0F) {
        packed[placed++] = entries[i].pos;
      }
    }
    Arrays.sort(packed, 0, placed);
    hidden = placed == packed.length ? packed : Arrays.copyOf(packed, placed);
  }
}
