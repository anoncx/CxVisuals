package me.cxiy.cxvisuals.modules.idlecamera.camera.shots;

import java.util.Random;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.core.math.Vec3d;
import me.cxiy.cxvisuals.modules.idlecamera.camera.CameraPose;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Shot;
import me.cxiy.cxvisuals.modules.idlecamera.camera.ShotContext;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Subject;

public final class PushInShot implements Shot {
  private static final double NEAR = 1.5;
  private static final double TRAVEL = 2.8;

  private final double duration;
  private final double angle;
  private final double drift;
  private final double lift;

  public PushInShot(Random rng, double duration) {
    this.duration = duration;
    angle = ShotMath.range(rng, 10, 70) * ShotMath.sign(rng);
    drift = ShotMath.range(rng, 4, 9) * ShotMath.sign(rng);
    lift = ShotMath.range(rng, 0.15, 0.45);
  }

  @Override
  public CameraPose sample(ShotContext ctx, double t) {
    Subject subject = ctx.subject();
    double size = subject.size();
    double p = Easing.smootherstep(ShotMath.progress(t, duration));
    double reach = TRAVEL * Math.min(ctx.motion().speed(), 1.5);
    double distance = (NEAR + reach * (1.0 - p)) * size + ShotMath.STANDOFF;
    Vec3d pos = ShotMath.around(ctx, angle + drift * p, distance, lift * size);
    Vec3d target = subject.aim(0.43);
    return CameraPose.lookAt(pos, target, 0.0, ShotMath.fov(ctx, 60.0 - 20.0 * p));
  }
}
