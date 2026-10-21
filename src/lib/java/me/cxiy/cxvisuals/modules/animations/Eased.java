package me.cxiy.cxvisuals.modules.animations;

public final class Eased {
  private static final float REST = 0.01F;

  private final float rate;
  private final float hold;
  private final float drain;
  private float value;
  private float ghost;
  private float held;
  private boolean primed;

  public Eased(float rate, float hold, float drain) {
    this.rate = rate;
    this.hold = hold;
    this.drain = drain;
  }

  public void snap(float target) {
    value = target;
    ghost = target;
    held = 0.0F;
    primed = true;
  }

  public void update(float dt, float target) {
    if (!primed) {
      snap(target);
      return;
    }
    if (!(dt > 0.0F)) {
      return;
    }
    float before = value;
    value += (target - value) * (1.0F - (float) Math.exp(-dt * rate));
    if (Math.abs(target - value) < REST) {
      value = target;
    }
    if (value < before && ghost <= before) {
      held = hold;
    }
    if (ghost < value) {
      ghost = value;
    } else if (held > 0.0F) {
      held -= dt;
    } else if (ghost > value) {
      ghost = Math.max(value, ghost - drain * dt);
    }
  }

  public float value() {
    return value;
  }

  public float ghost() {
    return ghost;
  }
}
