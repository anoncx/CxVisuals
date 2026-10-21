package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.module.ClientSettings.WindowMotion;

public final class WindowFrame {
  private static final float DISSOLVE_PAD = 24.0F;
  private static final float MAX_RADIUS = 22.0F;

  private boolean folded;

  public boolean open(
      Canvas c,
      Theme t,
      Presence presence,
      WindowMotion style,
      float originX,
      float originY,
      float width,
      float height,
      float screenW,
      float screenH,
      float fit) {
    if (presence.phase() < 0.01F) {
      return false;
    }
    float radius = Math.min(t.radius(), MAX_RADIUS);
    float cardW = presence.width(style, width);
    float cardH = presence.height(style, height);
    float cardX = originX + (width - cardW) * 0.5F;
    float cardY = originY + presence.rise(style) + (height - cardH) * 0.5F;
    folded = cardW < width - 0.5F || cardH < height - 0.5F;
    c.pushTransform(fit, screenW * 0.5F, screenH * 0.5F);
    c.pushAlpha(presence.alpha(style));
    c.pushTransform(presence.scale(style), originX + width * 0.5F, originY + height * 0.5F);
    c.beginDissolve(
        originX - DISSOLVE_PAD,
        originY - DISSOLVE_PAD,
        width + DISSOLVE_PAD * 2.0F,
        height + DISSOLVE_PAD * 2.0F,
        presence.erosion(style));
    c.pushLayer(10.0F);
    c.shadow(cardX, cardY, cardW, cardH, radius, 28.0F, 10.0F, t.cardShadow());
    if (t.glass()) {
      c.glass(cardX, cardY, cardW, cardH, radius, t.paper());
    } else {
      c.roundRect(cardX, cardY, cardW, cardH, radius, t.paper());
    }
    c.rim(
        cardX,
        cardY,
        cardW,
        cardH,
        radius,
        1.0F,
        Math.min(cardH, radius * 1.4F),
        Depth.rimColor(t));
    if (folded) {
      c.pushClip(cardX, cardY, cardW, cardH);
    }
    c.pushAlpha(presence.content(style));
    return true;
  }

  public void close(Canvas c) {
    c.popAlpha();
    if (folded) {
      c.popClip();
    }
    c.popLayer();
    c.endDissolve();
    c.popTransform();
    c.popAlpha();
    c.popTransform();
  }
}
