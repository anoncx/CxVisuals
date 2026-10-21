package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Theme;

public final class Stage {
  static final float SHIFT = 24.0F;
  static final float OUT = 0.18F;
  static final float IN = 0.24F;

  private static final float POP_FROM = 0.96F;
  private static final float OFFSCREEN = -100000.0F;

  private ClientSettings.WindowMotion motion = ClientSettings.WindowMotion.MORPH;
  private Page page;
  private Page old;
  private int depth;
  private int direction;
  private float elapsed = IN;
  private boolean placed;
  private float fromX;
  private float fromY;
  private float fromW;
  private float fromH;
  private float x;
  private float y;
  private float w;
  private float h;
  private float toX;
  private float toY;
  private float toW;
  private float toH;
  private float alphaIn = 1.0F;
  private float alphaOut;
  private float shiftIn;
  private float shiftOut;
  private float scaleIn = 1.0F;

  public void motion(ClientSettings.WindowMotion next) {
    motion = next;
  }

  public Page page() {
    return page;
  }

  public boolean moving() {
    return elapsed < IN;
  }

  public void target(float[] out) {
    out[0] = toX;
    out[1] = toY;
    out[2] = toW;
    out[3] = toH;
  }

  public float alphaIn() {
    return alphaIn;
  }

  Page outgoing() {
    return old;
  }

  float w() {
    return w;
  }

  float h() {
    return h;
  }

  float shiftIn() {
    return shiftIn;
  }

  float shiftOut() {
    return shiftOut;
  }

  float scaleIn() {
    return scaleIn;
  }

  public void show(Page next, int nextDepth) {
    if (next == page) {
      depth = nextDepth;
      return;
    }
    old = page;
    if (old != null) {
      old.freeze();
    }
    direction = page == null ? 0 : Integer.compare(nextDepth, depth);
    depth = nextDepth;
    page = next;
    next.entered();
    fromX = x;
    fromY = y;
    fromW = w;
    fromH = h;
    elapsed = 0.0F;
  }

  public void draw(
      Canvas c,
      Theme t,
      float roomX,
      float roomY,
      float roomW,
      float roomH,
      float mouseX,
      float mouseY,
      float dt) {
    if (page == null) {
      return;
    }
    elapsed = Math.min(IN, elapsed + dt);
    page.measure(c, roomW, roomH);
    toW = Math.min(page.wantW, roomW);
    toH = Math.min(page.wantH, roomH);
    toX = roomX + (roomW - toW) * 0.5F;
    toY = roomY + (roomH - toH) * 0.5F;
    boolean slide = motion == ClientSettings.WindowMotion.SLIDE && direction != 0;
    boolean morph =
        motion == ClientSettings.WindowMotion.MORPH || motion == ClientSettings.WindowMotion.POP;
    float in = elapsed / IN;
    float out = Math.min(1.0F, elapsed / (slide ? IN : OUT));
    if (!placed || !morph || in >= 1.0F) {
      x = toX;
      y = toY;
      w = toW;
      h = toH;
      placed = true;
    } else {
      float bend =
          (float)
              (motion == ClientSettings.WindowMotion.POP
                  ? Easing.overshoot(in)
                  : Easing.snappy(in));
      x = fromX + (toX - fromX) * bend;
      y = fromY + (toY - fromY) * bend;
      w = fromW + (toW - fromW) * bend;
      h = fromH + (toH - fromH) * bend;
    }
    page.place(toX, toY, toW, toH);
    float eased = in >= 1.0F ? 1.0F : (float) Easing.snappy(in);
    float gone = out >= 1.0F ? 1.0F : (float) Easing.snappy(out);
    boolean shifts = motion == ClientSettings.WindowMotion.MORPH;
    alphaIn = slide ? 1.0F : eased;
    alphaOut = slide ? 1.0F : 1.0F - gone;
    shiftIn =
        slide
            ? direction * roomW * (1.0F - eased)
            : shifts ? direction * SHIFT * (1.0F - eased) : 0.0F;
    shiftOut = slide ? -direction * roomW * gone : shifts ? -direction * SHIFT * gone : 0.0F;
    scaleIn =
        motion == ClientSettings.WindowMotion.POP ? POP_FROM + (1.0F - POP_FROM) * eased : 1.0F;
    if (page.plated()) {
      Kit.window(c, t, x, y, w, h);
    }
    c.pushClip(x, y, w, h);
    if (old != null) {
      if (out >= 1.0F) {
        old = null;
      } else {
        paint(c, t, old, alphaOut, shiftOut, 1.0F, OFFSCREEN, OFFSCREEN, dt);
      }
    }
    paint(c, t, page, alphaIn, shiftIn, scaleIn, mouseX, mouseY, dt);
    c.popClip();
  }

  private void paint(
      Canvas c,
      Theme t,
      Page target,
      float alpha,
      float shift,
      float scale,
      float mouseX,
      float mouseY,
      float dt) {
    c.pushAlpha(alpha);
    c.pushAffine(scale, shift, 0.0F, x + w * 0.5F, y + h * 0.5F);
    target.draw(c, t, mouseX, mouseY, dt);
    c.popTransform();
    c.popAlpha();
  }
}
