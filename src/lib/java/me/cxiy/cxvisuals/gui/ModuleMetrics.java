package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.gui.spatial.Layer;

final class ModuleMetrics {
  static final float HEADER = 52.0F;
  static final float HEADER_GAP = 12.0F;
  static final float HEADER_INSET = 4.0F;
  static final float TITLE_TOP = 10.0F;
  static final float COMPACT_HEADER = 38.0F;
  static final float COMPACT_BARE = 2.0F;
  static final float TABS_HEIGHT = 30.0F;
  static final float TABS_GAP = 12.0F;
  static final float GROUP_LABEL = 28.0F;
  static final float GROUP_GAP = 10.0F;
  static final float ROW = 50.0F;
  static final float ROW_DESCRIBED = 64.0F;
  static final float ROW_INSET = 16.0F;
  static final float CONTROL_INSET = 14.0F;
  static final float PANEL_INSET = 8.0F;
  static final float TEXT_GAP = 12.0F;
  static final float LINE_GAP = 1.0F;
  static final float MIN_TEXT = 170.0F;
  static final float STACK_LIFT = 8.0F;
  static final float STACK_PAD = 6.0F;
  static final float GRID_BOTTOM = 14.0F;
  static final float GRID_DROP = 8.0F;
  static final float HIDDEN = 0.004F;
  static final float OFFSCREEN = -10000.0F;
  static final float REVEAL_RISE = 12.0F;
  static final float PIN_SHADOW = 18.0F;
  static final float SCROLL_STEP = 40.0F;
  static final float SCROLL_RESPONSE = 18.0F;
  static final float PAGE_SLIDE = 10.0F;
  static final float CONTENT_HEIGHT = Layer.height(Layer.ROW);
  static final float CARD_HEIGHT = Layer.height(Layer.CARD);

  private ModuleMetrics() {}

  static float clamp01(float value) {
    return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
  }

  static float ease(float current, float target, float dt) {
    float next = current + (target - current) * (1.0F - (float) Math.exp(-dt * SCROLL_RESPONSE));
    return Math.abs(target - next) < 0.05F ? target : next;
  }
}
