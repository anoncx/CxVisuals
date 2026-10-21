package me.cxiy.cxvisuals.modules.idlecamera.camera.shots;

import java.util.Random;
import me.cxiy.cxvisuals.core.math.Vec3d;
import me.cxiy.cxvisuals.modules.idlecamera.camera.CameraPose;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Shot;
import me.cxiy.cxvisuals.modules.idlecamera.camera.ShotContext;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Subject;

public final class DollyShot implements Shot {
  private final double duration;
  private final double angle;
  private final double distance;
  private final double halfTravel;
  private final double fov;
  private final double tilt;

  public DollyShot(Random rng, double duration) {
    this.duration = duration;
    angle = ShotMath.range(rng, 0, 360);
    distance = ShotMath.range(rng, 2.0, 2.9);
    halfTravel = ShotMath.range(rng, 1.0, 1.6) * ShotMath.sign(rng);
    fov = ShotMath.range(rng, 45, 55);
    tilt = ShotMath.range(rng, 0.2, 0.5) * ShotMath.sign(rng);
  }

  @Override
  public CameraPose sample(ShotContext ctx, double t) {
    Subject subject = ctx.subject();
    double size = subject.size();
    double u = duration <= 0.0 ? 0.0 : t / duration;
    double slide = halfTravel * size * Math.min(ctx.motion().speed(), 1.5) * (2.0 * u - 1.0);
    double rad = Math.toRadians(ctx.heading() + angle);
    Vec3d base =
        ShotMath.around(ctx, angle, distance * size + ShotMath.STANDOFF, subject.height() * 0.25);
    Vec3d pos = base.add(Math.cos(rad) * slide, 0, Math.sin(rad) * slide);
    Vec3d target = subject.aim(0.29);
    return CameraPose.lookAt(pos, target, ctx.motion().maxRollDeg() * tilt, ShotMath.fov(ctx, fov));
  }
}
