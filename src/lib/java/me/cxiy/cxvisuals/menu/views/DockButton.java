package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;

final class DockButton {
  static final float SIZE = 44.0F;

  private static final float GLYPH = 22.0F;
  private static final float LIFT = 0.05F;
  private static final float PRESS = 0.95F;
  private static final float RIPPLE_REACH = 0.55F;
  private static final float RIPPLE_ALPHA = 0.6F;
  private static final float LABEL_GAP = 10.0F;
  private static final float TIP_PAD = 11.0F;
  private static final float TIP_HEIGHT = 26.0F;
  private static final float TIP_RISE = 8.0F;

  private final Anim hover = Anim.hover(0.0F);
  private final Anim ripple = new Anim(1.0F, 0.55F, Anim.Curve.LINEAR);
  private final Sfx sfx;
  private final int glyph;
  private boolean down;
  private boolean over;
  private float x;
  private float y;

  DockButton(int glyph, Sfx sfx) {
    this.glyph = glyph;
    this.sfx = sfx;
  }

  boolean contains(float mx, float my) {
    return Kit.inside(mx, my, x, y, SIZE, SIZE);
  }

  void press() {
    down = true;
    sfx.press();
  }

  boolean release(float mx, float my) {
    boolean fire = down && contains(mx, my);
    down = false;
    return fire;
  }

  void draw(
      Canvas c,
      Theme t,
      String label,
      float left,
      float top,
      boolean enabled,
      float mouseX,
      float mouseY,
      float dt) {
    x = left;
    y = top;
    boolean entered = enabled && contains(mouseX, mouseY);
    if (entered && !over) {
      ripple.snap(0.0F);
      ripple.set(1.0F);
      sfx.hover(0);
    }
    over = entered;
    hover.set(entered ? 1.0F : 0.0F);
    hover.update(dt);
    ripple.update(dt);
    float lit = hover.get();
    c.pushAlpha(enabled ? 1.0F : 0.4F);
    c.pushTransform((down ? PRESS : 1.0F) * (1.0F + LIFT * lit), x + SIZE * 0.5F, y + SIZE * 0.5F);
    Depth.raised(c, t, x, y, SIZE, SIZE, SIZE * 0.5F, Theme.mix(t.surface2(), t.ink(), 0.1F * lit));
    Pictograms.draw(
        c,
        t,
        glyph,
        x + (SIZE - GLYPH) * 0.5F,
        y + (SIZE - GLYPH) * 0.5F,
        GLYPH,
        Theme.mix(t.ink2(), t.ink(), lit),
        false);
    c.popTransform();
    float spread = ripple.get();
    if (spread < 1.0F && over) {
      float grow = SIZE * (1.0F + RIPPLE_REACH * spread);
      c.roundRectBorder(
          x + (SIZE - grow) * 0.5F,
          y + (SIZE - grow) * 0.5F,
          grow,
          grow,
          grow * 0.5F,
          1.2F,
          Theme.withAlpha(t.ink(), RIPPLE_ALPHA * (1.0F - spread)));
    }
    c.popAlpha();
    if (lit > 0.02F && !label.isEmpty()) {
      tip(c, t, label, lit);
    }
  }

  private void tip(Canvas c, Theme t, String label, float lit) {
    float width = c.textWidth(FontStyle.LABEL, label) + TIP_PAD * 2.0F;
    float tipX = x + (SIZE - width) * 0.5F;
    float tipY = y - LABEL_GAP - TIP_HEIGHT + TIP_RISE * (1.0F - lit);
    c.pushAlpha(lit);
    c.shadow(tipX, tipY, width, TIP_HEIGHT, TIP_HEIGHT * 0.5F, 14.0F, 5.0F, t.cardShadow());
    Depth.raised(c, t, tipX, tipY, width, TIP_HEIGHT, TIP_HEIGHT * 0.5F, t.surface2());
    c.text(
        FontStyle.LABEL,
        label,
        tipX + TIP_PAD,
        tipY + (TIP_HEIGHT - c.lineHeight(FontStyle.LABEL)) * 0.5F,
        t.ink());
    c.popAlpha();
  }
}
