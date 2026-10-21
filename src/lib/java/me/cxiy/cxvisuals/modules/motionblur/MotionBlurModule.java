package me.cxiy.cxvisuals.modules.motionblur;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;

public final class MotionBlurModule extends Module {
  public enum Quality {
    LOW(6),
    MEDIUM(10),
    HIGH(16);

    final int samples;

    Quality(int samples) {
      this.samples = samples;
    }
  }

  public static final String ID = "motion_blur";
  public static final float MAX_SCALE = 64.0F;
  public static final float MAX_STREAK = 0.06F;

  private static final float FULL_EXPOSURE = 1.0F / 24.0F;
  private static final float LONGEST_FRAME = 0.1F;
  private static final double CUT_SPEED = 200.0;
  private static final float CENTRE_INNER = 0.45F;
  private static final float CENTRE_OUTER = 1.3F;

  public final NumberSetting turning;
  public final NumberSetting moving;
  public final NumberSetting centre;
  public final EnumSetting<Quality> quality;
  public final NumberSetting hands;

  public MotionBlurModule(KeyBinder keys) {
    super(ID, "Motion Blur", false, keys);
    Page blur = section("blur", "Blur");

    Group strength = blur.group("Strength");
    turning =
        strength
            .number("turning", "Turning", 35.0, 0.0, 100.0, 5.0, "%")
            .describe("Blur from looking around.");
    moving =
        strength
            .number("moving", "Moving", 60.0, 0.0, 100.0, 5.0, "%")
            .describe("Blur from travelling: sprinting, riding, gliding.");

    Group look = blur.group("Look");
    centre =
        look.number("centre", "Sharp centre", 30.0, 0.0, 100.0, 5.0, "%")
            .describe("Keeps what you aim at clear.");
    quality = look.enumOf("quality", "Quality", Quality.MEDIUM, "Low", "Medium", "High");
    hands =
        look.number("hands", "Hand blur", 0.0, 0.0, 100.0, 5.0, "%")
            .describe("Shader packs draw the hand into the world. 0 keeps it sharp.");
  }

  public float turnScale(float frameSeconds) {
    return scale(turning.get(), frameSeconds);
  }

  public float moveScale(float frameSeconds) {
    return scale(moving.get(), frameSeconds);
  }

  private static float scale(double percent, float frameSeconds) {
    if (frameSeconds <= 0.0F || percent <= 0.0) {
      return 0.0F;
    }
    return Math.min(MAX_SCALE, (float) (percent / 100.0) * FULL_EXPOSURE / frameSeconds);
  }

  public boolean steady(float frameSeconds, double distance) {
    return frameSeconds > 0.0F
        && frameSeconds <= LONGEST_FRAME
        && distance <= CUT_SPEED * frameSeconds;
  }

  public int samples() {
    return quality.get().samples;
  }

  public float handBlur() {
    return (float) Math.min(1.0, hands.get() / 100.0);
  }

  public float centreInner() {
    return (float) (centre.get() / 100.0) * CENTRE_INNER;
  }

  public float centreOuter() {
    return (float) (centre.get() / 100.0) * CENTRE_OUTER;
  }

  @Override
  public String group() {
    return "Camera";
  }
}
