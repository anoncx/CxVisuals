package me.cxiy.cxvisuals.modules.environment.lyrics;

import me.cxiy.cxvisuals.modules.environment.EnvironmentModule;
import me.cxiy.cxvisuals.modules.environment.EnvironmentModule.Placement;
import me.cxiy.cxvisuals.modules.environment.View;
import me.cxiy.cxvisuals.modules.hud.media.LyricTracker;
import me.cxiy.cxvisuals.modules.hud.media.Lyrics;
import me.cxiy.cxvisuals.modules.hud.media.MediaSnapshot;

public final class WorldLyrics {
  public static final int CAPACITY = 8;
  public static final float UNIT = 0.018F;
  public static final float NEAREST = 1.5F;
  public static final float WIDEST = 9.0F;
  public static final float GAP = 0.22F;
  public static final float LINE_HEIGHT = 20.0F;

  private static final double MARGIN = 0.3;
  private static final double FAR = 64.0;
  private static final float SPREAD = 0.08F;
  private static final float RISE = 0.1F;
  private static final float RAISE = 0.09F;
  private static final float ROLL = 0.07F;
  private static final long MILLI = 1_000_000L;
  private static final int UNSET = Integer.MIN_VALUE;
  private static final int MAX_ROWS = 2;
  private static final long SEEK = 1200L;
  private static final double EDGE = 1.0e-4;
  private static final float LIFT = 0.04F;
  private static final float FLAT = 0.35F;
  private static final float SMALLEST = 0.08F;
  private static final float ROW_STEP = 1.35F;
  private static final float PAD = 1.1F;
  private static final float BEHIND = 0.2F;
  private static final int[] SPOTS = {
    1, 0, 1, 1, 1, -1, -1, 0, -1, 1, -1, -1, 1, 2, 1, -2, -1, 2, -1, -2
  };
  private static final float SPAN = WIDEST / 5.0F;
  private static final float LONGEST_STEP = 0.1F;
  private static final float FOLLOW = 12.0F;
  private static final float SWAY = 0.04F;
  private static final float MAX_SWAY = 0.105F;

  private final EnvironmentModule module;
  private final LyricTracker tracker = new LyricTracker();
  private final Stanza[] pool = new Stanza[CAPACITY];
  private Lyrics source;
  private Placement placement;
  private int line = UNSET;
  private int word = UNSET;
  private int spawns;
  private boolean primed;
  private double lastX;
  private double lastY;
  private double lastZ;
  private long lastNanos;
  private long heard = Long.MIN_VALUE;
  private double placedX;
  private double placedY;
  private double placedZ;
  private final float[] normal = new float[3];
  private float basisRx;
  private float basisRy;
  private float basisRz;
  private float basisUx;
  private float basisUy;
  private float basisUz;

  public WorldLyrics(EnvironmentModule module) {
    this.module = module;
    for (int i = 0; i < CAPACITY; i++) {
      pool[i] = new Stanza();
    }
  }

  public Stanza stanza(int index) {
    return pool[index];
  }

  public int alive() {
    int count = 0;
    for (int i = 0; i < CAPACITY; i++) {
      if (pool[i].alive) {
        count++;
      }
    }
    return count;
  }

  public void clear() {
    for (int i = 0; i < CAPACITY; i++) {
      pool[i].alive = false;
    }
    forget();
  }

  private void forget() {
    tracker.reset();
    source = null;
    line = UNSET;
    word = UNSET;
    heard = Long.MIN_VALUE;
  }

  public void update(
      View view, MediaSnapshot media, Lyrics lyrics, long nowNanos, Glyphs glyphs, Reach reach) {
    double dx = view.x - lastX;
    double dy = view.y - lastY;
    double dz = view.z - lastZ;
    if (primed && dx * dx + dy * dy + dz * dz > FAR * FAR) {
      clear();
    }
    lastX = view.x;
    lastY = view.y;
    lastZ = view.z;
    float step =
        primed ? Math.max(0.0F, Math.min(LONGEST_STEP, (nowNanos - lastNanos) / 1.0e9F)) : 0.0F;
    lastNanos = nowNanos;
    primed = true;

    boolean calm = module.calm.get();
    Placement mode = module.placement.get();
    boolean active =
        module.lyrics.get()
            && media.playing()
            && media.present()
            && lyrics.state() == Lyrics.State.FOUND;
    if (!active) {
      retire(nowNanos);
      forget();
    } else {
      long position = media.livePositionMs(nowNanos) + module.offset.get().longValue();
      boolean sung = tracker.update(lyrics, position, media.durationMs());
      int nowLine = tracker.line();
      int nowWord = sung ? tracker.word() : UNSET;
      boolean rewound = heard != Long.MIN_VALUE && position < heard - SEEK;
      heard = position;
      if (lyrics != source || mode != placement || rewound) {
        retire(nowNanos);
        line = UNSET;
        word = UNSET;
      }
      source = lyrics;
      placement = mode;
      if (line != UNSET && nowLine < line) {
        age(nowNanos, calm);
        return;
      }
      boolean fresh = nowLine != line;
      if (fresh) {
        word = UNSET;
      }
      boolean advanced = sung && (word == UNSET || nowWord > word);
      if (mode == Placement.WORDS) {
        if (advanced) {
          String text = lyrics.line(nowLine);
          spawnWord(
              view,
              text.substring(tracker.wordStart(nowWord), tracker.wordEnd(nowWord)),
              nowNanos,
              calm,
              glyphs,
              reach);
        }
      } else {
        if (nowLine >= 0 && fresh) {
          retire(nowNanos);
          if (sung) {
            spawnLine(
                view, lyrics.line(nowLine), nowNanos, calm, mode == Placement.FLOAT, glyphs, reach);
          }
        }
        follow(view, sung, step, calm, reach);
      }
      if (nowLine >= 0) {
        line = nowLine;
      }
      if (advanced) {
        word = nowWord;
      }
    }
    age(nowNanos, calm);
  }

  private void retire(long now) {
    for (int i = 0; i < CAPACITY; i++) {
      Stanza s = pool[i];
      if (s.alive && s.leaving == Long.MAX_VALUE) {
        s.leaving = now;
      }
    }
  }

  private void age(long now, boolean calm) {
    int kept = module.kept.get().intValue();
    int staying = 0;
    for (int i = 0; i < CAPACITY; i++) {
      Stanza s = pool[i];
      if (!s.alive) {
        continue;
      }
      if (s.leaving == Long.MAX_VALUE && now - s.born > s.lifeNanos) {
        s.leaving = now;
      }
      if (s.leaving == Long.MAX_VALUE && !s.whole) {
        staying++;
      }
    }
    while (staying > kept) {
      Stanza oldest = null;
      for (int i = 0; i < CAPACITY; i++) {
        Stanza s = pool[i];
        if (s.alive
            && !s.whole
            && s.leaving == Long.MAX_VALUE
            && (oldest == null || s.born < oldest.born)) {
          oldest = s;
        }
      }
      oldest.leaving = now;
      staying--;
    }
    for (int i = 0; i < CAPACITY; i++) {
      Stanza s = pool[i];
      if (s.alive
          && s.leaving != Long.MAX_VALUE
          && (now - s.leaving) / 1.0e9F
              > Effects.leaveSeconds(module.leave.get(), calm, s.letterCount)) {
        s.alive = false;
      }
    }
  }

  private Stanza slot() {
    Stanza leaving = null;
    Stanza oldest = null;
    for (int i = 0; i < CAPACITY; i++) {
      Stanza s = pool[i];
      if (!s.alive) {
        return s;
      }
      if (s.leaving != Long.MAX_VALUE && (leaving == null || s.leaving < leaving.leaving)) {
        leaving = s;
      }
      if (oldest == null || s.born < oldest.born) {
        oldest = s;
      }
    }
    return leaving != null ? leaving : oldest;
  }

  private static int mix(int value) {
    int h = value * 0x9E3779B1;
    h = (h ^ (h >>> 15)) * 0x85EBCA6B;
    return h ^ (h >>> 13);
  }

  private float cap() {
    return SPAN * module.distance.get().floatValue();
  }

  private float place(View view, Reach reach, double lateral, double vertical) {
    double wanted = module.distance.get();
    double ox = view.fx * wanted + view.rx * lateral + view.ux * vertical;
    double oy = view.fy * wanted + view.ry * lateral + view.uy * vertical;
    double oz = view.fz * wanted + view.rz * lateral + view.uz * vertical;
    double length = Math.sqrt(ox * ox + oy * oy + oz * oz);
    float dx = (float) (ox / length);
    float dy = (float) (oy / length);
    float dz = (float) (oz / length);
    double probe = length + MARGIN;
    double free = reach.clear(view, dx, dy, dz, probe, normal);
    if (free >= probe - EDGE) {
      placedX = view.x + ox;
      placedY = view.y + oy;
      placedZ = view.z + oz;
      basis(view.rx, view.ry, view.rz, view.ux, view.uy, view.uz);
      return 1.0F;
    }
    float nx = normal[0];
    float ny = normal[1];
    float nz = normal[2];
    placedX = view.x + dx * free + nx * LIFT;
    placedY = view.y + dy * free + ny * LIFT;
    placedZ = view.z + dz * free + nz * LIFT;
    float along = view.ux * nx + view.uy * ny + view.uz * nz;
    float ux = view.ux - nx * along;
    float uy = view.uy - ny * along;
    float uz = view.uz - nz * along;
    if (ux * ux + uy * uy + uz * uz < FLAT * FLAT) {
      float ahead = view.fx * nx + view.fy * ny + view.fz * nz;
      float sign = along >= 0.0F ? 1.0F : -1.0F;
      ux = (view.fx - nx * ahead) * sign;
      uy = (view.fy - ny * ahead) * sign;
      uz = (view.fz - nz * ahead) * sign;
    }
    float size = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
    if (size < EDGE) {
      basis(view.rx, view.ry, view.rz, view.ux, view.uy, view.uz);
    } else {
      ux /= size;
      uy /= size;
      uz /= size;
      basis(uy * nz - uz * ny, uz * nx - ux * nz, ux * ny - uy * nx, ux, uy, uz);
    }
    return (float) Math.max(SMALLEST, free / length);
  }

  private void basis(float rx, float ry, float rz, float ux, float uy, float uz) {
    basisRx = rx;
    basisRy = ry;
    basisRz = rz;
    basisUx = ux;
    basisUy = uy;
    basisUz = uz;
  }

  private void seat(Stanza s) {
    s.x = placedX;
    s.y = placedY;
    s.z = placedZ;
    s.rx = basisRx;
    s.ry = basisRy;
    s.rz = basisRz;
    s.ux = basisUx;
    s.uy = basisUy;
    s.uz = basisUz;
  }

  private Stanza begin(View view, String text, long now, boolean calm, float span, int seed) {
    Stanza s = slot();
    s.alive = true;
    s.follows = false;
    s.whole = false;
    s.text = text;
    s.born = now;
    s.leaving = Long.MAX_VALUE;
    s.lifeNanos = Long.MAX_VALUE;
    s.sung = 0;
    s.fill = 1.0F;
    s.seed = seed & 0x3FF;
    s.width = span;
    s.scale = (float) (module.size.get() / 100.0) * UNIT;
    if (span * s.scale > cap()) {
      s.scale = cap() / span;
    }
    s.full = s.scale;
    s.rx = view.rx;
    s.ry = view.ry;
    s.rz = view.rz;
    s.ux = view.ux;
    s.uy = view.uy;
    s.uz = view.uz;
    s.roll = calm ? 0.0F : ((seed >>> 20) & 1) == 0 ? ROLL : -ROLL;
    return s;
  }

  private void spawnWord(
      View view, String text, long now, boolean calm, Glyphs glyphs, Reach reach) {
    int seed = mix(++spawns);
    float span = glyphs.width(text);
    Stanza s = begin(view, text, now, calm, span, seed);
    double d = module.distance.get();
    float spread = (seed & 0xFFFF) / 65535.0F;
    float rise = ((seed >>> 8) & 0xFF) / 127.5F - 1.0F;
    double side = (spawns & 1) == 0 ? 1.0 : -1.0;
    double reach0 = d * GAP + span * s.scale * 0.5 + d * SPREAD * spread;
    double lateral = side * reach0;
    double vertical = d * RISE * rise;
    if (module.apart.get()) {
      float halfWide = span * s.scale * 0.5F / (float) d;
      float halfTall = LINE_HEIGHT * s.scale * 0.5F / (float) d;
      double step = LINE_HEIGHT * s.scale * ROW_STEP;
      boolean found = false;
      for (int i = 0; i < SPOTS.length && !found; i += 2) {
        double tryLateral = side * SPOTS[i] * reach0;
        double tryVertical = SPOTS[i + 1] == 0 ? vertical : vertical * 0.3 + SPOTS[i + 1] * step;
        if (!covered(view, s, tryLateral / d, tryVertical / d, halfWide, halfTall, false, now)) {
          lateral = tryLateral;
          vertical = tryVertical;
          found = true;
        }
      }
      if (!found) {
        covered(view, s, lateral / d, vertical / d, halfWide, halfTall, true, now);
      }
    }
    s.scale = s.full * place(view, reach, lateral, vertical);
    seat(s);
    s.words = 1;
    s.parts[0] = text;
    s.widths[0] = span;
    s.lefts[0] = -span * 0.5F;
    s.tops[0] = 0.0F;
    s.letters[0] = 0;
    s.letterCount = text.length();
    s.height = LINE_HEIGHT;
    s.lifeNanos =
        (long)
            (module.hold.get() * Math.max(1L, tracker.wordEndMs() - tracker.wordStartMs()) * MILLI);
  }

  private void spawnLine(
      View view,
      String text,
      long now,
      boolean calm,
      boolean floating,
      Glyphs glyphs,
      Reach reach) {
    int seed = mix(++spawns);
    Stanza s = begin(view, text, now, calm, 1.0F, seed);
    s.whole = true;
    s.follows = floating;
    s.roll = 0.0F;
    s.fill = 0.0F;
    int total = tracker.words();
    s.reserve(total);
    float scale = (float) (module.size.get() / 100.0) * UNIT;
    float limit = cap() / scale;
    float space = glyphs.width(" ");
    int row = 0;
    int placed = 0;
    int letter = 0;
    float cursor = 0.0F;
    float widest = 0.0F;
    int rowStart = 0;
    for (int i = 0; i < total; i++) {
      String part = text.substring(tracker.wordStart(i), tracker.wordEnd(i));
      float span = glyphs.width(part);
      float start = cursor == 0.0F ? 0.0F : cursor + space;
      if (start + span > limit && cursor > 0.0F) {
        centre(s, rowStart, placed, cursor);
        if (++row >= MAX_ROWS) {
          break;
        }
        rowStart = placed;
        cursor = 0.0F;
        start = 0.0F;
      }
      s.parts[placed] = part;
      s.widths[placed] = span;
      s.lefts[placed] = start;
      s.tops[placed] = row;
      s.letters[placed] = letter;
      letter += part.length();
      placed++;
      cursor = start + span;
      widest = Math.max(widest, cursor);
    }
    if (row < MAX_ROWS) {
      centre(s, rowStart, placed, cursor);
    }
    int rows = Math.min(MAX_ROWS, row + 1);
    for (int i = 0; i < placed; i++) {
      s.tops[i] = s.tops[i] * LINE_HEIGHT - (rows - 1) * LINE_HEIGHT * 0.5F;
    }
    s.words = placed;
    s.letterCount = letter;
    s.width = widest;
    s.height = rows * LINE_HEIGHT;
    s.full = widest * scale > cap() ? cap() / widest : scale;
    s.scale = s.full * place(view, reach, 0.0, module.distance.get() * RAISE);
    seat(s);
  }

  private static void centre(Stanza s, int from, int to, float rowWidth) {
    for (int i = from; i < to; i++) {
      s.lefts[i] -= rowWidth * 0.5F;
    }
  }

  private void follow(View view, boolean sung, float step, boolean calm, Reach reach) {
    for (int i = 0; i < CAPACITY; i++) {
      Stanza s = pool[i];
      if (!s.alive || !s.whole || s.leaving != Long.MAX_VALUE) {
        continue;
      }
      if (sung) {
        s.sung = tracker.word();
        s.fill = tracker.wordProgress();
      }
      if (!s.follows) {
        continue;
      }
      double ease = calm ? 1.0 : 1.0 - Math.exp(-step * FOLLOW);
      float near = place(view, reach, 0.0, module.distance.get() * RAISE);
      s.scale += (s.full * near - s.scale) * (float) ease;
      s.x += (placedX - s.x) * ease;
      s.y += (placedY - s.y) * ease;
      s.z += (placedZ - s.z) * ease;
      s.rx = basisRx;
      s.ry = basisRy;
      s.rz = basisRz;
      s.ux = basisUx;
      s.uy = basisUy;
      s.uz = basisUz;
      s.roll = calm ? 0.0F : Math.max(-MAX_SWAY, Math.min(MAX_SWAY, -view.yawRate * SWAY));
    }
  }

  private boolean covered(
      View view,
      Stanza self,
      double across,
      double above,
      float halfWide,
      float halfTall,
      boolean evict,
      long now) {
    boolean hit = false;
    for (int i = 0; i < CAPACITY; i++) {
      Stanza other = pool[i];
      if (other == self || !other.alive || other.whole) {
        continue;
      }
      double dx = other.x - view.x;
      double dy = other.y - view.y;
      double dz = other.z - view.z;
      double depth = dx * view.fx + dy * view.fy + dz * view.fz;
      if (depth < BEHIND) {
        continue;
      }
      double otherAcross = (dx * view.rx + dy * view.ry + dz * view.rz) / depth;
      double otherAbove = (dx * view.ux + dy * view.uy + dz * view.uz) / depth;
      double wide = (halfWide + other.width * other.scale * 0.5 / depth) * PAD;
      double tall = (halfTall + other.height * other.scale * 0.5 / depth) * PAD;
      if (Math.abs(across - otherAcross) < wide && Math.abs(above - otherAbove) < tall) {
        if (!evict) {
          return true;
        }
        hit = true;
        if (other.leaving == Long.MAX_VALUE) {
          other.leaving = now;
        }
      }
    }
    return hit;
  }
}
