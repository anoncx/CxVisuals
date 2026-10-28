package me.cxiy.cxvisuals.modules.map.share;

public final class Smoother {
  private static final double HALF_LIFE_SECONDS = 0.07;
  private static final double SNAP_DISTANCE = 24.0;

  private double[] at;

  public double[] step(double[] target, double seconds) {
    if (at == null || distance(at, target) > SNAP_DISTANCE) {
      at = target.clone();
      return at.clone();
    }
    double keep = Math.pow(0.5, Math.max(0.0, seconds) / HALF_LIFE_SECONDS);
    for (int i = 0; i < at.length; i++) {
      at[i] = target[i] + (at[i] - target[i]) * keep;
    }
    return at.clone();
  }

  private static double distance(double[] a, double[] b) {
    double dx = a[0] - b[0];
    double dy = a[1] - b[1];
    double dz = a[2] - b[2];
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }
}
