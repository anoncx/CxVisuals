package me.cxiy.cxvisuals.modules.map;

public final class Relief {
  public enum Terrain {
    RELIEF,
    CONTOURS,
    FLAT
  }

  private static final float SLOPE = 0.055F;
  private static final float DARKEST = 0.66F;
  private static final float BRIGHTEST = 1.24F;
  private static final float SEA = 63.0F;
  private static final float ALTITUDE = 0.09F;
  private static final float ALTITUDE_SPAN = 160.0F;
  private static final float CONTOUR = 0.8F;
  private static final int CONTOUR_STEP = 3;
  private static final float SHORE = 1.12F;
  private static final float DEEP = 0.035F;
  private static final float DEEP_FLOOR = 0.62F;
  private static final float GRAIN = 0.045F;
  private static final float VIBRANCE = 1.18F;

  private Relief() {}

  public static float light(Terrain terrain, int h, int west, int north, int water, int shore) {
    if (terrain == Terrain.FLAT) {
      return 1.0F;
    }
    float light;
    if (water > 0) {
      light = Math.max(DEEP_FLOOR, 1.0F - (water - 1) * DEEP);
      if (shore < water) {
        light *= SHORE;
      }
    } else {
      int d = (h - west) + (h - north);
      light = clamp(1.0F + d * SLOPE, DARKEST, BRIGHTEST);
      light *= 1.0F + clamp((h - SEA) / ALTITUDE_SPAN, -0.6F, 1.0F) * ALTITUDE;
      if (terrain == Terrain.CONTOURS
          && ((h >> CONTOUR_STEP) != (west >> CONTOUR_STEP)
              || (h >> CONTOUR_STEP) != (north >> CONTOUR_STEP))) {
        light *= CONTOUR;
      }
    }
    return light;
  }

  public static float grain(int x, int z) {
    int h = x * 374761393 + z * 668265263;
    h = (h ^ (h >>> 13)) * 1274126177;
    return 1.0F + ((h >>> 24) / 255.0F - 0.5F) * GRAIN * 2.0F;
  }

  public static int rgb(int color, float light) {
    int r0 = (color >> 16) & 0xFF;
    int g0 = (color >> 8) & 0xFF;
    int b0 = color & 0xFF;
    float grey = (r0 * 0.299F + g0 * 0.587F + b0 * 0.114F);
    int r = channel(vivid(r0, grey), light);
    int g = channel(vivid(g0, grey), light);
    int b = channel(vivid(b0, grey), light);
    return (r << 16) | (g << 8) | b;
  }

  private static int vivid(int value, float grey) {
    return Math.max(0, Math.min(255, Math.round(grey + (value - grey) * VIBRANCE)));
  }

  public static int pixel(int rgb, int alpha) {
    return (alpha << 24) | ((rgb & 0xFF) << 16) | (rgb & 0xFF00) | ((rgb >> 16) & 0xFF);
  }

  private static int channel(int value, float light) {
    float lit = value * light;
    if (light > 1.0F) {
      lit += (255.0F - value) * (light - 1.0F) * 0.35F;
    }
    return Math.max(0, Math.min(255, Math.round(lit)));
  }

  private static float clamp(float value, float min, float max) {
    return value < min ? min : Math.min(value, max);
  }
}
