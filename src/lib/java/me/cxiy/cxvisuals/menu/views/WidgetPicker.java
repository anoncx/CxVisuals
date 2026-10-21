package me.cxiy.cxvisuals.menu.views;

import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;

final class WidgetPicker {
  enum Result {
    NONE,
    CHOSEN,
    CANCEL
  }

  private static final int COLUMNS = 3;
  private static final int NONE = -1;
  private static final float TILE_W = 128.0F;
  private static final float TILE_H = 92.0F;
  private static final float GAP = 12.0F;
  private static final float PAD = 24.0F;
  private static final float HEAD = 64.0F;
  private static final float FOOT = 70.0F;
  private static final float GLYPH = 30.0F;
  private static final float DIM = 0.62F;
  private static final int SCRIM = 0xFF000000;
  private static final String HEADING = Lang.key("Add a widget");
  private static final String CANCEL = Lang.key("Cancel");
  private static final String EMPTY = Lang.key("Every widget is already on the menu");

  private final Anim open = Anim.reveal(0.0F);
  private final Anim[] hovers = new Anim[Widgets.all().size()];
  private final Anim cancelHover = Anim.hover(0.0F);
  private List<Widget> options = List.of();
  private Widget chosen;
  private boolean shown;
  private int slot = NONE;
  private int pressed = NONE;
  private int focus = NONE;
  private float fx;
  private float fy;
  private float fw;
  private float fh;
  private float x;
  private float y;
  private float w;
  private float h;
  private float cancelX;
  private float cancelY;
  private float cancelW;
  private float cancelH;

  WidgetPicker() {
    for (int i = 0; i < hovers.length; i++) {
      hovers[i] = Anim.hover(0.0F);
    }
  }

  boolean active() {
    return shown || open.get() > 0.001F;
  }

  boolean interactive() {
    return shown;
  }

  int slot() {
    return slot;
  }

  Widget chosen() {
    return chosen;
  }

  void show(int target, List<Widget> choices) {
    slot = target;
    options = choices;
    shown = true;
    pressed = NONE;
    focus = choices.isEmpty() ? NONE : 0;
  }

  void close() {
    shown = false;
  }

  private int columns() {
    return Math.max(1, Math.min(COLUMNS, options.size()));
  }

  private int rows() {
    return Math.max(1, (options.size() + COLUMNS - 1) / COLUMNS);
  }

  private float optionX(int index) {
    return x
        + PAD
        + index % COLUMNS * (TILE_W + GAP)
        + (w - PAD * 2.0F - columns() * TILE_W - (columns() - 1) * GAP) * 0.5F;
  }

  private float optionY(int index) {
    return y + HEAD + index / COLUMNS * (TILE_H + GAP);
  }

  void place(float px, float py, float pw, float ph) {
    fx = px;
    fy = py;
    fw = pw;
    fh = ph;
    float inner = columns() * TILE_W + (columns() - 1) * GAP;
    w = Math.min(pw - 40.0F, Math.max(inner + PAD * 2.0F, 300.0F));
    h = HEAD + rows() * TILE_H + (rows() - 1) * GAP + FOOT;
    x = px + (pw - w) * 0.5F;
    y = py + (ph - h) * 0.5F;
    cancelW = 120.0F;
    cancelH = Kit.PILL + 4.0F;
    cancelX = x + (w - cancelW) * 0.5F;
    cancelY = y + h - FOOT + (FOOT - cancelH) * 0.5F - 4.0F;
  }

  private int optionAt(float mx, float my) {
    for (int i = 0; i < options.size(); i++) {
      if (Kit.inside(mx, my, optionX(i), optionY(i), TILE_W, TILE_H)) {
        return i;
      }
    }
    return NONE;
  }

  boolean press(float mx, float my) {
    pressed = optionAt(mx, my);
    if (pressed == NONE && Kit.inside(mx, my, cancelX, cancelY, cancelW, cancelH)) {
      pressed = options.size();
    }
    return pressed != NONE || Kit.inside(mx, my, x, y, w, h);
  }

  Result release(float mx, float my) {
    int began = pressed;
    pressed = NONE;
    if (began == NONE) {
      return Kit.inside(mx, my, x, y, w, h) ? Result.NONE : Result.CANCEL;
    }
    if (began == options.size()) {
      return Kit.inside(mx, my, cancelX, cancelY, cancelW, cancelH) ? Result.CANCEL : Result.NONE;
    }
    if (optionAt(mx, my) != began) {
      return Result.NONE;
    }
    chosen = options.get(began);
    return Result.CHOSEN;
  }

  Result move(int dx, int dy) {
    if (options.isEmpty()) {
      return Result.NONE;
    }
    int next = focus == NONE ? 0 : focus + dx + dy * COLUMNS;
    focus = Math.max(0, Math.min(options.size() - 1, next));
    return Result.NONE;
  }

  Result confirm() {
    if (focus == NONE || options.isEmpty()) {
      return Result.CANCEL;
    }
    chosen = options.get(focus);
    return Result.CHOSEN;
  }

  void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    open.set(shown ? 1.0F : 0.0F);
    open.update(dt);
    float amount = open.get();
    if (amount <= 0.001F) {
      return;
    }
    c.pushAlpha(amount);
    c.roundRect(fx, fy, fw, fh, 0.0F, Theme.withAlpha(SCRIM, DIM));
    c.pushTransform(0.94F + 0.06F * amount, x + w * 0.5F, y + h * 0.5F);
    Kit.window(c, t, x, y, w, h);
    c.text(
        FontStyle.TITLE,
        Lang.tr(HEADING),
        x + PAD,
        y + (HEAD - c.lineHeight(FontStyle.TITLE)) * 0.5F,
        t.ink());
    if (options.isEmpty()) {
      String none = Lang.tr(EMPTY);
      c.text(
          FontStyle.LABEL,
          none,
          x + (w - c.textWidth(FontStyle.LABEL, none)) * 0.5F,
          y + HEAD + 20.0F,
          t.grey());
    }
    int over = optionAt(mouseX, mouseY);
    for (int i = 0; i < options.size(); i++) {
      tile(c, t, i, over == i, dt);
    }
    cancelHover.set(Kit.inside(mouseX, mouseY, cancelX, cancelY, cancelW, cancelH) ? 1.0F : 0.0F);
    cancelHover.update(dt);
    Kit.pill(
        c,
        t,
        Lang.tr(CANCEL),
        cancelX,
        cancelY,
        cancelW,
        cancelH,
        cancelHover.get(),
        pressed == options.size() ? 1.0F : 0.0F,
        false,
        true);
    c.popTransform();
    c.popAlpha();
  }

  private void tile(Canvas c, Theme t, int index, boolean over, float dt) {
    Widget widget = options.get(index);
    Anim hover = hovers[index];
    hover.set(over || focus == index ? 1.0F : 0.0F);
    hover.update(dt);
    float tx = optionX(index);
    float ty = optionY(index);
    float lit = hover.get();
    c.pushTransform(
        (pressed == index ? 0.97F : 1.0F) * (1.0F + 0.04F * lit),
        tx + TILE_W * 0.5F,
        ty + TILE_H * 0.5F);
    c.roundRectBorder(
        tx,
        ty,
        TILE_W,
        TILE_H,
        Kit.WINDOW_RADIUS * 0.75F,
        1.0F,
        Theme.withAlpha(t.ink(), 0.14F + 0.6F * lit));
    Pictograms.draw(
        c,
        t,
        widget.glyph(),
        tx + (TILE_W - GLYPH) * 0.5F,
        ty + 14.0F,
        GLYPH,
        Theme.mix(t.ink2(), t.ink(), lit),
        lit > 0.5F);
    String name = widget.title();
    c.text(
        FontStyle.LABEL,
        name,
        tx + (TILE_W - c.textWidth(FontStyle.LABEL, name)) * 0.5F,
        ty + TILE_H - 14.0F - c.lineHeight(FontStyle.LABEL),
        Theme.mix(t.ink2(), t.ink(), lit));
    c.popTransform();
  }
}
