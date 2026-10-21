package me.cxiy.cxvisuals.gui;

public final class Hsv {
  private static final int OPAQUE = 0xFF000000;

  private Hsv() {}

  public static int argb(float hue, float saturation, float value) {
    float h = (hue - (float) Math.floor(hue)) * 6.0F;
    float s = clamp(saturation);
    float v = clamp(value);
    int sector = (int) h % 6;
    float f = h - (float) Math.floor(h);
    float p = v * (1.0F - s);
    float q = v * (1.0F - s * f);
    float t = v * (1.0F - s * (1.0F - f));
    float r;
    float g;
    float b;
    switch (sector) {
      case 0 -> {
        r = v;
        g = t;
        b = p;
      }
      case 1 -> {
        r = q;
        g = v;
        b = p;
      }
      case 2 -> {
        r = p;
        g = v;
        b = t;
      }
      case 3 -> {
        r = p;
        g = q;
        b = v;
      }
      case 4 -> {
        r = t;
        g = p;
        b = v;
      }
      default -> {
        r = v;
        g = p;
        b = q;
      }
    }
    return OPAQUE
        | Math.round(r * 255.0F) << 16
        | Math.round(g * 255.0F) << 8
        | Math.round(b * 255.0F);
  }

  public static void of(int argb, float[] out) {
    float r = (argb >> 16 & 0xFF) / 255.0F;
    float g = (argb >> 8 & 0xFF) / 255.0F;
    float b = (argb & 0xFF) / 255.0F;
    float max = Math.max(r, Math.max(g, b));
    float min = Math.min(r, Math.min(g, b));
    float span = max - min;
    float hue = 0.0F;
    if (span > 0.0F) {
      if (max == r) {
        hue = (g - b) / span;
      } else if (max == g) {
        hue = 2.0F + (b - r) / span;
      } else {
        hue = 4.0F + (r - g) / span;
      }
      hue /= 6.0F;
      if (hue < 0.0F) {
        hue += 1.0F;
      }
    }
    out[0] = hue;
    out[1] = max <= 0.0F ? 0.0F : span / max;
    out[2] = max;
  }

  private static float clamp(float value) {
    return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
  }
}
