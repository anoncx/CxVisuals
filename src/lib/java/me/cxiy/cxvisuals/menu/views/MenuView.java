package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Theme;

public abstract class MenuView {
  protected float screenW;
  protected float screenH;

  public void resize(float width, float height) {
    screenW = width;
    screenH = height;
  }

  public abstract void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt);

  public boolean mouseDown(float mx, float my) {
    return false;
  }

  public void mouseUp(float mx, float my) {}

  public void mouseDrag(float mx, float my) {}

  public boolean scroll(float mx, float my, float amount) {
    return false;
  }

  public boolean keyDown(int keyCode, int scanCode, int modifiers) {
    return false;
  }

  public boolean charTyped(int codepoint) {
    return false;
  }

  public boolean typing() {
    return false;
  }
}
