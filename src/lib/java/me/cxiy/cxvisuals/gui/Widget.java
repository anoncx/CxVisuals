package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.gui.spatial.Spring;

public abstract class Widget {
  public float x;
  public float y;
  public float w;
  public float h;

  private final Spring pop = Spring.critical(220.0F);

  public final float lift(boolean hovered, float dt) {
    pop.step(dt, hovered ? 1.0F : 0.0F);
    return pop.value();
  }

  public void place(float x, float y) {
    this.x = x;
    this.y = y;
  }

  public float measure(Canvas c) {
    return w;
  }

  public abstract void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt);

  public boolean mouseDown(float mx, float my) {
    return false;
  }

  public void mouseUp(float mx, float my) {}

  public boolean secondaryDown(float mx, float my) {
    return false;
  }

  public void mouseDrag(float mx, float my) {}

  public boolean scroll(float mx, float my, float amount) {
    return false;
  }

  public boolean keyDown(int keyCode) {
    return false;
  }

  public boolean keyDown(int keyCode, boolean control, boolean shift) {
    return keyDown(keyCode);
  }

  public TextInput focusedText() {
    return null;
  }

  public void finishEditing() {}

  public boolean charTyped(int codepoint) {
    return false;
  }

  public boolean contains(float mx, float my) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
  }

  protected static float centered(Canvas c, FontStyle font, float top, float height) {
    return top + (height - c.lineHeight(font)) * 0.5F;
  }
}
