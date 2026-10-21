package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.settings.ActionSetting;

public final class Button extends Widget {
  private static final float PADDING = 32.0F;

  private final ActionSetting setting;
  private final Anim press = Anim.press(0.0F);
  private final Anim hover = Anim.hover(0.0F);
  private boolean pressed;

  public Button(ActionSetting setting) {
    this.setting = setting;
    this.w = 72.0F;
    this.h = 30.0F;
  }

  @Override
  public float measure(Canvas c) {
    w = (float) Math.ceil(c.textWidth(FontStyle.LABEL, setting.buttonLabel()) + PADDING);
    return w;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    press.set(pressed ? 1.0F : 0.0F);
    hover.set(contains(mouseX, mouseY) ? 1.0F : 0.0F);
    press.update(dt);
    hover.update(dt);

    boolean primary = setting.kind() == ActionSetting.Kind.PRIMARY;
    int base = primary ? t.ink() : t.surface2();
    int fill = Theme.mix(base, primary ? t.paper() : t.ink(), 0.08F * hover.get());
    int ink =
        switch (setting.kind()) {
          case PRIMARY -> t.paper();
          case DANGER -> t.alert();
          case QUIET -> t.ink();
        };

    String label = setting.buttonLabel();
    c.pushTransform(1.0F - 0.05F * press.get(), x + w * 0.5F, y + h * 0.5F);
    Depth.raised(c, t, x, y, w, h, h * 0.5F, fill);
    float textX = x + (w - c.textWidth(FontStyle.LABEL, label)) * 0.5F;
    c.text(FontStyle.LABEL, label, textX, centered(c, FontStyle.LABEL, y, h), ink);
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
      Sound.play(Sound.Cue.TAB);
      setting.run();
    }
  }
}
