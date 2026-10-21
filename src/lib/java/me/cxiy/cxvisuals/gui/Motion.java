package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.core.module.ClientSettings.WindowMotion;

public final class Motion {
  private static final float EVAPORATE_OPEN_SECONDS = 0.5F;
  private static final float EVAPORATE_CLOSE_SECONDS = 0.26F;

  private static volatile float pace = 1.0F;

  private Motion() {}

  public static void pace(float factor) {
    pace = Math.max(0.1F, factor);
  }

  public static float erosion(WindowMotion motion, float phase) {
    return motion == WindowMotion.EVAPORATE ? 1.0F - (float) Easing.smoothstep(phase) : 0.0F;
  }

  public static float openTime(WindowMotion motion) {
    return switch (motion) {
      case MORPH -> 0.42F;
      case POP -> 0.3F;
      case SLIDE -> 0.32F;
      case FADE -> 0.18F;
      case EVAPORATE -> EVAPORATE_OPEN_SECONDS;
    };
  }

  public static float closeTime(WindowMotion motion) {
    return motion == WindowMotion.MORPH
        ? 0.22F
        : motion == WindowMotion.EVAPORATE ? EVAPORATE_CLOSE_SECONDS : 0.16F;
  }

  public static float advance(WindowMotion motion, float phase, boolean closing, float dt) {
    float step = dt * pace;
    float next = phase + (closing ? -step / closeTime(motion) : step / openTime(motion));
    return next < 0.0F ? 0.0F : Math.min(next, 1.0F);
  }

  public static float grow(float t, boolean closing) {
    return (float) (closing ? Easing.snappy(t) : Easing.spring(t));
  }

  public static float alpha(WindowMotion motion, float phase) {
    return switch (motion) {
      case MORPH -> Math.min(1.0F, phase * 6.0F);
      case POP -> Math.min(1.0F, phase * 3.0F);
      case SLIDE -> Math.min(1.0F, phase * 2.5F);
      case FADE -> (float) Easing.smoothstep(phase);
      case EVAPORATE -> (float) Easing.smoothstep(phase);
    };
  }

  public static float foldWidth(float phase, boolean closing) {
    return grow(phase / 0.72F, closing);
  }

  public static float foldHeight(float phase, boolean closing) {
    return grow((phase - 0.14F) / 0.78F, closing);
  }

  public static float reveal(float phase, boolean closing) {
    return closing
        ? Math.max(0.0F, Math.min(1.0F, (phase - 0.55F) / 0.35F))
        : (float) Easing.smootherstep((phase - 0.33F) / 0.45F);
  }

  public static float pop(float from, float phase, boolean closing) {
    return from + (1.0F - from) * grow(phase, closing);
  }

  public static float slide(float distance, float phase) {
    return (1.0F - (float) Easing.snappy(phase)) * distance;
  }
}
