package me.cxiy.cxvisuals.menu.views;

import java.util.ArrayList;
import java.util.List;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.menu.model.Control;
import me.cxiy.cxvisuals.menu.model.Form;
import me.cxiy.cxvisuals.menu.model.FormSource;
import me.cxiy.cxvisuals.menu.model.ItemList;

public final class FormPage extends Page {
  static final int DIALOG = 0;
  static final int SHEET = 1;
  static final int LIST = 2;
  static final int SPLIT = 3;
  static final int MENU = 4;
  static final float CAPTION = 34.0F;
  static final float SCROLL_STEP = 46.0F;
  static final float SCROLL_RESPONSE = 18.0F;

  private static final int MENU_MAX = 12;
  private static final long DOUBLE_NANOS = 400_000_000L;

  static final class RowView {
    final String caption;
    final String label;
    final List<ControlView> controls = new ArrayList<>();

    RowView(Form.Row row) {
      caption = row.caption;
      label = row.label;
      for (Control control : row.controls) {
        controls.add(new ControlView(control));
      }
    }

    boolean pills() {
      if (controls.size() < 2 && label == null) {
        return false;
      }
      for (ControlView view : controls) {
        if (view.control.kind() != Control.Kind.BUTTON) {
          return false;
        }
      }
      return !controls.isEmpty();
    }
  }

  final FormSource source;
  final List<RowView> rows = new ArrayList<>();
  final List<ControlView> actions = new ArrayList<>();
  final List<ControlView> sidebar = new ArrayList<>();
  final List<ControlView> tabs = new ArrayList<>();
  final List<ControlView> buttons = new ArrayList<>();
  final List<String> wrapped = new ArrayList<>();
  Form form;
  int layout;
  float wx;
  float wy;
  float ww;
  float wh;
  float top;
  float footer;
  float footerPad;
  float footerGap;
  float bodyTop;
  float bodyHeight;
  float bodyLeft;
  float bodyWidth;
  float contentHeight;
  float itemsTop;
  float scroll;
  float scrollGoal;
  float sideScroll;
  float clock;

  private float wrappedWidth = -1.0F;
  private ControlView held;
  private ControlView focused;
  private int clickedItem = ItemList.NONE;
  private long clickedAt;

  public FormPage(FormSource source) {
    this.source = source;
  }

  int layout() {
    return layout;
  }

  float age() {
    return age;
  }

  private boolean ready() {
    if (form == null) {
      if (frozen) {
        return false;
      }
      rebuild();
    } else if (!frozen && source.changed()) {
      rebuild();
    }
    return true;
  }

  private void rebuild() {
    form = source.read();
    rows.clear();
    actions.clear();
    sidebar.clear();
    tabs.clear();
    buttons.clear();
    wrappedWidth = -1.0F;
    held = null;
    focused = null;
    for (Form.Row row : form.rows) {
      rows.add(new RowView(row));
    }
    for (Control control : form.actions) {
      actions.add(new ControlView(control));
    }
    for (Control control : form.sidebar) {
      sidebar.add(new ControlView(control));
    }
    for (Control control : form.tabs) {
      tabs.add(new ControlView(control));
    }
    layout =
        form.items != null
            ? LIST
            : !form.sidebar.isEmpty() ? SPLIT : plain() ? DIALOG : isMenu() ? MENU : SHEET;
    if (layout == MENU) {
      for (RowView row : rows) {
        buttons.add(row.controls.get(0));
      }
      buttons.addAll(actions);
    }
    scrollGoal = Math.max(0.0F, scrollGoal);
  }

  private boolean isMenu() {
    if (!form.tabs.isEmpty() || !form.lines.isEmpty() || form.busy || form.progress >= 0.0F) {
      return false;
    }
    int count = actions.size();
    for (Form.Row row : form.rows) {
      if (row.caption != null || row.label != null || row.controls.size() != 1) {
        return false;
      }
      if (row.controls.get(0).kind() != Control.Kind.BUTTON) {
        return false;
      }
      count++;
    }
    if (count < 1 || count > MENU_MAX) {
      return false;
    }
    for (Control control : form.actions) {
      if (control.kind() != Control.Kind.BUTTON) {
        return false;
      }
    }
    return true;
  }

  private boolean plain() {
    if (!form.tabs.isEmpty()) {
      return false;
    }
    for (Form.Row row : form.rows) {
      for (Control control : row.controls) {
        if (control.kind() != Control.Kind.TEXT) {
          return false;
        }
      }
    }
    return true;
  }

  void wrap(Canvas c, float width) {
    if (wrappedWidth == width) {
      return;
    }
    wrappedWidth = width;
    wrapped.clear();
    for (String paragraph : form.lines) {
      for (String line : paragraph.split("\n")) {
        breakLine(c, line.strip(), width);
      }
    }
  }

  private void breakLine(Canvas c, String line, float width) {
    if (line.isEmpty()) {
      return;
    }
    String[] words = line.split(" ");
    StringBuilder current = new StringBuilder();
    for (String word : words) {
      if (current.length() > 0 && c.textWidth(FontStyle.LABEL, current + " " + word) > width) {
        wrapped.add(current.toString());
        current.setLength(0);
      }
      if (current.length() > 0) {
        current.append(' ');
      }
      current.append(word);
    }
    if (current.length() > 0) {
      wrapped.add(current.toString());
    }
  }

  static float rowHeight(RowView row) {
    return row.caption != null ? CAPTION : ControlView.ROW;
  }

  @Override
  public void measure(Canvas c, float roomW, float roomH) {
    if (!ready()) {
      wantW = pw;
      wantH = ph;
      return;
    }
    if (layout == DIALOG || layout == MENU) {
      DialogLayout.measure(this, c, roomW, roomH);
    } else {
      SheetLayout.measure(this, c, roomW, roomH);
    }
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    if (!ready()) {
      return;
    }
    clock += dt;
    age += dt;
    wx = px;
    wy = py;
    ww = pw;
    wh = ph;
    for (RowView row : rows) {
      for (ControlView view : row.controls) {
        view.shown = false;
      }
    }
    if (layout == DIALOG || layout == MENU) {
      DialogLayout.draw(this, c, t, mouseX, mouseY, dt);
    } else {
      SheetLayout.draw(this, c, t, mouseX, mouseY, dt);
    }
  }

  private static ControlView find(List<ControlView> views, float mx, float my) {
    for (ControlView view : views) {
      if (view.down(mx, my)) {
        return view;
      }
    }
    return null;
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (form == null || frozen) {
      return false;
    }
    ControlView hit = find(actions, mx, my);
    if (hit == null) {
      hit = find(sidebar, mx, my);
    }
    if (hit == null) {
      hit = find(tabs, mx, my);
    }
    if (hit == null
        && (layout == DIALOG
            || layout == MENU
            || Kit.inside(mx, my, bodyLeft, bodyTop, bodyWidth, bodyHeight))) {
      for (RowView row : rows) {
        hit = find(row.controls, mx, my);
        if (hit != null) {
          break;
        }
      }
    }
    if (focused != null && focused != hit) {
      focused.field.focus(false);
      focused = null;
    }
    if (hit != null) {
      held = hit;
      if (hit.field != null) {
        focused = hit;
      }
      return true;
    }
    int item = ListLayout.itemAt(this, mx, my);
    if (item != ItemList.NONE) {
      long now = System.nanoTime();
      boolean twice = item == clickedItem && now - clickedAt < DOUBLE_NANOS;
      clickedItem = twice ? ItemList.NONE : item;
      clickedAt = now;
      form.items.select(item);
      if (twice) {
        form.items.open(item);
      }
      return true;
    }
    return Kit.inside(mx, my, wx, wy, ww, wh);
  }

  @Override
  public void mouseUp(float mx, float my) {
    if (held != null) {
      ControlView view = held;
      held = null;
      view.up(mx, my);
    }
  }

  @Override
  public void mouseDrag(float mx, float my) {
    if (held != null) {
      held.drag(mx);
    }
  }

  @Override
  public boolean scroll(float mx, float my, float amount) {
    if (form == null || frozen) {
      return false;
    }
    if (layout == SPLIT && Kit.inside(mx, my, wx, wy, SheetLayout.SIDEBAR, wh)) {
      sideScroll -= amount * SCROLL_STEP;
      return true;
    }
    scrollGoal -= amount * SCROLL_STEP;
    return true;
  }

  @Override
  public boolean keyDown(int keyCode, int scanCode, int modifiers) {
    if (form == null || frozen) {
      return false;
    }
    if (focused != null) {
      if (keyCode == Kit.ESCAPE) {
        focused.field.focus(false);
        focused = null;
        return true;
      }
      if (keyCode != Kit.ENTER && keyCode != Kit.KEYPAD_ENTER) {
        focused.field.key(keyCode, (modifiers & Kit.CONTROL) != 0, (modifiers & Kit.SHIFT) != 0);
        return true;
      }
    }
    if (source.key(keyCode, scanCode, modifiers)) {
      return true;
    }
    if (keyCode == Kit.ESCAPE) {
      source.back();
      return true;
    }
    if (keyCode == Kit.ENTER || keyCode == Kit.KEYPAD_ENTER) {
      if (layout == LIST && form.items.selected() != ItemList.NONE) {
        form.items.open(form.items.selected());
        return true;
      }
      List<ControlView> pool = layout == MENU ? buttons : actions;
      for (ControlView view : pool) {
        if (view.control.leads() && view.control.enabled()) {
          view.control.press();
          return true;
        }
      }
      if (layout == MENU) {
        for (ControlView view : buttons) {
          if (view.control.enabled()) {
            view.control.press();
            return true;
          }
        }
      }
    }
    return false;
  }

  @Override
  public boolean charTyped(int codepoint) {
    return focused != null && focused.field.type(codepoint);
  }

  @Override
  public boolean typing() {
    return focused != null;
  }
}
