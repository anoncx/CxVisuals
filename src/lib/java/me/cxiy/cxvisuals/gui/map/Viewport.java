package me.cxiy.cxvisuals.gui.map;

import me.cxiy.cxvisuals.modules.map.TileKey;

public final class Viewport {
  public static final float MIN_SCALE = 1.0F / 48.0F;
  public static final float MAX_SCALE = 40.0F;
  private static final float LEVEL_BIAS = 0.3F;

  public double centreX;
  public double centreZ;
  public float scale = 1.0F;
  public float angle;
  public float screenX;
  public float screenY;

  private float cos = 1.0F;
  private float sin;

  public void set(double x, double z, float pixelsPerBlock, float radians) {
    centreX = x;
    centreZ = z;
    scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, pixelsPerBlock));
    angle = radians;
    cos = (float) Math.cos(radians);
    sin = (float) Math.sin(radians);
  }

  public void origin(float x, float y) {
    screenX = x;
    screenY = y;
  }

  public static float facing(float yawDegrees) {
    return (float) (Math.PI - Math.toRadians(yawDegrees));
  }

  public float screenX(double worldX, double worldZ) {
    float dx = (float) (worldX - centreX);
    float dz = (float) (worldZ - centreZ);
    return screenX + (dx * cos - dz * sin) * scale;
  }

  public float screenY(double worldX, double worldZ) {
    float dx = (float) (worldX - centreX);
    float dz = (float) (worldZ - centreZ);
    return screenY + (dx * sin + dz * cos) * scale;
  }

  public double worldX(float sx, float sy) {
    float dx = (sx - screenX) / scale;
    float dy = (sy - screenY) / scale;
    return centreX + dx * cos + dy * sin;
  }

  public double worldZ(float sx, float sy) {
    float dx = (sx - screenX) / scale;
    float dy = (sy - screenY) / scale;
    return centreZ - dx * sin + dy * cos;
  }

  public float cos() {
    return cos;
  }

  public float sin() {
    return sin;
  }

  public int level() {
    float levels = (float) (Math.log(1.0 / scale) / Math.log(2.0)) + LEVEL_BIAS;
    return Math.max(0, Math.min(TileKey.MAX_LEVEL, (int) Math.floor(levels)));
  }
}
