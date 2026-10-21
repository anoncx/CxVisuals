package me.cxiy.cxvisuals.modules.idlecamera.camera;

import me.cxiy.cxvisuals.core.math.Vec3d;

public record Subject(
    Vec3d anchor, Vec3d velocity, double facingYaw, double width, double height, Vec3d head) {
  public static final double MIN_SIZE = 0.6;

  private static final double UPRIGHT_HEAD = 0.35;
  private static final double LYING_HEAD = 0.87;
  private static final double CROWN_ABOVE_EYE = 0.2;

  public Subject(Vec3d anchor, Vec3d velocity, double facingYaw, double width, double height) {
    this(anchor, velocity, facingYaw, width, height, anchor.add(0, height * UPRIGHT_HEAD, 0));
  }

  public static Subject upright(
      Vec3d base, Vec3d eye, Vec3d velocity, double facingYaw, double width) {
    double height = Math.max(CROWN_ABOVE_EYE, eye.y() + CROWN_ABOVE_EYE - base.y());
    return new Subject(base.add(0, height * 0.5, 0), velocity, facingYaw, width, height, eye);
  }

  public static Subject lying(
      Vec3d origin,
      Vec3d velocity,
      double facingYaw,
      double bodyYaw,
      double bodyPitch,
      double length,
      double thickness,
      double start,
      double lift) {
    double yaw = Math.toRadians(bodyYaw);
    double pitch = Math.toRadians(bodyPitch);
    double flat = Math.cos(pitch);
    double dx = -Math.sin(yaw) * flat;
    double dy = -Math.sin(pitch);
    double dz = Math.cos(yaw) * flat;
    double middle = start + length * 0.5;
    double crown = start + length * LYING_HEAD;
    Vec3d centre = origin.add(dx * middle, dy * middle + lift, dz * middle);
    Vec3d head = origin.add(dx * crown, dy * crown + lift, dz * crown);
    double height = Math.max(thickness, length * Math.abs(dy));
    return new Subject(centre, velocity, facingYaw, length, height, head);
  }

  public Vec3d aim(double towardHead) {
    return anchor.lerp(head, towardHead);
  }

  public double size() {
    return Math.max(MIN_SIZE, Math.max(width, height));
  }

  public Vec3d feet() {
    return anchor.add(0, -height * 0.5, 0);
  }
}
