package me.cxiy.cxvisuals.modules.idlecamera.camera.shots;

import java.util.Random;
import me.cxiy.cxvisuals.core.math.Vec3d;
import me.cxiy.cxvisuals.modules.idlecamera.camera.ShotContext;

public final class ShotMath {
  public static final double STANDOFF = 0.5;

  private ShotMath() {}

  public static Vec3d around(
      ShotContext ctx, double angleDeg, double radius, double heightAboveAnchor) {
    return around(ctx.subject().anchor(), ctx.heading() + angleDeg, radius, heightAboveAnchor);
  }

  public static Vec3d around(
      Vec3d centre, double worldAngleDeg, double radius, double heightAbove) {
    double rad = Math.toRadians(worldAngleDeg);
    return new Vec3d(
        centre.x() - Math.sin(rad) * radius,
        centre.y() + heightAbove,
        centre.z() + Math.cos(rad) * radius);
  }

  public static double range(Random rng, double lo, double hi) {
    return lo + (hi - lo) * rng.nextDouble();
  }

  public static double sign(Random rng) {
    return rng.nextBoolean() ? 1.0 : -1.0;
  }

  public static double fov(ShotContext ctx, double shotFov) {
    return ctx.motion().fovChanges() ? shotFov : ctx.motion().baseFov();
  }

  public static double progress(double t, double duration) {
    return duration <= 0.0 ? 1.0 : Math.min(1.0, Math.max(0.0, t / duration));
  }
}
