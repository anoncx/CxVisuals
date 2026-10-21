package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.i18n.Lang;

public final class IconButton extends Widget {
  public enum Glyph {
    CLOSE,
    DETACH,
    DOCK,
    SEARCH
  }

  public static final float SIZE = 22.0F;
  public static final String LABEL_OPEN = Lang.key("Open in window");
  public static final String LABEL_DOCK = Lang.key("Dock");
  public static final String LABEL_CLOSE = Lang.key("Close");
  public static final String LABEL_SEARCH = Lang.key("Search");

  private static final float PAD = 5.0F;
  private static final float STROKE = 1.5F;
  private static final float REST_MIX = 0.06F;
  private static final float HOVER_MIX = 0.16F;
  private static final float DWELL = 0.4F;
  private static final float LABEL_PAD = 9.0F;
  private static final float LABEL_GAP = 6.0F;
  private static final int DETACH_DOTS = 3;
  private static final float DETACH_DOT = 1.8F;
  private static final String CROSS = "×";

  private final Glyph glyph;
  private final String label;
  private final Runnable action;
  private final Anim hover = Anim.hover(0.0F);
  private final Anim press = Anim.press(0.0F);
  private final Anim reveal = Anim.state(0.0F);
  private float dwell;
  private boolean pressed;

  public IconButton(Glyph glyph, Runnable action) {
    this(glyph, "", action);
  }

  public IconButton(Glyph glyph, String label, Runnable action) {
    this.glyph = glyph;
    this.label = label;
    this.action = action;
    this.w = SIZE;
    this.h = SIZE;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    boolean over = contains(mouseX, mouseY);
    dwell = over ? dwell + dt : 0.0F;
    hover.set(over ? 1.0F : 0.0F);
    press.set(pressed ? 1.0F : 0.0F);
    reveal.set(!label.isEmpty() && (dwell > DWELL || pressed) ? 1.0F : 0.0F);
    hover.update(dt);
    press.update(dt);
    reveal.update(dt);

    int ink = Theme.mix(t.ink2(), t.ink(), hover.get());
    float shown = reveal.get();
    float extra = 0.0F;
    String text = null;
    if (shown > 0.01F) {
      text = Lang.tr(label);
      extra = (c.textWidth(FontStyle.CAPTION, text) + LABEL_PAD + LABEL_GAP) * shown;
    }
    c.pushTransform(1.0F - 0.08F * press.get(), x + w * 0.5F, y + h * 0.5F);
    int plate = Theme.withAlpha(t.ink(), REST_MIX + (HOVER_MIX - REST_MIX) * hover.get());
    c.roundRect(x - extra, y, w + extra, h, h * 0.5F, plate);
    if (text != null) {
      c.pushAlpha(shown);
      c.text(
          FontStyle.CAPTION,
          text,
          x - extra + LABEL_PAD,
          centered(c, FontStyle.CAPTION, y, h),
          t.ink());
      c.popAlpha();
    }
    drawGlyph(c, ink);
    c.popTransform();
  }

  private void drawGlyph(Canvas c, int ink) {
    float left = x + PAD;
    float top = y + PAD;
    float size = w - PAD * 2.0F;
    switch (glyph) {
      case CLOSE ->
          c.text(
              FontStyle.ROW,
              CROSS,
              x + (w - c.textWidth(FontStyle.ROW, CROSS)) * 0.5F,
              centered(c, FontStyle.ROW, y, h) - 1.0F,
              ink);
      case DETACH -> {
        float body = size * 0.8F;
        c.roundRectBorder(left, top + size - body, body, body, 2.5F, STROKE, ink);
        float arm = size * 0.55F;
        c.roundRect(left + size - arm, top, arm, STROKE, STROKE * 0.5F, ink);
        c.roundRect(left + size - STROKE, top, STROKE, arm, STROKE * 0.5F, ink);
        for (int dot = 0; dot < DETACH_DOTS; dot++) {
          float step = size * (0.3F + 0.2F * dot);
          c.roundRect(
              left + step - DETACH_DOT * 0.5F,
              top + size - step - DETACH_DOT * 0.5F,
              DETACH_DOT,
              DETACH_DOT,
              DETACH_DOT * 0.5F,
              ink);
        }
      }
      case DOCK -> {
        c.roundRectBorder(left, top, size, size, 2.5F, STROKE, ink);
        c.roundRect(
            left + 2.5F, top + size * 0.5F, size * 0.5F - 1.0F, size * 0.5F - 2.5F, 1.0F, ink);
      }
      case SEARCH -> {
        float lens = size * 0.72F;
        c.roundRectBorder(left, top, lens, lens, lens * 0.5F, STROKE, ink);
        for (int dot = 0; dot < DETACH_DOTS - 1; dot++) {
          float step = lens * 0.78F + size * 0.13F * (dot + 1);
          c.roundRect(
              left + step - DETACH_DOT * 0.5F,
              top + step - DETACH_DOT * 0.5F,
              DETACH_DOT,
              DETACH_DOT,
              DETACH_DOT * 0.5F,
              ink);
        }
      }
    }
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (!contains(mx, my)) {
      return false;
    }
    pressed = true;
    return true;
  }

  @Override
  public void mouseUp(float mx, float my) {
    if (!pressed) {
      return;
    }
    pressed = false;
    if (contains(mx, my)) {
      Sound.play(Sound.Cue.TAB);
      action.run();
    }
  }
}
