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
    PIN
  }

  public static final float BUTTON = 34.0F;
  private static final float STROKE = 1.6F;

  private MapChrome() {}

  public static void panel(Canvas c, Theme t, float x, float y, float w, float h, float radius) {
    c.shadow(x, y, w, h, radius, 22.0F, 8.0F, 0x59000000);
    c.roundRect(x, y, w, h, radius, Theme.withAlpha(t.paper() | 0xFF000000, 0.88F));
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

  public static void glyph(Canvas c, Glyph glyph, float cx, float cy, float r, int ink) {
    float s = STROKE;
    switch (glyph) {
      case PLUS -> {
        c.roundRect(cx - r, cy - s * 0.5F, r * 2.0F, s, s * 0.5F, ink);
        c.roundRect(cx - s * 0.5F, cy - r, s, r * 2.0F, s * 0.5F, ink);
      }
      case MINUS -> c.roundRect(cx - r, cy - s * 0.5F, r * 2.0F, s, s * 0.5F, ink);
      case LOCATE -> {
        float ring = r * 1.3F;
        c.roundRectBorder(
            cx - ring * 0.5F * 1.2F,
            cy - ring * 0.5F * 1.2F,
            ring * 1.2F,
            ring * 1.2F,
            ring * 0.6F,
            s,
            ink);
        c.roundRect(cx - r * 0.28F, cy - r * 0.28F, r * 0.56F, r * 0.56F, r * 0.28F, ink);
        c.roundRect(cx - s * 0.5F, cy - r * 1.15F, s, r * 0.35F, s * 0.5F, ink);
        c.roundRect(cx - s * 0.5F, cy + r * 0.8F, s, r * 0.35F, s * 0.5F, ink);
        c.roundRect(cx - r * 1.15F, cy - s * 0.5F, r * 0.35F, s, s * 0.5F, ink);
        c.roundRect(cx + r * 0.8F, cy - s * 0.5F, r * 0.35F, s, s * 0.5F, ink);
      }
      case SHARE -> {
        c.roundRectBorder(cx - r * 0.8F, cy - r * 0.2F, r * 1.6F, r * 1.2F, r * 0.3F, s, ink);
        c.roundRect(cx - s * 0.5F, cy - r * 1.05F, s, r * 1.25F, s * 0.5F, ink);
        c.roundRect(cx - r * 0.42F, cy - r * 0.78F, r * 0.84F, s, s * 0.5F, ink);
      }
      case TRASH -> {
        c.roundRect(cx - r * 0.85F, cy - r * 0.7F, r * 1.7F, s, s * 0.5F, ink);
        c.roundRect(cx - r * 0.3F, cy - r * 0.98F, r * 0.6F, s, s * 0.5F, ink);
        c.roundRectBorder(cx - r * 0.6F, cy - r * 0.5F, r * 1.2F, r * 1.45F, r * 0.25F, s, ink);
      }
      case CLOSE -> {
        c.roundRect(cx - r * 0.75F, cy - s * 0.5F, r * 1.5F, s, s * 0.5F, ink);
        c.roundRect(cx - s * 0.5F, cy - r * 0.75F, s, r * 1.5F, s * 0.5F, ink);
      }
      case SIDEBAR -> {
        c.roundRectBorder(cx - r, cy - r * 0.8F, r * 2.0F, r * 1.6F, r * 0.35F, s, ink);
        c.roundRect(cx + r * 0.2F, cy - r * 0.8F, s, r * 1.6F, 0.0F, ink);
      }
      case PIN -> {
        c.roundRectBorder(cx - r * 0.6F, cy - r, r * 1.2F, r * 1.2F, r * 0.6F, s, ink);
        c.roundRect(cx - s * 0.5F, cy + r * 0.1F, s, r * 0.9F, s * 0.5F, ink);
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
