package me.cxiy.cxvisuals.gui.map;

import me.cxiy.cxvisuals.gui.Theme;

public final class MapStyle {
  private static final float DARK_LIFT = 0.13F;
  private static final float LIGHT_LIFT = 0.05F;

  private MapStyle() {}

  public static boolean dark(Theme t) {
    int ink = t.ink();
    return ((ink >> 16) & 0xFF) + ((ink >> 8) & 0xFF) + (ink & 0xFF) > 384;
  }

  public static int[] sky(Theme t) {
    boolean dark = dark(t);
    int top = dark ? 0xFF1C1D22 : 0xFFF6F6F8;
    int bottom = dark ? 0xFF0B0B0D : 0xFFE2E3E8;
    return new int[] {top, bottom};
  }

  public static int fog(Theme t) {
    int base = t.surface2() | 0xFF000000;
    return Theme.mix(base, t.ink() | 0xFF000000, dark(t) ? DARK_LIFT : LIGHT_LIFT);
  }
}
