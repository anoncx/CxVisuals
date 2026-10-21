package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.settings.NumberSetting;

public final class Slider extends Widget {
  private static final float TRACK = 150.0F;
  private static final float MIN_TRACK = 72.0F;
  private static final float GAP = 12.0F;
  private static final float READOUT = 56.0F;
  private static final float KNOB = 16.0F;
  private static final float BAR = 4.0F;
  private static final int KNOB_COLOR = 0xFFFFFFFF;
  private static final int KNOB_SHADOW = 0x40000000;
  private static final int KNOB_RING = 0x1F000000;

  private final NumberSetting setting;
  private final Anim position;
  private final Anim grip = Anim.pop(0.0F);
  private float track = TRACK;
  private boolean dragging;

  public Slider(NumberSetting setting) {
    this.setting = setting;
    this.position = Anim.state((float) setting.fraction());
    this.w = TRACK + GAP + READOUT;
    this.h = 24.0F;
  }

  public void limit(float max) {
    track = Math.max(MIN_TRACK, Math.min(TRACK, max - GAP - READOUT));
    w = track + GAP + READOUT;
  }

  public float trackX() {
    return x;
  }

  public float trackWidth() {
    return track;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    float fraction = (float) setting.fraction();
    if (dragging) {
      position.snap(fraction);
    } else {
      position.set(fraction);
    }
    grip.set(dragging ? 1.0F : 0.0F);
    position.update(dt);
    grip.update(dt);

    float middle = y + h * 0.5F;
    float knobCenter = x + KNOB * 0.5F + (track - KNOB) * position.get();
    c.roundRect(x, middle - BAR * 0.5F, track, BAR, BAR * 0.5F, t.surface2());
    c.roundRect(x, middle - BAR * 0.5F, Math.max(BAR, knobCenter - x), BAR, BAR * 0.5F, t.ink());

    float knobX = knobCenter - KNOB * 0.5F;
    float knobY = middle - KNOB * 0.5F;
    c.pushTransform(1.0F + 0.14F * grip.get(), knobCenter, middle);
    c.shadow(knobX, knobY, KNOB, KNOB, KNOB * 0.5F, 4.0F, 1.0F, KNOB_SHADOW);
    Depth.knob(c, knobX, knobY, KNOB, KNOB, KNOB * 0.5F, KNOB_COLOR);
    c.roundRectBorder(knobX, knobY, KNOB, KNOB, KNOB * 0.5F, 1.0F, KNOB_RING);
    c.popTransform();

    String value = setting.format();
    float textX = x + w - c.textWidth(FontStyle.MONO, value);
    c.text(
        FontStyle.MONO,
        value,
        textX,
        centered(c, FontStyle.MONO, y, h),
        Theme.mix(t.grey(), t.ink(), grip.get()));
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (mx < x - KNOB * 0.5F || mx > x + track + KNOB * 0.5F || my < y || my >= y + h) {
      return false;
    }
    dragging = true;
    apply(mx);
    return true;
  }

  @Override
  public void mouseDrag(float mx, float my) {
    if (dragging) {
      apply(mx);
    }
  }

  @Override
  public void mouseUp(float mx, float my) {
    dragging = false;
  }

  private void apply(float mx) {
    double before = setting.get();
    setting.setFraction((mx - x - KNOB * 0.5F) / (track - KNOB));
    if (setting.get() != before) {
      Sound.play(Sound.Cue.STEP);
    }
  }
}
