package me.cxiy.cxvisuals.gui;

import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.Configs;
import me.cxiy.cxvisuals.core.settings.TextSetting;

public final class ConfigPanel extends Widget {
  public static final int KEY_ENTER = 257;
  public static final int KEY_KEYPAD_ENTER = 335;
  public static final int KEY_BACKSPACE = 259;
  public static final int KEY_ESCAPE = 256;

  private static final String[] ACTIONS = Lang.keys("Load", "Update", "Delete");
  private static final int LOAD = 0;
  private static final int UPDATE = 1;
  private static final int DELETE = 2;
  private static final int SAVE = 3;
  private static final int FIELD = 4;
  private static final int NONE = -1;
  private static final String PLACEHOLDER = Lang.key("Name a new config");
  private static final String EMPTY =
      Lang.key("Nothing saved yet. A config stores every setting of every module.");
  private static final String SAVE_LABEL = Lang.key("Save");
  private static final float ROW = 42.0F;
  private static final float INSET = 8.0F;
  private static final float PILL = 26.0F;
  private static final float PILL_PAD = 13.0F;
  private static final float PILL_GAP = 6.0F;
  private static final float DOT = 5.0F;
  private static final float FIELD_HEIGHT = 30.0F;
  private static final float FOOT = 46.0F;
  private static final float CARET_PERIOD = 1.0F;

  private final Configs configs;
  private final TextSetting active;
  private final float[] actionWidths = new float[ACTIONS.length];
  private final StringBuilder draft = new StringBuilder(Configs.MAX_NAME);
  private String draftText = "";
  private float saveWidth = -1.0F;
  private boolean focused;
  private float blink;
  private int pressedRow = NONE;
  private int pressedAction = NONE;

  public ConfigPanel(Configs configs, TextSetting active) {
    this.configs = configs;
    this.active = active;
    this.h = ROW + FOOT;
  }

  public boolean focused() {
    return focused;
  }

  public String draft() {
    return draftText;
  }

  public float actionX(int action) {
    float right = x + w - INSET;
    for (int i = ACTIONS.length - 1; i > action; i--) {
      right -= actionWidths[i] + PILL_GAP;
    }
    return right - actionWidths[action] * 0.5F;
  }

  public float rowY(int row) {
    return y + row * ROW + ROW * 0.5F;
  }

  public float fieldX() {
    return x + INSET + 20.0F;
  }

  public float footY() {
    return y + h - FOOT * 0.5F;
  }

  public float saveX() {
    return x + w - INSET - saveWidth * 0.5F;
  }

  @Override
  public float measure(Canvas c) {
    if (saveWidth < 0.0F) {
      saveWidth =
          (float)
              Math.ceil(c.textWidth(FontStyle.LABEL, Lang.tr(SAVE_LABEL)) + PILL_PAD * 2.0F + 8.0F);
      for (int i = 0; i < ACTIONS.length; i++) {
        actionWidths[i] =
            (float)
                Math.ceil(c.textWidth(FontStyle.CAPTION, Lang.tr(ACTIONS[i])) + PILL_PAD * 2.0F);
      }
    }
    h = Math.max(1, configs.names().size()) * ROW + FOOT;
    return w;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    measure(c);
    List<String> names = configs.names();
    float rowLine = c.lineHeight(FontStyle.ROW);
    if (names.isEmpty()) {
      c.text(
          FontStyle.SMALL,
          Lang.tr(EMPTY),
          x + INSET,
          y + (ROW - c.lineHeight(FontStyle.SMALL)) * 0.5F,
          t.grey());
    }
    String current = active.get();
    for (int row = 0; row < names.size(); row++) {
      String name = names.get(row);
      float top = y + row * ROW;
      if (row > 0) {
        c.roundRect(x + INSET, top, w - INSET, 1.0F, 0.0F, t.hair());
      }
      boolean live = name.equals(current);
      c.roundRect(
          x + INSET, top + (ROW - DOT) * 0.5F, DOT, DOT, DOT * 0.5F, live ? t.red() : t.dotOff());
      c.text(FontStyle.ROW, name, x + INSET + DOT + 9.0F, top + (ROW - rowLine) * 0.5F, t.ink());
      float right = x + w - INSET;
      for (int action = ACTIONS.length - 1; action >= 0; action--) {
        float width = actionWidths[action];
        float left = right - width;
        float pillTop = top + (ROW - PILL) * 0.5F;
        boolean over =
            mouseX >= left && mouseX < right && mouseY >= pillTop && mouseY < pillTop + PILL;
        boolean down = pressedRow == row && pressedAction == action;
        int fill = Theme.mix(t.surface2(), t.ink(), down ? 0.16F : over ? 0.08F : 0.0F);
        Depth.raised(c, t, left, pillTop, width, PILL, PILL * 0.5F, fill);
        c.text(
            FontStyle.CAPTION,
            Lang.tr(ACTIONS[action]),
            left + PILL_PAD,
            centered(c, FontStyle.CAPTION, pillTop, PILL),
            action == DELETE ? t.alert() : t.ink());
        right = left - PILL_GAP;
      }
    }

    float footTop = y + h - FOOT;
    c.roundRect(x + INSET, footTop, w - INSET, 1.0F, 0.0F, t.hair());
    float fieldTop = footTop + (FOOT - FIELD_HEIGHT) * 0.5F;
    float saveLeft = x + w - INSET - saveWidth;
    float fieldLeft = x + INSET;
    float fieldWidth = saveLeft - PILL_GAP - fieldLeft;
    Depth.inset(
        c, t, fieldLeft, fieldTop, fieldWidth, FIELD_HEIGHT, FIELD_HEIGHT * 0.5F, t.surface2());
    if (focused) {
      c.roundRectBorder(
          fieldLeft, fieldTop, fieldWidth, FIELD_HEIGHT, FIELD_HEIGHT * 0.5F, 1.0F, t.focus());
    }
    float textTop = centered(c, FontStyle.LABEL, fieldTop, FIELD_HEIGHT);
    boolean blank = draftText.isEmpty();
    c.text(
        FontStyle.LABEL,
        blank ? Lang.tr(PLACEHOLDER) : draftText,
        fieldLeft + 13.0F,
        textTop,
        blank ? t.grey() : t.ink());
    if (focused) {
      blink = (blink + dt) % CARET_PERIOD;
      if (blink < CARET_PERIOD * 0.5F) {
        float caret =
            fieldLeft + 13.0F + (blank ? 0.0F : c.textWidth(FontStyle.LABEL, draftText) + 1.0F);
        c.roundRect(caret, fieldTop + 8.0F, 1.5F, FIELD_HEIGHT - 16.0F, 0.75F, t.ink());
      }
    }
    boolean ready = !blank;
    boolean saveDown = pressedAction == SAVE;
    int fill = ready ? t.ink() : t.surface2();
    Depth.raised(
        c,
        t,
        saveLeft,
        fieldTop,
        saveWidth,
        FIELD_HEIGHT,
        FIELD_HEIGHT * 0.5F,
        Theme.mix(fill, ready ? t.paper() : t.ink(), saveDown ? 0.16F : 0.0F));
    c.text(
        FontStyle.LABEL,
        Lang.tr(SAVE_LABEL),
        saveLeft + (saveWidth - c.textWidth(FontStyle.LABEL, Lang.tr(SAVE_LABEL))) * 0.5F,
        textTop,
        ready ? t.knobOn() : t.grey());
  }

  private int actionAt(float mx, float my, int row) {
    float top = y + row * ROW + (ROW - PILL) * 0.5F;
    if (my < top || my >= top + PILL) {
      return NONE;
    }
    float right = x + w - INSET;
    for (int action = ACTIONS.length - 1; action >= 0; action--) {
      float left = right - actionWidths[action];
      if (mx >= left && mx < right) {
        return action;
      }
      right = left - PILL_GAP;
    }
    return NONE;
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    pressedRow = NONE;
    pressedAction = NONE;
    if (!contains(mx, my)) {
      focused = false;
      return false;
    }
    float footTop = y + h - FOOT;
    if (my >= footTop) {
      boolean onSave = mx >= x + w - INSET - saveWidth;
      pressedAction = onSave ? SAVE : FIELD;
      focused = !onSave || focused;
      blink = 0.0F;
      return true;
    }
    focused = false;
    int row = (int) Math.floor((my - y) / ROW);
    if (row >= 0 && row < configs.names().size()) {
      int action = actionAt(mx, my, row);
      if (action != NONE) {
        pressedRow = row;
        pressedAction = action;
      }
    }
    return true;
  }

  @Override
  public void mouseUp(float mx, float my) {
    int row = pressedRow;
    int action = pressedAction;
    pressedRow = NONE;
    pressedAction = NONE;
    if (action == SAVE) {
      if (contains(mx, my) && my >= y + h - FOOT && mx >= x + w - INSET - saveWidth) {
        commit();
      }
      return;
    }
    if (row == NONE || row >= configs.names().size() || actionAt(mx, my, row) != action) {
      return;
    }
    String name = configs.names().get(row);
    switch (action) {
      case LOAD -> {
        if (configs.load(name)) {
          active.set(name);
        }
      }
      case UPDATE -> {
        active.set(name);
        configs.save(name);
      }
      case DELETE -> {
        configs.delete(name);
        if (name.equals(active.get())) {
          active.set("");
        }
      }
      default -> {}
    }
  }

  private void commit() {
    String name = Configs.clean(draftText);
    if (name.isEmpty()) {
      return;
    }
    active.set(name);
    if (configs.save(name)) {
      draft.setLength(0);
      draftText = "";
      focused = false;
    }
  }

  @Override
  public boolean keyDown(int keyCode) {
    if (!focused) {
      return false;
    }
    if (keyCode == KEY_ESCAPE) {
      focused = false;
    } else if (keyCode == KEY_ENTER || keyCode == KEY_KEYPAD_ENTER) {
      commit();
    } else if (keyCode == KEY_BACKSPACE && draft.length() > 0) {
      draft.setLength(draft.length() - 1);
      draftText = draft.toString();
    }
    blink = 0.0F;
    return true;
  }

  @Override
  public boolean charTyped(int codepoint) {
    if (!focused) {
      return false;
    }
    if (Configs.allowed(codepoint)
        && Character.isBmpCodePoint(codepoint)
        && draft.length() < Configs.MAX_NAME) {
      draft.append((char) codepoint);
      draftText = draft.toString();
    }
    blink = 0.0F;
    return true;
  }
}
