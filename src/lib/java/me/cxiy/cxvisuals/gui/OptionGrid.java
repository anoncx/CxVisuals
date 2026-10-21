package me.cxiy.cxvisuals.gui;

import java.util.List;
import me.cxiy.cxvisuals.core.settings.Choice;

public final class OptionGrid extends Widget {
  private static final float CELL = 30.0F;
  private static final float GAP = 6.0F;
  private static final float PADDING = 24.0F;
  private static final float MIN_CELL = 72.0F;

  private final Choice choice;
  private final List<String> labels;
  private final Anim column;
  private final Anim line;
  private final Anim press = Anim.pop(0.0F);
  private final Anim[] emphasis;
  private final Anim[] hover;
  private int columns = 1;
  private int lines = 1;
  private float cell;
  private float fitted = -1.0F;
  private int pressedIndex = -1;

  public OptionGrid(Choice choice) {
    this.choice = choice;
    this.labels = choice.labels();
    this.column = Anim.pop(0.0F);
    this.line = Anim.pop(0.0F);
    this.emphasis = new Anim[labels.size()];
    this.hover = new Anim[labels.size()];
    for (int i = 0; i < emphasis.length; i++) {
      emphasis[i] = Anim.state(i == choice.index() ? 1.0F : 0.0F);
      hover[i] = Anim.hover(0.0F);
    }
  }

  public void fit(Canvas c, float width) {
    if (width == fitted) {
      return;
    }
    fitted = width;
    float widest = 0.0F;
    for (int i = 0; i < labels.size(); i++) {
      widest = Math.max(widest, c.textWidth(FontStyle.LABEL, labels.get(i)));
    }
    float least = Math.max(MIN_CELL, (float) Math.ceil(widest + PADDING));
    int count = labels.size();
    int most = Math.max(1, Math.min(count, (int) ((width + GAP) / (least + GAP))));
    lines = (count + most - 1) / most;
    columns = (count + lines - 1) / lines;
    cell = (width - GAP * (columns - 1)) / columns;
    w = width;
    h = lines * CELL + (lines - 1) * GAP;
    int current = choice.index();
    column.snap(current % columns);
    line.snap(current / columns);
  }

  public int columns() {
    return columns;
  }

  public int lines() {
    return lines;
  }

  public float cellWidth() {
    return cell;
  }

  public float cellHeight() {
    return CELL;
  }

  public float cellX(int index) {
    return x + (index % columns) * (cell + GAP);
  }

  public float cellY(int index) {
    return y + (index / columns) * (CELL + GAP);
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    int current = choice.index();
    int hovered = indexAt(mouseX, mouseY);
    column.set(current % columns);
    line.set(current / columns);
    press.set(pressedIndex >= 0 ? 1.0F : 0.0F);
    column.update(dt);
    line.update(dt);
    press.update(dt);

    float radius = CELL * 0.5F;
    for (int i = 0; i < labels.size(); i++) {
      Depth.inset(c, t, cellX(i), cellY(i), cell, CELL, radius, t.surface2());
    }

    float thumbX = x + column.get() * (cell + GAP);
    float thumbY = y + line.get() * (CELL + GAP);
    c.pushTransform(1.0F - 0.04F * press.get(), thumbX + cell * 0.5F, thumbY + radius);
    c.shadow(thumbX, thumbY, cell, CELL, radius, 3.0F, 1.0F, t.shadow());
    Depth.raised(c, t, thumbX, thumbY, cell, CELL, radius, t.raised());
    c.popTransform();

    for (int i = 0; i < labels.size(); i++) {
      Anim tone = emphasis[i];
      Anim glow = hover[i];
      tone.set(i == current ? 1.0F : 0.0F);
      glow.set(i == hovered ? 1.0F : 0.0F);
      tone.update(dt);
      glow.update(dt);
      String label = labels.get(i);
      float textX = cellX(i) + (cell - c.textWidth(FontStyle.LABEL, label)) * 0.5F;
      int idle = Theme.mix(t.grey(), t.ink2(), glow.get());
      c.text(
          FontStyle.LABEL,
          label,
          textX,
          centered(c, FontStyle.LABEL, cellY(i), CELL),
          Theme.mix(idle, t.ink(), tone.get()));
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
      choice.setIndex(pressed);
    }
  }

  private int indexAt(float mx, float my) {
    if (cell <= 0.0F || !contains(mx, my)) {
      return -1;
    }
    float stepX = cell + GAP;
    float stepY = CELL + GAP;
    int col = (int) ((mx - x) / stepX);
    int row = (int) ((my - y) / stepY);
    if (mx - x - col * stepX >= cell || my - y - row * stepY >= CELL) {
      return -1;
    }
    int index = row * columns + col;
    return col < columns && index < labels.size() ? index : -1;
  }
}
