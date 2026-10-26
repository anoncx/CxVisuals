package me.cxiy.cxvisuals.modules.swings;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PaletteSetting;

public final class SwingsModule extends Module {
  public static final String ID = "swings";

  public final EnumSetting<Look> look;
  public final PaletteSetting palette;
  public final NumberSetting length;
  public final NumberSetting width;
  public final NumberSetting glow;
  public final NumberSetting intensity;
  public final BoolSetting self;
  public final BoolSetting firstPerson;
  public final BoolSetting players;
  public final BoolSetting mobs;
  public final NumberSetting range;
  public final BoolSetting swords;
  public final BoolSetting axes;
  public final BoolSetting tools;
  public final BoolSetting others;

  public SwingsModule(KeyBinder keys) {
    super(ID, "Swing Trails", false, keys);
    Page page = section("trails", "Trails");

    Group style = page.group("Look");
    look = style.enumOf("look", "Style", Look.GLOW, Look.LABELS);
    palette = style.palette("colour", "Colour", 0);
    length =
        style
            .number("length", "Length", 0.35, 0.1, 1.0, 0.05, "s")
            .describe("How long a trail takes to fade away.");
    width =
        style
            .number("width", "Width", 100.0, 40.0, 200.0, 5.0, "%")
            .describe("How far the light reaches past the edge of the blade.");
    glow = style.number("glow", "Glow", 100.0, 0.0, 200.0, 5.0, "%");
    intensity = style.number("intensity", "Intensity", 100.0, 20.0, 200.0, 5.0, "%");

    Group targets = page.group("Targets");
    self = targets.bool("self", "Yourself", true);
    firstPerson =
        targets
            .bool("first_person", "First person", true)
            .describe("Draws the trail of the item in your own hand.");
    players = targets.bool("players", "Other players", true);
    mobs = targets.bool("mobs", "Mobs", false);
    range =
        targets
            .number("range", "Range", 32.0, 8.0, 96.0, 4.0, "")
            .describe("Blocks. Trails fade out gradually over the far half of this distance.");

    Group items = page.group("Items");
    swords = items.bool("swords", "Swords and spears", true);
    axes = items.bool("axes", "Axes", true);
    tools = items.bool("tools", "Pickaxes, shovels and hoes", true);
    others = items.bool("others", "Maces and tridents", true);
  }

  public boolean trailsLive() {
    return enabled.get() && (self.get() || players.get() || mobs.get());
  }

  public float seconds() {
    return length.get().floatValue();
  }

  public float widthScale() {
    return (float) (width.get() / 100.0);
  }

  public float glowScale() {
    return (float) (glow.get() / 100.0);
  }

  public float intensityScale() {
    return (float) (intensity.get() / 100.0);
  }

  public float reach() {
    return range.get().floatValue();
  }

  @Override
  public String group() {
    return "World";
  }
}
