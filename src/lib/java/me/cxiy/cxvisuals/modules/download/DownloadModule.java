package me.cxiy.cxvisuals.modules.download;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PanelSetting;

public final class DownloadModule extends Module {
  public enum Outside {
    EMPTY,
    GENERATED
  }

  public enum Mode {
    CREATIVE,
    SURVIVAL
  }

  public static final String ID = "download";
  public static final String RECORD_KEY = ID + ".record";

  public final Recording recording = new Recording();
  public final PanelSetting panel;
  public final BoolSetting containers;
  public final BoolSetting entities;
  public final EnumSetting<Outside> outside;
  public final EnumSetting<Mode> mode;

  public DownloadModule(KeyBinder keys) {
    super(ID, "World Download", true, keys);
    Page page = section("download", "World Download");
    panel = page.group("Download").panel("controls");
    Group keep = page.group("What to keep");
    containers =
        keep.bool("containers", "Container contents", true)
            .describe("Chests, barrels and other containers keep the items you saw inside them.");
    entities =
        keep.bool("entities", "Entities", true)
            .describe(
                "Item frames, armour stands, animals and other entities nearby. Players stay out.");
    Group world = page.group("New world");
    outside =
        world
            .enumOf("outside", "Around the download", Outside.EMPTY, "Empty", "New terrain")
            .describe("Empty leaves the rest of the Overworld as void, so the edges stay clean.");
    mode = world.enumOf("mode", "Game mode", Mode.CREATIVE, "Creative", "Survival");
    page.group("Shortcut")
        .key(
            "record_key",
            "Start or stop recording",
            keys.bind(RECORD_KEY, "Start or stop recording", KeyBinder.UNBOUND));
  }

  @Override
  public String group() {
    return "World";
  }
}
