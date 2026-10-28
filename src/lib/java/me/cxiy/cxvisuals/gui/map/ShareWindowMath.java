package me.cxiy.cxvisuals.gui.map;

final class ShareWindowMath {
  private ShareWindowMath() {}

  static boolean inside(float px, float py, float x, float y, float w, float h) {
    return px >= x && px < x + w && py >= y && py < y + h;
  }
}
