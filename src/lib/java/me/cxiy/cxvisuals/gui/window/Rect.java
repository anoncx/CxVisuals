package me.cxiy.cxvisuals.gui.window;

public record Rect(float x, float y, float w, float h) {
  public static final Rect NONE = new Rect(0.0F, 0.0F, 0.0F, 0.0F);

  public float right() {
    return x + w;
  }

  public float bottom() {
    return y + h;
  }

  public boolean contains(float px, float py) {
    return px >= x && px < x + w && py >= y && py < y + h;
  }

  public boolean overlaps(Rect other) {
    return x < other.right() && right() > other.x && y < other.bottom() && bottom() > other.y;
  }
}
