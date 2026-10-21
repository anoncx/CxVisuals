package me.cxiy.cxvisuals.gui;

import java.util.List;
import me.cxiy.cxvisuals.core.settings.Choice;

public final class Stepper extends Widget {
  private static final String PREVIOUS = "‹";
  private static final String NEXT = "›";
  private static final float ARROW = 26.0F;
  private static final float SLIDE = 10.0F;

  private final Choice choice;
  private final List<String> labels;
  private final Anim settle = Anim.reveal(1.0F);
  private final Anim press = Anim.press(0.0F);
  private final Anim leftHover = Anim.hover(0.0F);
  private final Anim rightHover = Anim.hover(0.0F);
  private int shown;
  private int direction = 1;
  private int pressed;
  private boolean measured;

  public Stepper(Choice choice) {
    this.choice = choice;
    this.labels = choice.labels();
    this.shown = choice.index();
    this.w = 120.0F;
    this.h = 30.0F;
  }

  @Override
  public float measure(Canvas c) {
    if (!measured) {
      measured = true;
      float widest = 0.0F;
      for (int i = 0; i < labels.size(); i++) {
        widest = Math.max(widest, c.textWidth(FontStyle.LABEL, labels.get(i)));
      }
      w = (float) Math.ceil(widest + ARROW * 2.0F);
    }
    return w;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    measure(c);
    int current = choice.index();
    if (current != shown) {
      shown = current;
      settle.snap(0.0F);
      settle.set(1.0F);
    }
    boolean inside = contains(mouseX, mouseY);
    boolean onLeft = inside && mouseX < x + w / 3.0F;
    press.set(pressed != 0 ? 1.0F : 0.0F);
    leftHover.set(onLeft ? 1.0F : 0.0F);
    rightHover.set(inside && !onLeft ? 1.0F : 0.0F);
    settle.update(dt);
    press.update(dt);
    leftHover.update(dt);
    rightHover.update(dt);

    float textTop = centered(c, FontStyle.LABEL, y, h);
    c.pushTransform(1.0F - 0.03F * press.get(), x + w * 0.5F, y + h * 0.5F);
    Depth.inset(c, t, x, y, w, h, h * 0.5F, t.surface2());
    float arrowWidth = c.textWidth(FontStyle.LABEL, PREVIOUS);
    c.text(
        FontStyle.LABEL,
        PREVIOUS,
        x + (ARROW - arrowWidth) * 0.5F + 2.0F,
        textTop,
        Theme.mix(t.grey(), t.ink(), leftHover.get()));
    c.text(
        FontStyle.LABEL,
        NEXT,
        x + w - ARROW + (ARROW - arrowWidth) * 0.5F - 2.0F,
        textTop,
        Theme.mix(t.grey(), t.ink(), rightHover.get()));

    String label = labels.get(shown);
    float arrived = Math.max(0.0F, Math.min(1.0F, settle.get()));
    float labelX =
        x + (w - c.textWidth(FontStyle.LABEL, label)) * 0.5F + direction * SLIDE * (1.0F - arrived);
    c.pushAlpha(arrived);
    c.text(FontStyle.LABEL, label, labelX, textTop, t.ink());
    c.popAlpha();
    c.popTransform();
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (!contains(mx, my)) {
      return false;
    }
    pressed = mx < x + w / 3.0F ? -1 : 1;
    return true;
  }

  @Override
  public void mouseUp(float mx, float my) {
    int step = pressed;
    pressed = 0;
    if (step != 0 && contains(mx, my)) {
      int count = labels.size();
      direction = step;
      Sound.play(Sound.Cue.TAB);
      choice.setIndex(Math.floorMod(choice.index() + step, count));
    }
  }
}
