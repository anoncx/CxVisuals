package me.cxiy.cxvisuals.modules.idlecamera.camera.shots;

import java.util.Random;
import me.cxiy.cxvisuals.core.math.Vec3d;
import me.cxiy.cxvisuals.modules.idlecamera.camera.CameraPose;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Shot;
import me.cxiy.cxvisuals.modules.idlecamera.camera.ShotContext;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Subject;

public final class CloseUpShot implements Shot {
  private final double duration;
  private final double startAngle;
  private final double arc;
  private final double distance;
  private final double fov;
  private final double rollRate;
  private final double rollPhase;

  public CloseUpShot(Random rng, double duration) {
    this.duration = duration;
    startAngle = ShotMath.range(rng, 15, 45) * ShotMath.sign(rng);
    arc = ShotMath.range(rng, 10, 15) * ShotMath.sign(rng);
    distance = ShotMath.range(rng, 1.2, 1.6);
    fov = ShotMath.range(rng, 25, 35);
    rollRate = ShotMath.range(rng, 0.2, 0.35);
    rollPhase = ShotMath.range(rng, 0, Math.PI * 2);
  }

  @Override
  public CameraPose sample(ShotContext ctx, double t) {
    Subject subject = ctx.subject();
    double size = subject.size();
    double speed = ctx.motion().speed();
    double travel = duration <= 0.0 ? 0.0 : t / duration * speed;
    Vec3d pos =
        ShotMath.around(
            subject.head(),
            ctx.heading() + startAngle + arc * travel,
            distance * size + ShotMath.STANDOFF,
            0.05 * size);
    double roll = 1.5 * ctx.motion().maxRollDeg() * Math.sin(rollPhase + rollRate * t * speed);
    return CameraPose.lookAt(pos, subject.head(), roll, ShotMath.fov(ctx, fov));
  }
}
