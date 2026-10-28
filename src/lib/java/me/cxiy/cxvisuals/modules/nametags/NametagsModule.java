package me.cxiy.cxvisuals.modules.nametags;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.NumberSetting;

public final class NametagsModule extends Module {
  public static final String ID = "nametags";

  public final BoolSetting background;
  public final NumberSetting opacity;
  public final BoolSetting everything;

  public NametagsModule(KeyBinder keys) {
    super(ID, "Nametags", true, keys);
    hostedBy("hud", 0);
    Group look = section("nametags", "Nametags").group("Look");
    background =
        look.bool("background", "Background", true).describe("A rounded plate behind each name.");
    opacity = look.number("opacity", "Background opacity", 45.0, 10.0, 90.0, 5.0, "%");
    everything =
        look.bool("everything", "Mobs and holograms", false)
            .describe(
                "Also restyles named mobs and floating text. Off keeps those as the server made them.");
  }

  @Override
  public String group() {
    return "Player";
  }

  public float plate() {
    return background.get() ? (float) (opacity.get() / 100.0) : 0.0F;
  }
}
