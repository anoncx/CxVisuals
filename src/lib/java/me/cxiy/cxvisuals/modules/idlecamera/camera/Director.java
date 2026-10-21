package me.cxiy.cxvisuals.modules.idlecamera.camera;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import me.cxiy.cxvisuals.core.math.Angles;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.core.math.Vec3d;

public final class Director {
  public enum State {
    OFF,
    ENTERING,
    RUNNING,
    EXITING
  }

  public record Output(CameraPose pose, double weight) {}

  public static final double ENTER_TIME = 1.5;
  public static final double EXIT_TIME = 0.6;
  public static final double MAX_DT = 0.1;
  public static final double WALL_MARGIN = Avoidance.MARGIN;

  private static final Output IDLE = new Output(null, 0.0);
  private static final int MAX_REJECTIONS = 6;
  private static final double CUT_CHANCE = 0.3;
  private static final double MIN_SOLO_TIME = 4.0;

  private final DirectorConfig config;
  private final Random rng;
  private final Set<ShotType> sessionDisabled = new HashSet<>();
  private final List<ShotType> pool = new ArrayList<>();

  private State state = State.OFF;
  private double weight;
  private double phaseTime;
  private double phaseFrom;

  private ShotType type;
  private Shot shot;
  private double shotTime;
  private double shotLength;
  private double heading;
  private int shotCount;

  private Shot previous;
  private double previousTime;
  private double previousHeading;
  private double blendTime;
  private double blendLength;
  private double blendArc;

  private final Avoidance avoidance = new Avoidance();

  public Director(DirectorConfig config, Random rng) {
    this.config = config;
    this.rng = rng;
  }

  public State state() {
    return state;
  }

  public ShotType currentType() {
    return type;
  }

  public int shotCount() {
    return shotCount;
  }

  public boolean canStart() {
    for (int i = 0; i < ShotRegistry.ALL.size(); i++) {
      if (playable(ShotRegistry.ALL.get(i))) {
        return true;
      }
    }
    return false;
  }

  public void start() {
    if (state == State.ENTERING || state == State.RUNNING || !canStart()) {
      return;
    }
    phaseFrom = state == State.EXITING ? weight : 0.0;
    phaseTime = 0.0;
    state = State.ENTERING;
  }

  public void stop() {
    if (state != State.ENTERING && state != State.RUNNING) {
      return;
    }
    phaseFrom = weight;
    phaseTime = 0.0;
    state = State.EXITING;
  }

  public void abort() {
    state = State.OFF;
    weight = 0.0;
    type = null;
    shot = null;
    previous = null;
    avoidance.reset();
  }

  public void disableForSession(ShotType disabled) {
    sessionDisabled.add(disabled);
  }

  public Output update(double dt, Subject subject, WorldProbe probe) {
    if (state == State.OFF) {
      return IDLE;
    }
    double step = Math.min(Math.max(dt, 0.0), MAX_DT);
    advancePhase(step);
    if (state == State.OFF) {
      return IDLE;
    }
    MotionParams motion = config.motion();
    if (shot == null && !nextShot(subject, probe, motion, true)) {
      abort();
      return IDLE;
    }
    shotTime += step;
    if (previous != null) {
      previousTime += step;
      blendTime += step;
      if (blendTime >= blendLength) {
        previous = null;
      }
    }
    if (previous == null && shotTime >= shotLength && state != State.EXITING) {
      boolean cut = cutNext();
      if (!nextShot(subject, probe, motion, cut)) {
        abort();
        return IDLE;
      }
    }
    CameraPose pose = shot.sample(new ShotContext(subject, heading, motion), shotTime);
    if (previous != null) {
      CameraPose from =
          previous.sample(new ShotContext(subject, previousHeading, motion), previousTime);
      Vec3d anchor = subject.anchor();
      double raw = Angles.delta(azimuth(anchor, from.pos()), azimuth(anchor, pose.pos()));
      blendArc += Angles.delta(blendArc, raw);
      pose =
          blendAround(anchor, from, pose, blendArc, Easing.smootherstep(blendTime / blendLength));
    }
    if (config.avoidBlocks()) {
      pose = avoidance.resolve(pose, subject.anchor(), probe, step);
    }
    return new Output(pose, weight);
  }

  private void advancePhase(double step) {
    phaseTime += step;
    if (state == State.ENTERING) {
      weight = phaseFrom + (1.0 - phaseFrom) * Easing.smootherstep(phaseTime / ENTER_TIME);
      if (phaseTime >= ENTER_TIME) {
        weight = 1.0;
        state = State.RUNNING;
      }
    } else if (state == State.EXITING) {
      weight = phaseFrom * (1.0 - Easing.smootherstep(phaseTime / EXIT_TIME));
      if (phaseTime >= EXIT_TIME) {
        abort();
      }
    }
  }

  private boolean cutNext() {
    Transition transition = config.transition();
    if (config.blendTime() <= 0.0 || transition == Transition.CUT) {
      return true;
    }
    return transition == Transition.MIX && rng.nextDouble() < CUT_CHANCE;
  }

  private boolean playable(ShotType candidate) {
    return config.shotEnabled(candidate) && !sessionDisabled.contains(candidate);
  }

  private boolean nextShot(Subject subject, WorldProbe probe, MotionParams motion, boolean cut) {
    pool.clear();
    for (int i = 0; i < ShotRegistry.ALL.size(); i++) {
      ShotType candidate = ShotRegistry.ALL.get(i);
      if (playable(candidate)) {
        pool.add(candidate);
      }
    }
    if (pool.isEmpty()) {
      return false;
    }
    if (pool.size() > 1) {
      pool.remove(type);
    }
    double nextHeading = subject.facingYaw();
    ShotContext ctx = new ShotContext(subject, nextHeading, motion);
    ShotType chosenType = null;
    Shot chosen = null;
    double length = 0.0;
    for (int attempt = 0; attempt < MAX_REJECTIONS; attempt++) {
      chosenType = pick();
      length = config.shotLength() * (0.7 + 0.6 * rng.nextDouble());
      chosen = chosenType.create(rng, length);
      if (!config.avoidBlocks() || fits(chosen, chosenType, ctx, length, probe)) {
        break;
      }
      chosen = null;
    }
    if (chosen == null) {
      if (playable(ShotRegistry.CLOSE_UP)) {
        chosenType = ShotRegistry.CLOSE_UP;
      }
      length = config.shotLength() * (0.7 + 0.6 * rng.nextDouble());
      chosen = chosenType.create(rng, length);
    }
    if (cut || shot == null) {
      previous = null;
      avoidance.reset();
    } else {
      previous = shot;
      previousTime = shotTime;
      previousHeading = heading;
      blendTime = 0.0;
      CameraPose from = shot.sample(new ShotContext(subject, heading, motion), shotTime);
      CameraPose to = chosen.sample(ctx, 0.0);
      blendArc =
          Angles.delta(azimuth(subject.anchor(), from.pos()), azimuth(subject.anchor(), to.pos()));
      blendLength = config.blendTime() * Math.min(3.0, Math.max(1.0, Math.abs(blendArc) / 60.0));
      length = Math.max(length, blendLength + MIN_SOLO_TIME);
    }
    type = chosenType;
    shot = chosen;
    shotTime = 0.0;
    shotLength = length;
    heading = nextHeading;
    shotCount++;
    return true;
  }

  private ShotType pick() {
    double total = 0.0;
    for (int i = 0; i < pool.size(); i++) {
      total += Math.max(0.0, config.shotWeight(pool.get(i)));
    }
    if (total <= 0.0) {
      return pool.get(rng.nextInt(pool.size()));
    }
    double roll = rng.nextDouble() * total;
    for (int i = 0; i < pool.size(); i++) {
      roll -= Math.max(0.0, config.shotWeight(pool.get(i)));
      if (roll <= 0.0) {
        return pool.get(i);
      }
    }
    return pool.get(pool.size() - 1);
  }

  private static boolean fits(
      Shot candidate, ShotType candidateType, ShotContext ctx, double length, WorldProbe probe) {
    Vec3d anchor = ctx.subject().anchor();
    double need = candidateType.minClear() * ctx.subject().size();
    for (int i = 0; i <= 2; i++) {
      Vec3d pos = candidate.sample(ctx, length * 0.5 * i).pos();
      if (anchor.distance(pos) > need && probe.clearDistance(anchor, pos) < need) {
        return false;
      }
    }
    return true;
  }

  private static double azimuth(Vec3d anchor, Vec3d pos) {
    return Math.toDegrees(Math.atan2(-(pos.x() - anchor.x()), pos.z() - anchor.z()));
  }

  static CameraPose blendAround(Vec3d anchor, CameraPose a, CameraPose b, double arc, double w) {
    if (w <= 0.0) {
      return a;
    }
    if (w >= 1.0) {
      return b;
    }
    double ax = a.pos().x() - anchor.x();
    double az = a.pos().z() - anchor.z();
    double bx = b.pos().x() - anchor.x();
    double bz = b.pos().z() - anchor.z();
    double radius = lerp(Math.sqrt(ax * ax + az * az), Math.sqrt(bx * bx + bz * bz), w);
    double angle = Math.toRadians(azimuth(anchor, a.pos()) + arc * w);
    Vec3d pos =
        new Vec3d(
            anchor.x() - Math.sin(angle) * radius,
            lerp(a.pos().y(), b.pos().y(), w),
            anchor.z() + Math.cos(angle) * radius);
    CameraPose aimA = CameraPose.lookAt(a.pos(), anchor, 0.0, 0.0);
    CameraPose aimB = CameraPose.lookAt(b.pos(), anchor, 0.0, 0.0);
    CameraPose aim = CameraPose.lookAt(pos, anchor, 0.0, 0.0);
    double yawOffset =
        lerp(Angles.delta(aimA.yaw(), a.yaw()), Angles.delta(aimB.yaw(), b.yaw()), w);
    double pitchOffset = lerp(a.pitch() - aimA.pitch(), b.pitch() - aimB.pitch(), w);
    return new CameraPose(
        pos,
        Angles.wrap(aim.yaw() + yawOffset),
        aim.pitch() + pitchOffset,
        lerp(a.roll(), b.roll(), w),
        lerp(a.fov(), b.fov(), w));
  }

  private static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }
}
