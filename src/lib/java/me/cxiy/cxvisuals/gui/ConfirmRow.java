package me.cxiy.cxvisuals.gui;

public final class ConfirmRow extends Widget {
  private static final float HEIGHT = 48.0F;
  private static final float LEFT = 8.0F;
  private static final float RIGHT = 6.0F;
  private static final float BUTTON = 30.0F;
  private static final float BUTTON_PAD = 16.0F;
  private static final float ARMED_SECONDS = 4.0F;
  private static final int ON_ALERT = 0xFFFFFFFF;

  private final String title;
  private final String description;
  private final String label;
  private final String confirm;
  private final Runnable action;
  private final Anim press = Anim.press(0.0F);
  private final Anim hover = Anim.hover(0.0F);
  private final Anim arm = Anim.state(0.0F);
  private float labelWidth = -1.0F;
  private float confirmWidth;
  private float armed;
  private boolean pressed;

  public ConfirmRow(
      String title, String description, String label, String confirm, Runnable action) {
    this.title = title;
    this.description = description;
    this.label = label;
    this.confirm = confirm;
    this.action = action;
    this.h = HEIGHT;
  }

  public boolean armed() {
    return armed > 0.0F;
  }

  public float buttonX() {
    return x + w - RIGHT - width() * 0.5F;
  }

  public float buttonY() {
    return y + HEIGHT * 0.5F;
  }

  private float width() {
    float amount = Math.max(0.0F, Math.min(1.0F, arm.get()));
    return labelWidth + (confirmWidth - labelWidth) * amount;
  }

  @Override
  public float measure(Canvas c) {
    if (labelWidth < 0.0F) {
      labelWidth = (float) Math.ceil(c.textWidth(FontStyle.LABEL, label) + BUTTON_PAD * 2.0F);
      confirmWidth = (float) Math.ceil(c.textWidth(FontStyle.LABEL, confirm) + BUTTON_PAD * 2.0F);
    }
    return w;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    measure(c);
    armed = Math.max(0.0F, armed - dt);
    press.set(pressed ? 1.0F : 0.0F);
    hover.set(overButton(mouseX, mouseY) ? 1.0F : 0.0F);
    arm.set(armed > 0.0F ? 1.0F : 0.0F);
    press.update(dt);
    hover.update(dt);
    arm.update(dt);

    float rowLine = c.lineHeight(FontStyle.ROW);
    float smallLine = c.lineHeight(FontStyle.SMALL);
    float textTop = y + (HEIGHT - rowLine - smallLine - 1.0F) * 0.5F;
    c.text(FontStyle.ROW, title, x + LEFT, textTop, t.ink());
    c.text(FontStyle.SMALL, description, x + LEFT, textTop + rowLine + 1.0F, t.grey());

    float amount = Math.max(0.0F, Math.min(1.0F, arm.get()));
    float width = width();
    float left = x + w - RIGHT - width;
    float top = y + (HEIGHT - BUTTON) * 0.5F;
    int fill = Theme.mix(Theme.mix(t.surface2(), t.alert(), amount), t.ink(), 0.08F * hover.get());
    String text = amount > 0.5F ? confirm : label;
    float fade = Math.abs(amount * 2.0F - 1.0F);
    c.pushTransform(1.0F - 0.05F * press.get(), left + width * 0.5F, top + BUTTON * 0.5F);
    Depth.raised(c, t, left, top, width, BUTTON, BUTTON * 0.5F, fill);
    c.pushAlpha(fade);
    c.text(
        FontStyle.LABEL,
        text,
        left + (width - c.textWidth(FontStyle.LABEL, text)) * 0.5F,
        centered(c, FontStyle.LABEL, top, BUTTON),
        Theme.mix(t.alert(), ON_ALERT, amount));
    c.popAlpha();
    c.popTransform();
  }

  private boolean overButton(float mx, float my) {
    float width = width();
    float left = x + w - RIGHT - width;
    float top = y + (HEIGHT - BUTTON) * 0.5F;
    return mx >= left && mx < left + width && my >= top && my < top + BUTTON;
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (!contains(mx, my)) {
      armed = 0.0F;
      return false;
    }
    pressed = overButton(mx, my);
    if (!pressed) {
      armed = 0.0F;
    }
    return true;
  }

  @Override
  public void mouseUp(float mx, float my) {
    if (!pressed) {
      return;
    }
    pressed = false;
    if (!overButton(mx, my)) {
      return;
    }
    if (armed > 0.0F) {
      armed = 0.0F;
      action.run();
    } else {
      armed = ARMED_SECONDS;
    }
  }
}
