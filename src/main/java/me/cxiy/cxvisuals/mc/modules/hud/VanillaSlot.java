package me.cxiy.cxvisuals.mc.modules.hud;

import me.cxiy.cxvisuals.gui.spatial.Layer;
import me.cxiy.cxvisuals.modules.hud.HostedElement;

public final class VanillaSlot {
  public interface Fallback {
    void apply(VanillaSlot slot, float screenW, float screenH);
  }

  final HostedElement element;
  private final Fallback fallback;
  float x;
  float y;
  float w;
  float h;
  int stamp = -1;
  int depth;
  float layer = Layer.HUD_MID;
  boolean lettered;
  boolean floats = true;

  VanillaSlot(HostedElement element, Fallback fallback) {
    this.element = element;
    this.fallback = fallback;
  }

  public void natural(float left, float top, float width, float height) {
    set(left, top, width, height);
    stamp = Hud.frameIndex();
  }

  void set(float left, float top, float width, float height) {
    x = left;
    y = top;
    w = width;
    h = height;
  }

  void settle(int frame, float screenW, float screenH, float unit) {
    boolean live = stamp == frame;
    if (!live) {
      fallback.apply(this, screenW, screenH);
    }
    element.natural(x / unit, y / unit, w / unit, h / unit, live);
  }
}
