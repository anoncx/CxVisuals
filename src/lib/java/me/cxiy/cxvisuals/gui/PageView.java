package me.cxiy.cxvisuals.gui;

import static me.cxiy.cxvisuals.gui.ModuleMetrics.CARD_HEIGHT;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.CONTROL_INSET;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.GRID_BOTTOM;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.GRID_DROP;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.GROUP_GAP;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.GROUP_LABEL;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.HIDDEN;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.LINE_GAP;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.MIN_TEXT;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.OFFSCREEN;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.PANEL_INSET;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.PIN_SHADOW;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.REVEAL_RISE;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.ROW;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.ROW_DESCRIBED;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.ROW_INSET;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.SCROLL_STEP;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.STACK_LIFT;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.STACK_PAD;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.TEXT_GAP;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.clamp01;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.ease;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.core.settings.Choice;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.InfoSetting;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PaletteSetting;
import me.cxiy.cxvisuals.core.settings.PanelSetting;
import me.cxiy.cxvisuals.core.settings.PickSetting;
import me.cxiy.cxvisuals.core.settings.Setting;

final class PageView {
  private static final float REVEAL_MARGIN = 56.0F;
  private static final float FLASH_SECONDS = 1.1F;
  private static final float FLASH_INSET = 6.0F;
  private static final float FLASH_RADIUS = 10.0F;
  private static final float FLASH_ALPHA = 0.12F;
  private static final float POP_INSET = 4.0F;
  private static final float POP_LIFT = 2.0F;

  static final class RowView {
    final Setting<?> setting;
    final Widget control;
    final OptionGrid grid;
    final Anim unfold;
    final boolean first;
    final boolean panel;
    final boolean pinned;
    final List<String> lines = new ArrayList<>(2);
    String info;
    float flash;
    GroupView owner;
    String title = "";
    String titleSource;
    String fittedText = "";
    float height;
    float textHeight;
    float fittedRoom = -1.0F;
    float fittedControl = -1.0F;
    boolean stacked;
    float top;
    boolean open;

    RowView(Setting<?> setting, Widget control, String info, boolean first) {
      this.setting = setting;
      this.control = control;
      this.grid = control instanceof Stepper ? new OptionGrid((Choice) setting) : null;
      this.unfold = grid == null ? null : Anim.move(0.0F);
      this.info = info;
      this.first = first;
      this.panel =
          setting instanceof PanelSetting
              || setting instanceof PaletteSetting
              || setting instanceof PickSetting;
      this.pinned = setting instanceof PanelSetting sheet && sheet.pinned();
      this.textHeight = setting.description().isEmpty() ? ROW : ROW_DESCRIBED;
      this.height = panel ? panelHeight() : textHeight;
    }

    float panelHeight() {
      return control == null ? 0.0F : control.h + PANEL_INSET * 2.0F;
    }

    boolean resizePanel() {
      if (!panel || height == panelHeight()) {
        return false;
      }
      height = panelHeight();
      return true;
    }

    boolean fit(Canvas c, float width, float controlW, float controlH, float lineHeight) {
      float beside =
          width - ROW_INSET - CONTROL_INSET - (controlW > 0.0F ? controlW + TEXT_GAP : 0.0F);
      boolean stack = controlW > 0.0F && beside < MIN_TEXT;
      float room = stack ? width - ROW_INSET - CONTROL_INSET : beside;
      String description = setting.description();
      String name = setting.title();
      if (room == fittedRoom
          && controlH == fittedControl
          && stack == stacked
          && name.equals(titleSource)
          && description.equals(fittedText)) {
        return false;
      }
      float before = height;
      fittedRoom = room;
      fittedControl = controlH;
      fittedText = description;
      titleSource = name;
      stacked = stack;
      title = c.fit(FontStyle.ROW, name, room);
      lines.clear();
      if (description.isEmpty()) {
        textHeight = ROW;
      } else {
        wrap(c, description, room);
        textHeight = ROW_DESCRIBED + (lines.size() - 1) * (lineHeight + LINE_GAP);
      }
      height = stack ? textHeight + controlH + STACK_PAD : textHeight;
      return height != before;
    }

    private void wrap(Canvas c, String description, float room) {
      int start = 0;
      int fits = -1;
      int length = description.length();
      for (int i = 0; i <= length; i++) {
        if (i < length && description.charAt(i) != ' ') {
          continue;
        }
        if (c.textWidth(FontStyle.SMALL, description.substring(start, i)) <= room || fits < 0) {
          fits = i;
        } else {
          lines.add(description.substring(start, fits));
          start = fits + 1;
          fits = i;
        }
      }
      lines.add(description.substring(start));
    }

    float unfolded() {
      return grid == null ? 0.0F : clamp01(unfold.get());
    }

    float extent() {
      return grid == null ? height : height + (grid.h + GRID_BOTTOM) * clamp01(unfold.get());
    }
  }

  interface PopOut {
    void open(Page page, PanelSetting setting);
  }

  static final class GroupView {
    final String title;
    final Supplier<Boolean> source;
    final List<RowView> rows = new ArrayList<>();
    IconButton popOut;
    float top;
    float cardHeight;
    float presence = 1.0F;

    GroupView(String title, Supplier<Boolean> source) {
      this.title = title;
      this.source = source;
      boolean shown = true;
      try {
        shown = source.get();
      } catch (RuntimeException e) {
        shown = true;
      }
      if (!shown) {
        presence = 0.0F;
      }
    }

    boolean live() {
      return presence > 0.5F;
    }

    boolean wanted() {
      try {
        return source.get();
      } catch (RuntimeException e) {
        return true;
      }
    }
  }

  final List<GroupView> groups = new ArrayList<>();
  final List<RowView> rows = new ArrayList<>();
  float contentHeight;
  float scroll;
  float scrollTarget;
  private RowView stuck;
  private float stuckTop;
  private boolean bandVisible;
  private float bandTop;
  private float bandClipTop;
  private float bandClipBottom;

  PageView(Page source, Function<Setting<?>, Widget> controls, PopOut popOut) {
    for (Group group : source.groups()) {
      GroupView view = new GroupView(group.title(), group::shown);
      for (Setting<?> setting : group.settings()) {
        RowView row =
            new RowView(setting, controls.apply(setting), infoFor(setting), view.rows.isEmpty());
        row.owner = view;
        view.rows.add(row);
        rows.add(row);
        if (popOut != null
            && row.panel
            && !row.pinned
            && row.control != null
            && setting instanceof PanelSetting panel) {
          view.popOut =
              new IconButton(
                  IconButton.Glyph.DETACH, IconButton.LABEL_OPEN, () -> popOut.open(source, panel));
        }
      }
      groups.add(view);
    }
    layout();
  }

  private static String infoFor(Setting<?> setting) {
    return setting instanceof InfoSetting info ? info.text() : null;
  }

  void layout() {
    float cursor = 0.0F;
    for (int g = 0; g < groups.size(); g++) {
      GroupView view = groups.get(g);
      view.top = cursor;
      float rowTop = 0.0F;
      for (int r = 0; r < view.rows.size(); r++) {
        RowView row = view.rows.get(r);
        row.top = rowTop;
        rowTop += row.extent() * view.presence;
      }
      view.cardHeight = rowTop;
      cursor += (GROUP_LABEL + rowTop + GROUP_GAP) * view.presence;
    }
    contentHeight = cursor;
  }

  RowView row(String id) {
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i).setting.id().equals(id)) {
        return rows.get(i);
      }
    }
    return null;
  }

  float maxScroll(float viewHeight) {
    return Math.max(0.0F, contentHeight - viewHeight);
  }

  void adopt(PageView previous) {
    scroll = previous.scroll;
    scrollTarget = previous.scrollTarget;
  }

  boolean reveal(String settingId) {
    RowView row = row(settingId);
    if (row == null) {
      return false;
    }
    GroupView group = row.owner;
    group.presence = 1.0F;
    layout();
    scrollTarget = Math.max(0.0F, group.top + GROUP_LABEL + row.top - REVEAL_MARGIN);
    row.flash = 1.0F;
    return true;
  }

  void advance(float viewHeight, float dt) {
    boolean unfolding = false;
    for (int i = 0; i < rows.size(); i++) {
      RowView row = rows.get(i);
      if (row.flash > 0.0F) {
        row.flash = Math.max(0.0F, row.flash - dt / FLASH_SECONDS);
      }
      if (row.grid != null && !row.unfold.settled()) {
        row.unfold.update(dt);
        unfolding = true;
      }
    }
    if (unfolding) {
      layout();
    }
    scrollTarget = Math.max(0.0F, Math.min(maxScroll(viewHeight), scrollTarget));
    scroll = ease(scroll, scrollTarget, dt);
  }

  void arrange(Canvas c, float width) {
    float smallLine = c.lineHeight(FontStyle.SMALL);
    boolean rewrapped = false;
    for (int i = 0; i < rows.size(); i++) {
      RowView row = rows.get(i);
      if (row.panel) {
        if (row.control != null) {
          row.control.w = width - PANEL_INSET * 2.0F;
          row.control.measure(c);
        }
        rewrapped |= row.resizePanel();
        continue;
      }
      float controlW = 0.0F;
      float controlH = 0.0F;
      if (row.control != null) {
        if (row.control instanceof Segmented segmented) {
          segmented.limit(width - ROW_INSET - CONTROL_INSET);
        } else if (row.control instanceof Slider slider) {
          slider.limit(width - ROW_INSET - CONTROL_INSET);
        }
        controlW = row.control.measure(c);
        controlH = row.control.h;
      }
      rewrapped |= row.fit(c, width, controlW, controlH, smallLine);
    }
    if (rewrapped) {
      layout();
    }
  }

  void draw(
      Canvas c,
      Theme t,
      ModuleView.Painter painter,
      ModuleView.Reveal reveal,
      float x,
      float width,
      float viewTop,
      float viewHeight,
      float fade,
      float slide,
      float mx,
      float my,
      float dt) {
    float viewBottom = viewTop + viewHeight;
    float gridWidth = width - ROW_INSET * 2.0F;
    float smallLine = c.lineHeight(FontStyle.SMALL);
    arrange(c, width);
    c.pushAlpha(fade);
    c.pushClip(x - 2.0F, viewTop, width + 4.0F, viewHeight);

    boolean pointerInside = my >= viewTop && my < viewBottom && !overStuck(mx, my, x, width);
    float hoverX = pointerInside ? mx : OFFSCREEN;
    float hoverY = pointerInside ? my : OFFSCREEN;
    stuck = null;
    float rowLine = c.lineHeight(FontStyle.ROW);
    float captionLine = c.lineHeight(FontStyle.CAPTION);

    for (int g = 0; g < groups.size(); g++) {
      GroupView group = groups.get(g);
      float next = ease(group.presence, group.wanted() ? 1.0F : 0.0F, dt);
      if (next != group.presence) {
        group.presence = next;
        layout();
      }
      float shown = reveal.at(2 + g);
      float groupTop = viewTop - scroll + group.top + slide + (1.0F - shown) * REVEAL_RISE;
      float cardTop = groupTop + GROUP_LABEL * group.presence;
      boolean visible =
          shown > HIDDEN
              && group.presence > HIDDEN
              && cardTop + group.cardHeight >= viewTop
              && groupTop <= viewBottom;
      if (visible) {
        c.pushAlpha(shown * group.presence);
        c.text(FontStyle.CAPTION, group.title, x + 4.0F, cardTop - captionLine - 6.0F, t.grey());
        if (group.popOut != null) {
          group.popOut.place(
              x + width - IconButton.SIZE - POP_INSET, cardTop - IconButton.SIZE - POP_LIFT);
          group.popOut.draw(c, t, hoverX, hoverY, dt);
        }
        c.pushLayer(CARD_HEIGHT);
        Depth.card(c, t, x, cardTop, width, group.cardHeight);
        c.popLayer();
      }
      bandVisible = visible;
      bandTop = cardTop;
      bandClipTop = viewTop;
      bandClipBottom = viewBottom;
      for (int r = 0; r < group.rows.size(); r++) {
        drawRow(
            c,
            t,
            painter,
            group.rows.get(r),
            x,
            width,
            gridWidth,
            hoverX,
            hoverY,
            rowLine,
            smallLine,
            dt);
      }
      if (visible) {
        c.popAlpha();
      }
    }

    if (stuck != null) {
      c.shadow(x, stuckTop, width, stuck.height, t.radius(), PIN_SHADOW, 6.0F, t.shadow());
      Depth.card(c, t, x, stuckTop, width, stuck.height);
      boolean inside = mx >= x && mx < x + width && my >= stuckTop && my < stuckTop + stuck.height;
      stuck.control.draw(c, t, inside ? mx : OFFSCREEN, inside ? my : OFFSCREEN, dt);
    }

    c.popClip();
    Depth.scrollFade(
        c,
        t,
        t.paper(),
        x - 2.0F,
        viewTop,
        width + 4.0F,
        viewHeight,
        scroll,
        maxScroll(viewHeight));
    c.popAlpha();
  }

  private void drawRow(
      Canvas c,
      Theme t,
      ModuleView.Painter painter,
      RowView row,
      float x,
      float width,
      float gridWidth,
      float hoverX,
      float hoverY,
      float rowLine,
      float smallLine,
      float dt) {
    float rowTop = bandTop + row.top;
    Widget control = row.control;
    if (row.panel) {
      if (control != null && row.pinned && bandVisible && rowTop < bandClipTop) {
        stuck = row;
        stuckTop = bandClipTop;
        control.place(x + PANEL_INSET, bandClipTop + PANEL_INSET);
        return;
      }
      if (control != null) {
        control.place(x + PANEL_INSET, rowTop + PANEL_INSET);
        if (bandVisible && rowTop + row.height >= bandClipTop && rowTop <= bandClipBottom) {
          control.draw(c, t, hoverX, hoverY, dt);
        }
      }
      return;
    }
    if (control != null) {
      float controlWidth = control.measure(c);
      if (row.stacked) {
        control.place(x + ROW_INSET, rowTop + row.textHeight - STACK_LIFT);
      } else {
        control.place(
            x + width - CONTROL_INSET - controlWidth, rowTop + (row.height - control.h) * 0.5F);
      }
    }
    OptionGrid grid = row.grid;
    float unfolded = row.unfolded();
    if (grid != null) {
      if (grid.w != gridWidth) {
        grid.fit(c, gridWidth);
        layout();
      }
      grid.place(x + ROW_INSET, rowTop + row.height - (1.0F - unfolded) * GRID_DROP);
    }
    if (!bandVisible || rowTop + row.extent() < bandClipTop || rowTop > bandClipBottom) {
      return;
    }
    if (!row.first) {
      c.roundRect(x + ROW_INSET, rowTop, width - ROW_INSET, 1.0F, 0.0F, t.hair());
    }
    if (row.flash > 0.0F) {
      float glow = (float) Easing.smoothstep(row.flash);
      c.roundRect(
          x + FLASH_INSET,
          rowTop + 2.0F,
          width - FLASH_INSET * 2.0F,
          row.height - 4.0F,
          FLASH_RADIUS,
          Theme.withAlpha(t.ink(), FLASH_ALPHA * glow));
    }
    drawLabel(c, t, row, x, rowTop, rowLine, smallLine);
    if (unfolded > HIDDEN) {
      c.pushClip(x, rowTop + row.height - GRID_DROP, width, row.extent() - row.height + GRID_DROP);
      c.pushAlpha(unfolded);
      grid.draw(c, t, row.open ? hoverX : OFFSCREEN, hoverY, dt);
      c.popAlpha();
      c.popClip();
    }
    if (control != null) {
      painter.draw(c, t, control, hoverX, hoverY, dt);
    } else if (row.setting instanceof InfoSetting live) {
      row.info = live.text();
      info(c, t, row, x, width, rowTop);
    } else if (row.info != null) {
      info(c, t, row, x, width, rowTop);
    }
  }

  private static void drawLabel(
      Canvas c, Theme t, RowView row, float x, float rowTop, float rowLine, float smallLine) {
    if (row.lines.isEmpty()) {
      c.text(
          FontStyle.ROW,
          row.title,
          x + ROW_INSET,
          rowTop + (row.textHeight - rowLine) * 0.5F,
          t.ink());
      return;
    }
    float step = smallLine + LINE_GAP;
    float textTop = rowTop + (row.textHeight - rowLine - step * row.lines.size()) * 0.5F;
    c.text(FontStyle.ROW, row.title, x + ROW_INSET, textTop, t.ink());
    for (int line = 0; line < row.lines.size(); line++) {
      c.text(
          FontStyle.SMALL,
          row.lines.get(line),
          x + ROW_INSET,
          textTop + rowLine + LINE_GAP + line * step,
          t.grey());
    }
  }

  private static void info(Canvas c, Theme t, RowView row, float x, float width, float rowTop) {
    float room =
        width - ROW_INSET - CONTROL_INSET - c.textWidth(FontStyle.ROW, row.title) - TEXT_GAP * 2.0F;
    String text = c.fit(FontStyle.MONO, row.info, Math.max(room, 0.0F));
    float infoX = x + width - CONTROL_INSET - c.textWidth(FontStyle.MONO, text);
    c.text(
        FontStyle.MONO,
        text,
        infoX,
        rowTop + (row.height - c.lineHeight(FontStyle.MONO)) * 0.5F,
        t.grey());
  }

  boolean overStuck(float mx, float my, float x, float width) {
    return stuck != null
        && mx >= x
        && mx < x + width
        && my >= stuckTop
        && my < stuckTop + stuck.height;
  }

  Widget pick(float mx, float my, float x, float width, float viewTop, float viewHeight) {
    if (overStuck(mx, my, x, width)) {
      return stuck.control.mouseDown(mx, my) ? stuck.control : null;
    }
    if (my < viewTop || my >= viewTop + viewHeight) {
      return null;
    }
    for (int g = 0; g < groups.size(); g++) {
      IconButton popOut = groups.get(g).popOut;
      if (popOut != null && groups.get(g).live() && popOut.mouseDown(mx, my)) {
        return popOut;
      }
    }
    for (int i = 0; i < rows.size(); i++) {
      RowView row = rows.get(i);
      if (!row.owner.live() || row == stuck) {
        continue;
      }
      if (row.control != null && row.control.mouseDown(mx, my)) {
        return row.control;
      }
      if (row.open && row.grid.mouseDown(mx, my)) {
        return row.grid;
      }
    }
    return null;
  }

  boolean secondaryDown(float mx, float my, float viewTop) {
    for (int i = 0; i < rows.size(); i++) {
      RowView row = rows.get(i);
      if (row.owner.live()
          && row.panel
          && row.control != null
          && row.control.secondaryDown(mx, my)) {
        return true;
      }
    }
    for (int g = 0; g < groups.size(); g++) {
      GroupView group = groups.get(g);
      if (!group.live()) {
        continue;
      }
      float cardTop = viewTop - scroll + group.top + GROUP_LABEL;
      for (int r = 0; r < group.rows.size(); r++) {
        RowView row = group.rows.get(r);
        float rowTop = cardTop + row.top;
        if (row.grid != null && my >= rowTop && my < rowTop + row.extent()) {
          row.open = !row.open;
          row.unfold.set(row.open ? 1.0F : 0.0F);
          return true;
        }
      }
    }
    return true;
  }

  boolean scroll(float mx, float my, float amount, float viewHeight) {
    for (int i = 0; i < rows.size(); i++) {
      RowView row = rows.get(i);
      if (row.panel
          && row.control != null
          && row.owner.live()
          && row.control.scroll(mx, my, amount)) {
        return true;
      }
    }
    scrollTarget =
        Math.max(0.0F, Math.min(maxScroll(viewHeight), scrollTarget - amount * SCROLL_STEP));
    return true;
  }

  boolean keyDown(int keyCode, boolean control, boolean shift) {
    for (int i = 0; i < rows.size(); i++) {
      RowView row = rows.get(i);
      if (row.owner.live()
          && row.panel
          && row.control != null
          && row.control.keyDown(keyCode, control, shift)) {
        return true;
      }
    }
    return false;
  }

  boolean charTyped(int codepoint) {
    for (int i = 0; i < rows.size(); i++) {
      RowView row = rows.get(i);
      if (row.owner.live()
          && row.panel
          && row.control != null
          && row.control.charTyped(codepoint)) {
        return true;
      }
    }
    return false;
  }

  KeyButton capturing() {
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i).control instanceof KeyButton button && button.capturing()) {
        return button;
      }
    }
    return null;
  }

  TextInput focused() {
    for (int i = 0; i < rows.size(); i++) {
      RowView row = rows.get(i);
      if (!row.owner.live() || row.control == null) {
        continue;
      }
      TextInput field = row.control.focusedText();
      if (field != null) {
        return field;
      }
    }
    return null;
  }

  void cancelCapture(Widget except) {
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i).control instanceof KeyButton button && button != except) {
        button.cancel();
      }
      Widget control = rows.get(i).control;
      if (control != null && control != except) {
        control.finishEditing();
      }
    }
  }
}
