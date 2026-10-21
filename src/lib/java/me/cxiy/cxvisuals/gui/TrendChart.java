package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.modules.donutsmp.Money;
import me.cxiy.cxvisuals.modules.donutsmp.PriceHistory;

public final class TrendChart {
  private static final int MAX_POINTS = 420;
  private static final int MIN_POINTS = 24;
  private static final float POINT_STEP = 1.6F;
  private static final float SPARK_STEP = 1.5F;
  private static final int SPARK_MIN = 10;
  private static final int SPARK_MAX = 110;
  private static final float AXIS = 58.0F;
  private static final float FOOT = 24.0F;
  private static final float TOP = 10.0F;
  private static final float RIGHT = 10.0F;
  private static final int GRID_LINES = 4;
  private static final int TICKS = 4;
  private static final float LINE = 2.0F;
  private static final float GLOW_WIDTH = 5.0F;
  private static final float GLOW_SOFT = 7.0F;
  private static final float GLOW_ALPHA = 0.28F;
  private static final float FILL_ALPHA = 0.34F;
  private static final float SPARK_FILL = 0.3F;
  private static final float HEADROOM = 0.14F;
  private static final float SMOOTHING = 0.026F;
  private static final float REVEAL_SECONDS = 0.9F;
  private static final float DOT = 3.4F;
  private static final float PULSE_SECONDS = 1.8F;
  private static final float PULSE_REACH = 11.0F;
  private static final float HOVER_DOT = 5.0F;
  private static final float TIP_PAD = 10.0F;
  private static final float TIP_GAP = 12.0F;
  private static final float SPARK_LINE = 1.5F;

  private final Anim reveal = new Anim(1.0F, REVEAL_SECONDS, Anim.Curve.SNAPPY);
  private final Anim hover = Anim.hover(0.0F);
  private final Line main = new Line();
  private final Line mini = new Line();
  private final float[] drawX = new float[MAX_POINTS + 2];
  private final float[] drawY = new float[MAX_POINTS + 2];
  private float tipX;
  private float tipY;
  private float tipValue;
  private long tipTime;
  private float clock;

  private static final class Line {
    float[] raw = new float[0];
    float[] curve = new float[0];
    float[] work = new float[0];
    float[] xs = new float[0];
    float[] ys = new float[0];
    int first;

    void size(int n) {
      if (raw.length != n) {
        raw = new float[n];
        curve = new float[n];
        work = new float[n];
        xs = new float[n];
        ys = new float[n];
      }
    }
  }

  public void restart() {
    reveal.snap(0.0F);
    reveal.set(1.0F);
  }

  public void draw(
      Canvas c,
      Theme t,
      PriceHistory history,
      String id,
      int kind,
      long from,
      long to,
      float x,
      float y,
      float w,
      float h,
      float mx,
      float my,
      int accent,
      float dt) {
    reveal.update(dt);
    clock = (clock + dt) % PULSE_SECONDS;
    float pixel = c.pixel();
    float px = snap(x + AXIS, pixel);
    float py = y + TOP;
    float pw = snap(w - AXIS - RIGHT, pixel);
    float ph = h - TOP - FOOT;
    int n = Math.max(MIN_POINTS, Math.min(MAX_POINTS, (int) (pw / POINT_STEP)));
    main.size(n);
    history.sample(id, kind, from, to, main.raw);
    main.first = smooth(main.raw, main.curve, main.work, Math.max(2, Math.round(n * SMOOTHING)));

    float low = Float.MAX_VALUE;
    float high = -Float.MAX_VALUE;
    for (int i = main.first; i < n; i++) {
      low = Math.min(low, main.curve[i]);
      high = Math.max(high, main.curve[i]);
    }
    boolean any = main.first < n;
    if (any) {
      float span = high - low;
      float pad = span <= 0.0F ? Math.max(1.0F, Math.abs(high) * 0.04F) : span * HEADROOM;
      low = Math.max(0.0F, low - pad);
      high += pad;
    } else {
      low = 0.0F;
      high = 1.0F;
    }
    float range = high - low;

    drawGrid(c, t, px, py, pw, ph, low, range);
    drawTicks(c, t, from, to, px, py + ph, pw);
    if (!any) {
      String empty = "Collecting prices";
      c.text(
          FontStyle.LABEL,
          empty,
          px + (pw - c.textWidth(FontStyle.LABEL, empty)) * 0.5F,
          py + (ph - c.lineHeight(FontStyle.LABEL)) * 0.5F,
          t.grey());
      return;
    }

    float base = py + ph;
    for (int i = main.first; i < n; i++) {
      main.xs[i] = px + pw * i / (n - 1);
      main.ys[i] = base - (main.curve[i] - low) / range * ph;
    }
    float eased = 1.0F - (1.0F - reveal.get()) * (1.0F - reveal.get()) * (1.0F - reveal.get());
    float length = Math.max(main.first + 1.0F, eased * (n - 1));
    int whole = Math.min(n - 1, (int) length);
    int count = 0;
    for (int i = main.first; i <= whole; i++) {
      drawX[count] = main.xs[i];
      drawY[count++] = main.ys[i];
    }
    float fraction = length - whole;
    if (fraction > 0.0F && whole < n - 1) {
      drawX[count] = main.xs[whole] + (main.xs[whole + 1] - main.xs[whole]) * fraction;
      drawY[count++] = main.ys[whole] + (main.ys[whole + 1] - main.ys[whole]) * fraction;
    }
    c.pushClip(px - 4.0F, py - 14.0F, pw + 22.0F, ph + 24.0F);
    paint(c, drawX, drawY, count, base, accent, LINE, FILL_ALPHA, true);
    drawHead(c, accent, drawX[count - 1], drawY[count - 1], count > n - 3);
    c.popClip();

    boolean over = mx >= px && mx < px + pw && my >= py - TOP && my < base + FOOT * 0.5F;
    hover.set(over ? 1.0F : 0.0F);
    hover.update(dt);
    if (over) {
      int index = Math.max(0, Math.min(n - 1, Math.round((mx - px) / pw * (n - 1))));
      if (index >= main.first) {
        tipX = main.xs[index];
        tipY = main.ys[index];
        tipValue = main.raw[index];
        tipTime = from + (to - from) * index / (n - 1);
      } else {
        hover.set(0.0F);
      }
    }
    if (hover.get() > 0.01F && !Float.isNaN(tipValue)) {
      drawCursor(c, t, py, ph, px, pw, to, accent);
    }
  }

  private static void paint(
      Canvas c,
      float[] xs,
      float[] ys,
      int count,
      float base,
      int accent,
      float width,
      float fillAlpha,
      boolean glow) {
    if (count < 2) {
      return;
    }
    if (!c.curves()) {
      float half = width * 0.5F;
      for (int i = 0; i < count; i++) {
        c.roundRect(xs[i] - half, ys[i] - half, width, width, half, accent);
      }
      return;
    }
    c.area(xs, ys, count, base, Theme.withAlpha(accent, fillAlpha), Theme.withAlpha(accent, 0.0F));
    if (glow) {
      c.stroke(xs, ys, count, GLOW_WIDTH, GLOW_SOFT, Theme.withAlpha(accent, GLOW_ALPHA));
    }
    c.stroke(xs, ys, count, width, 0.0F, accent);
  }

  private void drawHead(Canvas c, int accent, float cx, float cy, boolean settled) {
    float phase = clock / PULSE_SECONDS;
    float ring = DOT + (PULSE_REACH - DOT) * phase;
    float fade = settled ? (1.0F - phase) * 0.35F : 0.0F;
    if (fade > 0.01F) {
      c.roundRect(
          cx - ring, cy - ring, ring * 2.0F, ring * 2.0F, ring, Theme.withAlpha(accent, fade));
    }
    c.roundRect(
        cx - DOT - 2.5F,
        cy - DOT - 2.5F,
        (DOT + 2.5F) * 2.0F,
        (DOT + 2.5F) * 2.0F,
        DOT + 2.5F,
        Theme.withAlpha(accent, 0.24F));
    c.roundRect(cx - DOT, cy - DOT, DOT * 2.0F, DOT * 2.0F, DOT, accent);
  }

  private static float snap(float value, float pixel) {
    return Math.round(value / pixel) * pixel;
  }

  private static int smooth(float[] source, float[] target, float[] work, int radius) {
    int n = source.length;
    int first = 0;
    while (first < n && Float.isNaN(source[first])) {
      first++;
    }
    for (int i = 0; i < first; i++) {
      target[i] = Float.NaN;
    }
    if (first >= n) {
      return n;
    }
    System.arraycopy(source, first, work, first, n - first);
    for (int pass = 0; pass < 3; pass++) {
      for (int i = first; i < n; i++) {
        float sum = 0.0F;
        for (int k = -radius; k <= radius; k++) {
          sum += work[Math.max(first, Math.min(n - 1, i + k))];
        }
        target[i] = sum / (radius * 2 + 1);
      }
      System.arraycopy(target, first, work, first, n - first);
    }
    return first;
  }

  private void drawGrid(
      Canvas c, Theme t, float px, float py, float pw, float ph, float low, float range) {
    for (int i = 0; i <= GRID_LINES; i++) {
      float f = i / (float) GRID_LINES;
      float gy = py + ph * f;
      c.roundRect(px, gy - 0.5F, pw, 1.0F, 0.5F, t.hair());
      String label = Money.format(low + range * (1.0F - f));
      float ly =
          Math.max(
              py - 4.0F,
              Math.min(
                  py + ph - c.lineHeight(FontStyle.CAPTION) + 4.0F,
                  gy - c.lineHeight(FontStyle.CAPTION) * 0.5F));
      c.text(
          FontStyle.CAPTION,
          label,
          px - 10.0F - c.textWidth(FontStyle.CAPTION, label),
          ly,
          t.grey());
    }
  }

  private void drawTicks(Canvas c, Theme t, long from, long to, float px, float base, float pw) {
    for (int i = 0; i <= TICKS; i++) {
      float f = i / (float) TICKS;
      String label = i == TICKS ? "now" : ago(to - (long) (from + (to - from) * f));
      float width = c.textWidth(FontStyle.CAPTION, label);
      float lx = px + pw * f - width * (i == 0 ? 0.0F : i == TICKS ? 1.0F : 0.5F);
      c.text(FontStyle.CAPTION, label, lx, base + 8.0F, t.grey());
    }
  }

  private void drawCursor(
      Canvas c, Theme t, float py, float ph, float px, float pw, long to, int accent) {
    float strength = hover.get();
    c.roundRectGradient(
        tipX - 0.5F,
        py,
        1.0F,
        ph,
        0.0F,
        Theme.withAlpha(t.ink(), 0.0F),
        Theme.withAlpha(t.ink(), 0.26F * strength));
    float halo = HOVER_DOT + 4.5F;
    c.roundRect(
        tipX - halo,
        tipY - halo,
        halo * 2.0F,
        halo * 2.0F,
        halo,
        Theme.withAlpha(accent, 0.2F * strength));
    c.roundRect(
        tipX - HOVER_DOT - 1.5F,
        tipY - HOVER_DOT - 1.5F,
        (HOVER_DOT + 1.5F) * 2.0F,
        (HOVER_DOT + 1.5F) * 2.0F,
        HOVER_DOT + 1.5F,
        Theme.withAlpha(t.paper() | 0xFF000000, strength));
    c.roundRect(
        tipX - HOVER_DOT,
        tipY - HOVER_DOT,
        HOVER_DOT * 2.0F,
        HOVER_DOT * 2.0F,
        HOVER_DOT,
        Theme.withAlpha(accent, strength));

    String value = "$" + Money.format(tipValue);
    String when = tipTime >= to - 1L ? "now" : ago(to - tipTime) + " ago";
    float vw = c.textWidth(FontStyle.ROW, value);
    float ww = c.textWidth(FontStyle.CAPTION, when);
    float tw = Math.max(vw, ww) + TIP_PAD * 2.0F;
    float th =
        c.lineHeight(FontStyle.ROW) + c.lineHeight(FontStyle.CAPTION) + TIP_PAD * 2.0F - 2.0F;
    float tx = tipX + TIP_GAP + tw > px + pw ? tipX - TIP_GAP - tw : tipX + TIP_GAP;
    float ty = Math.max(py, Math.min(py + ph - th, tipY - th * 0.5F));
    c.pushAlpha(strength);
    c.shadow(tx, ty, tw, th, 10.0F, 12.0F, 4.0F, t.cardShadow());
    Depth.raised(c, t, tx, ty, tw, th, 10.0F, t.surface2());
    c.text(FontStyle.ROW, value, tx + TIP_PAD, ty + TIP_PAD - 2.0F, t.ink());
    c.text(
        FontStyle.CAPTION,
        when,
        tx + TIP_PAD,
        ty + TIP_PAD - 2.0F + c.lineHeight(FontStyle.ROW),
        t.grey());
    c.popAlpha();
  }

  public void sparkline(
      Canvas c,
      PriceHistory history,
      String id,
      int kind,
      long from,
      long to,
      float x,
      float y,
      float w,
      float h,
      int color) {
    int n = sparkPoints(w);
    history.sample(id, kind, from, to, mini.raw);
    renderSpark(c, n, x, y, w, h, color, SPARK_LINE);
  }

  public void curve(
      Canvas c,
      float[] data,
      int count,
      float x,
      float y,
      float w,
      float h,
      int color,
      float thickness) {
    int n = sparkPoints(w);
    for (int i = 0; i < n; i++) {
      float at = n == 1 ? count - 1 : (count - 1) * (float) i / (n - 1);
      int lo = (int) at;
      int hi = Math.min(count - 1, lo + 1);
      float a = data[lo];
      float b = data[hi];
      mini.raw[i] = Float.isNaN(a) || Float.isNaN(b) ? Float.NaN : a + (b - a) * (at - lo);
    }
    renderSpark(c, n, x, y, w, h, color, thickness);
  }

  private int sparkPoints(float w) {
    int n = Math.max(SPARK_MIN, Math.min(SPARK_MAX, (int) (w / SPARK_STEP)));
    mini.size(n);
    return n;
  }

  private void renderSpark(
      Canvas c, int n, float x, float y, float w, float h, int color, float thickness) {
    mini.first = smooth(mini.raw, mini.curve, mini.work, Math.max(1, n / 12));
    if (mini.first >= n) {
      return;
    }
    float low = Float.MAX_VALUE;
    float high = -Float.MAX_VALUE;
    for (int i = mini.first; i < n; i++) {
      low = Math.min(low, mini.curve[i]);
      high = Math.max(high, mini.curve[i]);
    }
    float span = high - low;
    float inner = h - thickness * 2.0F;
    int count = 0;
    for (int i = mini.first; i < n; i++) {
      float f = span <= 0.0F ? 0.5F : (mini.curve[i] - low) / span;
      drawX[count] = x + w * i / (n - 1);
      drawY[count++] = y + thickness + inner * (1.0F - f);
    }
    paint(c, drawX, drawY, count, y + h, color, thickness, SPARK_FILL, false);
  }

  public static String ago(long seconds) {
    long s = Math.max(0L, seconds);
    if (s < 90L) {
      return s + "s";
    }
    if (s < 5400L) {
      return Math.round(s / 60.0F) + "m";
    }
    if (s < 129_600L) {
      return Math.round(s / 3600.0F) + "h";
    }
    return Math.round(s / 86_400.0F) + "d";
  }
}
