package me.cxiy.cxvisuals.gui.window;

import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Theme;

public interface Pane {
  String title();

  float preferredWidth();

  float minimumWidth();

  float preferredHeight(float limit);

  default void measure(Canvas c, float width) {}

  void draw(
      Canvas c, Theme t, float x, float y, float w, float h, float mouseX, float mouseY, float dt);

  boolean mouseDown(float mx, float my);

  void mouseUp(float mx, float my);

  void mouseDrag(float mx, float my);

  boolean scroll(float mx, float my, float amount);

  boolean secondaryDown(float mx, float my);

  boolean keyDown(int keyCode, int scanCode, boolean control, boolean shift);

  boolean charTyped(int codepoint);

  default boolean typing() {
    return false;
  }

  void blur();
}
