package me.cxiy.cxvisuals.gui;

import static me.cxiy.cxvisuals.gui.ModuleMetrics.CONTENT_HEIGHT;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.SCROLL_STEP;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.ease;

import java.util.function.Function;
import me.cxiy.cxvisuals.gui.window.Pane;

public final class WidgetPane implements Pane {
  private static final float INSET = 12.0F;
  private static final float TOP = 2.0F;

  private static final Widget NONE =
      new Widget() {
        @Override
        public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {}
      };

  private final String title;
  private final float preferred;
  private final float minimum;
  private final Widget widget;
  private float room = 1.0F;
  private float scroll;
  private float scrollTarget;
  private float viewTop;
  private float viewHeight;
  private float viewLeft;
  private float viewWidth;
  private Widget held;

  public WidgetPane(
      String title, float preferredWidth, float minimumWidth, Function<PanelHost, Widget> make) {
    this.title = title;
    this.preferred = preferredWidth;
    this.minimum = minimumWidth;
    Widget made =
        make.apply(
            new PanelHost() {
              @Override
              public void openTab(String pageId) {}

              @Override
              public float room() {
                return room;
              }
            });
    this.widget = made == null ? NONE : made;
  }

  public boolean empty() {
    return widget == NONE;
  }

  @Override
  public String title() {
    return title;
  }

  @Override
  public float preferredWidth() {
    return preferred;
  }

  @Override
  public float minimumWidth() {
    return minimum;
  }

  @Override
  public float preferredHeight(float limit) {
    return Math.min(limit, TOP + widget.h + INSET * 2.0F);
  }

  @Override
  public void measure(Canvas c, float width) {
    widget.w = width - INSET * 2.0F;
    widget.measure(c);
  }

  private float maxScroll() {
    return Math.max(0.0F, widget.h - viewHeight);
  }

  @Override
  public void draw(
      Canvas c, Theme t, float x, float y, float w, float h, float mouseX, float mouseY, float dt) {
    viewLeft = x + INSET;
    viewWidth = w - INSET * 2.0F;
    viewTop = y + TOP + INSET * 0.5F;
    viewHeight = h - TOP - INSET;
    room = viewHeight;
    widget.w = viewWidth;
    widget.measure(c);
    scrollTarget = Math.max(0.0F, Math.min(maxScroll(), scrollTarget));
    scroll = ease(scroll, scrollTarget, dt);
    widget.place(viewLeft, viewTop - scroll);
    boolean inside =
        mouseX >= viewLeft
            && mouseX < viewLeft + viewWidth
            && mouseY >= viewTop
            && mouseY < viewTop + viewHeight;
    c.pushLayer(CONTENT_HEIGHT);
    c.pushClip(viewLeft - 2.0F, viewTop, viewWidth + 4.0F, viewHeight);
    widget.draw(
        c,
        t,
        inside ? mouseX : Float.NEGATIVE_INFINITY,
        inside ? mouseY : Float.NEGATIVE_INFINITY,
        dt);
    c.popClip();
    Depth.scrollFade(
        c,
        t,
        t.paper(),
        viewLeft - 2.0F,
        viewTop,
        viewWidth + 4.0F,
        viewHeight,
        scroll,
        maxScroll());
    c.popLayer();
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (my < viewTop || my >= viewTop + viewHeight || !widget.mouseDown(mx, my)) {
      return false;
    }
    held = widget;
    return true;
  }

  @Override
  public void mouseUp(float mx, float my) {
    if (held != null) {
      held.mouseUp(mx, my);
      held = null;
    }
  }

  @Override
  public void mouseDrag(float mx, float my) {
    if (held != null) {
      held.mouseDrag(mx, my);
    }
  }

  @Override
  public boolean scroll(float mx, float my, float amount) {
    if (widget.scroll(mx, my, amount)) {
      return true;
    }
    scrollTarget = Math.max(0.0F, Math.min(maxScroll(), scrollTarget - amount * SCROLL_STEP));
    return true;
  }

  @Override
  public boolean secondaryDown(float mx, float my) {
    return widget.secondaryDown(mx, my);
  }

  @Override
  public boolean keyDown(int keyCode, int scanCode, boolean control, boolean shift) {
    return widget.keyDown(keyCode, control, shift);
  }

  @Override
  public boolean charTyped(int codepoint) {
    return widget.charTyped(codepoint);
  }

  @Override
  public boolean typing() {
    return widget.focusedText() != null;
  }

  @Override
  public void blur() {
    widget.finishEditing();
    if (held != null) {
      held.mouseUp(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY);
      held = null;
    }
  }
}
