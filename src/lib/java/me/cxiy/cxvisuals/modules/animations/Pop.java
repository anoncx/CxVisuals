package me.cxiy.cxvisuals.modules.animations;

public final class Pop {
  private float phase = 1.0F;

  public void trigger() {
    phase = 0.0F;
  }

  public void update(float dt, float seconds) {
    if (phase >= 1.0F) {
      return;
    }
    if (!(seconds > 0.0F)) {
      phase = 1.0F;
      return;
    }
    if (dt > 0.0F) {
      phase = Math.min(1.0F, phase + dt / seconds);
    }
  }

  public boolean active() {
    return phase < 1.0F;
  }

  public float scale(float amount) {
    return phase >= 1.0F ? 1.0F : 1.0F + amount * (float) Math.sin(Math.PI * phase);
  }
}
