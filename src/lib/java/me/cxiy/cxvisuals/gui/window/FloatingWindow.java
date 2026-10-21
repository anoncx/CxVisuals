package me.cxiy.cxvisuals.gui.window;

import me.cxiy.cxvisuals.core.module.ClientSettings.WindowMotion;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.IconButton;
import me.cxiy.cxvisuals.gui.Presence;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.WindowFrame;
import me.cxiy.cxvisuals.gui.spatial.Layer;
import me.cxiy.cxvisuals.gui.spatial.Rig;
import me.cxiy.cxvisuals.gui.spatial.Warp;

public final class FloatingWindow {
  public static final float BAR = 28.0F;
  public static final float MIN_HEIGHT = 120.0F;

  private static final float GRIP_DOT = 2.0F;
  private static final float GRIP_STEP = 5.0F;
  private static final int GRIP_COLUMNS = 3;
  private static final int GRIP_ROWS = 2;
  private static final float GRIP_INSET = 16.0F;
  private static final float BUTTON_INSET = 8.0F;
  private static final float BUTTON_GAP = 2.0F;
  private static final float TITLE_GAP = 8.0F;
  private static final float REST_ALPHA = 0.9F;
  private static final float HEIGHT_RESPONSE = 14.0F;
  private static final float SETTLED = 0.5F;
  private static final float CONTENT_Z = Layer.height(Layer.ROW);
  private static final float NO_POINTER = Float.NEGATIVE_INFINITY;

  private enum Grab {
    NONE,
    BAR,
    PANE,
    BUTTON
  }

  private final String id;
  private final Pane pane;
  private final Presence presence = new Presence();
  private final WindowFrame frame = new WindowFrame();
  private final Anim focus = Anim.state(1.0F);
  private final Rig rig = new Rig();
  private final Warp warp = new Warp();
  private final Runnable closed;
  private final IconButton close;
  private final IconButton dock;
  private IconButton held;
  private Grab grab = Grab.NONE;
  private float grabX;
  private float grabY;
  private float pointerX;
  private float pointerY;
  private float x;
  private float y;
  private float w;
  private float h;
  private boolean open = true;
  private boolean placed;
  private boolean moved;
  private boolean snapped;

  public FloatingWindow(String id, Pane pane, Runnable onDock) {
    this(id, pane, onDock, () -> {});
  }

  public FloatingWindow(String id, Pane pane, Runnable onDock, Runnable onClosed) {
    this.id = id;
    this.pane = pane;
    this.closed = onClosed;
    this.close = new IconButton(IconButton.Glyph.CLOSE, IconButton.LABEL_CLOSE, this::close);
    this.dock =
        onDock == null
            ? null
            : new IconButton(
                IconButton.Glyph.DOCK,
                IconButton.LABEL_DOCK,
                () -> {
                  onDock.run();
                  close();
                });
    this.w = pane.preferredWidth();
    this.h = MIN_HEIGHT;
  }

  public String id() {
    return id;
  }

  public Pane pane() {
    return pane;
  }

  public boolean placed() {
    return placed;
  }

  public float minimumWidth() {
    return pane.minimumWidth();
  }

  public float preferredWidth() {
    return pane.preferredWidth();
  }

  public float x() {
    return x;
  }

  public float y() {
    return y;
  }

  public float width() {
    return w;
  }

  public float height() {
    return h;
  }

  public Rect bounds() {
    return new Rect(x, y, w, h);
  }

  public void moveTo(float nextX, float nextY) {
    x = nextX;
    y = nextY;
    placed = true;
  }

  public void dismiss() {
    open = false;
  }

  public void close() {
    dismiss();
    closed.run();
  }

  public void settle() {
    presence.snap(1.0F);
  }

  public boolean snapped() {
    return snapped;
  }

  public void snapped(boolean value) {
    snapped = value;
  }

  public boolean moved() {
    return moved;
  }

  public void markMoved() {
    moved = true;
  }

  public boolean gone() {
    return !open && presence.gone();
  }

  public boolean closing() {
    return !open;
  }

  public boolean dragging() {
    return grab == Grab.BAR;
  }

  public void project(float rawX, float rawY) {
    warp.unmap(rawX, rawY, CONTENT_Z);
    pointerX = warp.x();
    pointerY = warp.y();
  }

  public boolean hit(float rawX, float rawY) {
    project(rawX, rawY);
    return open && inside(pointerX, pointerY);
  }

  private boolean inside(float px, float py) {
    return px >= x && px < x + w && py >= y && py < y + h;
  }

  public float wanted(float room) {
    return Math.max(
        Math.min(MIN_HEIGHT, room), Math.min(room, BAR + pane.preferredHeight(room - BAR)));
  }

  public void arrange(Canvas c) {
    pane.measure(c, w);
  }

  public void resize(float width) {
    w = Math.max(pane.minimumWidth(), Math.min(pane.preferredWidth(), width));
  }

  public void fit(float limitW, float limitH, float dt) {
    w = Math.min(w, Math.max(1.0F, limitW));
    float goal = wanted(Math.max(1.0F, limitH));
    float next = h + (goal - h) * (1.0F - (float) Math.exp(-dt * HEIGHT_RESPONSE));
    h = Math.abs(goal - next) < SETTLED ? goal : next;
  }

  public void draw(
      Canvas c,
      Theme t,
      WindowMotion style,
      Look look,
      float screenW,
      float screenH,
      float rawX,
      float rawY,
      float dt) {
    presence.step(style, open, dt);
    focus.set(look.focused ? 1.0F : 0.0F);
    focus.update(dt);
    project(rawX, rawY);
    float centreX = x + w * 0.5F;
    float centreY = y + h * 0.5F;
    boolean over = look.hovered && inside(pointerX, pointerY);
    rig.configure(look.spatial, look.strength);
    rig.gate(presence.phase());
    boolean still = !over || grab == Grab.BAR;
    rig.feedPointer(
        still ? 0.0F : (pointerX - centreX) / (w * 0.5F),
        still ? 0.0F : (pointerY - centreY) / (h * 0.5F));
    rig.update(dt);
    warp.set(rig.yaw(), rig.pitch(), centreX, centreY, Rig.DISTANCE);
    float hoverX = look.hovered ? pointerX : NO_POINTER;
    float hoverY = look.hovered ? pointerY : NO_POINTER;

    c.perspective(rig.yaw(), rig.pitch(), Rig.DISTANCE, centreX, centreY);
    c.pushAlpha(REST_ALPHA + (1.0F - REST_ALPHA) * focus.get());
    if (frame.open(c, t, presence, style, x, y, w, h, screenW, screenH, 1.0F)) {
      drawBar(c, t, hoverX, hoverY, dt);
      pane.draw(c, t, x, y + BAR, w, h - BAR, hoverX, hoverY, dt);
      frame.close(c);
    }
    c.popAlpha();
    c.perspective(0.0F, 0.0F, Rig.DISTANCE);
  }

  private void drawBar(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    float gripTop = y + (BAR - (GRIP_ROWS - 1) * GRIP_STEP - GRIP_DOT) * 0.5F;
    int dot = Theme.withAlpha(t.ink(), 0.25F + 0.2F * focus.get());
    for (int column = 0; column < GRIP_COLUMNS; column++) {
      for (int row = 0; row < GRIP_ROWS; row++) {
        c.roundRect(
            x + GRIP_INSET + column * GRIP_STEP,
            gripTop + row * GRIP_STEP,
            GRIP_DOT,
            GRIP_DOT,
            GRIP_DOT * 0.5F,
            dot);
      }
    }
    float top = y + (BAR - IconButton.SIZE) * 0.5F;
    close.place(x + w - BUTTON_INSET - IconButton.SIZE, top);
    float edge = close.x;
    if (dock != null) {
      dock.place(close.x - BUTTON_GAP - IconButton.SIZE, top);
      edge = dock.x;
    }
    float titleX = x + GRIP_INSET + GRIP_COLUMNS * GRIP_STEP + TITLE_GAP;
    String title = pane.title();
    c.text(
        FontStyle.LABEL,
        c.fit(FontStyle.LABEL, title, edge - TITLE_GAP - titleX),
        titleX,
        y + (BAR - c.lineHeight(FontStyle.LABEL)) * 0.5F,
        t.ink());
    if (dock != null) {
      dock.draw(c, t, mouseX, mouseY, dt);
    }
    close.draw(c, t, mouseX, mouseY, dt);
  }

  public boolean mouseDown(float rawX, float rawY) {
    if (!hit(rawX, rawY)) {
      return false;
    }
    float mx = pointerX;
    float my = pointerY;
    if (close.mouseDown(mx, my)) {
      held = close;
      grab = Grab.BUTTON;
    } else if (dock != null && dock.mouseDown(mx, my)) {
      held = dock;
      grab = Grab.BUTTON;
    } else if (my < y + BAR) {
      grab = Grab.BAR;
      grabX = mx - x;
      grabY = my - y;
    } else {
      grab = pane.mouseDown(mx, my) ? Grab.PANE : Grab.NONE;
    }
    return true;
  }

  public float dragX() {
    return pointerX - grabX;
  }

  public float dragY() {
    return pointerY - grabY;
  }

  public void mouseUp(float rawX, float rawY) {
    project(rawX, rawY);
    Grab released = grab;
    grab = Grab.NONE;
    if (released == Grab.BUTTON && held != null) {
      held.mouseUp(pointerX, pointerY);
    } else if (released == Grab.PANE) {
      pane.mouseUp(pointerX, pointerY);
    }
    held = null;
  }

  public void mouseDrag(float rawX, float rawY) {
    project(rawX, rawY);
    if (grab == Grab.PANE) {
      pane.mouseDrag(pointerX, pointerY);
    }
  }

  public boolean scroll(float rawX, float rawY, float amount) {
    return hit(rawX, rawY) && pointerY >= y + BAR && pane.scroll(pointerX, pointerY, amount);
  }

  public boolean secondaryDown(float rawX, float rawY) {
    return hit(rawX, rawY) && pointerY >= y + BAR && pane.secondaryDown(pointerX, pointerY);
  }

  public boolean keyDown(int keyCode, int scanCode, boolean control, boolean shift) {
    return pane.keyDown(keyCode, scanCode, control, shift);
  }

  public boolean charTyped(int codepoint) {
    return pane.charTyped(codepoint);
  }

  public boolean typing() {
    return pane.typing();
  }

  public void blur() {
    pane.blur();
  }

  public static final class Look {
    boolean focused;
    boolean hovered;
    boolean spatial;
    float strength;

    void set(boolean focused, boolean hovered, boolean spatial, float strength) {
      this.focused = focused;
      this.hovered = hovered;
      this.spatial = spatial;
      this.strength = strength;
    }
  }
}
