package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;

final class Pager {
  static final float ARROW = 38.0F;
  static final float ROOM = 64.0F;
  static final float FOOT = 30.0F;
  static final int NONE = 0;
  static final int PREVIOUS = -1;
  static final int NEXT = 1;

  private static final float DOT = 5.0F;
  private static final float GAP = 8.0F;
  private static final float LIFT = 0.06F;
  private static final float PRESS = 0.94F;
  private static final float RUBBER = 0.35F;
  private static final float TURN_AT = 0.22F;
  private static final String BACK = "‹";
  private static final String FORWARD = "›";

  private final Anim slide = Anim.move(0.0F);
  private final Anim previous = Anim.hover(0.0F);
  private final Anim next = Anim.hover(0.0F);
  private int page;
  private int pressed = NONE;
  private float drag;
  private float previousX;
  private float nextX;
  private float arrowY;

  int page() {
    return page;
  }

  float position() {
    return slide.get() - drag;
  }

  void drag(float pages, int total) {
    float target = page - pages;
    float last = total - 1;
    if (target < 0.0F) {
      target *= RUBBER;
    } else if (target > last) {
      target = last + (target - last) * RUBBER;
    }
    drag = page - target;
  }

  boolean dragging() {
    return drag != 0.0F;
  }

  void drop(int total) {
    float moved = -drag;
    int target = moved > TURN_AT ? page + 1 : moved < -TURN_AT ? page - 1 : page;
    slide.snap(position());
    drag = 0.0F;
    select(target, total);
  }

  void select(int target, int pages) {
    page = Math.max(0, Math.min(pages - 1, target));
  }

  void step(int delta, int pages) {
    select(page + delta, pages);
  }

  void place(float x, float w, float centreY) {
    previousX = x + (ROOM - ARROW) * 0.5F;
    nextX = x + w - (ROOM + ARROW) * 0.5F;
    arrowY = centreY - ARROW * 0.5F;
  }

  void update(float dt) {
    if (drag == 0.0F) {
      slide.set(page);
      slide.update(dt);
    }
  }

  int hit(float mx, float my, int pages) {
    if (pages < 2) {
      return NONE;
    }
    if (page > 0 && Kit.inside(mx, my, previousX, arrowY, ARROW, ARROW)) {
      return PREVIOUS;
    }
    return page < pages - 1 && Kit.inside(mx, my, nextX, arrowY, ARROW, ARROW) ? NEXT : NONE;
  }

  void press(int side) {
    pressed = side;
  }

  int release() {
    int side = pressed;
    pressed = NONE;
    return side;
  }

  void draw(Canvas c, Theme t, float mouseX, float mouseY, int pages, boolean live, float dt) {
    if (pages < 2) {
      return;
    }
    arrow(c, t, BACK, previousX, previous, page > 0, PREVIOUS, live, mouseX, mouseY, dt);
    arrow(c, t, FORWARD, nextX, next, page < pages - 1, NEXT, live, mouseX, mouseY, dt);
  }

  private void arrow(
      Canvas c,
      Theme t,
      String glyph,
      float x,
      Anim hover,
      boolean enabled,
      int side,
      boolean live,
      float mouseX,
      float mouseY,
      float dt) {
    hover.set(enabled && live && Kit.inside(mouseX, mouseY, x, arrowY, ARROW, ARROW) ? 1.0F : 0.0F);
    hover.update(dt);
    c.pushAlpha(enabled ? 1.0F : 0.3F);
    c.pushTransform(
        (pressed == side ? PRESS : 1.0F) * (1.0F + LIFT * hover.get()),
        x + ARROW * 0.5F,
        arrowY + ARROW * 0.5F);
    Depth.raised(
        c,
        t,
        x,
        arrowY,
        ARROW,
        ARROW,
        ARROW * 0.5F,
        Theme.mix(t.surface2(), t.ink(), 0.1F * hover.get()));
    c.text(
        FontStyle.TITLE,
        glyph,
        x + (ARROW - c.textWidth(FontStyle.TITLE, glyph)) * 0.5F,
        arrowY + (ARROW - c.lineHeight(FontStyle.TITLE)) * 0.5F - 2.0F,
        t.ink());
    c.popTransform();
    c.popAlpha();
  }

  void dots(Canvas c, Theme t, float x, float y, float w, int pages) {
    if (pages < 2) {
      return;
    }
    float span = pages * DOT + (pages - 1) * GAP;
    float left = x + (w - span) * 0.5F;
    for (int i = 0; i < pages; i++) {
      float near = Math.max(0.0F, 1.0F - Math.abs(i - position()));
      float size = DOT * (1.0F + 0.5F * near);
      c.roundRect(
          left + i * (DOT + GAP) + (DOT - size) * 0.5F,
          y + (DOT - size) * 0.5F,
          size,
          size,
          size * 0.5F,
          Theme.mix(t.dotOff(), t.ink(), near));
    }
  }
}
