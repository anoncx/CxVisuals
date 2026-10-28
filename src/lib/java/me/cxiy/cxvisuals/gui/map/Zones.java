package me.cxiy.cxvisuals.gui.map;

import java.util.ArrayList;
import java.util.List;

final class Zones {
  private record Zone(float x, float y, float w, float h, Runnable action) {}

  private final List<Zone> zones = new ArrayList<>();

  void clear() {
    zones.clear();
  }

  void add(float x, float y, float w, float h, Runnable action) {
    zones.add(new Zone(x, y, w, h, action));
  }

  boolean press(float mx, float my) {
    for (int i = zones.size() - 1; i >= 0; i--) {
      Zone zone = zones.get(i);
      if (ShareWindowMath.inside(mx, my, zone.x(), zone.y(), zone.w(), zone.h())) {
        zone.action().run();
        return true;
      }
    }
    return false;
  }
}
