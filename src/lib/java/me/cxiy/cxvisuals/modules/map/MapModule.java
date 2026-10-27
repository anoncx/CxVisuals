package me.cxiy.cxvisuals.modules.map;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.ActionSetting;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PanelSetting;
import me.cxiy.cxvisuals.modules.map.Relief.Terrain;

public final class MapModule extends Module {
  public enum MobIcons {
    HEADS,
    DOTS
  }

  public enum Caves {
    AUTO,
    OFF
  }

  public static final String ID = "map";
  public static final String OPEN_KEY = ID + ".open";
  public static final String ZOOM_IN_KEY = ID + ".zoom_in";
  public static final String ZOOM_OUT_KEY = ID + ".zoom_out";
  public static final int KEY_M = 77;

  public final MapAtlas atlas = new MapAtlas();
  public final EnumSetting<Terrain> terrain;
  public final BoolSetting clouds;
  public final BoolSetting solid;
  public final EnumSetting<Caves> caves;
  public final BoolSetting waypoints;
  public final BoolSetting friends;
  public final BoolSetting players;
  public final BoolSetting mobs;
  public final EnumSetting<MobIcons> mobIcons;
  public final PanelSetting storage;

  public MapModule(KeyBinder keys, Runnable open) {
    super(ID, "Map", true, keys);
    action("open", "Open map", ActionSetting.Kind.QUIET, open);
    Page page = section("map", "Map");
    Group look = page.group("Terrain");
    terrain =
        look.enumOf("terrain", "Shading", Terrain.RELIEF, "Relief", "Contours", "Flat")
            .describe(
                "Relief lights hills from the north west. Contours adds a line every eight"
                    + " blocks.");
    clouds =
        look.bool("clouds", "Clouds", true)
            .describe("Places you haven't explored yet hide under slowly drifting clouds.");
    solid =
        look.bool("solid", "3D map", false)
            .describe("Builds the data for 3D. Off scans faster and hides the 3D button.");
    caves =
        look.enumOf("caves", "Cave view", Caves.AUTO, "Automatic", "Off")
            .describe("Underground, the minimap shows the cave around you instead of the surface.");
    Group marks = page.group("On the map");
    waypoints = marks.bool("waypoints", "Waypoints", true);
    friends =
        marks
            .bool("friends", "Friends", true)
            .describe("Friends on your server who share their location.");
    players = marks.bool("players", "Nearby players", true);
    mobs =
        marks
            .bool("mobs", "Mobs", true)
            .describe(
                "Animals as soft dots and monsters in red, in the range the game shows them.");
    mobIcons =
        marks
            .enumOf("mob_icons", "Mob icons", MobIcons.HEADS, "Heads", "Dots")
            .describe("Heads show each mob's face on a tile, red rimmed for monsters.");
    storage = page.group("This world").panel("storage");
    Group shortcuts = page.group("Shortcuts");
    shortcuts.key("open_key", "Open map", keys.bind(OPEN_KEY, "Open map", KEY_M));
    shortcuts.key(
        "zoom_in_key",
        "Minimap zoom in",
        keys.bind(ZOOM_IN_KEY, "Minimap zoom in", KeyBinder.UNBOUND));
    shortcuts.key(
        "zoom_out_key",
        "Minimap zoom out",
        keys.bind(ZOOM_OUT_KEY, "Minimap zoom out", KeyBinder.UNBOUND));
  }

  @Override
  public String group() {
    return "World";
  }
}
