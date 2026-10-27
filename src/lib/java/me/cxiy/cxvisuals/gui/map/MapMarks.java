package me.cxiy.cxvisuals.gui.map;

import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;

public final class MapMarks {
  public static final int SHADOW = 0x59000000;
  public static final int WHITE = 0xFFFFFFFF;

  private static final int BEAM_RAYS = 9;
  private static final float BEAM_SPREAD = 0.075F;
  private static final float BEAM_LENGTH = 3.4F;
  private static final float BEAM_ALPHA = 0.15F;
  private static final float[] XS = new float[2];
  private static final float[] YS = new float[2];

  private MapMarks() {}

  public static void player(Canvas c, Theme t, float x, float y, float size, float radians) {
    if (c.curves()) {
      for (int i = 0; i < BEAM_RAYS; i++) {
        float offset = i - (BEAM_RAYS - 1) * 0.5F;
        float angle = radians + offset * BEAM_SPREAD;
        float reach = size * BEAM_LENGTH * (1.0F - Math.abs(offset) * 0.06F);
        XS[0] = x;
        YS[0] = y;
        XS[1] = x + (float) Math.sin(angle) * reach;
        YS[1] = y - (float) Math.cos(angle) * reach;
        c.stroke(XS, YS, 2, size * 0.42F, size * 0.6F, Theme.withAlpha(t.red(), BEAM_ALPHA));
      }
    }
    float ring = size * 0.62F;
    float dot = size * 0.4F;
    c.shadow(x - ring, y - ring, ring * 2.0F, ring * 2.0F, ring, 6.0F, 1.5F, SHADOW);
    c.roundRect(x - ring, y - ring, ring * 2.0F, ring * 2.0F, ring, WHITE);
    c.roundRect(x - dot, y - dot, dot * 2.0F, dot * 2.0F, dot, t.red());
  }

  public static void waypoint(Canvas c, float x, float y, float size, int color, float alpha) {
    float half = size * 0.5F;
    float ring = Math.max(1.5F, size * 0.2F);
    float outer = half + ring;
    c.shadow(
        x - outer,
        y - outer,
        outer * 2.0F,
        outer * 2.0F,
        outer * 0.45F,
        5.0F,
        1.0F,
        Theme.withAlpha(SHADOW, alpha));
    c.roundRect(
        x - outer,
        y - outer,
        outer * 2.0F,
        outer * 2.0F,
        outer * 0.45F,
        Theme.withAlpha(WHITE, alpha));
    c.roundRect(x - half, y - half, size, size, half * 0.45F, Theme.withAlpha(color, alpha));
  }

  public static void death(Canvas c, float x, float y, float size, int color, float alpha) {
    waypoint(c, x, y, size, color, alpha);
    if (!c.curves()) {
      return;
    }
    float arm = size * 0.24F;
    float width = Math.max(1.2F, size * 0.14F);
    int ink = Theme.withAlpha(WHITE, alpha);
    XS[0] = x - arm;
    YS[0] = y - arm;
    XS[1] = x + arm;
    YS[1] = y + arm;
    c.stroke(XS, YS, 2, width, 0.4F, ink);
    XS[0] = x + arm;
    XS[1] = x - arm;
    c.stroke(XS, YS, 2, width, 0.4F, ink);
  }

  public static void friend(
      Canvas c, Theme t, int entityId, String name, float x, float y, float size, float alpha) {
    float half = size * 0.5F;
    float ring = Math.max(1.5F, size * 0.1F);
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
      c.roundRect(x - half, y - half, size, size, half, t.surface2() | 0xFF000000);
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
    c.roundRect(x - half - 1.5F, y - half - 1.5F, size + 3.0F, size + 3.0F, half + 1.5F, WHITE);
    c.roundRect(x - half, y - half, size, size, half, 0xFF1C1C1E);
  }

  public static void mob(Canvas c, Theme t, float x, float y, float size, boolean hostile) {
    float half = size * 0.5F;
    int fill = hostile ? t.red() : 0xFFE5E5EA;
    int ring = hostile ? 0xE6FFFFFF : 0x99000000;
    c.roundRect(x - half - 1.0F, y - half - 1.0F, size + 2.0F, size + 2.0F, half + 1.0F, ring);
    c.roundRect(x - half, y - half, size, size, half, fill);
  }

  public static void label(Canvas c, Theme t, String text, float x, float y, float alpha) {
    float width = c.textWidth(FontStyle.CAPTION, text) + 14.0F;
    float height = c.lineHeight(FontStyle.CAPTION) + 7.0F;
    float left = x - width * 0.5F;
    c.pushAlpha(alpha);
    c.shadow(left, y, width, height, height * 0.5F, 8.0F, 2.0F, SHADOW);
    c.roundRect(
        left, y, width, height, height * 0.5F, Theme.withAlpha(t.paper() | 0xFF000000, 0.9F));
    c.roundRectBorder(left, y, width, height, height * 0.5F, 1.0F, t.hair());
    c.text(FontStyle.CAPTION, text, left + 7.0F, y + 3.5F, t.ink());
    c.popAlpha();
  }
}
