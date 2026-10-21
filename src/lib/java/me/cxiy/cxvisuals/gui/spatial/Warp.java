package me.cxiy.cxvisuals.gui.spatial;

import org.joml.Matrix4f;

public final class Warp {
  private static final float MIN_DISTANCE = 1.0F;
  private static final float MIN_DET = 1.0e-9F;

  private float pivotX;
  private float pivotY;
  private float distance = 1.0F;
  private float sinYaw;
  private float cosYaw = 1.0F;
  private float sinPitch;
  private float cosPitch = 1.0F;
  private boolean flat = true;
  private float outX;
  private float outY;
  private float outW = 1.0F;
  private int left;
  private int top;
  private int right;
  private int bottom;

  public void set(float yaw, float pitch, float centreX, float centreY, float focal) {
    boolean valid =
        Float.isFinite(yaw)
            && Float.isFinite(pitch)
            && Float.isFinite(centreX)
            && Float.isFinite(centreY)
            && focal >= MIN_DISTANCE
            && Float.isFinite(focal);
    flat = !valid || (yaw == 0.0F && pitch == 0.0F);
    pivotX = centreX;
    pivotY = centreY;
    distance = focal;
    if (flat) {
      sinYaw = 0.0F;
      cosYaw = 1.0F;
      sinPitch = 0.0F;
      cosPitch = 1.0F;
      return;
    }
    sinYaw = (float) Math.sin(yaw);
    cosYaw = (float) Math.cos(yaw);
    sinPitch = (float) Math.sin(pitch);
    cosPitch = (float) Math.cos(pitch);
  }

  public void copy(Warp other) {
    pivotX = other.pivotX;
    pivotY = other.pivotY;
    distance = other.distance;
    sinYaw = other.sinYaw;
    cosYaw = other.cosYaw;
    sinPitch = other.sinPitch;
    cosPitch = other.cosPitch;
    flat = other.flat;
  }

  public Matrix4f matrix(Matrix4f dest) {
    if (flat) {
      return dest.identity();
    }
    return dest.set(
            cosYaw,
            sinPitch * sinYaw,
            0.0F,
            cosPitch * sinYaw / distance,
            0.0F,
            cosPitch,
            0.0F,
            -sinPitch / distance,
            sinYaw,
            -sinPitch * cosYaw,
            0.0F,
            (1.0F - cosPitch * cosYaw) / distance,
            0.0F,
            0.0F,
            0.0F,
            1.0F)
        .translateLocal(pivotX, pivotY, 0.0F)
        .translate(-pivotX, -pivotY, 0.0F);
  }

  public boolean flat() {
    return flat;
  }

  public float x() {
    return outX;
  }

  public float y() {
    return outY;
  }

  public float w() {
    return outW;
  }

  public void map(float x, float y, float height) {
    if (flat) {
      outX = x;
      outY = y;
      outW = 1.0F;
      return;
    }
    float dx = x - pivotX;
    float dy = y - pivotY;
    float x1 = dx * cosYaw + height * sinYaw;
    float z1 = -dx * sinYaw + height * cosYaw;
    float y2 = dy * cosPitch - z1 * sinPitch;
    float z2 = dy * sinPitch + z1 * cosPitch - height;
    float w = (distance - z2) / distance;
    outW = w;
    outX = pivotX + x1 / w;
    outY = pivotY + y2 / w;
  }

  public void bounds(float x0, float y0, float x1, float y1, float height, int width, int limit) {
    map(x0, y0, height);
    float minX = outX;
    float maxX = outX;
    float minY = outY;
    float maxY = outY;
    for (int corner = 1; corner < 4; corner++) {
      map((corner & 1) == 0 ? x0 : x1, corner < 2 ? y0 : y1, height);
      minX = Math.min(minX, outX);
      maxX = Math.max(maxX, outX);
      minY = Math.min(minY, outY);
      maxY = Math.max(maxY, outY);
    }
    left = clamp((int) Math.floor(minX), width);
    top = clamp((int) Math.floor(minY), limit);
    right = Math.max(left, clamp((int) Math.ceil(maxX), width));
    bottom = Math.max(top, clamp((int) Math.ceil(maxY), limit));
  }

  private static int clamp(int value, int max) {
    return value < 0 ? 0 : Math.min(value, max);
  }

  public int left() {
    return left;
  }

  public int top() {
    return top;
  }

  public int right() {
    return right;
  }

  public int bottom() {
    return bottom;
  }

  public boolean unmap(float screenX, float screenY, float height) {
    if (flat) {
      outX = screenX;
      outY = screenY;
      return true;
    }
    float sx = screenX - pivotX;
    float sy = screenY - pivotY;
    float k = (cosPitch * cosYaw - 1.0F) * height;
    float a1 = sx * cosPitch * sinYaw - distance * cosYaw;
    float b1 = -sx * sinPitch;
    float c1 = sx * distance - sx * k - distance * sinYaw * height;
    float a2 = sy * cosPitch * sinYaw - distance * sinPitch * sinYaw;
    float b2 = -sy * sinPitch - distance * cosPitch;
    float c2 = sy * distance - sy * k + distance * sinPitch * cosYaw * height;
    float det = a1 * b2 - a2 * b1;
    if (!(Math.abs(det) > MIN_DET)) {
      outX = screenX;
      outY = screenY;
      return false;
    }
    outX = pivotX + (b1 * c2 - c1 * b2) / det;
    outY = pivotY + (a2 * c1 - a1 * c2) / det;
    return true;
  }
}
