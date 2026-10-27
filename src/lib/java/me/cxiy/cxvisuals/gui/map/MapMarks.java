package me.cxiy.cxvisuals.gui.map;

import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;

public final class MapMarks {
  public static final int SHADOW = 0x66000000;
  public static final int WHITE = 0xFFFFFFFF;

  private static final float[] ARROW_X = {0.0F, 0.72F, 0.0F, -0.72F, 0.0F};
  private static final float[] ARROW_Y = {-1.0F, 0.78F, 0.36F, 0.78F, -1.0F};
  private static final float[] XS = new float[ARROW_X.length];
  private static final float[] YS = new float[ARROW_X.length];
  private static final float[] LINE_X = new float[2];
  private static final float[] LINE_Y = new float[2];
  private static final int FILL = 8;

  private MapMarks() {}

  public static void player(Canvas c, float x, float y, float size, float radians, int fill) {
    float cos = (float) Math.cos(radians);
    float sin = (float) Math.sin(radians);
    c.shadow(
        x - size * 0.6F,
        y - size * 0.6F,
        size * 1.2F,
        size * 1.2F,
        size * 0.6F,
        6.0F,
        1.5F,
        SHADOW);
    if (c.curves()) {
      for (int i = 0; i < ARROW_X.length; i++) {
        float ax = ARROW_X[i] * size;
        float ay = ARROW_Y[i] * size;
        XS[i] = x + ax * cos - ay * sin;
        YS[i] = y + ax * sin + ay * cos;
      }
      c.stroke(XS, YS, XS.length, size * 0.78F, 1.2F, 0x59000000);
      c.stroke(XS, YS, XS.length, size * 0.52F, 0.5F, WHITE);
      c.stroke(XS, YS, XS.length, size * 0.22F, 0.4F, fill);
      for (int k = 1; k < FILL; k++) {
        float f = k / (float) FILL;
        float bx = f < 0.5F ? lerp(XS[3], XS[2], f * 2.0F) : lerp(XS[2], XS[1], f * 2.0F - 1.0F);
        float by = f < 0.5F ? lerp(YS[3], YS[2], f * 2.0F) : lerp(YS[2], YS[1], f * 2.0F - 1.0F);
        LINE_X[0] = XS[0];
        LINE_Y[0] = YS[0];
        LINE_X[1] = bx;
        LINE_Y[1] = by;
        c.stroke(LINE_X, LINE_Y, 2, size * 0.3F, 0.4F, fill);
      }
      return;
    }
    c.roundRect(x - size * 0.45F, y - size * 0.45F, size * 0.9F, size * 0.9F, size * 0.45F, fill);
  }

  private static float lerp(float a, float b, float t) {
    return a + (b - a) * t;
  }

  public static void waypoint(Canvas c, float x, float y, float size, int color, float alpha) {
    float half = size * 0.5F;
    float ring = Math.max(1.0F, size * 0.18F);
    c.shadow(
        x - half, y - half, size, size, size * 0.32F, 5.0F, 1.0F, Theme.withAlpha(SHADOW, alpha));
    c.roundRect(
        x - half - ring,
        y - half - ring,
        size + ring * 2.0F,
        size + ring * 2.0F,
        size * 0.32F + ring,
        Theme.withAlpha(WHITE, alpha));
    c.roundRect(x - half, y - half, size, size, size * 0.32F, Theme.withAlpha(color, alpha));
  }

  public static void death(Canvas c, float x, float y, float size, int color, float alpha) {
    waypoint(c, x, y, size, color, alpha);
    float bar = size * 0.5F;
    float thin = Math.max(1.0F, size * 0.16F);
    c.roundRect(
        x - thin * 0.5F, y - bar * 0.5F, thin, bar, thin * 0.5F, Theme.withAlpha(WHITE, alpha));
    c.roundRect(
        x - bar * 0.5F, y - bar * 0.2F, bar, thin, thin * 0.5F, Theme.withAlpha(WHITE, alpha));
  }

  public static void friend(
      Canvas c, Theme t, int entityId, String name, float x, float y, float size, float alpha) {
    float half = size * 0.5F;
    float ring = Math.max(1.0F, size * 0.12F);
    c.pushAlpha(alpha);
    c.shadow(x - half, y - half, size, size, half, 6.0F, 1.5F, SHADOW);
    c.roundRect(
        x - half - ring,
        y - half - ring,
        size + ring * 2.0F,
        size + ring * 2.0F,
        half + ring,
        WHITE);
    if (entityId < 0 || !c.face(entityId, x - half, y - half, size, half)) {
      c.roundRect(x - half, y - half, size, size, half, t.surface2());
      String initial = name == null || name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
      float width = c.textWidth(FontStyle.CAPTION, initial);
      c.text(
          FontStyle.CAPTION,
          initial,
          x - width * 0.5F,
          y - c.lineHeight(FontStyle.CAPTION) * 0.5F,
          t.ink());
    }
    c.popAlpha();
  }

  public static void other(Canvas c, Theme t, float x, float y, float size) {
    float half = size * 0.5F;
    c.roundRect(
        x - half - 1.0F,
        y - half - 1.0F,
        size + 2.0F,
        size + 2.0F,
        half + 1.0F,
        Theme.withAlpha(t.paper(), 0.8F));
    c.roundRect(x - half, y - half, size, size, half, t.ink2());
  }

  public static void label(Canvas c, Theme t, String text, float x, float y, float alpha) {
    float width = c.textWidth(FontStyle.CAPTION, text) + 12.0F;
    float height = c.lineHeight(FontStyle.CAPTION) + 6.0F;
    float left = x - width * 0.5F;
    c.pushAlpha(alpha);
    c.shadow(left, y, width, height, height * 0.5F, 8.0F, 2.0F, SHADOW);
    c.roundRect(left, y, width, height, height * 0.5F, Theme.withAlpha(t.paper(), 0.86F));
    c.roundRectBorder(left, y, width, height, height * 0.5F, 1.0F, t.hair());
    c.text(FontStyle.CAPTION, text, left + 6.0F, y + 3.0F, t.ink());
    c.popAlpha();
  }
}
