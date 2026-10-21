package me.cxiy.cxvisuals.modules.idlecamera.camera.shots;

import java.util.Random;
import me.cxiy.cxvisuals.core.math.Vec3d;
import me.cxiy.cxvisuals.modules.idlecamera.camera.CameraPose;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Shot;
import me.cxiy.cxvisuals.modules.idlecamera.camera.ShotContext;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Subject;

public final class HeroShot implements Shot {
  private final double duration;
  private final double startAngle;
  private final double arc;
  private final double distance;
  private final double lift;
  private final double fov;
  private final double tilt;

  public HeroShot(Random rng, double duration) {
    this.duration = duration;
    startAngle = ShotMath.range(rng, 25, 50) * ShotMath.sign(rng);
    arc = ShotMath.range(rng, 20, 40) * ShotMath.sign(rng);
    distance = ShotMath.range(rng, 1.5, 2.2);
    lift = ShotMath.range(rng, 0.1, 0.3);
    fov = ShotMath.range(rng, 40, 50);
    tilt = ShotMath.range(rng, 0.6, 1.0) * ShotMath.sign(rng);
  }

  @Override
  public CameraPose sample(ShotContext ctx, double t) {
    Subject subject = ctx.subject();
    double size = subject.size();
    double travel = duration <= 0.0 ? 0.0 : t / duration * ctx.motion().speed();
    double height = lift * size - subject.height() * 0.5;
    Vec3d pos =
        ShotMath.around(
            ctx, startAngle + arc * travel, distance * size + ShotMath.STANDOFF, height);
    Vec3d target = subject.aim(0.57);
    return CameraPose.lookAt(pos, target, ctx.motion().maxRollDeg() * tilt, ShotMath.fov(ctx, fov));
  }
}
