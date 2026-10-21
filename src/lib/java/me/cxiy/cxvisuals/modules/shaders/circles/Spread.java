package me.cxiy.cxvisuals.modules.shaders.circles;

import me.cxiy.cxvisuals.core.math.Easing;

public enum Spread {
  EXPAND,
  POP,
  IMPLODE;

  private static final float POP_SHARE = 0.4F;

  public float scale(float progress) {
    float t = unit(progress);
    return switch (this) {
      case EXPAND -> 0.12F + 0.88F * (1.0F - cube(1.0F - t));
      case POP -> (float) Easing.overshoot(t / POP_SHARE);
      case IMPLODE -> 1.15F - (float) Math.pow(t, 2.2) * 1.0F;
    };
  }

  public float fade(float progress) {
    float t = unit(progress);
    float in = smooth(t / (this == IMPLODE ? 0.15F : 0.06F));
    float from =
        switch (this) {
          case EXPAND -> 0.35F;
          case POP -> 0.55F;
          case IMPLODE -> 0.7F;
        };
    return in * (1.0F - smooth((t - from) / (1.0F - from)));
  }

  private static float unit(float t) {
    return t < 0.0F ? 0.0F : Math.min(t, 1.0F);
  }

  private static float cube(float t) {
    return t * t * t;
  }

  private static float smooth(float t) {
    float c = unit(t);
    return c * c * (3.0F - 2.0F * c);
  }
}
