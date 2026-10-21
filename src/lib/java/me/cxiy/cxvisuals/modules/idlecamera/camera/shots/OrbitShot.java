package me.cxiy.cxvisuals.modules.idlecamera.camera.shots;

import java.util.Random;
import me.cxiy.cxvisuals.core.math.Vec3d;
import me.cxiy.cxvisuals.modules.idlecamera.camera.CameraPose;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Shot;
import me.cxiy.cxvisuals.modules.idlecamera.camera.ShotContext;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Subject;

public final class OrbitShot implements Shot {
  private final double startAngle;
  private final double rate;
  private final double radius;
  private final double heightRate;
  private final double heightPhase;
  private final double fov;
  private final double rollRate;
  private final double rollPhase;

  public OrbitShot(Random rng, double duration) {
    startAngle = ShotMath.range(rng, 0, 360);
    rate = ShotMath.range(rng, 10, 18) * ShotMath.sign(rng);
    radius = ShotMath.range(rng, 1.9, 3.0);
    heightRate = ShotMath.range(rng, 0.12, 0.22);
    heightPhase = ShotMath.range(rng, 0, Math.PI * 2);
    fov = ShotMath.range(rng, 48, 58);
    rollRate = ShotMath.range(rng, 0.15, 0.3);
    rollPhase = ShotMath.range(rng, 0, Math.PI * 2);
  }

  @Override
  public CameraPose sample(ShotContext ctx, double t) {
    Subject subject = ctx.subject();
    double size = subject.size();
    double time = t * ctx.motion().speed();
    double height = (0.9 + 0.6 * Math.sin(heightPhase + heightRate * time)) * size;
    Vec3d pos =
        ShotMath.around(ctx, startAngle + rate * time, radius * size + ShotMath.STANDOFF, height);
    Vec3d target = subject.aim(0.29);
    double roll = ctx.motion().maxRollDeg() * Math.sin(rollPhase + rollRate * time);
    return CameraPose.lookAt(pos, target, roll, ShotMath.fov(ctx, fov));
  }
}
