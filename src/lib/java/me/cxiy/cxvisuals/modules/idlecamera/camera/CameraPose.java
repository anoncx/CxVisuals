package me.cxiy.cxvisuals.modules.idlecamera.camera;

import me.cxiy.cxvisuals.core.math.Angles;
import me.cxiy.cxvisuals.core.math.Vec3d;

public record CameraPose(Vec3d pos, double yaw, double pitch, double roll, double fov) {
  public static CameraPose lookAt(Vec3d pos, Vec3d target, double roll, double fov) {
    double dx = target.x() - pos.x();
    double dy = target.y() - pos.y();
    double dz = target.z() - pos.z();
    double flat = Math.sqrt(dx * dx + dz * dz);
    if (flat < 1e-9 && Math.abs(dy) < 1e-9) {
      return new CameraPose(pos, 0, 0, roll, fov);
    }
    double yaw = flat < 1e-9 ? 0 : Math.toDegrees(Math.atan2(-dx, dz));
    double pitch = -Math.toDegrees(Math.atan2(dy, flat));
    return new CameraPose(pos, yaw, pitch, roll, fov);
  }

  public static CameraPose blend(CameraPose a, CameraPose b, double w) {
    if (w <= 0.0) {
      return a;
    }
    if (w >= 1.0) {
      return b;
    }
    return new CameraPose(
        a.pos.lerp(b.pos, w),
        Angles.lerp(a.yaw, b.yaw, w),
        a.pitch + (b.pitch - a.pitch) * w,
        a.roll + (b.roll - a.roll) * w,
        a.fov + (b.fov - a.fov) * w);
  }

  public boolean isFinite() {
    return pos.isFinite()
        && Double.isFinite(yaw)
        && Double.isFinite(pitch)
        && Double.isFinite(roll)
        && Double.isFinite(fov);
  }
}
