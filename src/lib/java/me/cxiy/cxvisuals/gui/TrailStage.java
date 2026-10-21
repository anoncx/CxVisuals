package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;
import me.cxiy.cxvisuals.modules.shaders.trails.Runner;

public final class TrailStage extends Widget {
  private static final String HINT = Lang.key("Drag to orbit");
  private static final String OFF = Lang.key("Off in game");
  private static final float HEIGHT = 196.0F;
  private static final float INSET = 12.0F;
  private static final float TAG_HEIGHT = 20.0F;
  private static final float TITLE_SCALE = 0.62F;
  private static final float DRIFT = 0.12F;
  private static final float ORBIT = 0.011F;
  private static final float TILT = 0.006F;
  private static final float LOWEST_TILT = 0.16F;
  private static final float HIGHEST_TILT = 1.1F;
  private static final float GLIDE = 4.0F;
  private static final int INK = 0xFFFFFFFF;
  private static final int STAGE = 0xFF050506;
  private static final int SEGMENTS = 48;

  private final ShadersModule module;
  private final Anim hover = Anim.state(0.0F);
  private final float[] xs = new float[SEGMENTS + 1];
  private final float[] ys = new float[SEGMENTS + 1];
  private float clock;
  private float yaw = 0.55F;
  private float pitch = 0.5F;
  private float spin;
  private float lastX;
  private float lastY;
  private boolean dragging;

  public TrailStage(ShadersModule module) {
    this.module = module;
    this.h = HEIGHT;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    advance(dt);
    hover.set(contains(mouseX, mouseY) || dragging ? 1.0F : 0.0F);
    hover.update(dt);
    float radius = Math.max(6.0F, t.radius() - 4.0F);
    if (!c.stage(Canvas.TRAILS, x, y, w, h, yaw, pitch, clock, radius)) {
      fallback(c, radius);
    }
    overlay(c, t);
  }

  private void advance(float dt) {
    clock = (clock + dt) % Runner.wrap();
    if (!dragging) {
      yaw += spin * dt + DRIFT * dt * (1.0F - hover.get() * 0.8F);
      spin *= (float) Math.exp(-GLIDE * dt);
    }
  }

  private void fallback(Canvas c, float radius) {
    c.roundRect(x, y, w, h, radius, STAGE);
    float cx = x + w * 0.5F;
    float cy = y + h * 0.5F;
    float scale = Math.min(w, h) * 0.2F;
    for (int i = 0; i <= SEGMENTS; i++) {
      float angle = clock * 3.0F - i * 0.05F;
      xs[i] = cx + (float) Math.cos(angle) * scale * 1.6F;
      ys[i] = cy + (float) Math.sin(angle) * scale * 0.7F;
    }
    int first = module.trailPalette.get().first() | 0xFF000000;
    c.stroke(xs, ys, SEGMENTS + 1, 8.0F, 6.0F, Theme.withAlpha(first, 0.25F));
    c.stroke(xs, ys, SEGMENTS + 1, 2.0F, 1.0F, first);
  }

  private void overlay(Canvas c, Theme t) {
    String name =
        module.trailStyle.labels().get(module.trailStyle.index()).toUpperCase(Lang.locale());
    float titleX = x + INSET;
    float titleY = y + INSET - 2.0F;
    c.pushTransform(TITLE_SCALE, titleX, titleY);
    c.text(FontStyle.DOTS, name, titleX, titleY, Theme.withAlpha(INK, 0.92F));
    c.popTransform();
    String detail = format(module.trailSeconds()) + " s";
    c.text(
        FontStyle.CAPTION,
        detail,
        titleX,
        titleY + c.lineHeight(FontStyle.DOTS) * TITLE_SCALE + 2.0F,
        Theme.withAlpha(INK, 0.5F));

    if (!module.trailsLive()) {
      String text = Lang.tr(OFF);
      float textW = c.textWidth(FontStyle.CAPTION, text);
      float tagW = textW + 16.0F;
      float tagX = x + w - INSET - tagW;
      float tagY = y + h - TAG_HEIGHT - INSET;
      c.pushAlpha(0.85F);
      c.roundRect(tagX, tagY, tagW, TAG_HEIGHT, TAG_HEIGHT * 0.5F, 0x8C000000);
      c.roundRectBorder(
          tagX, tagY, tagW, TAG_HEIGHT, TAG_HEIGHT * 0.5F, 1.0F, Theme.withAlpha(INK, 0.14F));
      c.text(
          FontStyle.CAPTION,
          text,
          tagX + 8.0F,
          centered(c, FontStyle.CAPTION, tagY, TAG_HEIGHT),
          Theme.withAlpha(INK, 0.9F));
      c.popAlpha();
    }

    float hint = hover.get();
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
    lastX = mx;
    lastY = my;
    dragging = true;
    spin = 0.0F;
    return true;
  }

  @Override
  public void mouseDrag(float mx, float my) {
    if (!dragging) {
      return;
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
    dragging = false;
  }
}
