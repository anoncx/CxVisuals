package me.cxiy.cxvisuals.gui;

public final class Depth {
  private static final float RIM_WIDTH = 1.0F;
  private static final float RIM_REACH = 1.4F;
  private static final float RIM_GAIN = 2.6F;
  private static final float INSET_WIDTH = 1.0F;

  private Depth() {}

  public static void raised(
      Canvas c, Theme t, float x, float y, float w, float h, float radius, int base) {
    c.roundRect(x, y, w, h, radius, base);
    c.rim(x, y, w, h, radius, RIM_WIDTH, Math.min(h, radius * RIM_REACH), rimColor(t));
  }

  private static final float EDGE_FADE = 22.0F;
  private static final float EDGE_SOLID = 8.0F;
  private static final float EDGE_SHOW = 6.0F;

  public static void scrollFade(
      Canvas c, Theme t, int plate, float x, float y, float w, float h, float scroll, float max) {
    if (t.translucent() || max <= 0.0F) {
      return;
    }
    int solid = plate | 0xFF000000;
    int clear = Theme.withAlpha(solid, 0.0F);
    float above = Math.min(1.0F, scroll / EDGE_SHOW);
    float below = Math.min(1.0F, (max - scroll) / EDGE_SHOW);
    float ramp = EDGE_FADE - EDGE_SOLID;
    if (above > 0.0F) {
      int near = Theme.withAlpha(solid, above);
      c.roundRect(x, y, w, EDGE_SOLID, 0.0F, near);
      c.roundRectGradient(x, y + EDGE_SOLID, w, ramp, 0.0F, near, clear);
    }
    if (below > 0.0F) {
      int near = Theme.withAlpha(solid, below);
      c.roundRect(x, y + h - EDGE_SOLID, w, EDGE_SOLID, 0.0F, near);
      c.roundRectGradient(x, y + h - EDGE_FADE, w, ramp, 0.0F, clear, near);
    }
  }

  public static int rimColor(Theme t) {
    int edge = t.edge();
    int alpha = Math.min(255, Math.round((edge >>> 24) * RIM_GAIN));
    return (alpha << 24) | (edge & 0x00FFFFFF);
  }

  public static void inset(
      Canvas c, Theme t, float x, float y, float w, float h, float radius, int base) {
    c.roundRect(x, y, w, h, radius, base);
    c.rim(x, y, w, h, radius, INSET_WIDTH, h, t.inset());
  }

  public static void card(Canvas c, Theme t, float x, float y, float w, float h) {
    if ((t.lift() >>> 24) != 0) {
      c.shadow(x, y, w, h, t.radius(), 22.0F, 8.0F, t.lift());
    }
    if ((t.cardShadow() >>> 24) != 0) {
      c.shadow(x, y, w, h, t.radius(), 12.0F, 5.0F, t.cardShadow());
    }
    raised(c, t, x, y, w, h, t.radius(), t.surface());
  }

  public static void knob(Canvas c, float x, float y, float w, float h, float radius, int base) {
    int top = Theme.mix(base, (base & 0xFF000000) | 0x00FFFFFF, 0.10F);
    int bottom = Theme.mix(base, base & 0xFF000000, 0.12F);
    c.roundRectGradient(x, y, w, h, radius, top, bottom);
  }
}
