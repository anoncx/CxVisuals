package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

public final class KeystrokesElement extends HudElement {
  private static final int FORWARD = 0;
  private static final int LEFT = 1;
  private static final int BACK = 2;
  private static final int RIGHT = 3;
  private static final int ATTACK = 4;
  private static final int USE = 5;
  private static final int JUMP = 6;
  private static final int KEYS = 7;
  private static final String[] LABELS = {"W", "A", "S", "D", Lang.key("LMB"), Lang.key("RMB"), ""};
  private static final float KEY = 30.0F;
  private static final float GAP = 4.0F;
  private static final float MOUSE = 24.0F;
  private static final float SPACE = 18.0F;
  private static final float RADIUS = 9.0F;
  private static final float WIDTH = KEY * 3.0F + GAP * 2.0F;
  private static final float DASH = 26.0F;

  public final BoolSetting mouse;
  public final BoolSetting space;

  private final Anim[] press = new Anim[KEYS];
  private final float[] labelWidths = new float[KEYS];
  private boolean labelled;

  public KeystrokesElement(Settings settings) {
    super(
        settings,
        "keystrokes",
        "Keystrokes",
        Category.INFO,
        false,
        Anchor.BOTTOM_RIGHT,
        -10.0,
        -10.0);
    this.mouse = group().bool("mouse", "Mouse buttons", true);
    this.space = group().bool("space", "Jump bar", true);
    for (int i = 0; i < KEYS; i++) {
      press[i] = Anim.state(0.0F);
    }
  }

  @Override
  public float radius() {
    return RADIUS;
  }

  @Override
  public void measure(Canvas c, HudData data) {
    if (!labelled || reworded()) {
      labelled = true;
      for (int i = 0; i < KEYS; i++) {
        labelWidths[i] =
            c.textWidth(i < ATTACK ? FontStyle.LABEL : FontStyle.CAPTION, Lang.tr(LABELS[i]));
      }
    }
    w = WIDTH;
    h = KEY * 2.0F + GAP + (mouse.get() ? MOUSE + GAP : 0.0F) + (space.get() ? SPACE + GAP : 0.0F);
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    key(c, t, FORWARD, data.forward, x + KEY + GAP, y, KEY, KEY, dt, data.still);
    float row = y + KEY + GAP;
    key(c, t, LEFT, data.left, x, row, KEY, KEY, dt, data.still);
    key(c, t, BACK, data.back, x + KEY + GAP, row, KEY, KEY, dt, data.still);
    key(c, t, RIGHT, data.right, x + (KEY + GAP) * 2.0F, row, KEY, KEY, dt, data.still);
    row += KEY + GAP;
    if (mouse.get()) {
      float half = (WIDTH - GAP) * 0.5F;
      key(c, t, ATTACK, data.attack, x, row, half, MOUSE, dt, data.still);
      key(c, t, USE, data.use, x + half + GAP, row, half, MOUSE, dt, data.still);
      row += MOUSE + GAP;
    }
    if (space.get()) {
      key(c, t, JUMP, data.jump, x, row, WIDTH, SPACE, dt, data.still);
    }
  }

  private void key(
      Canvas c,
      Theme t,
      int index,
      boolean down,
      float x,
      float y,
      float width,
      float height,
      float dt,
      boolean still) {
    Anim anim = press[index];
    if (down || still) {
      anim.snap(down ? 1.0F : 0.0F);
    } else {
      anim.set(0.0F);
      anim.update(dt);
    }
    float amount = Math.max(0.0F, Math.min(1.0F, anim.get()));
    float radius = Math.min(RADIUS, height * 0.5F);
    c.pushTransform(1.0F - 0.06F * amount, x + width * 0.5F, y + height * 0.5F);
    HudPaint.plate(c, t, x, y, width, height, radius);
    if (amount > 0.0F) {
      c.roundRect(x, y, width, height, radius, Theme.withAlpha(t.ink(), amount));
    }
    int ink = Theme.mix(t.ink(), t.knobOn(), amount);
    c.pushContrast(Theme.mix(t.paper(), t.ink(), amount));
    if (index == JUMP) {
      c.roundRect(x + (width - DASH) * 0.5F, y + (height - 3.0F) * 0.5F, DASH, 3.0F, 1.5F, ink);
    } else {
      FontStyle font = index < ATTACK ? FontStyle.LABEL : FontStyle.CAPTION;
      c.text(
          font,
          Lang.tr(LABELS[index]),
          x + (width - labelWidths[index]) * 0.5F,
          HudPaint.centred(c, font, y, height),
          ink);
    }
    c.popContrast();
    c.popTransform();
  }
}
