package me.cxiy.cxvisuals.modules.animations;

import me.cxiy.cxvisuals.gui.spatial.Spring;

public final class SlotGlide {
  private static final float STIFFNESS = 420.0F;
  private static final float DAMPING = 30.0F;
  private static final float LOW = -0.5F;
  private static final float HIGH = 8.5F;

  private final Spring spring = new Spring(STIFFNESS, DAMPING);
  private int selected = -1;
  private boolean changed;

  public void update(float dt, int slot, boolean glide) {
    changed = selected != -1 && slot != selected;
    if (selected == -1 || !glide) {
      spring.snap(slot);
    } else {
      spring.step(dt, slot);
    }
    selected = slot;
  }

  public float position() {
    float value = spring.value();
    return value < LOW ? LOW : Math.min(value, HIGH);
  }

  public boolean changed() {
    return changed;
  }
}
