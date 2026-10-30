package me.cxiy.cxvisuals.gui.map;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
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

  default boolean forecasting() {
    return false;
  }

  int height(int x, int z);

  List<Waypoints.Target> friends();

  void shareArea(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, Waypoints.Target to);

  boolean downloadArea(int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ);

  void findBlocks(String query, double nearX, double nearZ, Consumer<BlockSearch.Result> done);

  MapPlayer player(int entityId);

  String notice();

  default boolean regionsAvailable() {
    return false;
  }

  default boolean regionsOn() {
    return false;
  }

  default void regionsOn(boolean on) {}

  default int regionAt(double x, double z) {
    return 0;
  }

  default ShareHost share() {
    return ShareHost.NONE;
  }

  default ExploreHost explore(Supplier<int[]> selection) {
    return ExploreHost.NONE;
  }
}
