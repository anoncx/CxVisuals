package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Theme;

public final class FramePage extends Page {
  @Override
  public void measure(Canvas c, float roomW, float roomH) {
    wantW = roomW;
    wantH = roomH;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {}
}
