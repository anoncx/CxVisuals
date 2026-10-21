package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;
import me.cxiy.cxvisuals.modules.shaders.circles.Contour;
import me.cxiy.cxvisuals.modules.shaders.circles.Rehearsal;

public final class CircleStage extends Widget {
  private static final String HINT = Lang.key("Drag to orbit · Click to replay");
  private static final String JUMP = Lang.key("Jump");
  private static final String LAND = Lang.key("Land");
  private static final String OFF = Lang.key("Off in game");
  private static final float HEIGHT = 196.0F;
  private static final float INSET = 12.0F;
  private static final float TRACK = 22.0F;
  private static final float TRACK_PAD = 14.0F;
  private static final float THUMB = 8.0F;
  private static final float TAG_HEIGHT = 20.0F;
  private static final float TITLE_SCALE = 0.62F;
  private static final float DRIFT = 0.16F;
  private static final float ORBIT = 0.011F;
  private static final float TILT = 0.006F;
  private static final float LOWEST_TILT = 0.16F;
  private static final float HIGHEST_TILT = 1.1F;
  private static final float CLICK = 3.0F;
  private static final float GLIDE = 4.0F;
  private static final float FLASH = 0.45F;
  private static final int SEGMENTS = 64;
  private static final int INK = 0xFFFFFFFF;
  private static final int STAGE = 0xFF050506;

  private final ShadersModule module;
  private final Anim hover = Anim.state(0.0F);
  private final Anim held = Anim.press(0.0F);
  private final float[] xs = new float[SEGMENTS + 1];
  private final float[] ys = new float[SEGMENTS + 1];
  private float clock;
  private float yaw = 0.55F;
  private float pitch = 0.5F;
  private float spin;
  private float flash;
  private String flashed = JUMP;
  private float downX;
  private float downY;
  private float lastX;
  private float lastY;
  private boolean dragging;
  private boolean scrubbing;
  private boolean moved;

  public CircleStage(ShadersModule module) {
    this.module = module;
    this.h = HEIGHT;
  }

  public float clock() {
    return clock;
  }

  public float trackY() {
    return y + h - TRACK * 0.5F;
  }

  public float trackX(float share) {
    return x + TRACK_PAD + (w - TRACK_PAD * 2.0F) * share;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    advance(dt);
    boolean over = contains(mouseX, mouseY);
    hover.set(over || dragging ? 1.0F : 0.0F);
    held.set(scrubbing ? 1.0F : 0.0F);
    hover.update(dt);
    held.update(dt);
    float radius = Math.max(6.0F, t.radius() - 4.0F);
    if (!c.stage(Canvas.CIRCLES, x, y, w, h, yaw, pitch, clock, radius)) {
      fallback(c, radius);
    }
    overlay(c, t);
  }

  private void advance(float dt) {
    float loop = Rehearsal.loop(module);
    if (!dragging) {
      yaw += spin * dt + DRIFT * dt * (1.0F - hover.get() * 0.8F);
      spin *= (float) Math.exp(-GLIDE * dt);
    }
    if (scrubbing) {
      return;
    }
    float before = clock;
    clock += dt;
    if (clock >= loop) {
      clock -= loop;
      before = -1.0F;
    }
    if (crossed(before, clock, 0.0F) && Rehearsal.jumps(module)) {
      ping(JUMP);
    }
    if (crossed(before, clock, Rehearsal.AIR) && module.onLand.get()) {
      ping(LAND);
    }
    flash = Math.max(0.0F, flash - dt);
  }

  private static boolean crossed(float before, float after, float mark) {
    return before < mark && after >= mark;
  }

  private void ping(String what) {
    flashed = what;
    flash = FLASH;
  }

  private void fallback(Canvas c, float radius) {
    c.roundRect(x, y, w, h, radius, STAGE);
    Contour contour = module.ringContour();
    float cx = x + w * 0.5F;
    float cy = y + h * 0.5F;
    float scale = Math.min(w, h) * 0.32F / Math.max(contour.peak(), 1.0F);
    for (int i = 0; i <= SEGMENTS; i++) {
      float turn = (float) i / SEGMENTS;
      float angle = turn * (float) (Math.PI * 2.0);
      float r = contour.at(turn) * scale;
      xs[i] = cx + (float) Math.sin(angle) * r;
      ys[i] = cy - (float) Math.cos(angle) * r * 0.45F;
    }
    int first = module.ringPalette.get().first() | 0xFF000000;
    c.stroke(xs, ys, SEGMENTS + 1, 10.0F, 8.0F, Theme.withAlpha(first, 0.25F));
    c.stroke(xs, ys, SEGMENTS + 1, 2.0F, 1.0F, first);
  }

  private void overlay(Canvas c, Theme t) {
    String name = module.ring.labels().get(module.ring.index()).toUpperCase(Lang.locale());
    float titleX = x + INSET;
    float titleY = y + INSET - 2.0F;
    c.pushTransform(TITLE_SCALE, titleX, titleY);
    c.text(FontStyle.DOTS, name, titleX, titleY, Theme.withAlpha(INK, 0.92F));
    c.popTransform();
    String detail =
        module.spread.labels().get(module.spread.index())
            + "  ·  "
            + format(module.ringSeconds())
            + " s";
    c.text(
        FontStyle.CAPTION,
        detail,
        titleX,
        titleY + c.lineHeight(FontStyle.DOTS) * TITLE_SCALE + 2.0F,
        Theme.withAlpha(INK, 0.5F));

    tag(c, t);

    float hint = hover.get() * (1.0F - held.get());
    if (hint > 0.01F) {
      String text = Lang.tr(HINT);
      float hintW = c.textWidth(FontStyle.CAPTION, text);
      c.text(
          FontStyle.CAPTION,
          text,
          x + w - INSET - hintW,
          y + INSET,
          Theme.withAlpha(INK, 0.45F * hint));
    }

    float loop = Rehearsal.loop(module);
    float share = Math.clamp(clock / loop, 0.0F, 1.0F);
    float left = trackX(0.0F);
    float right = trackX(1.0F);
    float lineY = trackY();
    float thickness = 2.0F + held.get();
    c.roundRect(
        left,
        lineY - thickness * 0.5F,
        right - left,
        thickness,
        thickness * 0.5F,
        Theme.withAlpha(INK, 0.16F));
    c.roundRect(
        left,
        lineY - thickness * 0.5F,
        (right - left) * share,
        thickness,
        thickness * 0.5F,
        Theme.withAlpha(INK, 0.55F));
    float air = Rehearsal.AIR / loop;
    c.roundRect(trackX(air) - 1.0F, lineY - 4.0F, 2.0F, 8.0F, 1.0F, Theme.withAlpha(INK, 0.3F));
    float knob = THUMB + 4.0F * held.get();
    c.roundRect(trackX(share) - knob * 0.5F, lineY - knob * 0.5F, knob, knob, knob * 0.5F, INK);
  }

  private void tag(Canvas c, Theme t) {
    boolean live = module.circlesLive();
    String text = Lang.tr(live ? flashed : OFF);
    float glow = live ? flash / FLASH : 0.0F;
    float textW = c.textWidth(FontStyle.CAPTION, text);
    float tagW = textW + (live ? 26.0F : 16.0F);
    float tagX = x + w - INSET - tagW;
    float tagY = y + h - TRACK - TAG_HEIGHT - 4.0F;
    if (!live || glow > 0.01F) {
      float alpha = live ? glow : 0.85F;
      c.pushAlpha(alpha);
      c.roundRect(tagX, tagY, tagW, TAG_HEIGHT, TAG_HEIGHT * 0.5F, 0x8C000000);
      c.roundRectBorder(
          tagX, tagY, tagW, TAG_HEIGHT, TAG_HEIGHT * 0.5F, 1.0F, Theme.withAlpha(INK, 0.14F));
      float textX = tagX + 8.0F;
      if (live) {
        c.roundRect(tagX + 8.0F, tagY + TAG_HEIGHT * 0.5F - 3.0F, 6.0F, 6.0F, 3.0F, t.red());
        textX += 12.0F;
      }
      c.text(
          FontStyle.CAPTION,
          text,
          textX,
          centered(c, FontStyle.CAPTION, tagY, TAG_HEIGHT),
          Theme.withAlpha(INK, 0.9F));
      c.popAlpha();
    }
  }

  private static String format(float seconds) {
    int tenths = Math.round(seconds * 10.0F);
    return tenths / 10 + "." + tenths % 10;
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (!contains(mx, my)) {
      return false;
    }
    downX = mx;
    downY = my;
    lastX = mx;
    lastY = my;
    moved = false;
    if (my >= y + h - TRACK) {
      scrubbing = true;
      scrub(mx);
    } else {
      dragging = true;
      spin = 0.0F;
    }
    return true;
  }

  @Override
  public void mouseDrag(float mx, float my) {
    if (scrubbing) {
      scrub(mx);
      return;
    }
    if (!dragging) {
      return;
    }
    if (Math.abs(mx - downX) + Math.abs(my - downY) > CLICK) {
      moved = true;
    }
    float dx = mx - lastX;
    yaw += dx * ORBIT;
    pitch = Math.clamp(pitch + (my - lastY) * TILT, LOWEST_TILT, HIGHEST_TILT);
    spin = spin * 0.5F + dx * ORBIT * 30.0F;
    lastX = mx;
    lastY = my;
  }

  @Override
  public void mouseUp(float mx, float my) {
    if (dragging && !moved) {
      clock = 0.0F;
      spin = 0.0F;
      if (Rehearsal.jumps(module)) {
        ping(JUMP);
      }
    }
    dragging = false;
    scrubbing = false;
  }

  private void scrub(float mx) {
    float share = Math.clamp((mx - trackX(0.0F)) / (trackX(1.0F) - trackX(0.0F)), 0.0F, 1.0F);
    clock = share * Rehearsal.loop(module);
  }
}
