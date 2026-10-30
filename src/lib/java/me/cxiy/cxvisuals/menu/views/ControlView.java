package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.Field;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.menu.model.Control;

final class ControlView {
  static final float ROW = 46.0F;
  static final float INSET = 16.0F;

  private static final float TRACK = 150.0F;
  private static final float BAR = 4.0F;
  private static final float KNOB = 18.0F;
  private static final float READOUT_GAP = 12.0F;
  private static final float VALUE_PAD = 13.0F;
  private static final float VALUE_HEIGHT = 28.0F;
  private static final float CHEVRON = 14.0F;
  private static final int KNOB_SHADOW = 0x40000000;
  private static final int KNOB_COLOR = 0xFFFFFFFF;
  private static final float OFF = 0.38F;
  private static final String NEXT = "›";
  private static final String WIDEST = "100%";

  final Control control;
  final Field field;
  float x;
  float y;
  float w;
  float h;
  boolean shown;

  private final Anim hover = Anim.hover(0.0F);
  private final Anim press = Anim.press(0.0F);
  private final Anim position;
  private final Anim tint;
  private float trackX;
  private float readoutWidth;
  private boolean pressed;
  private boolean dragging;
  private String pushed;

  ControlView(Control control) {
    this.control = control;
    float initial = control.on() ? 1.0F : 0.0F;
    position = Anim.pop(initial);
    tint = Anim.reveal(initial);
    if (control.kind() == Control.Kind.TEXT) {
      field = new Field(control.label(), control.limit());
      pushed = control.text();
      field.set(pushed);
    } else {
      field = null;
    }
  }

  private void animate(float mouseX, float mouseY, float dt) {
    hover.set(shown && control.enabled() && Kit.inside(mouseX, mouseY, x, y, w, h) ? 1.0F : 0.0F);
    press.set(pressed ? 1.0F : 0.0F);
    hover.update(dt);
    press.update(dt);
  }

  void pill(Canvas c, Theme t, float mouseX, float mouseY, float dt, boolean filled) {
    shown = true;
    animate(mouseX, mouseY, dt);
    Kit.pill(
        c, t, control.label(), x, y, w, h, hover.get(), press.get(), filled, control.enabled());
  }

  void item(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    shown = true;
    animate(mouseX, mouseY, dt);
    float amount = Math.max(control.on() ? 1.0F : 0.0F, hover.get() * 0.45F);
    if (amount > 0.01F) {
      c.pushAlpha(amount);
      Depth.raised(c, t, x, y, w, h, 9.0F, t.surface2());
      c.popAlpha();
    }
    int ink = control.on() ? t.ink() : Theme.mix(t.ink2(), t.ink(), hover.get());
    c.pushClip(x, y, w - 6.0F, h);
    c.text(
        FontStyle.LABEL,
        control.label(),
        x + 12.0F,
        y + (h - c.lineHeight(FontStyle.LABEL)) * 0.5F,
        control.enabled() ? ink : t.grey());
    c.popClip();
  }

  void row(Canvas c, Theme t, String label, float mouseX, float mouseY, float dt) {
    shown = true;
    animate(mouseX, mouseY, dt);
    boolean enabled = control.enabled();
    if (!enabled) {
      c.pushAlpha(OFF);
    }
    float middle = y + h * 0.5F;
    float right = x + w - INSET;
    float labelTop = middle - c.lineHeight(FontStyle.ROW) * 0.5F;
    switch (control.kind()) {
      case TOGGLE -> {
        float target = control.on() ? 1.0F : 0.0F;
        position.set(target);
        tint.set(target);
        position.update(dt);
        tint.update(dt);
        c.text(FontStyle.ROW, label, x + INSET, labelTop, t.ink());
        Kit.toggle(
            c,
            t,
            right - Kit.TOGGLE_WIDTH,
            middle - Kit.TOGGLE_HEIGHT * 0.5F,
            position.get(),
            tint.get(),
            press.get());
      }
      case CYCLE -> {
        String value = control.value();
        float width = (float) Math.ceil(c.textWidth(FontStyle.CAPTION, value) + VALUE_PAD * 2.0F);
        float left = right - width;
        c.text(FontStyle.ROW, label, x + INSET, labelTop, t.ink());
        c.pushTransform(1.0F - 0.05F * press.get(), left + width * 0.5F, middle);
        Depth.raised(
            c,
            t,
            left,
            middle - VALUE_HEIGHT * 0.5F,
            width,
            VALUE_HEIGHT,
            VALUE_HEIGHT * 0.5F,
            Theme.mix(t.surface2(), t.ink(), 0.08F * hover.get()));
        c.text(
            FontStyle.CAPTION,
            value,
            left + VALUE_PAD,
            middle - c.lineHeight(FontStyle.CAPTION) * 0.5F,
            t.ink());
        c.popTransform();
      }
      case SLIDER -> {
        String value = control.value();
        float shownWidth = c.textWidth(FontStyle.MONO, value);
        readoutWidth =
            Math.max(
                readoutWidth, Math.max(shownWidth, c.textWidth(FontStyle.MONO, WIDEST)));
        float readout = readoutWidth;
        trackX = right - readout - READOUT_GAP - TRACK;
        float fraction = Math.max(0.0F, Math.min(1.0F, control.fraction()));
        float centre = trackX + KNOB * 0.5F + (TRACK - KNOB) * fraction;
        c.text(FontStyle.ROW, label, x + INSET, labelTop, t.ink());
        c.roundRect(trackX, middle - BAR * 0.5F, TRACK, BAR, BAR * 0.5F, t.surface2());
        c.roundRect(
            trackX, middle - BAR * 0.5F, Math.max(BAR, centre - trackX), BAR, BAR * 0.5F, t.ink());
        c.shadow(
            centre - KNOB * 0.5F,
            middle - KNOB * 0.5F,
            KNOB,
            KNOB,
            KNOB * 0.5F,
            4.0F,
            1.0F,
            KNOB_SHADOW);
        Depth.knob(
            c, centre - KNOB * 0.5F, middle - KNOB * 0.5F, KNOB, KNOB, KNOB * 0.5F, KNOB_COLOR);
        c.text(
            FontStyle.MONO,
            value,
            right - shownWidth,
            middle - c.lineHeight(FontStyle.MONO) * 0.5F,
            Theme.mix(t.grey(), t.ink(), press.get()));
      }
      case TEXT -> {
        if (field.focused()) {
          String typed = field.text();
          if (!typed.equals(pushed)) {
            pushed = typed;
            control.text(typed);
          }
        } else {
          pushed = control.text();
          field.set(pushed);
        }
        field.draw(c, t, x + INSET, middle - Field.HEIGHT * 0.5F, w - INSET * 2.0F, dt);
      }
      case BUTTON -> {
        if (hover.get() > 0.01F || pressed) {
          c.pushAlpha(Math.max(hover.get(), press.get()));
          c.roundRect(
              x + 4.0F, y + 3.0F, w - 8.0F, h - 6.0F, 10.0F, Theme.withAlpha(t.ink(), 0.07F));
          c.popAlpha();
        }
        c.text(FontStyle.ROW, label, x + INSET, labelTop, t.ink());
        c.text(
            FontStyle.ROW, NEXT, right - CHEVRON * 0.5F + hover.get() * 2.0F, labelTop, t.grey());
      }
    }
    if (!enabled) {
      c.popAlpha();
    }
  }

  boolean down(float mx, float my) {
    if (!shown || !control.enabled() || !Kit.inside(mx, my, x, y, w, h)) {
      return false;
    }
    pressed = true;
    if (control.kind() == Control.Kind.SLIDER) {
      dragging = true;
      slide(mx);
    } else if (control.kind() == Control.Kind.TEXT) {
      field.focus(true);
      field.press(mx);
    }
    return true;
  }

  void drag(float mx) {
    if (dragging) {
      slide(mx);
    } else if (field != null && pressed) {
      field.drag(mx);
    }
  }

  void up(float mx, float my) {
    if (!pressed) {
      return;
    }
    pressed = false;
    if (dragging) {
      dragging = false;
      control.release();
    } else if (field != null) {
      field.release();
    } else if (Kit.inside(mx, my, x, y, w, h)) {
      control.press();
    }
  }

  private void slide(float mx) {
    control.fraction(Math.max(0.0F, Math.min(1.0F, (mx - trackX - KNOB * 0.5F) / (TRACK - KNOB))));
  }
}
