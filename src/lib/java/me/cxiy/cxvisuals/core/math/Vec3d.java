package me.cxiy.cxvisuals.core.math;

public record Vec3d(double x, double y, double z) {
  public static final Vec3d ZERO = new Vec3d(0, 0, 0);

  public Vec3d add(Vec3d o) {
    return new Vec3d(x + o.x, y + o.y, z + o.z);
  }

  public Vec3d add(double dx, double dy, double dz) {
    return new Vec3d(x + dx, y + dy, z + dz);
  }

  public Vec3d sub(Vec3d o) {
    return new Vec3d(x - o.x, y - o.y, z - o.z);
  }

  public Vec3d scale(double s) {
    return new Vec3d(x * s, y * s, z * s);
  }

  public double length() {
    return Math.sqrt(x * x + y * y + z * z);
  }

  public double distance(Vec3d o) {
    double dx = x - o.x;
    double dy = y - o.y;
    double dz = z - o.z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  public Vec3d lerp(Vec3d o, double t) {
    return new Vec3d(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t);
  }

  public boolean isFinite() {
    return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
  }
}
