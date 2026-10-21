package me.cxiy.cxvisuals.gui;

public record Theme(
    int paper,
    int surface,
    int surface2,
    int well,
    int edge,
    int lift,
    int raised,
    int ink,
    int ink2,
    int grey,
    int hair,
    int red,
    int alert,
    int dotOff,
    int focus,
    int shadow,
    int knobOn,
    int inset,
    int cardShadow,
    float radius,
    boolean glass) {
  private static final float GLASS_CARD_BASE = 0.25F;
  private static final float GLASS_CARD_RANGE = 0.45F;

  public static final Theme DARK =
      new Theme(
          0xFF000000,
          0xFF1C1C1E,
          0xFF2C2C2E,
          0xFF111113,
          0x1CFFFFFF,
          0x00000000,
          0xFF5A5A5E,
          0xFFFFFFFF,
          0xFFD1D1D6,
          0xFF8E8E93,
          0x1AFFFFFF,
          0xFFD71921,
          0xFFFF5F57,
          0x24FFFFFF,
          0x73FFFFFF,
          0x80000000,
          0xFF000000,
          0x8C000000,
          0x73000000,
          14.0F,
          false);

  public static final Theme LIGHT =
      new Theme(
          0xFFF4F4F2,
          0xFFFFFFFF,
          0xFFE8E8E5,
          0xFFF0F0EE,
          0xFFFFFFFF,
          0x12000000,
          0xFFFFFFFF,
          0xFF000000,
          0xFF3A3A3C,
          0xFF8E8E93,
          0x14000000,
          0xFFD71921,
          0xFFC21219,
          0x21000000,
          0x59000000,
          0x26000000,
          0xFFFFFFFF,
          0x24000000,
          0x00000000,
          14.0F,
          false);

  public boolean translucent() {
    return glass || (paper >>> 24) < 255;
  }

  public Theme flat() {
    return translucent()
        ? this
        : new Theme(
            paper,
            paper,
            surface2,
            well,
            edge,
            lift,
            raised,
            ink,
            ink2,
            grey,
            hair,
            red,
            alert,
            dotOff,
            focus,
            shadow,
            knobOn,
            inset,
            cardShadow,
            radius,
            false);
  }

  public Theme clear(float opacity) {
    return seeThrough(withAlpha(paper, opacity), withAlpha(surface, opacity), false);
  }

  public Theme glass(float opacity) {
    float amount = Math.max(0.0F, Math.min(1.0F, opacity));
    return seeThrough(
        withAlpha(paper, amount),
        withAlpha(surface, GLASS_CARD_BASE + GLASS_CARD_RANGE * amount),
        true);
  }

  private Theme seeThrough(int paperColor, int surfaceColor, boolean asGlass) {
    return new Theme(
        paperColor,
        surfaceColor,
        surface2,
        well,
        edge,
        0,
        raised,
        ink,
        ink2,
        grey,
        hair,
        red,
        alert,
        dotOff,
        focus,
        shadow,
        knobOn,
        inset,
        0,
        radius,
        asGlass);
  }

  public static int withAlpha(int argb, float alpha) {
    int a = Math.round((argb >>> 24) * Math.max(0.0F, Math.min(1.0F, alpha)));
    return (a << 24) | (argb & 0x00FFFFFF);
  }

  public static int mix(int from, int to, float t) {
    if (t <= 0.0F) {
      return from;
    }
    if (t >= 1.0F) {
      return to;
    }
    int a = channel(from >>> 24, to >>> 24, t);
    int r = channel((from >> 16) & 0xFF, (to >> 16) & 0xFF, t);
    int g = channel((from >> 8) & 0xFF, (to >> 8) & 0xFF, t);
    int b = channel(from & 0xFF, to & 0xFF, t);
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  private static int channel(int from, int to, float t) {
    return Math.round(from + (to - from) * t);
  }
}
