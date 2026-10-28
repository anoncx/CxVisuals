package me.cxiy.cxvisuals.gui.map;

import java.util.List;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.map.BlockSearch;
import me.cxiy.cxvisuals.modules.map.MapSource;
import me.cxiy.cxvisuals.modules.waypoints.Waypoints;

public interface MapHost {
  MapSource source();

  HudData data();

  Waypoints waypoints();

  String title();

  String dimension();

  int height(int x, int z);

  List<Waypoints.Target> friends();

  void shareArea(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, Waypoints.Target to);

  boolean downloadArea(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ);

  void findBlocks(String query, double nearX, double nearZ, Consumer<BlockSearch.Result> done);

  MapPlayer player(int entityId);

  String notice();

  default ShareHost share() {
    return ShareHost.NONE;
  }
}
