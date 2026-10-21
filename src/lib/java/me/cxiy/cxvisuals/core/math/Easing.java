package me.cxiy.cxvisuals.core.math;

public final class Easing {
  private Easing() {}

  public static double clamp01(double t) {
    return t < 0.0 ? 0.0 : Math.min(t, 1.0);
  }

  public static double smoothstep(double t) {
    double c = clamp01(t);
    return c * c * (3.0 - 2.0 * c);
  }

  public static double smootherstep(double t) {
    double c = clamp01(t);
    return c * c * c * (c * (c * 6.0 - 15.0) + 10.0);
  }

  public static double overshoot(double t) {
    return bezier(0.34, 1.56, 0.64, 1.0, t);
  }

  public static double spring(double t) {
    return bezier(0.3, 1.3, 0.55, 1.0, t);
  }

  public static double snappy(double t) {
    return bezier(0.2, 0.8, 0.2, 1.0, t);
  }

  public static double bezier(double x1, double y1, double x2, double y2, double t) {
    double x = clamp01(t);
    if (x <= 0.0) {
      return 0.0;
    }
    if (x >= 1.0) {
      return 1.0;
    }
    double s = solve(x1, x2, x);
    return axis(y1, y2, s);
  }

  private static double axis(double p1, double p2, double s) {
    double inv = 1.0 - s;
    return 3.0 * inv * inv * s * p1 + 3.0 * inv * s * s * p2 + s * s * s;
  }

  private static double slope(double p1, double p2, double s) {
    double inv = 1.0 - s;
    return 3.0 * inv * inv * p1 + 6.0 * inv * s * (p2 - p1) + 3.0 * s * s * (1.0 - p2);
  }

  private static double solve(double x1, double x2, double x) {
    double s = x;
    for (int i = 0; i < 8; i++) {
      double error = axis(x1, x2, s) - x;
      if (Math.abs(error) < 1e-7) {
        return s;
      }
      double d = slope(x1, x2, s);
      if (Math.abs(d) < 1e-6) {
        break;
      }
      s -= error / d;
    }
    double lo = 0.0;
    double hi = 1.0;
    s = x;
    for (int i = 0; i < 32; i++) {
      double value = axis(x1, x2, s);
      if (Math.abs(value - x) < 1e-7) {
        break;
      }
      if (value < x) {
        lo = s;
      } else {
        hi = s;
      }
      s = (lo + hi) * 0.5;
    }
    return s;
  }
}
