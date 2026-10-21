package me.cxiy.cxvisuals.gui.spatial;

public final class Layer {
  public static final float SHADOW = -0.8F;
  public static final float CARD = 0.3F;
  public static final float ROW = 0.6F;
  public static final float POP = 0.5F;
  public static final float HUD_FAR = -0.3F;
  public static final float HUD_MID = 0.25F;
  public static final float HUD_NEAR = 0.6F;

  private static final float HEIGHT = 50.0F;

  private Layer() {}

  public static float height(float z) {
    return z * HEIGHT;
  }
}
