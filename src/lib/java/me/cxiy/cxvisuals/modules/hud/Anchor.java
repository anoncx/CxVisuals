package me.cxiy.cxvisuals.modules.hud;

public enum Anchor {
  NATIVE(0.0F, 0.0F),
  TOP_LEFT(0.0F, 0.0F),
  TOP(0.5F, 0.0F),
  TOP_RIGHT(1.0F, 0.0F),
  LEFT(0.0F, 0.5F),
  CENTER(0.5F, 0.5F),
  RIGHT(1.0F, 0.5F),
  BOTTOM_LEFT(0.0F, 1.0F),
  BOTTOM(0.5F, 1.0F),
  BOTTOM_RIGHT(1.0F, 1.0F);

  private static final Anchor[] GRID = {
    TOP_LEFT, TOP, TOP_RIGHT, LEFT, CENTER, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT
  };
  private static final float THIRD = 1.0F / 3.0F;

  public final float fx;
  public final float fy;

  Anchor(float fx, float fy) {
    this.fx = fx;
    this.fy = fy;
  }

  public static Anchor nearest(float centreX, float centreY) {
    return GRID[band(centreY) * 3 + band(centreX)];
  }

  public static float pivot(float fraction) {
    return band(fraction) * 0.5F;
  }

  private static int band(float fraction) {
    return fraction < THIRD ? 0 : fraction > 1.0F - THIRD ? 2 : 1;
  }
}
