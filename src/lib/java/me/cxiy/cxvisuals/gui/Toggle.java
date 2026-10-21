package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.settings.BoolSetting;

public final class Toggle extends Widget {
  private static final float INSET = 2.5F;
  private static final float KNOB = 20.0F;
  private static final float STRETCH = 6.0F;
  private static final int KNOB_SHADOW = 0x33000000;

  private final BoolSetting setting;
  private final Anim position;
  private final Anim tint;
  private final Anim stretch = Anim.press(0.0F);
  private final Anim press = Anim.pop(0.0F);
  private boolean pressed;

  public Toggle(BoolSetting setting) {
    this.setting = setting;
    float initial = setting.get() ? 1.0F : 0.0F;
    this.position = Anim.pop(initial);
    this.tint = Anim.reveal(initial);
    this.w = 42.0F;
    this.h = 25.0F;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    float target = setting.get() ? 1.0F : 0.0F;
    position.set(target);
    tint.set(target);
    stretch.set(pressed ? 1.0F : 0.0F);
    press.set(pressed ? 1.0F : 0.0F);
    position.update(dt);
    tint.update(dt);
    stretch.update(dt);
    press.update(dt);

    float knobWidth = KNOB + STRETCH * stretch.get();
    float knobX = x + INSET + (w - knobWidth - INSET * 2.0F) * position.get();
    float knobY = y + INSET;
    float amount = Math.max(0.0F, Math.min(1.0F, tint.get()));

    c.pushTransform(1.0F - 0.06F * press.get(), x + w * 0.5F, y + h * 0.5F);
    c.roundRect(x, y, w, h, h * 0.5F, Theme.mix(t.surface2(), t.ink(), amount));
    c.rim(x, y, w, h, h * 0.5F, 1.0F, h, Theme.withAlpha(t.inset(), 1.0F - amount));
    c.shadow(knobX, knobY, knobWidth, KNOB, KNOB * 0.5F, 3.0F, 1.0F, KNOB_SHADOW);
    Depth.knob(
        c, knobX, knobY, knobWidth, KNOB, KNOB * 0.5F, Theme.mix(0xFFFFFFFF, t.knobOn(), amount));
    c.popTransform();
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
      setting.set(!setting.get());
      Sound.toggled(setting.get());
    }
  }
}
