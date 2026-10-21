package me.cxiy.cxvisuals.gui.spatial;

public final class Spring {
  private static final float STEP = 1.0F / 120.0F;
  private static final int MAX_STEPS = 8;
  private static final float REST = 0.0005F;

  private final float stiffness;
  private final float damping;
  private float value;
  private float velocity;

  public Spring(float stiffness, float damping) {
    this.stiffness = stiffness;
    this.damping = damping;
  }

  public static Spring critical(float stiffness) {
    return new Spring(stiffness, 2.0F * (float) Math.sqrt(stiffness));
  }

  public void step(float dt, float target) {
    if (settled(target)) {
      value = target;
      velocity = 0.0F;
      return;
    }
    float remaining = Math.min(dt, STEP * MAX_STEPS);
    while (remaining > 0.0F) {
      float h = Math.min(STEP, remaining);
      velocity += (stiffness * (target - value) - damping * velocity) * h;
      value += velocity * h;
      remaining -= h;
    }
  }

  public float value() {
    return value;
  }

  public void snap(float next) {
    value = next;
    velocity = 0.0F;
  }

  public boolean settled(float target) {
    return Math.abs(target - value) < REST && Math.abs(velocity) < REST;
  }
}
