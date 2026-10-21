package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.math.Easing;

public final class Anim {
  public enum Curve {
    OVERSHOOT,
    SNAPPY,
    LINEAR
  }

  public static final float HOVER_SECONDS = 0.14F;
  public static final float PRESS_SECONDS = 0.12F;
  public static final float STATE_SECONDS = 0.20F;
  public static final float REVEAL_SECONDS = 0.24F;
  public static final float MOVE_SECONDS = 0.28F;
  public static final float POP_SECONDS = 0.32F;

  private final float seconds;
  private final Curve curve;
  private float from;
  private float target;
  private float value;
  private float elapsed;

  public Anim(float initial, float seconds, Curve curve) {
    this.seconds = seconds;
    this.curve = curve;
    this.from = initial;
    this.target = initial;
    this.value = initial;
    this.elapsed = seconds;
  }

  public static Anim hover(float initial) {
    return new Anim(initial, HOVER_SECONDS, Curve.SNAPPY);
  }

  public static Anim press(float initial) {
    return new Anim(initial, PRESS_SECONDS, Curve.SNAPPY);
  }

  public static Anim state(float initial) {
    return new Anim(initial, STATE_SECONDS, Curve.SNAPPY);
  }

  public static Anim reveal(float initial) {
    return new Anim(initial, REVEAL_SECONDS, Curve.SNAPPY);
  }

  public static Anim move(float initial) {
    return new Anim(initial, MOVE_SECONDS, Curve.SNAPPY);
  }

  public static Anim pop(float initial) {
    return new Anim(initial, POP_SECONDS, Curve.OVERSHOOT);
  }

  public void set(float next) {
    if (next == target) {
      return;
    }
    from = value;
    target = next;
    elapsed = 0.0F;
  }

  public void snap(float next) {
    from = next;
    target = next;
    value = next;
    elapsed = seconds;
  }

  public void update(float dt) {
    if (elapsed >= seconds) {
      return;
    }
    elapsed = Math.min(seconds, elapsed + dt);
    if (elapsed >= seconds) {
      value = target;
      return;
    }
    double t = elapsed / seconds;
    double eased =
        switch (curve) {
          case OVERSHOOT -> Easing.overshoot(t);
          case SNAPPY -> Easing.snappy(t);
          case LINEAR -> t;
        };
    value = from + (target - from) * (float) eased;
  }

  public float get() {
    return value;
  }

  public float target() {
    return target;
  }

  public boolean settled() {
    return elapsed >= seconds;
  }
}
