package me.cxiy.cxvisuals.gui.map;

import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;

public final class MapChrome {
  public enum Glyph {
    PLUS,
    MINUS,
    LOCATE,
    SHARE,
    TRASH,
    CLOSE,
    SIDEBAR,
    PIN,
    DOWNLOAD,
    CUBE,
    FIND,
    GROUP,
    EXPLORE,
    REGIONS
  }

  public static final float BUTTON = 34.0F;
  private static final float STROKE = 1.6F;

  private MapChrome() {}

  public interface Frost {
    void draw(float x, float y, float w, float h, float radius);
  }

  private static final float FROST_VEIL = 0.5F;

  public static void panel(
      Canvas c, Theme t, Frost frost, float x, float y, float w, float h, float radius) {
    c.shadow(x, y, w, h, radius, 22.0F, 8.0F, 0x59000000);
    if (t.glass() && frost != null) {
      frost.draw(x, y, w, h, radius);
      c.roundRect(x, y, w, h, radius, Theme.withAlpha(t.paper() | 0xFF000000, FROST_VEIL));
    } else {
      c.roundRect(x, y, w, h, radius, t.paper());
    }
    c.roundRectBorder(x, y, w, h, radius, 1.0F, t.hair());
    c.rim(x, y, w, h, radius, 1.0F, Math.min(h, radius * 1.4F), Depth.rimColor(t));
  }

  public static boolean hit(float mx, float my, float x, float y, float w, float h) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
  }

  public static void button(
      Canvas c, Theme t, Glyph glyph, float x, float y, float size, float hover, boolean danger) {
    if (hover > 0.01F) {
      c.roundRect(
          x + 3.0F,
          y + 3.0F,
          size - 6.0F,
          size - 6.0F,
          (size - 6.0F) * 0.5F,
          Theme.withAlpha(t.ink(), 0.08F * hover));
    }
    int ink = danger ? Theme.mix(t.ink2(), t.red(), hover) : Theme.mix(t.ink2(), t.ink(), hover);
    glyph(c, glyph, x + size * 0.5F, y + size * 0.5F, size * 0.36F, ink);
  }

  private static final float[] XS = new float[6];
  private static final float[] YS = new float[6];

  private static void line(Canvas c, int ink, float... points) {
    int count = points.length / 2;
    for (int i = 0; i < count; i++) {
      XS[i] = points[i * 2];
      YS[i] = points[i * 2 + 1];
    }
    c.stroke(XS, YS, count, STROKE, 0.5F, ink);
  }

  public static void glyph(Canvas c, Glyph glyph, float cx, float cy, float r, int ink) {
    float s = STROKE;
    if (!c.curves()) {
      c.roundRect(cx - r * 0.4F, cy - r * 0.4F, r * 0.8F, r * 0.8F, r * 0.4F, ink);
      return;
    }
    switch (glyph) {
      case PLUS -> {
        line(c, ink, cx - r, cy, cx + r, cy);
        line(c, ink, cx, cy - r, cx, cy + r);
      }
      case MINUS -> line(c, ink, cx - r, cy, cx + r, cy);
      case CLOSE -> {
        line(c, ink, cx - r * 0.7F, cy - r * 0.7F, cx + r * 0.7F, cy + r * 0.7F);
        line(c, ink, cx + r * 0.7F, cy - r * 0.7F, cx - r * 0.7F, cy + r * 0.7F);
      }
      case LOCATE -> {
        float w = r * 0.95F;
        line(
            c,
            ink,
            cx + w,
            cy - w,
            cx - w,
            cy - w * 0.05F,
            cx - w * 0.05F,
            cy + w * 0.05F,
            cx + w * 0.05F,
            cy + w,
            cx + w,
            cy - w);
      }
      case SHARE -> {
        line(
            c,
            ink,
            cx - r * 0.45F,
            cy - r * 0.15F,
            cx - r * 0.8F,
            cy - r * 0.15F,
            cx - r * 0.8F,
            cy + r,
            cx + r * 0.8F,
            cy + r,
            cx + r * 0.8F,
            cy - r * 0.15F,
            cx + r * 0.45F,
            cy - r * 0.15F);
        line(c, ink, cx, cy + r * 0.35F, cx, cy - r);
        line(c, ink, cx - r * 0.42F, cy - r * 0.6F, cx, cy - r, cx + r * 0.42F, cy - r * 0.6F);
      }
      case DOWNLOAD -> {
        line(c, ink, cx, cy - r, cx, cy + r * 0.3F);
        line(
            c,
            ink,
            cx - r * 0.45F,
            cy - r * 0.12F,
            cx,
            cy + r * 0.32F,
            cx + r * 0.45F,
            cy - r * 0.12F);
        line(
            c,
            ink,
            cx - r * 0.85F,
            cy + r * 0.35F,
            cx - r * 0.85F,
            cy + r,
            cx + r * 0.85F,
            cy + r,
            cx + r * 0.85F,
            cy + r * 0.35F);
      }
      case TRASH -> {
        line(c, ink, cx - r * 0.9F, cy - r * 0.6F, cx + r * 0.9F, cy - r * 0.6F);
        line(
            c,
            ink,
            cx - r * 0.3F,
            cy - r * 0.6F,
            cx - r * 0.3F,
            cy - r,
            cx + r * 0.3F,
            cy - r,
            cx + r * 0.3F,
            cy - r * 0.6F);
        line(
            c,
            ink,
            cx - r * 0.65F,
            cy - r * 0.6F,
            cx - r * 0.5F,
            cy + r,
            cx + r * 0.5F,
            cy + r,
            cx + r * 0.65F,
            cy - r * 0.6F);
      }
      case SIDEBAR -> {
        c.roundRectBorder(cx - r, cy - r * 0.8F, r * 2.0F, r * 1.6F, r * 0.35F, s, ink);
        line(c, ink, cx + r * 0.25F, cy - r * 0.8F, cx + r * 0.25F, cy + r * 0.8F);
      }
      case FIND -> {
        c.roundRectBorder(cx - r, cy - r, r * 1.5F, r * 1.5F, r * 0.75F, s, ink);
        line(c, ink, cx + r * 0.2F, cy + r * 0.2F, cx + r * 0.95F, cy + r * 0.95F);
      }
      case CUBE -> {
        float w = r * 0.92F;
        float q = r * 0.5F;
        line(
            c, ink, cx, cy - r, cx + w, cy - q, cx + w, cy + q, cx, cy + r, cx - w, cy + q, cx - w,
            cy - q);
        line(c, ink, cx - w, cy - q, cx, cy, cx + w, cy - q);
        line(c, ink, cx, cy, cx, cy + r);
        line(c, ink, cx - w, cy - q, cx, cy - r);
      }
      case GROUP -> {
        c.roundRectBorder(cx - r * 0.95F, cy - r * 0.85F, r * 0.9F, r * 0.9F, r * 0.45F, s, ink);
        c.roundRectBorder(cx + r * 0.05F, cy - r * 0.85F, r * 0.9F, r * 0.9F, r * 0.45F, s, ink);
        line(c, ink, cx - r, cy + r, cx - r * 0.85F, cy + r * 0.25F, cx - r * 0.1F, cy + r * 0.25F);
        line(c, ink, cx + r, cy + r, cx + r * 0.85F, cy + r * 0.25F, cx + r * 0.1F, cy + r * 0.25F);
      }
      case EXPLORE -> {
        c.roundRectBorder(cx - r, cy - r, r * 2.0F, r * 2.0F, r, s, ink);
        line(
            c,
            ink,
            cx - r * 0.35F,
            cy + r * 0.35F,
            cx + r * 0.55F,
            cy - r * 0.55F,
            cx + r * 0.1F,
            cy + r * 0.1F,
            cx - r * 0.35F,
            cy + r * 0.35F);
        c.roundRect(cx - r * 0.12F, cy - r * 0.12F, r * 0.24F, r * 0.24F, r * 0.12F, ink);
      }
      case REGIONS -> {
        float q = r * 0.34F;
        c.roundRectBorder(cx - r, cy - r, r * 2.0F, r * 2.0F, r * 0.28F, s, ink);
        c.roundRect(cx - r * 0.12F, cy - r, r * 0.24F, r * 2.0F, 0.0F, ink);
        c.roundRect(cx - r, cy - r * 0.12F, r * 2.0F, r * 0.24F, 0.0F, ink);
        c.roundRect(cx - r * 0.78F, cy - r * 0.78F, q, q, q * 0.3F, ink);
        c.roundRect(cx + r * 0.44F, cy + r * 0.44F, q, q, q * 0.3F, ink);
      }
      case PIN -> {
        c.roundRectBorder(cx - r * 0.6F, cy - r, r * 1.2F, r * 1.2F, r * 0.6F, s, ink);
        line(c, ink, cx - r * 0.42F, cy - r * 0.05F, cx, cy + r, cx + r * 0.42F, cy - r * 0.05F);
      }
    }
  }

  public static float chip(Canvas c, Theme t, String text, float x, float y, int dot) {
    float height = c.lineHeight(FontStyle.CAPTION) + 8.0F;
    float width = c.textWidth(FontStyle.CAPTION, text) + (dot != 0 ? 26.0F : 16.0F);
    c.roundRect(x, y, width, height, height * 0.5F, Theme.withAlpha(t.ink(), 0.07F));
    float textX = x + 8.0F;
    if (dot != 0) {
      c.roundRect(x + 9.0F, y + height * 0.5F - 3.0F, 6.0F, 6.0F, 3.0F, dot);
      textX += 10.0F;
    }
    c.text(FontStyle.CAPTION, text, textX, y + 4.0F, t.ink2());
    return width;
  }
}
