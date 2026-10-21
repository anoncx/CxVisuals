package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;

final class Kit {
  static final float WINDOW_RADIUS = 22.0F;
  static final float PAD = 20.0F;
  static final float PILL = 32.0F;
  static final float PILL_PAD = 18.0F;
  static final float GAP = 8.0F;
  static final float TIGHT_PAD = 11.0F;
  static final float TIGHT_GAP = 6.0F;
  static final int SHIFT = 1;
  static final int CONTROL = 2;
  static final int ESCAPE = 256;
  static final int ENTER = 257;
  static final int KEYPAD_ENTER = 335;

  private static final int WINDOW_SHADOW = 0x66000000;
  private static final float DISABLED = 0.38F;
  private static final float TOGGLE_INSET = 2.5F;
  private static final float TOGGLE_KNOB = 20.0F;
  private static final float TOGGLE_STRETCH = 6.0F;
  private static final int KNOB_SHADOW = 0x33000000;
  private static final float DOT = 5.0F;
  private static final float DOT_GAP = 6.0F;
  private static final int DOTS = 3;
  private static final float STAGGER = 0.03F;
  private static final float REVEAL = 0.24F;
  private static final int STAGGERED = 8;

  static final float TOGGLE_WIDTH = 42.0F;
  static final float TOGGLE_HEIGHT = 25.0F;

  private Kit() {}

  static void window(Canvas c, Theme t, float x, float y, float w, float h) {
    float radius = Math.min(WINDOW_RADIUS, Math.min(w, h) * 0.5F);
    c.shadow(x, y, w, h, radius, 44.0F, 18.0F, WINDOW_SHADOW);
    if (t.glass()) {
      c.glass(x, y, w, h, radius, t.paper());
    } else {
      c.roundRect(x, y, w, h, radius, t.paper());
    }
    c.roundRectBorder(x, y, w, h, radius, 1.0F, t.hair());
    c.rim(x, y, w, h, radius, 1.0F, radius * 1.4F, Depth.rimColor(t));
  }

  static float pillWidth(Canvas c, String label) {
    return pillWidth(c, label, PILL_PAD);
  }

  static float pillWidth(Canvas c, String label, float pad) {
    return (float) Math.ceil(c.textWidth(FontStyle.LABEL, label) + pad * 2.0F);
  }

  static void pill(
      Canvas c,
      Theme t,
      String label,
      float x,
      float y,
      float w,
      float h,
      float hover,
      float press,
      boolean filled,
      boolean enabled) {
    int base = filled ? t.ink() : t.surface2();
    int fill = Theme.mix(base, filled ? t.paper() : t.ink(), 0.08F * hover);
    int ink = filled ? t.paper() | 0xFF000000 : t.ink();
    if (!enabled) {
      c.pushAlpha(DISABLED);
    }
    c.pushTransform(1.0F - 0.04F * press, x + w * 0.5F, y + h * 0.5F);
    Depth.raised(c, t, x, y, w, h, h * 0.5F, fill);
    float width = c.textWidth(FontStyle.LABEL, label);
    if (width > w - 16.0F) {
      c.pushClip(x + 8.0F, y, w - 16.0F, h);
      c.text(FontStyle.LABEL, label, x + 8.0F, y + (h - c.lineHeight(FontStyle.LABEL)) * 0.5F, ink);
      c.popClip();
    } else {
      c.text(
          FontStyle.LABEL,
          label,
          x + (w - width) * 0.5F,
          y + (h - c.lineHeight(FontStyle.LABEL)) * 0.5F,
          ink);
    }
    c.popTransform();
    if (!enabled) {
      c.popAlpha();
    }
  }

  static void toggle(
      Canvas c, Theme t, float x, float y, float position, float tint, float stretch) {
    float knobWidth = TOGGLE_KNOB + TOGGLE_STRETCH * stretch;
    float knobX = x + TOGGLE_INSET + (TOGGLE_WIDTH - knobWidth - TOGGLE_INSET * 2.0F) * position;
    float knobY = y + TOGGLE_INSET;
    float amount = Math.max(0.0F, Math.min(1.0F, tint));
    c.roundRect(
        x,
        y,
        TOGGLE_WIDTH,
        TOGGLE_HEIGHT,
        TOGGLE_HEIGHT * 0.5F,
        Theme.mix(t.surface2(), t.ink(), amount));
    c.rim(
        x,
        y,
        TOGGLE_WIDTH,
        TOGGLE_HEIGHT,
        TOGGLE_HEIGHT * 0.5F,
        1.0F,
        TOGGLE_HEIGHT,
        Theme.withAlpha(t.inset(), 1.0F - amount));
    c.shadow(knobX, knobY, knobWidth, TOGGLE_KNOB, TOGGLE_KNOB * 0.5F, 3.0F, 1.0F, KNOB_SHADOW);
    Depth.knob(
        c,
        knobX,
        knobY,
        knobWidth,
        TOGGLE_KNOB,
        TOGGLE_KNOB * 0.5F,
        Theme.mix(0xFFFFFFFF, t.knobOn(), amount));
  }

  static void dots(Canvas c, Theme t, float centreX, float centreY, float clock) {
    float span = DOTS * DOT + (DOTS - 1) * DOT_GAP;
    for (int i = 0; i < DOTS; i++) {
      float wave = 0.5F + 0.5F * (float) Math.sin(clock * 5.0F - i * 0.9F);
      int ink = i == 0 ? Theme.mix(t.ink(), t.red(), wave) : t.ink();
      c.roundRect(
          centreX - span * 0.5F + i * (DOT + DOT_GAP),
          centreY - DOT * 0.5F,
          DOT,
          DOT,
          DOT * 0.5F,
          Theme.withAlpha(ink, 0.25F + 0.75F * wave));
    }
  }

  static float reveal(float age, int order) {
    float t = (age - Math.min(order, STAGGERED) * STAGGER) / REVEAL;
    return t <= 0.0F ? 0.0F : t >= 1.0F ? 1.0F : (float) Easing.snappy(t);
  }

  static boolean inside(float mx, float my, float x, float y, float w, float h) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
  }
}
