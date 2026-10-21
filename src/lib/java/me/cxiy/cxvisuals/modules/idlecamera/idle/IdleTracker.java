package me.cxiy.cxvisuals.modules.idlecamera.idle;

public final class IdleTracker {
  public static final double MOUSE_DEADZONE = 2.0;

  private double idle;
  private boolean activity;
  private boolean hasReference;
  private double referenceX;
  private double referenceY;

  public void poke() {
    idle = 0.0;
    activity = true;
  }

  public void reset() {
    idle = 0.0;
  }

  public void mouseMoved(double x, double y) {
    if (!hasReference) {
      hasReference = true;
      referenceX = x;
      referenceY = y;
      return;
    }
    double dx = x - referenceX;
    double dy = y - referenceY;
    if (dx * dx + dy * dy > MOUSE_DEADZONE * MOUSE_DEADZONE) {
      referenceX = x;
      referenceY = y;
      poke();
    }
  }

  public void advance(double dt) {
    idle += dt;
  }

  public double idleSeconds() {
    return idle;
  }

  public boolean consumeActivity() {
    boolean was = activity;
    activity = false;
    return was;
  }
}
