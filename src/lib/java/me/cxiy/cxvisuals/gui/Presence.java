package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.module.ClientSettings.WindowMotion;

public final class Presence {
  private static final float PILL_WIDTH = 120.0F;
  private static final float PILL_HEIGHT = 40.0F;
  private static final float POP_FROM = 0.86F;
  private static final float SLIDE_FROM = 56.0F;
  private static final float PILL_SHARE = 0.6F;

  private float phase;
  private boolean closing;

  public void snap(float value) {
    phase = value;
  }

  public void step(WindowMotion motion, boolean open, float dt) {
    closing = !open;
    phase = Motion.advance(motion, phase, closing, dt);
  }

  public float phase() {
    return phase;
  }

  public boolean closing() {
    return closing;
  }

  public boolean gone() {
    return closing && phase <= 0.0F;
  }

  public float alpha(WindowMotion motion) {
    return motion == WindowMotion.EVAPORATE ? 1.0F : Motion.alpha(motion, phase);
  }

  public float scale(WindowMotion motion) {
    return motion == WindowMotion.POP ? Motion.pop(POP_FROM, phase, closing) : 1.0F;
  }

  public float rise(WindowMotion motion) {
    return motion == WindowMotion.SLIDE ? Motion.slide(SLIDE_FROM, phase) : 0.0F;
  }

  public float width(WindowMotion motion, float full) {
    if (motion != WindowMotion.MORPH) {
      return full;
    }
    float from = Math.min(PILL_WIDTH, full * PILL_SHARE);
    return from + (full - from) * Motion.foldWidth(phase, closing);
  }

  public float height(WindowMotion motion, float full) {
    if (motion != WindowMotion.MORPH) {
      return full;
    }
    float from = Math.min(PILL_HEIGHT, full * PILL_SHARE);
    return from + (full - from) * Motion.foldHeight(phase, closing);
  }

  public float erosion(WindowMotion motion) {
    return Motion.erosion(motion, phase);
  }

  public float content(WindowMotion motion) {
    return motion == WindowMotion.MORPH ? Motion.reveal(phase, closing) : 1.0F;
  }
}
