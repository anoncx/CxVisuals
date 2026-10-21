package me.cxiy.cxvisuals.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.PickSetting;

public final class PickList extends Widget {
  private static final float HEADER = 36.0F;
  private static final float LEFT = 8.0F;
  private static final float RIGHT = 6.0F;
  private static final float GAP = 8.0F;
  private static final float PILL = 52.0F;
  private static final float VIEW = 180.0F;
  private static final float ROW = 30.0F;
  private static final float BOX = 16.0F;
  private static final float BOX_RADIUS = 5.0F;
  private static final float BOTTOM = 4.0F;
  private static final int SEARCH_LIMIT = 40;
  private static final int NONE = -1;
  private static final int ALL = -2;
  private static final int CLEAR = -3;
  private static final long REFRESH_NANOS = 1_500_000_000L;

  private final PickSetting setting;
  private final Field field = new Field(Lang.key("Search"), SEARCH_LIMIT);
  private final List<Integer> shown = new ArrayList<>();
  private final List<String> matches = new ArrayList<>();
  private String query;
  private List<String> seen;
  private float scroll;
  private int pressed = NONE;
  private long refreshed;

  public PickList(PickSetting setting) {
    this.setting = setting;
    this.h = HEADER + Field.HEIGHT + GAP + VIEW + BOTTOM;
  }

  public boolean typing() {
    return field.focused();
  }

  public int visible() {
    filter();
    return shown.size();
  }

  public float fieldX() {
    return x + LEFT + 20.0F;
  }

  public float fieldY() {
    return y + HEADER + Field.HEIGHT * 0.5F;
  }

  public float allX() {
    return x + w - RIGHT - PILL - GAP - PILL * 0.5F;
  }

  public float noneX() {
    return x + w - RIGHT - PILL * 0.5F;
  }

  public float rowY(int row) {
    return listTop() + row * ROW - scroll + ROW * 0.5F;
  }

  private float listTop() {
    return y + HEADER + Field.HEIGHT + GAP;
  }

  private float fieldWidth() {
    return w - LEFT - RIGHT - (PILL + GAP) * (setting.isSingle() ? 1.0F : 2.0F);
  }

  private void filter() {
    String text = field.text().trim().toLowerCase(Locale.ROOT);
    if (text.equals(query) && seen == setting.ids()) {
      return;
    }
    query = text;
    seen = setting.ids();
    int options = seen.size();
    shown.clear();
    matches.clear();
    for (int i = 0; i < options; i++) {
      if (text.isEmpty()
          || setting.labels().get(i).toLowerCase(Locale.ROOT).contains(text)
          || setting.ids().get(i).contains(text)) {
        shown.add(i);
        matches.add(setting.ids().get(i));
      }
    }
    scroll = 0.0F;
  }

  private float overflow() {
    return Math.max(0.0F, shown.size() * ROW - VIEW);
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    long now = System.nanoTime();
    if (now - refreshed > REFRESH_NANOS) {
      refreshed = now;
      setting.refresh();
    }
    filter();
    scroll = Math.max(0.0F, Math.min(overflow(), scroll));
    c.text(
        FontStyle.ROW, setting.title(), x + LEFT, centered(c, FontStyle.ROW, y, HEADER), t.ink());
    String count =
        setting.isSingle()
            ? c.fit(FontStyle.SMALL, current(), w * 0.55F)
            : setting.get().size() + " " + Lang.tr("selected");
    c.text(
        FontStyle.SMALL,
        count,
        x + w - RIGHT - c.textWidth(FontStyle.SMALL, count),
        centered(c, FontStyle.SMALL, y, HEADER),
        t.grey());

    float barTop = y + HEADER;
    field.draw(c, t, x + LEFT, barTop, fieldWidth(), dt);
    if (!setting.isSingle()) {
      pill(c, t, allX(), barTop, Lang.tr("All"), pressed == ALL, mouseX, mouseY);
    }
    pill(c, t, noneX(), barTop, Lang.tr("None"), pressed == CLEAR, mouseX, mouseY);

    float top = listTop();
    Depth.inset(c, t, x + LEFT, top, w - LEFT - RIGHT, VIEW, t.radius(), t.well());
    c.pushClip(x + LEFT, top, w - LEFT - RIGHT, VIEW);
    if (shown.isEmpty()) {
      c.text(
          FontStyle.SMALL,
          Lang.tr("Nothing matches."),
          x + LEFT + 12.0F,
          centered(c, FontStyle.SMALL, top, ROW),
          t.grey());
    }
    int over = rowAt(mouseX, mouseY);
    int first = Math.max(0, (int) (scroll / ROW));
    int last = Math.min(shown.size(), first + (int) (VIEW / ROW) + 2);
    for (int row = first; row < last; row++) {
      int option = shown.get(row);
      float rowTop = top + row * ROW - scroll;
      boolean on = setting.picked(setting.ids().get(option));
      if (row == over) {
        c.roundRect(
            x + LEFT + 4.0F, rowTop + 2.0F, w - LEFT - RIGHT - 8.0F, ROW - 4.0F, 8.0F, t.hair());
      }
      float boxLeft = x + LEFT + 12.0F;
      float boxTop = rowTop + (ROW - BOX) * 0.5F;
      if (on) {
        Depth.raised(c, t, boxLeft, boxTop, BOX, BOX, BOX_RADIUS, t.ink());
        c.roundRect(boxLeft + 5.0F, boxTop + 5.0F, BOX - 10.0F, BOX - 10.0F, 3.0F, t.knobOn());
      } else {
        Depth.inset(c, t, boxLeft, boxTop, BOX, BOX, BOX_RADIUS, t.surface2());
      }
      c.text(
          FontStyle.LABEL,
          setting.labels().get(option),
          boxLeft + BOX + 10.0F,
          centered(c, FontStyle.LABEL, rowTop, ROW),
          on ? t.ink() : t.ink2());
    }
    c.popClip();
    if (overflow() > 0.0F) {
      float track = VIEW - 12.0F;
      float thumb = Math.max(18.0F, track * VIEW / (shown.size() * ROW));
      float at = top + 6.0F + (track - thumb) * scroll / overflow();
      c.roundRect(x + w - RIGHT - 5.0F, at, 3.0F, thumb, 1.5F, t.dotOff());
    }
  }

  private String current() {
    String chosen = setting.chosen();
    if (chosen.isEmpty()) {
      return Lang.tr("None");
    }
    int at = setting.ids().indexOf(chosen);
    return at < 0 ? chosen.replace("|", " · ") : setting.labels().get(at);
  }

  private void pill(
      Canvas c,
      Theme t,
      float centreX,
      float top,
      String label,
      boolean down,
      float mouseX,
      float mouseY) {
    float left = centreX - PILL * 0.5F;
    boolean over =
        mouseX >= left && mouseX < left + PILL && mouseY >= top && mouseY < top + Field.HEIGHT;
    Depth.raised(
        c,
        t,
        left,
        top,
        PILL,
        Field.HEIGHT,
        Field.HEIGHT * 0.5F,
        Theme.mix(t.surface2(), t.ink(), down ? 0.16F : over ? 0.08F : 0.0F));
    c.text(
        FontStyle.CAPTION,
        label,
        centreX - c.textWidth(FontStyle.CAPTION, label) * 0.5F,
        centered(c, FontStyle.CAPTION, top, Field.HEIGHT),
        t.ink());
  }

  private int rowAt(float mx, float my) {
    float top = listTop();
    if (mx < x + LEFT || mx >= x + w - RIGHT || my < top || my >= top + VIEW) {
      return NONE;
    }
    int row = (int) ((my - top + scroll) / ROW);
    return row >= 0 && row < shown.size() ? row : NONE;
  }

  private int target(float mx, float my) {
    float barTop = y + HEADER;
    if (my >= barTop && my < barTop + Field.HEIGHT) {
      if (!setting.isSingle() && Math.abs(mx - allX()) <= PILL * 0.5F) {
        return ALL;
      }
      if (Math.abs(mx - noneX()) <= PILL * 0.5F) {
        return CLEAR;
      }
      return NONE;
    }
    return rowAt(mx, my);
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    pressed = NONE;
    if (!contains(mx, my)) {
      field.focus(false);
      return false;
    }
    float barTop = y + HEADER;
    boolean typing =
        my >= barTop
            && my < barTop + Field.HEIGHT
            && mx >= x + LEFT
            && mx < x + LEFT + fieldWidth();
    field.focus(typing);
    if (typing) {
      field.press(mx);
    } else {
      filter();
      pressed = target(mx, my);
    }
    return true;
  }

  @Override
  public void mouseDrag(float mx, float my) {
    field.drag(mx);
  }

  @Override
  public void mouseUp(float mx, float my) {
    field.release();
    int was = pressed;
    pressed = NONE;
    if (was == NONE || target(mx, my) != was) {
      return;
    }
    if (was == ALL) {
      setting.mark(matches, true);
    } else if (was == CLEAR) {
      setting.mark(matches, false);
    } else {
      setting.toggle(setting.ids().get(shown.get(was)));
    }
  }

  @Override
  public boolean scroll(float mx, float my, float amount) {
    if (!contains(mx, my) || overflow() <= 0.0F || my < listTop()) {
      return false;
    }
    scroll = Math.max(0.0F, Math.min(overflow(), scroll - amount * ROW));
    return true;
  }

  @Override
  public void finishEditing() {
    field.focus(false);
  }

  @Override
  public boolean keyDown(int keyCode, boolean control, boolean shift) {
    return field.key(keyCode, control, shift);
  }

  @Override
  public boolean charTyped(int codepoint) {
    return field.type(codepoint);
  }
}
