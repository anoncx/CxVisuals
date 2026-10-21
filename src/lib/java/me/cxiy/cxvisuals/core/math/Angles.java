package me.cxiy.cxvisuals.core.math;

public final class Angles {
  private Angles() {}

  public static double wrap(double deg) {
    double w = deg % 360.0;
    if (w > 180.0) {
      w -= 360.0;
    } else if (w <= -180.0) {
      w += 360.0;
    }
    return w;
  }

  public static double delta(double from, double to) {
    double d = (to - from) % 360.0;
    if (d > 180.0) {
      d -= 360.0;
    } else if (d < -180.0) {
      d += 360.0;
    }
    return d;
  }

  public static double lerp(double a, double b, double t) {
    return wrap(a + delta(a, b) * t);
  }
}
