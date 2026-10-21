package me.cxiy.cxvisuals.gui.hud;

import me.cxiy.cxvisuals.core.math.Easing;

public final class CardMotion {
  public static final float LIFE = 6.5F;
  public static final float FADE_IN = 0.14F;
  public static final float TEXT_AT = 0.34F;
  public static final float TEXT_IN = 0.2F;
  public static final float PULSE_AT = 0.3F;
  public static final float PULSE = 0.7F;
  public static final float EXIT = 0.56F;
  public static final float EXIT_TEXT = 0.3F;
  public static final float EXIT_DRIFT = 14.0F;
  public static final float FOLLOW = 13.0F;
  public static final float ALERT = 1.4F;

  private static final float GROW_AT = 0.12F;
  private static final float GROW = 0.46F;
  private static final float EXIT_SHRINK = 0.75F;
  private static final float EXIT_FADE_AT = 0.55F;
  private static final float EXIT_SCALE = 0.08F;

  private CardMotion() {}

  public static float unit(float value) {
    return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
  }

  public static float leaving(float age) {
    return unit((age - (LIFE - EXIT)) / EXIT);
  }

  public static float width(float age, float least, float full) {
    float grown = (float) Easing.overshoot(unit((age - GROW_AT) / GROW));
    float shrunk = (float) Easing.snappy(unit(leaving(age) / EXIT_SHRINK));
    return least + (full - least) * grown * (1.0F - shrunk);
  }

  public static float alpha(float age, float out) {
    return unit(age / FADE_IN) * (1.0F - unit((out - EXIT_FADE_AT) / (1.0F - EXIT_FADE_AT)));
  }

  public static float text(float age, float out) {
    return unit((age - TEXT_AT) / TEXT_IN) * (1.0F - unit(out / EXIT_TEXT));
  }

  public static float slide(float age) {
    return 1.0F - unit((age - TEXT_AT) / TEXT_IN);
  }

  public static float scale(float out) {
    return 1.0F - EXIT_SCALE * out;
  }

  public static float drift(float out) {
    return EXIT_DRIFT * out * out;
  }

  public static float alert(float age) {
    float left = 1.0F - unit(age / ALERT);
    return left * left;
  }
}
