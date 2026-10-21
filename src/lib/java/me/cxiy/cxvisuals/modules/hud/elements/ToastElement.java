package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.ToastStack;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

public final class ToastElement extends HudElement {
  public static final double HOME_Y = 10.0;

  public final ToastStack stack = new ToastStack();

  public ToastElement(Settings settings) {
    super(settings, "toasts", "Notifications", Category.INFO, true, Anchor.TOP, 0.0, HOME_Y);
  }

  @Override
  public float radius() {
    return ToastStack.HEIGHT * 0.5F;
  }

  @Override
  public boolean instant() {
    return true;
  }

  @Override
  public boolean present(HudData data) {
    return stack.size() > 0;
  }

  public float align() {
    return anchor.get().fx;
  }

  public boolean rising() {
    return anchor.get().fy > 0.5F;
  }

  @Override
  public void measure(Canvas c, HudData data) {
    w = ToastStack.WIDTH;
    h = ToastStack.extent(Math.max(1, data.editing ? ToastStack.samples() : stack.size()));
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    if (data.editing) {
      stack.sample(c, t, x, y, h, align(), rising());
    } else {
      stack.draw(c, t, x, y, h, align(), rising(), dt);
    }
  }
}
