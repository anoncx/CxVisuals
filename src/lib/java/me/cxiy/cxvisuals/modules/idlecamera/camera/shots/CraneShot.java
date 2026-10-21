package me.cxiy.cxvisuals.modules.idlecamera.camera.shots;

import java.util.Random;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.core.math.Vec3d;
import me.cxiy.cxvisuals.modules.idlecamera.camera.CameraPose;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Shot;
import me.cxiy.cxvisuals.modules.idlecamera.camera.ShotContext;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Subject;

public final class CraneShot implements Shot {
  private final double duration;
  private final double angle;
  private final double drift;
  private final double fov;

  public CraneShot(Random rng, double duration) {
    this.duration = duration;
    angle = ShotMath.range(rng, 0, 360);
    drift = ShotMath.range(rng, 8, 14) * ShotMath.sign(rng);
    fov = ShotMath.range(rng, 50, 60);
  }

  @Override
  public CameraPose sample(ShotContext ctx, double t) {
    Subject subject = ctx.subject();
    double size = subject.size();
    double p = Easing.smoothstep(ShotMath.progress(t, duration));
    double reach = Math.min(ctx.motion().speed(), 1.5);
    double floor = 0.2 * size - subject.height() * 0.5;
    double height = floor + (2.8 * size * reach + subject.height() * 0.5) * p;
    double distance = (1.8 + 1.2 * reach * p) * size + ShotMath.STANDOFF;
    Vec3d pos = ShotMath.around(ctx, angle + drift * p, distance, height);
    return CameraPose.lookAt(pos, subject.anchor(), 0.0, ShotMath.fov(ctx, fov));
  }
}
