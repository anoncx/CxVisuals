package me.cxiy.cxvisuals.gui.map;

final class GroundAnchor {
  private double height = Double.NaN;

  double hold(double terrain) {
    if (Double.isNaN(height)) {
      height = terrain;
    }
    return height;
  }

  void reset() {
    height = Double.NaN;
  }
}
