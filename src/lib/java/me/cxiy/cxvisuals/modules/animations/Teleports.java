package me.cxiy.cxvisuals.modules.animations;

import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.modules.animations.AnimationsModule.TeleportStyle;

public final class Teleports {
  public enum Kind {
    NONE,
    GLIDE,
    FADE
  }

  private Kind kind = Kind.NONE;
  private double dx;
  private double dy;
  private double dz;
  private float dYaw;
  private float dPitch;
  private float phase = 1.0F;
  private float remaining;

  public static Kind classify(
      double distance, double min, double max, TeleportStyle style, boolean dimension) {
    if (style == TeleportStyle.OFF || distance != distance) {
      return Kind.NONE;
    }
    if (dimension) {
      return Kind.FADE;
    }
    if (distance < min) {
      return Kind.NONE;
    }
    return style == TeleportStyle.FADE || distance > max ? Kind.FADE : Kind.GLIDE;
  }

  public void start(
      Kind next, double fromX, double fromY, double fromZ, float fromYaw, float fromPitch) {
    kind = next;
    phase = next == Kind.NONE ? 1.0F : 0.0F;
    remaining = next == Kind.NONE ? 0.0F : 1.0F;
    dx = fromX;
    dy = fromY;
    dz = fromZ;
    dYaw = fromYaw - 360.0F * Math.round(fromYaw / 360.0F);
    dPitch = fromPitch;
  }

  public void update(float dt, float seconds) {
    if (phase >= 1.0F) {
      return;
    }
    phase = seconds > 0.0F && dt >= 0.0F ? Math.min(1.0F, phase + dt / seconds) : 1.0F;
    remaining = phase >= 1.0F ? 0.0F : 1.0F - (float) Easing.smootherstep(phase);
    if (phase >= 1.0F) {
      kind = Kind.NONE;
    }
  }

  public void clear() {
    kind = Kind.NONE;
    phase = 1.0F;
    remaining = 0.0F;
  }

  public boolean gliding() {
    return kind == Kind.GLIDE;
  }

  public double offsetX() {
    return kind == Kind.GLIDE ? dx * remaining : 0.0;
  }

  public double offsetY() {
    return kind == Kind.GLIDE ? dy * remaining : 0.0;
  }

  public double offsetZ() {
    return kind == Kind.GLIDE ? dz * remaining : 0.0;
  }

  public float yawOffset() {
    return kind == Kind.GLIDE ? dYaw * remaining : 0.0F;
  }

  public float pitchOffset() {
    return kind == Kind.GLIDE ? dPitch * remaining : 0.0F;
  }

  public float veil() {
    return kind == Kind.FADE ? remaining : 0.0F;
  }
}
