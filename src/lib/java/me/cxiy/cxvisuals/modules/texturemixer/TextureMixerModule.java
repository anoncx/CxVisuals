package me.cxiy.cxvisuals.modules.texturemixer;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.ActionSetting;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.TextSetting;

public final class TextureMixerModule extends Module {
  public static final String ID = "texturemixer";
  public static final String OPEN_KEY = ID + ".open";

  public final TextSetting selection;
  public final BoolSetting live;

  public TextureMixerModule(KeyBinder keys, Runnable open, Runnable reset) {
    super(ID, "Texture Mixer", true, keys);
    selection = settings.text("selection", "");
    Page page = section("studio", "Studio");
    Group studio = page.group("Studio");
    studio.action("open", "Mix texture packs", "Open", ActionSetting.Kind.PRIMARY, open);
    studio.key(
        "open_key", "Open key", keys.bind(OPEN_KEY, "Open Texture Mixer", KeyBinder.UNBOUND));
    live =
        studio
            .bool("live", "Apply live", true)
            .describe("Reloads the game's textures a moment after every change you make.");
    Group data = page.group("Mix");
    data.action("reset", "Picked textures", "Reset", ActionSetting.Kind.DANGER, reset);
  }

  @Override
  public String group() {
    return "Player";
  }
}
