package me.cxiy.cxvisuals.modules.hands;

import me.cxiy.cxvisuals.core.math.Easing;

public enum SwingAnimation {
  VANILLA("Vanilla"),
  SPIN("Spin"),
  FLIP("Flip"),
  SLASH("Slash"),
  STAB("Stab"),
  TAP("Tap");

  private static final float FULL_TURN = 360.0F;
  private static final float SLASH_RAISE = 0.22F;
  private static final float SLASH_START = 0.12F;
  private static final float SLASH_SPAN = 0.45F;
  private static final float SLASH_RELEASE = 0.62F;
  private static final float SLASH_WINDUP = -50.0F;
  private static final float SLASH_SWEEP = 118.0F;
  private static final float SLASH_FACE = -90.0F;

  public final String label;

  SwingAnimation(String label) {
    this.label = label;
  }

  public boolean replacesVanilla() {
    return this != VANILLA;
  }

  public void apply(PoseSink pose, float progress, int side, float intensity) {
    if (this == VANILLA || progress <= 0.0F || progress >= 1.0F) {
      return;
    }
    float t = progress;
    switch (this) {
      case SPIN -> {
        float lift = strike(t) * intensity;
        pose.translate(0.0F, 0.06F * lift, -0.12F * lift);
        pose.rotateY(side * FULL_TURN * turn(t));
      }
      case FLIP -> {
        float lift = strike(t) * intensity;
        pose.translate(0.0F, 0.08F * lift, -0.18F * lift);
        pose.rotateX(-FULL_TURN * turn(t));
      }
      case SLASH -> {
        float hold =
            turn(t / SLASH_RAISE) * (1.0F - turn((t - SLASH_RELEASE) / (1.0F - SLASH_RELEASE)));
        float across = turn((t - SLASH_START) / SLASH_SPAN);
        float arc = (float) Math.sin(Math.PI * across);
        float reach = hold * intensity;
        float bend = hold * Math.min(intensity, 1.0F);
        pose.translate(
            side * (0.14F - 0.66F * across) * reach,
            (0.16F + 0.22F * across + 0.05F * arc) * reach,
            (-0.05F - 0.2F * arc) * reach);
        pose.rotateX(-28.0F * bend);
        pose.rotateY(side * -22.0F * across * bend);
        pose.rotateZ(side * (SLASH_WINDUP + SLASH_SWEEP * across) * bend);
        pose.rotateY(side * SLASH_FACE * bend);
      }
      case STAB -> {
        float reach = strike(t) * intensity;
        pose.translate(side * -0.12F * reach, 0.12F * reach, -0.6F * reach);
        pose.rotateX(-70.0F * reach);
        pose.rotateY(side * 10.0F * reach);
      }
      case TAP -> {
        float nod = (float) Math.sin(Math.PI * t) * intensity;
        pose.translate(0.0F, -0.04F * nod, -0.05F * nod);
        pose.rotateX(-28.0F * nod);
      }
      default -> {}
    }
  }

  private static float strike(float t) {
    float rest = 1.0F - t;
    return (float) Math.sin(Math.PI * (1.0F - rest * rest));
  }

  private static float turn(float t) {
    return (float) Easing.smootherstep(t);
  }
}
