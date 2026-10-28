package me.cxiy.cxvisuals.modules.fullbright;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PaletteSetting;

public final class FullbrightModule extends Module {
  public enum Mode {
    LUMINANCE,
    GAMMA,
    NIGHT_VISION
  }

  public static final String ID = "fullbright";
  public static final float DARKEST = 0.03F;

  private static final float LEVELS = 15.0F;

  public final EnumSetting<Mode> mode;
  public final NumberSetting level;
  public final NumberSetting gamma;
  public final NumberSetting intensity;
  public final NumberSetting tint;
  public final PaletteSetting colour;

  private float tintRed = 1.0F;
  private float tintGreen = 1.0F;
  private float tintBlue = 1.0F;
  private boolean tinted;
  private boolean fresh;

  public FullbrightModule(KeyBinder keys) {
    super(ID, "Fullbright", false, keys);
    Page page = section("fullbright", "Fullbright");

    mode =
        page.group("Mode")
            .enumOf("mode", "Mode", Mode.LUMINANCE, "Luminance", "Gamma", "Night Vision")
            .describe(
                "Luminance sets a minimum light level, Gamma pushes the game's brightness curve, Night Vision lights everything like the potion.");

    level =
        page.group("Strength")
            .showWhen(() -> mode.get() == Mode.LUMINANCE)
            .number("level", "Light level", LEVELS, 0.0, LEVELS, 1.0, "")
            .describe("The darkest any spot can be. 15 is full daylight everywhere.");

    gamma =
        page.group("Strength")
            .showWhen(() -> mode.get() == Mode.GAMMA)
            .number("gamma", "Gamma", 8.0, 1.0, 16.0, 0.5, "×")
            .describe("How hard the brightness curve is pushed. Higher lifts the darkest spots.");

    intensity =
        page.group("Strength")
            .showWhen(() -> mode.get() == Mode.NIGHT_VISION)
            .number("intensity", "Intensity", 100.0, 10.0, 100.0, 5.0, "%")
            .describe("How strong the night vision glow is.");

    tint =
        page.group("Tint")
            .number("tint", "Tint", 0.0, 0.0, 100.0, 5.0, "%")
            .describe("Colours the light in every mode. 0 keeps it neutral.");
    colour =
        page.group("Tint colour")
            .showWhen(() -> tint.get() > 0.0)
            .palette("tint_colour", "Tint colour", 0);

    tint.onChange(() -> fresh = false);
    colour.onChange(() -> fresh = false);
  }

  public float floor() {
    return level.get().floatValue() / LEVELS;
  }

  public float gammaBoost() {
    return gamma.get().floatValue();
  }

  public float nightVision() {
    return (float) (intensity.get() / 100.0);
  }

  public boolean tinted() {
    refresh();
    return tinted;
  }

  public float tintRed() {
    refresh();
    return tintRed;
  }

  public float tintGreen() {
    refresh();
    return tintGreen;
  }

  public float tintBlue() {
    refresh();
    return tintBlue;
  }

  private void refresh() {
    if (fresh) {
      return;
    }
    fresh = true;
    float amount = (float) (tint.get() / 100.0);
    int argb = colour.get().first();
    float red = ((argb >> 16) & 0xFF) / 255.0F;
    float green = ((argb >> 8) & 0xFF) / 255.0F;
    float blue = (argb & 0xFF) / 255.0F;
    float peak = Math.max(red, Math.max(green, Math.max(blue, 1.0e-4F)));
    tinted = amount > 0.0F;
    tintRed = 1.0F + (red / peak - 1.0F) * amount;
    tintGreen = 1.0F + (green / peak - 1.0F) * amount;
    tintBlue = 1.0F + (blue / peak - 1.0F) * amount;
  }

  @Override
  public String group() {
    return "World";
  }
}
