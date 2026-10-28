package me.cxiy.cxvisuals.gui.map;

import java.util.List;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.TextWrap;
import me.cxiy.cxvisuals.gui.Theme;

final class SheetDraw {
  static final float BUTTON = 34.0F;
  static final int OPAQUE = 0xFF000000;

  private SheetDraw() {}

  static float pill(
      Canvas c,
      Theme t,
      Zones zones,
      String label,
      float right,
      float y,
      boolean primary,
      boolean disabled,
      float mx,
      float my,
      Runnable action) {
    float w = c.textWidth(FontStyle.LABEL, label) + 30.0F;
    float x = right - w;
    boolean over = !disabled && ShareWindowMath.inside(mx, my, x, y, w, BUTTON);
    int fill =
        primary
            ? Theme.withAlpha(t.ink(), disabled ? 0.25F : over ? 0.88F : 1.0F)
            : Theme.withAlpha(t.ink(), over ? 0.14F : 0.07F);
    c.roundRect(x, y, w, BUTTON, BUTTON * 0.5F, fill);
    c.text(
        FontStyle.LABEL,
        label,
        x + 15.0F,
        y + (BUTTON - c.lineHeight(FontStyle.LABEL)) * 0.5F,
        primary ? t.paper() | OPAQUE : t.ink());
    if (!disabled) {
      zones.add(x, y, w, BUTTON, action);
    }
    return w;
  }

  static void paragraph(
      Canvas c, FontStyle font, String text, float x, float y, float max, int color) {
    float line = c.lineHeight(font);
    List<String> rows = TextWrap.lines(s -> c.textWidth(font, s), text, max);
    for (int i = 0; i < rows.size(); i++) {
      c.text(font, rows.get(i), x, y + i * line, color);
    }
  }

  static float paragraphHeight(Canvas c, FontStyle font, String text, float max) {
    return TextWrap.lines(s -> c.textWidth(font, s), text, max).size() * c.lineHeight(font);
  }

  static void toggle(Canvas c, Theme t, float x, float y, boolean on) {
    float width = 38.0F;
    float height = 22.0F;
    float knob = height - 6.0F;
    c.roundRect(x, y, width, height, height * 0.5F, on ? t.ink() : t.surface2());
    c.roundRect(
        x + 3.0F + (on ? width - knob - 6.0F : 0.0F),
        y + 3.0F,
        knob,
        knob,
        knob * 0.5F,
        on ? t.paper() | OPAQUE : t.ink2());
  }
}
