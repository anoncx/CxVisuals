package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.Theme;

final class DockPlate {
  static final float DIP = 14.0F;

  private static final int ARC = 8;
  private static final int VALLEY = 56;
  private static final float VALLEY_SHARE = 0.34F;
  private static final int TOP = ARC + 1 + VALLEY + ARC + 1;
  private static final int SHADOW = 0x66000000;

  private final float[] topX = new float[TOP];
  private final float[] topY = new float[TOP];
  private final float[] edgeX = new float[TOP + 2 * (ARC + 1) + 1];
  private final float[] edgeY = new float[TOP + 2 * (ARC + 1) + 1];

  void draw(Canvas c, Theme t, float x, float y, float w, float h) {
    if (t.translucent() || !c.curves()) {
      Kit.window(c, t, x, y, w, h);
      return;
    }
    float radius = Math.min(Kit.WINDOW_RADIUS, h * 0.5F);
    int top = path(x, y, w, radius);
    int edge = outline(x, y, w, h, radius, top);
    int paper = t.paper() | 0xFF000000;
    c.shadow(x, y, w, h, radius, 44.0F, 18.0F, SHADOW);
    c.area(topX, topY, top, y + h - radius, paper, paper);
    c.roundRect(x, y + h - radius * 2.0F, w, radius * 2.0F, radius, paper);
    c.stroke(edgeX, edgeY, edge, 1.0F, 0.0F, t.hair());
    c.stroke(topX, topY, top, 1.0F, 0.0F, Depth.rimColor(t));
  }

  private int path(float x, float y, float w, float radius) {
    int n = 0;
    for (int i = 0; i <= ARC; i++) {
      double angle = Math.PI + Math.PI * 0.5 * i / ARC;
      topX[n] = x + radius + radius * (float) Math.cos(angle);
      topY[n++] = y + radius + radius * (float) Math.sin(angle);
    }
    float span = w * VALLEY_SHARE;
    float left = x + (w - span) * 0.5F;
    for (int i = 0; i < VALLEY; i++) {
      float u = i / (float) (VALLEY - 1);
      topX[n] = left + span * u;
      topY[n++] = y + DIP * (0.5F - 0.5F * (float) Math.cos(u * 2.0 * Math.PI));
    }
    for (int i = 0; i <= ARC; i++) {
      double angle = Math.PI * 1.5 + Math.PI * 0.5 * i / ARC;
      topX[n] = x + w - radius + radius * (float) Math.cos(angle);
      topY[n++] = y + radius + radius * (float) Math.sin(angle);
    }
    return n;
  }

  private int outline(float x, float y, float w, float h, float radius, int top) {
    System.arraycopy(topX, 0, edgeX, 0, top);
    System.arraycopy(topY, 0, edgeY, 0, top);
    int n = top;
    for (int i = 0; i <= ARC; i++) {
      double angle = Math.PI * 0.5 * i / ARC;
      edgeX[n] = x + w - radius + radius * (float) Math.cos(angle);
      edgeY[n++] = y + h - radius + radius * (float) Math.sin(angle);
    }
    for (int i = 0; i <= ARC; i++) {
      double angle = Math.PI * 0.5 + Math.PI * 0.5 * i / ARC;
      edgeX[n] = x + radius + radius * (float) Math.cos(angle);
      edgeY[n++] = y + h - radius + radius * (float) Math.sin(angle);
    }
    edgeX[n] = topX[0];
    edgeY[n++] = topY[0];
    return n;
  }
}
