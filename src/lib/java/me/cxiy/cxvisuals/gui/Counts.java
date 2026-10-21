package me.cxiy.cxvisuals.gui;

public final class Counts {
  private static final float REFERENCE = 40.0F;
  private static final float SMALLEST = 0.5F;
  private static final float LARGEST = 1.3F;
  private static final int SHORTEN_FROM = 10_000;

  private Counts() {}

  public static void draw(Canvas c, Theme t, int count, float right, float bottom, float item) {
    String label = count >= SHORTEN_FROM ? count / 1000 + "k" : String.valueOf(count);
    float scale = Math.max(SMALLEST, Math.min(LARGEST, item / REFERENCE));
    c.pushTransform(scale, right, bottom);
    c.text(
        FontStyle.CAPTION,
        label,
        right - c.textWidth(FontStyle.CAPTION, label),
        bottom - c.lineHeight(FontStyle.CAPTION),
        t.ink());
    c.popTransform();
  }
}
