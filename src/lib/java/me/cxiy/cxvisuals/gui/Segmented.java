package me.cxiy.cxvisuals.gui;

import java.util.List;
import me.cxiy.cxvisuals.core.settings.Choice;

public final class Segmented extends Widget {
  private static final float INSET = 3.0F;
  private static final float PADDING = 28.0F;
  private static final float MIN_PADDING = 12.0F;
  private static final float MIN_SEGMENT = 56.0F;

  private final Choice setting;
  private final List<String> labels;
  private final Anim thumb;
  private final Anim press = Anim.pop(0.0F);
  private final Anim[] emphasis;
  private final float[] starts;
  private final float[] widths;
  private final float[] natural;
  private final String[] shown;
  private float limit = Float.MAX_VALUE;
  private boolean sized;
  private int pressedIndex = -1;

  public Segmented(Choice setting) {
    this.setting = setting;
    this.labels = setting.labels();
    int count = labels.size();
    this.thumb = Anim.pop(setting.index());
    this.emphasis = new Anim[count];
    this.starts = new float[count + 1];
    this.widths = new float[count + 1];
    this.natural = new float[count];
    this.shown = new String[count];
    for (int i = 0; i < count; i++) {
      emphasis[i] = Anim.state(i == setting.index() ? 1.0F : 0.0F);
    }
    this.h = 30.0F;
  }

  public void limit(float max) {
    if (max != limit) {
      limit = max;
      sized = false;
    }
  }

  public float segmentX(int index) {
    return starts[index];
  }

  public float segmentWidth(int index) {
    return widths[index];
  }

  @Override
  public float measure(Canvas c) {
    if (!sized) {
      size(c);
    }
    return w;
  }

  private void size(Canvas c) {
    int count = labels.size();
    float text = 0.0F;
    for (int i = 0; i < count; i++) {
      natural[i] = (float) Math.ceil(c.textWidth(FontStyle.LABEL, labels.get(i)));
      text += natural[i];
    }
    float room = limit - INSET * 2.0F;
    float total = spread(PADDING, MIN_SEGMENT);
    if (total > room) {
      total = spread(Math.max(MIN_PADDING, (room - text) / count), 0.0F);
    }
    if (total > room) {
      for (int i = 0; i < count; i++) {
        widths[i] = room * natural[i] / text;
      }
      total = room;
    }
    float cursor = INSET;
    for (int i = 0; i < count; i++) {
      starts[i] = cursor;
      cursor += widths[i];
      shown[i] = c.fit(FontStyle.LABEL, labels.get(i), widths[i] - MIN_PADDING);
    }
    starts[count] = cursor;
    widths[count] = widths[count - 1];
    w = total + INSET * 2.0F;
    sized = true;
  }

  private float spread(float padding, float minimum) {
    float total = 0.0F;
    for (int i = 0; i < labels.size(); i++) {
      widths[i] = Math.max(minimum, natural[i] + padding);
      total += widths[i];
    }
    return total;
  }

  private static float lerp(float[] values, float position) {
    int low = (int) Math.floor(position);
    return values[low] + (values[low + 1] - values[low]) * (position - low);
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    measure(c);
    int current = setting.index();
    thumb.set(current);
    press.set(pressedIndex >= 0 ? 1.0F : 0.0F);
    thumb.update(dt);
    press.update(dt);

    Depth.inset(c, t, x, y, w, h, h * 0.5F, t.surface2());

    float position = Math.max(0.0F, Math.min(labels.size() - 1, thumb.get()));
    float thumbWidth = lerp(widths, position);
    float thumbX = x + lerp(starts, position);
    float thumbY = y + INSET;
    float thumbHeight = h - INSET * 2.0F;
    float radius = thumbHeight * 0.5F;
    c.pushTransform(1.0F - 0.05F * press.get(), thumbX + thumbWidth * 0.5F, y + h * 0.5F);
    c.shadow(thumbX, thumbY, thumbWidth, thumbHeight, radius, 3.0F, 1.0F, t.shadow());
    Depth.raised(c, t, thumbX, thumbY, thumbWidth, thumbHeight, radius, t.raised());
    c.popTransform();

    float textY = centered(c, FontStyle.LABEL, y, h);
    for (int i = 0; i < labels.size(); i++) {
      Anim tone = emphasis[i];
      tone.set(i == current ? 1.0F : 0.0F);
      tone.update(dt);
      String label = shown[i];
      float textX = x + starts[i] + (widths[i] - c.textWidth(FontStyle.LABEL, label)) * 0.5F;
      c.text(FontStyle.LABEL, label, textX, textY, Theme.mix(t.grey(), t.ink(), tone.get()));
    }
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    int index = indexAt(mx, my);
    if (index < 0) {
      return false;
    }
    pressedIndex = index;
    return true;
  }

  @Override
  public void mouseUp(float mx, float my) {
    int pressed = pressedIndex;
    pressedIndex = -1;
    if (pressed >= 0 && indexAt(mx, my) == pressed) {
      if (pressed != setting.index()) {
        Sound.play(Sound.Cue.TAB);
      }
      setting.setIndex(pressed);
    }
  }

  private int indexAt(float mx, float my) {
    if (!sized || !contains(mx, my)) {
      return -1;
    }
    float local = mx - x;
    for (int i = 0; i < labels.size(); i++) {
      if (local < starts[i] + widths[i]) {
        return i;
      }
    }
    return labels.size() - 1;
  }
}
