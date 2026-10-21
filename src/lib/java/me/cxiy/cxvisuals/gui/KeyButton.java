package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.KeySetting;

public final class KeyButton extends Widget {
  public static final int ESCAPE = 256;

  private static final String PROMPT = Lang.key("Press a key");
  private static final float PADDING = 28.0F;
  private static final float MIN_WIDTH = 64.0F;
  private static final float RADIUS = 9.0F;

  private final KeySetting setting;
  private final Anim press = Anim.press(0.0F);
  private final Anim focus = Anim.state(0.0F);
  private boolean pressed;
  private boolean capturing;

  public KeyButton(KeySetting setting) {
    this.setting = setting;
    this.w = MIN_WIDTH;
    this.h = 30.0F;
  }

  public boolean capturing() {
    return capturing;
  }

  public void cancel() {
    capturing = false;
  }

  private String text() {
    return capturing ? Lang.tr(PROMPT) : setting.handle().label();
  }

  @Override
  public float measure(Canvas c) {
    w = Math.max(MIN_WIDTH, (float) Math.ceil(c.textWidth(FontStyle.MONO, text()) + PADDING));
    return w;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    press.set(pressed ? 1.0F : 0.0F);
    focus.set(capturing ? 1.0F : 0.0F);
    press.update(dt);
    focus.update(dt);

    String text = text();
    c.pushTransform(1.0F - 0.04F * press.get(), x + w * 0.5F, y + h * 0.5F);
    Depth.inset(c, t, x, y, w, h, RADIUS, Theme.mix(t.surface2(), t.surface(), focus.get()));
    if (focus.get() > 0.01F) {
      c.roundRectBorder(x, y, w, h, RADIUS, 1.0F, Theme.withAlpha(t.focus(), focus.get()));
    }
    float textX = x + (w - c.textWidth(FontStyle.MONO, text)) * 0.5F;
    c.text(
        FontStyle.MONO,
        text,
        textX,
        centered(c, FontStyle.MONO, y, h),
        Theme.mix(t.ink(), t.grey(), focus.get()));
    c.popTransform();
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (!contains(mx, my)) {
      capturing = false;
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
      capturing = !capturing;
    }
  }

  public boolean keyDown(int keyCode, int scanCode) {
    if (!capturing) {
      return false;
    }
    capturing = false;
    if (keyCode != ESCAPE) {
      setting.handle().rebind(keyCode, scanCode);
    }
    return true;
  }
}
