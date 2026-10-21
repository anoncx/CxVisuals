package me.cxiy.cxvisuals.modules.shaders;

import org.joml.Matrix4fc;

public final class ScreenBounds {
  public static final int MIN_REACH = 3;
  public static final int MAX_REACH = 64;

  private static final float NEAR = 0.05F;
  private static final int LEVEL_MARGIN = 1;
  private static final float PAD = 0.5F;

  private int screenWidth;
  private int screenHeight;
  private float minX;
  private float minY;
  private float maxX;
  private float maxY;
  private float nearest;
  private boolean any;
  private boolean whole;
  private int x;
  private int y;
  private int width;
  private int height;

  public void reset(int screenWidth, int screenHeight) {
    this.screenWidth = screenWidth;
    this.screenHeight = screenHeight;
    minX = Float.MAX_VALUE;
    minY = Float.MAX_VALUE;
    maxX = -Float.MAX_VALUE;
    maxY = -Float.MAX_VALUE;
    nearest = Float.MAX_VALUE;
    any = false;
    whole = false;
    x = 0;
    y = 0;
    width = 0;
    height = 0;
  }

  public void add(
      Matrix4fc lens, float centreX, float feetY, float centreZ, float halfWidth, float tall) {
    any = true;
    float pad = Math.max(halfWidth + halfWidth, tall) * PAD;
    float side = halfWidth + pad;
    for (int corner = 0; corner < 8; corner++) {
      float px = centreX + ((corner & 1) == 0 ? -side : side);
      float py = (corner & 2) == 0 ? feetY - pad : feetY + tall + pad;
      float pz = centreZ + ((corner & 4) == 0 ? -side : side);
      float w = lens.m03() * px + lens.m13() * py + lens.m23() * pz + lens.m33();
      nearest = Math.min(nearest, Math.max(NEAR, w));
      if (w <= NEAR) {
        whole = true;
        continue;
      }
      float nx = (lens.m00() * px + lens.m10() * py + lens.m20() * pz + lens.m30()) / w;
      float ny = (lens.m01() * px + lens.m11() * py + lens.m21() * pz + lens.m31()) / w;
      minX = Math.min(minX, nx);
      maxX = Math.max(maxX, nx);
      minY = Math.min(minY, ny);
      maxY = Math.max(maxY, ny);
    }
  }

  public void everything() {
    any = true;
    whole = true;
  }

  public void finish(int margin) {
    if (!any) {
      return;
    }
    if (whole) {
      width = screenWidth;
      height = screenHeight;
      return;
    }
    int left =
        even(
            clamp((int) Math.floor((minX * 0.5F + 0.5F) * screenWidth) - margin, screenWidth),
            false,
            screenWidth);
    int right =
        even(
            clamp((int) Math.ceil((maxX * 0.5F + 0.5F) * screenWidth) + margin, screenWidth),
            true,
            screenWidth);
    int bottom =
        even(
            clamp((int) Math.floor((minY * 0.5F + 0.5F) * screenHeight) - margin, screenHeight),
            false,
            screenHeight);
    int top =
        even(
            clamp((int) Math.ceil((maxY * 0.5F + 0.5F) * screenHeight) + margin, screenHeight),
            true,
            screenHeight);
    if (right > left && top > bottom) {
      x = left;
      y = bottom;
      width = right - left;
      height = top - bottom;
    }
  }

  public boolean empty() {
    return width <= 0 || height <= 0;
  }

  public int x() {
    return x;
  }

  public int y() {
    return y;
  }

  public int width() {
    return width;
  }

  public int height() {
    return height;
  }

  public float nearest() {
    return nearest;
  }

  public static int reach(float reachBlocks, float pixelsPerBlock, float distance) {
    return Math.max(
        MIN_REACH, Math.min(MAX_REACH, Math.round(reachBlocks * pixelsPerBlock / distance)));
  }

  public static int size(int full, int level) {
    int size = full;
    for (int i = 0; i < level; i++) {
      size = (size + 1) / 2;
    }
    return Math.max(1, size);
  }

  public int x(int level) {
    return low(x, level);
  }

  public int y(int level) {
    return low(y, level);
  }

  public int width(int level) {
    return high(x + width, level, screenWidth) - low(x, level);
  }

  public int height(int level) {
    return high(y + height, level, screenHeight) - low(y, level);
  }

  private static int low(int edge, int level) {
    return level == 0 ? edge : Math.max(0, (edge >> level) - LEVEL_MARGIN);
  }

  private static int high(int edge, int level, int full) {
    if (level == 0) {
      return edge;
    }
    int scale = 1 << level;
    return Math.min(size(full, level), (edge + scale - 1) / scale + LEVEL_MARGIN);
  }

  private static int clamp(int value, int limit) {
    return Math.max(0, Math.min(limit, value));
  }

  private static int even(int value, boolean up, int limit) {
    return up ? Math.min(limit, (value + 1) & ~1) : value & ~1;
  }
}
