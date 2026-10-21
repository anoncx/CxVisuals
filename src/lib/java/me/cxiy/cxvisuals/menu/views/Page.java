package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.gui.Canvas;

public abstract class Page extends MenuView {
  public float wantW;
  public float wantH;
  protected float px;
  protected float py;
  protected float pw;
  protected float ph;
  protected float age;
  protected boolean frozen;

  public boolean plated() {
    return true;
  }

  public abstract void measure(Canvas c, float roomW, float roomH);

  public void place(float x, float y, float w, float h) {
    px = x;
    py = y;
    pw = w;
    ph = h;
  }

  public void entered() {
    age = 0.0F;
    frozen = false;
  }

  public void freeze() {
    frozen = true;
  }
}
