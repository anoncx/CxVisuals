package me.cxiy.cxvisuals.modules.shaders.circles;

import java.util.Arrays;

public final class Hops {
  public static final float LANDING_LOWEST = 0.6F;
  public static final float LANDING_HIGHEST = 1.8F;

  private static final double RISE = 0.05;
  private static final double SETTLED = 0.01;
  private static final double CLIMB = 0.04;
  private static final double LEAP = 0.6;
  private static final double HIGHEST_LEAP = 3.5;
  private static final double DROP = 0.25;
  private static final double SMALLEST_FALL = 0.4;
  private static final float FALL_GAIN = 0.12F;
  private static final int WAIT = 5;
  private static final int STILL = 2;
  private static final int QUIET = 12;

  public interface Listener {
    void hop(double x, double y, double z, float yaw, float strength);
  }

  private static final class Track {
    int id;
    int seen;
    double lastY;
    int quietJump;
    int quietLand;
    boolean grounded;
    double base;
    double peak;
    int rising;
    int settling;
    boolean standing;
    double floor;
    double top;
    boolean climbing;
    int still;
  }

  private Track[] tracks = new Track[16];
  private int count;
  private int generation;

  public void begin() {
    generation++;
  }

  public void step(
      int id,
      double x,
      double y,
      double z,
      float yaw,
      boolean onGround,
      boolean trusted,
      boolean jumps,
      boolean lands,
      Listener out) {
    Track track = find(id);
    if (track == null) {
      add(id, y, onGround);
      return;
    }
    track.seen = generation;
    track.quietJump = Math.max(0, track.quietJump - 1);
    track.quietLand = Math.max(0, track.quietLand - 1);
    ground(track, x, y, z, yaw, onGround, jumps, lands, out);
    if (!trusted) {
      motion(track, x, y, z, yaw, jumps, lands, out);
    }
    track.lastY = y;
  }

  private void ground(
      Track t,
      double x,
      double y,
      double z,
      float yaw,
      boolean onGround,
      boolean jumps,
      boolean lands,
      Listener out) {
    if (t.grounded && !onGround) {
      t.base = t.lastY;
      t.peak = Math.max(y, t.lastY);
      t.rising = WAIT;
      t.settling = 0;
    } else if (!t.grounded && onGround) {
      t.rising = 0;
      t.settling = WAIT;
      t.base = y;
    }
    if (onGround) {
      t.base = Math.min(t.base, y);
    } else {
      t.peak = Math.max(t.peak, y);
    }
    if (t.rising > 0) {
      if (y - t.base > RISE) {
        t.rising = 0;
        jump(t, x, t.base, z, yaw, jumps, out);
      } else {
        t.rising--;
      }
    }
    if (t.settling > 0 && (Math.abs(y - t.lastY) < SETTLED || --t.settling == 0)) {
      t.settling = 0;
      land(t, x, z, yaw, t.peak - t.base, t.base, lands, out);
    }
    t.grounded = onGround;
  }

  private void motion(
      Track t,
      double x,
      double y,
      double z,
      float yaw,
      boolean jumps,
      boolean lands,
      Listener out) {
    double dy = y - t.lastY;
    boolean settled = Math.abs(dy) < SETTLED;
    if (t.standing) {
      if (t.climbing) {
        t.top = Math.max(t.top, y);
        if (dy < -SETTLED) {
          t.climbing = false;
          double rise = t.top - t.floor;
          if (rise > LEAP && rise < HIGHEST_LEAP) {
            t.standing = false;
            jump(t, x, t.floor, z, yaw, jumps, out);
          }
        } else if (settled && t.still >= 1) {
          t.climbing = false;
        }
      } else if (dy > CLIMB && t.still >= STILL) {
        t.climbing = true;
        t.floor = t.lastY;
        t.top = y;
      } else if (dy < -DROP) {
        t.standing = false;
        t.top = t.lastY;
      }
    } else {
      t.top = Math.max(t.top, y);
      if (settled && t.still + 1 >= STILL) {
        t.standing = true;
        land(t, x, z, yaw, t.top - y, y, lands, out);
      }
    }
    t.still = settled ? t.still + 1 : 0;
  }

  private void jump(Track t, double x, double y, double z, float yaw, boolean jumps, Listener out) {
    if (t.quietJump == 0) {
      t.quietJump = QUIET;
      if (jumps) {
        out.hop(x, y, z, yaw, 1.0F);
      }
    }
  }

  private void land(
      Track t, double x, double z, float yaw, double fall, double y, boolean lands, Listener out) {
    if (fall >= SMALLEST_FALL && t.quietLand == 0) {
      t.quietLand = QUIET;
      if (lands) {
        out.hop(x, y, z, yaw, landing(fall));
      }
    }
  }

  public void end() {
    int kept = 0;
    for (int i = 0; i < count; i++) {
      if (tracks[i].seen == generation) {
        Track swap = tracks[kept];
        tracks[kept++] = tracks[i];
        tracks[i] = swap;
      }
    }
    count = kept;
  }

  public void clear() {
    count = 0;
  }

  public int tracked() {
    return count;
  }

  public static float landing(double fall) {
    return Math.clamp(LANDING_LOWEST + (float) fall * FALL_GAIN, LANDING_LOWEST, LANDING_HIGHEST);
  }

  private Track find(int id) {
    for (int i = 0; i < count; i++) {
      if (tracks[i].id == id) {
        return tracks[i];
      }
    }
    return null;
  }

  private void add(int id, double y, boolean onGround) {
    if (count == tracks.length) {
      tracks = Arrays.copyOf(tracks, count * 2);
    }
    if (tracks[count] == null) {
      tracks[count] = new Track();
    }
    Track t = tracks[count++];
    t.id = id;
    t.seen = generation;
    t.lastY = y;
    t.quietJump = 0;
    t.quietLand = 0;
    t.grounded = onGround;
    t.base = y;
    t.peak = y;
    t.rising = 0;
    t.settling = 0;
    t.standing = true;
    t.floor = y;
    t.top = y;
    t.climbing = false;
    t.still = STILL;
  }
}
