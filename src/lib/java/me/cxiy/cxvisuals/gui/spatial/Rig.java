package me.cxiy.cxvisuals.gui.spatial;

import me.cxiy.cxvisuals.gui.Canvas;

public final class Rig {
  public static final float DISTANCE = 1500.0F;

  private static final float MAX_ANGLE = 0.26F;
  private static final float SWAY_SHIFT = 16.0F;
  private static final float LOOK_RANGE = 200.0F;
  private static final float LOOK_SMOOTHING = 18.0F;
  private static final float LOOK_JUMP = 60.0F;
  private static final float PIXEL = 0.5F;
  private static final float ACTIVE_GAIN = 0.001F;
  private static final int DEPTH = 16;

  private final Spring tiltX = Spring.critical(160.0F);
  private final Spring tiltY = Spring.critical(160.0F);
  private final Spring swayX = new Spring(110.0F, 17.0F);
  private final Spring swayY = new Spring(110.0F, 17.0F);
  private final boolean[] pushed = new boolean[DEPTH];
  private int depth;
  private boolean enabled = true;
  private float strength = 0.5F;
  private float gate = 1.0F;
  private float targetTiltX;
  private float targetTiltY;
  private float targetSwayX;
  private float targetSwayY;
  private float lastYaw;
  private float lastPitch;
  private float rateX;
  private float rateY;
  private boolean looked;

  public void configure(boolean on, float amount) {
    enabled = on;
    strength = Math.max(0.0F, Math.min(1.0F, amount));
  }

  public void gate(float amount) {
    gate = Math.max(0.0F, Math.min(1.0F, amount));
  }

  public float gain() {
    return enabled ? strength * gate : 0.0F;
  }

  public boolean active() {
    return gain() > ACTIVE_GAIN;
  }

  public void feedPointer(float nx, float ny) {
    targetTiltX = clamp(nx);
    targetTiltY = clamp(ny);
  }

  public void feedLook(float yaw, float pitch, float dt) {
    if (looked && dt > 0.0F) {
      float turn = yaw - lastYaw;
      turn -= 360.0F * Math.round(turn / 360.0F);
      float nod = pitch - lastPitch;
      if (Math.abs(turn) > LOOK_JUMP || Math.abs(nod) > LOOK_JUMP) {
        turn = 0.0F;
        nod = 0.0F;
      }
      float blend = 1.0F - (float) Math.exp(-dt * LOOK_SMOOTHING);
      rateX += (turn / dt - rateX) * blend;
      rateY += (nod / dt - rateY) * blend;
      targetSwayX = soften(rateX / LOOK_RANGE);
      targetSwayY = soften(rateY / LOOK_RANGE);
    }
    lastYaw = yaw;
    lastPitch = pitch;
    looked = true;
  }

  public void update(float dt) {
    tiltX.step(dt, targetTiltX);
    tiltY.step(dt, targetTiltY);
    swayX.step(dt, targetSwayX);
    swayY.step(dt, targetSwayY);
  }

  public void rest() {
    targetTiltX = 0.0F;
    targetTiltY = 0.0F;
    targetSwayX = 0.0F;
    targetSwayY = 0.0F;
    tiltX.snap(0.0F);
    tiltY.snap(0.0F);
    swayX.snap(0.0F);
    swayY.snap(0.0F);
    rateX = 0.0F;
    rateY = 0.0F;
    looked = false;
  }

  public float tiltX() {
    return tiltX.value();
  }

  public float tiltY() {
    return tiltY.value();
  }

  public float swayX() {
    return swayX.value();
  }

  public float swayY() {
    return swayY.value();
  }

  public float yaw() {
    return tiltX.value() * MAX_ANGLE * gain();
  }

  public float pitch() {
    return -tiltY.value() * MAX_ANGLE * gain();
  }

  public float shiftX(float z) {
    return snap(-swayX.value() * SWAY_SHIFT * (1.0F + z) * gain());
  }

  public float shiftY(float z) {
    return snap(-swayY.value() * SWAY_SHIFT * (1.0F + z) * gain());
  }

  public float glint() {
    float x = tiltX.value();
    float y = tiltY.value();
    return Math.min(1.0F, (float) Math.sqrt(x * x + y * y)) * gain();
  }

  public void push(Canvas c, float z, float px, float py) {
    float x = active() ? shiftX(z) : 0.0F;
    float y = active() ? shiftY(z) : 0.0F;
    boolean on = x != 0.0F || y != 0.0F;
    pushed[depth++] = on;
    if (on) {
      c.pushAffine(1.0F, x, y, px, py);
    }
  }

  public void pop(Canvas c) {
    if (pushed[--depth]) {
      c.popTransform();
    }
  }

  private static float soften(float value) {
    return value != value ? 0.0F : (float) Math.tanh(value);
  }

  private static float snap(float value) {
    return Math.round(value / PIXEL) * PIXEL;
  }

  private static float clamp(float value) {
    return value != value ? 0.0F : Math.max(-1.0F, Math.min(1.0F, value));
  }
}
