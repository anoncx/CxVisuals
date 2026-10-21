package me.cxiy.cxvisuals.gui.accounts;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;

final class Pill {
  enum Tone {
    PRIMARY,
    QUIET,
    DANGER
  }

  static final float HEIGHT = 32.0F;

  private static final float PAD = 18.0F;
  private static final float DISABLED = 0.38F;

  private final Anim hover = Anim.hover(0.0F);
  private final Anim press = Anim.press(0.0F);
  private String label;
  private Tone tone;
  private boolean enabled = true;
  private boolean held;
  private float minimum;
  float x;
  float y;
  float w;
  float h = HEIGHT;

  Pill(String label, Tone tone) {
    this.label = label;
    this.tone = tone;
  }

  Pill label(String next) {
    label = next;
    return this;
  }

  Pill tone(Tone next) {
    tone = next;
    return this;
  }

  Pill enabled(boolean on) {
    enabled = on;
    return this;
  }

  Pill minimum(float width) {
    minimum = width;
    return this;
  }

  Pill height(float value) {
    h = value;
    return this;
  }

  float width(Canvas c) {
    return Math.max(
        minimum, (float) Math.ceil(c.textWidth(FontStyle.LABEL, Lang.tr(label)) + PAD * 2.0F));
  }

  boolean contains(float mx, float my) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
  }

  void draw(Canvas c, Theme t, float px, float py, float mx, float my, float dt) {
    draw(c, t, px, py, width(c), mx, my, dt);
  }

  void draw(Canvas c, Theme t, float px, float py, float pw, float mx, float my, float dt) {
    x = px;
    y = py;
    w = pw;
    hover.set(enabled && contains(mx, my) ? 1.0F : 0.0F);
    press.set(held ? 1.0F : 0.0F);
    hover.update(dt);
    press.update(dt);
    boolean primary = tone == Tone.PRIMARY;
    int base = primary ? t.ink() : t.surface2();
    int fill = Theme.mix(base, primary ? t.paper() : t.ink(), 0.08F * hover.get());
    int ink = primary ? t.paper() | 0xFF000000 : tone == Tone.DANGER ? t.alert() : t.ink();
    String text = Lang.tr(label);
    if (!enabled) {
      c.pushAlpha(DISABLED);
    }
    c.pushTransform(1.0F - 0.04F * press.get(), x + w * 0.5F, y + h * 0.5F);
    Depth.raised(c, t, x, y, w, h, h * 0.5F, fill);
    float width = c.textWidth(FontStyle.LABEL, text);
    float top = y + (h - c.lineHeight(FontStyle.LABEL)) * 0.5F;
    if (width > w - 16.0F) {
      c.pushClip(x + 8.0F, y, w - 16.0F, h);
      c.text(FontStyle.LABEL, text, x + 8.0F, top, ink);
      c.popClip();
    } else {
      c.text(FontStyle.LABEL, text, x + (w - width) * 0.5F, top, ink);
    }
    c.popTransform();
    if (!enabled) {
      c.popAlpha();
    }
  }

  boolean down(float mx, float my) {
    if (!enabled || !contains(mx, my)) {
      return false;
    }
    held = true;
    return true;
  }

  boolean up(float mx, float my) {
    boolean click = held && enabled && contains(mx, my);
    held = false;
    return click;
  }

  void release() {
    held = false;
  }
}
