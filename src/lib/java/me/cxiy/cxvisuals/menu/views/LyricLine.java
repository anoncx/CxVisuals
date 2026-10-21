package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;

final class LyricLine {
  private static final float FADE = 0.18F;
  private static final float SWAP = 0.16F;
  private static final float MIN_ALPHA = 0.01F;

  private final Anim shown = new Anim(0.0F, FADE, Anim.Curve.LINEAR);
  private final Anim swap = new Anim(1.0F, SWAP, Anim.Curve.LINEAR);
  private String line = "";
  private String before = "";

  void update(String next, float dt) {
    if (!next.isEmpty() && !next.equals(line)) {
      before = shown.get() > MIN_ALPHA ? line : "";
      line = next;
      swap.snap(0.0F);
      swap.set(1.0F);
    }
    shown.set(next.isEmpty() ? 0.0F : 1.0F);
    shown.update(dt);
    swap.update(dt);
  }

  float presence() {
    return shown.get();
  }

  void draw(Canvas c, Theme t, float x, float y, float w) {
    float presence = shown.get();
    if (presence < MIN_ALPHA) {
      return;
    }
    float arrival = swap.get();
    if (arrival < 1.0F && !before.isEmpty()) {
      text(c, t, before, x, y, w, presence * (1.0F - arrival));
    }
    text(c, t, line, x, y, w, presence * (before.isEmpty() ? 1.0F : arrival));
  }

  private static void text(
      Canvas c, Theme t, String value, float x, float y, float w, float alpha) {
    float width = Math.min(w, c.textWidth(FontStyle.CAPTION, value));
    c.pushAlpha(alpha);
    c.pushClip(x, y - 2.0F, w, c.lineHeight(FontStyle.CAPTION) + 4.0F);
    c.text(FontStyle.CAPTION, value, x + (w - width) * 0.5F, y, t.ink2());
    c.popClip();
    c.popAlpha();
  }
}
