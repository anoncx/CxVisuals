package me.cxiy.cxvisuals.modules.hands;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;

public final class HandsModule extends Module {
  public static final String ID = "hands";

  public final HandView main;
  public final HandView off;
  public final EnumSetting<SwingAnimation> animation;
  public final NumberSetting intensity;
  public final NumberSetting swingSpeed;
  public final NumberSetting idleSpin;
  public final BoolSetting equipDip;

  public HandsModule(KeyBinder keys) {
    super(ID, "Hands", false, keys);
    main = new HandView(section("main", "Main hand"));
    off = new HandView(section("off", "Off hand"));

    Page swing = section("swing", "Swing");
    SwingAnimation[] animations = SwingAnimation.values();
    String[] labels = new String[animations.length];
    for (int i = 0; i < animations.length; i++) {
      labels[i] = animations[i].label;
    }
    Group motion = swing.group("Animation");
    animation = motion.enumOf("animation", "Animation", SwingAnimation.VANILLA, labels);
    intensity = motion.number("intensity", "Intensity", 1.0, 0.0, 2.0, 0.05, "×");
    swingSpeed = motion.number("speed", "Swing speed", 1.0, 0.25, 2.0, 0.05, "×");

    Group extras = swing.group("Extras");
    idleSpin =
        extras
            .number("idle_spin", "Idle spin", 0.0, 0.0, 360.0, 5.0, "°/s")
            .describe("Keeps the held item turning.");
    equipDip =
        extras.bool("equip_dip", "Equip dip", true).describe("The drop when you switch items.");
  }

  public int swingDuration(int vanillaTicks) {
    return Math.max(1, (int) Math.round(vanillaTicks / swingSpeed.get()));
  }

  public float equipHeight(float vanillaHeight) {
    return equipDip.get() ? vanillaHeight : 0.0F;
  }

  public float idleAngle(double seconds) {
    double speed = idleSpin.get();
    return speed == 0.0 ? 0.0F : (float) ((seconds * speed) % 360.0);
  }

  @Override
  public String group() {
    return "Player";
  }
}
