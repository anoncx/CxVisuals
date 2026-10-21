package me.cxiy.cxvisuals.modules.idlecamera.camera;

import me.cxiy.cxvisuals.core.math.Angles;
import me.cxiy.cxvisuals.core.math.Vec3d;

public final class Avoidance {
  public static final double MARGIN = 0.3;

  private static final double EPSILON = 1.0e-4;
  private static final double MIN_RATIO = 0.05;
  private static final double RATIO_RECOVERY = 0.15;
  private static final double DETOUR_TIME = 0.35;
  private static final double SEARCH_INTERVAL = 0.2;
  private static final double WORTHWHILE_GAIN = 0.5;
  private static final double MIN_ELEVATION = -80.0;
  private static final double MAX_ELEVATION = 85.0;
  private static final double[] DETOUR_YAW = {25, -25, 0, 50, -50, 35, -35, 0, 75, -75, 60, -60, 0};
  private static final double[] DETOUR_PITCH = {0, 0, 22, 0, 0, 25, 25, 45, 0, 0, 35, 35, 65};

  private double ratio = 1.0;
  private double detourYaw;
  private double detourPitch;
  private double targetYaw;
  private double targetPitch;
  private double searchTimer;
  private boolean snap = true;

  public void reset() {
    ratio = 1.0;
    detourYaw = 0.0;
    detourPitch = 0.0;
    targetYaw = 0.0;
    targetPitch = 0.0;
    searchTimer = 0.0;
    snap = true;
  }

  public static double reach(Vec3d from, Vec3d to, WorldProbe probe) {
    double full = from.distance(to);
    if (full < EPSILON) {
      return 1.0;
    }
    double clear = probe.clearDistance(from, to);
    if (clear >= full - EPSILON) {
      return 1.0;
    }
    return Math.max(0.0, Math.min(1.0, (clear - MARGIN) / full));
  }

  public CameraPose resolve(CameraPose pose, Vec3d anchor, WorldProbe probe, double dt) {
    Vec3d wanted = pose.pos();
    double full = anchor.distance(wanted);
    if (full < EPSILON) {
      return pose;
    }
    double clear = probe.clearDistance(anchor, wanted);
    boolean blocked = clear < full - EPSILON;
    searchTimer -= dt;

    if (!blocked) {
      targetYaw = 0.0;
      targetPitch = 0.0;
    } else {
      boolean detouring = targetYaw != 0.0 || targetPitch != 0.0;
      boolean holds = detouring && isClear(anchor, wanted, full, targetYaw, targetPitch, probe);
      if (!holds && (searchTimer <= 0.0 || snap)) {
        search(anchor, wanted, full, clear, probe);
        searchTimer = SEARCH_INTERVAL;
      }
    }

    if (snap) {
      detourYaw = targetYaw;
      detourPitch = targetPitch;
    } else {
      double follow = 1.0 - Math.exp(-dt / DETOUR_TIME);
      detourYaw += (targetYaw - detourYaw) * follow;
      detourPitch += (targetPitch - detourPitch) * follow;
      if (Math.abs(targetYaw - detourYaw) < 0.01 && Math.abs(targetPitch - detourPitch) < 0.01) {
        detourYaw = targetYaw;
        detourPitch = targetPitch;
      }
    }

    boolean straight = detourYaw == 0.0 && detourPitch == 0.0;
    Vec3d position = straight ? wanted : swing(anchor, wanted, full, detourYaw, detourPitch);
    double reachable = straight ? clear : probe.clearDistance(anchor, position);
    double goal =
        reachable < full - EPSILON
            ? Math.min(1.0, Math.max(MIN_RATIO, (reachable - MARGIN) / full))
            : 1.0;
    if (snap || goal < ratio) {
      ratio = goal;
    } else {
      ratio += (goal - ratio) * (1.0 - Math.exp(-dt / RATIO_RECOVERY));
      if (goal - ratio < 1.0e-4) {
        ratio = goal;
      }
    }
    snap = false;

    if (straight && ratio >= 1.0) {
      return pose;
    }
    Vec3d placed = ratio >= 1.0 ? position : anchor.add(position.sub(anchor).scale(ratio));
    if (straight) {
      return new CameraPose(placed, pose.yaw(), pose.pitch(), pose.roll(), pose.fov());
    }
    CameraPose before = CameraPose.lookAt(wanted, anchor, 0.0, 0.0);
    CameraPose after = CameraPose.lookAt(position, anchor, 0.0, 0.0);
    double yaw = Angles.wrap(after.yaw() + Angles.delta(before.yaw(), pose.yaw()));
    double pitch = after.pitch() + (pose.pitch() - before.pitch());
    return new CameraPose(
        placed, yaw, Math.max(-90.0, Math.min(90.0, pitch)), pose.roll(), pose.fov());
  }

  private void search(Vec3d anchor, Vec3d wanted, double full, double clear, WorldProbe probe) {
    double bestClear = clear + WORTHWHILE_GAIN;
    double bestYaw = 0.0;
    double bestPitch = 0.0;
    for (int i = 0; i < DETOUR_YAW.length; i++) {
      double reachable =
          probe.clearDistance(anchor, swing(anchor, wanted, full, DETOUR_YAW[i], DETOUR_PITCH[i]));
      if (reachable >= full - EPSILON) {
        targetYaw = DETOUR_YAW[i];
        targetPitch = DETOUR_PITCH[i];
        return;
      }
      if (reachable > bestClear) {
        bestClear = reachable;
        bestYaw = DETOUR_YAW[i];
        bestPitch = DETOUR_PITCH[i];
      }
    }
    targetYaw = bestYaw;
    targetPitch = bestPitch;
  }

  private static boolean isClear(
      Vec3d anchor, Vec3d wanted, double full, double yaw, double pitch, WorldProbe probe) {
    return probe.clearDistance(anchor, swing(anchor, wanted, full, yaw, pitch)) >= full - EPSILON;
  }

  private static Vec3d swing(
      Vec3d anchor, Vec3d wanted, double full, double yawDeg, double pitchDeg) {
    double dx = wanted.x() - anchor.x();
    double dy = wanted.y() - anchor.y();
    double dz = wanted.z() - anchor.z();
    double azimuth = Math.atan2(-dx, dz) + Math.toRadians(yawDeg);
    double elevation =
        Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, dy / full)))) + pitchDeg;
    double lifted = Math.toRadians(Math.max(MIN_ELEVATION, Math.min(MAX_ELEVATION, elevation)));
    double flat = Math.cos(lifted) * full;
    return new Vec3d(
        anchor.x() - Math.sin(azimuth) * flat,
        anchor.y() + Math.sin(lifted) * full,
        anchor.z() + Math.cos(azimuth) * flat);
  }
}
