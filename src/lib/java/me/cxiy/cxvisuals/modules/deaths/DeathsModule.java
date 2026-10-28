package me.cxiy.cxvisuals.modules.deaths;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PaletteSetting;
import me.cxiy.cxvisuals.core.settings.PickSetting;

public final class DeathsModule extends Module {
  public static final String ID = "deaths";

  private static final float RANGE = 64.0F;

  public final EnumSetting<Fate> fate;
  public final PaletteSetting palette;
  public final NumberSetting duration;
  public final NumberSetting intensity;
  public final BoolSetting players;
  public final BoolSetting mobs;
  public final PickSetting only;
  public final BoolSetting self;

  public DeathsModule(KeyBinder keys) {
    super(ID, "Death Effects", false, keys);
    hostedBy("shaders", 0);
    Page page = section("effects", "Death");

    Group look = page.group("Look");
    fate =
        look.enumOf("effect", "Effect", Fate.RITUAL, Fate.LABELS)
            .describe(
                "What happens to a body when it dies. Random picks a different one each time.");
    palette = look.palette("colour", "Colour", 2);
    duration = look.number("duration", "Duration", 2.4, 1.0, 5.0, 0.1, "s");
    intensity = look.number("intensity", "Intensity", 100.0, 25.0, 200.0, 5.0, "%");

    Group targets = page.group("Targets");
    players = targets.bool("players", "Other players", true);
    mobs = targets.bool("mobs", "Mobs and animals", false);
    only =
        targets
            .picks("only", "Only these mobs")
            .describe("Pick mobs to limit the effect to them. Leave empty to affect every mob.");
    self =
        targets
            .bool("self", "Yourself", true)
            .describe("Seen in third person and by the idle camera.");
  }

  public boolean effectsLive() {
    return enabled.get() && (players.get() || mobs.get() || self.get());
  }

  public float seconds() {
    return duration.get().floatValue();
  }

  public float intensityScale() {
    return (float) (intensity.get() / 100.0);
  }

  public float range() {
    return RANGE;
  }

  @Override
  public String group() {
    return "World";
  }
}
