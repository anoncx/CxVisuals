package me.cxiy.cxvisuals.modules.waypoints;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PanelSetting;

public final class WaypointsModule extends Module {
  public static final String ID = "waypoints";
  public static final String ADD_KEY = ID + ".add";

  public final Waypoints waypoints = new Waypoints();
  public final PanelSetting list;
  public final BoolSetting world;
  public final BoolSetting friends;
  public final BoolSetting deaths;

  public WaypointsModule(KeyBinder keys) {
    super(ID, "Waypoints", true, keys);
    hostedBy("map", 0);
    Page page = section("waypoints", "Waypoints");
    list = page.group("This world").panel("list");
    deaths =
        page.group("Deaths")
            .bool("deaths", "Death waypoints", true)
            .describe(
                "Marks where you died and shows how long ago. Keeps the last three in each world.");
    Group view = page.group("In the world");
    world =
        view.bool("world", "Show in the world", true)
            .describe(
                "Each waypoint floats where it is, visible through walls, with its distance when you look at it.");
    friends =
        view.bool("friends", "Friends too", true)
            .describe("Friends on your server who share their location get a marker as well.");
    page.group("Shortcut")
        .key(
            "add_key",
            "Add waypoint here",
            keys.bind(ADD_KEY, "Add waypoint here", KeyBinder.UNBOUND));
  }

  @Override
  public String group() {
    return "World";
  }
}
