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

  public static int fog(Theme t) {
    int base = t.surface2() | 0xFF000000;
    return Theme.mix(base, t.ink() | 0xFF000000, dark(t) ? DARK_LIFT : LIGHT_LIFT);
  }
}
