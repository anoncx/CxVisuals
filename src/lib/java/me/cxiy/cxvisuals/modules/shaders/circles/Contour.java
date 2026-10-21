package me.cxiy.cxvisuals.modules.shaders.circles;

import java.util.Arrays;

public final class Contour {
  public static final int KNOTS = 48;
  public static final float LOWEST = 0.15F;
  public static final float HIGHEST = 1.6F;

  private static final float TURN = (float) (Math.PI * 2.0);
  private static final int TRACE = 512;

  public enum Form {
    CIRCLE,
    HEXAGON,
    STAR,
    HEART,
    FLOWER,
    SQUARE;

    public Contour contour() {
      return switch (this) {
        case CIRCLE -> new Contour(filled(1.0F));
        case HEXAGON -> polygon(6, 1.0F, 1.0F, 1.0F / 12.0F);
        case STAR -> polygon(5, 1.0F, 0.46F, 0.0F);
        case HEART -> heart();
        case FLOWER -> flower();
        case SQUARE -> polygon(4, 1.0F, 1.0F, 0.125F);
      };
    }
  }

  private final float[] knots;
  private final int hash;

  private Contour(float[] knots) {
    this.knots = knots;
    this.hash = Arrays.hashCode(knots);
  }

  public static Contour of(float[] radii) {
    if (radii.length != KNOTS) {
      throw new IllegalArgumentException("a contour has " + KNOTS + " knots, not " + radii.length);
    }
    float[] knots = new float[KNOTS];
    for (int i = 0; i < KNOTS; i++) {
      knots[i] = limit(radii[i]);
    }
    return new Contour(knots);
  }

  public static Contour circle() {
    return Form.CIRCLE.contour();
  }

  public float knot(int index) {
    return knots[Math.floorMod(index, KNOTS)];
  }

  public float[] knots() {
    return knots.clone();
  }

  public float peak() {
    float peak = 0.0F;
    for (float knot : knots) {
      peak = Math.max(peak, knot);
    }
    return peak;
  }

  public float at(float turn) {
    float position = (turn - (float) Math.floor(turn)) * KNOTS;
    int base = (int) position;
    float f = position - base;
    float a = knot(base - 1);
    float b = knot(base);
    float c = knot(base + 1);
    float d = knot(base + 2);
    float f2 = f * f;
    float f3 = f2 * f;
    float value =
        0.5F
            * (2.0F * b
                + (c - a) * f
                + (2.0F * a - 5.0F * b + 4.0F * c - d) * f2
                + (3.0F * b - a - 3.0F * c + d) * f3);
    return Math.max(LOWEST, value);
  }

  public Contour pull(int index, float target, float reach, int symmetry) {
    float[] next = knots.clone();
    int copies = Math.max(1, symmetry);
    int step = KNOTS / copies;
    float goal = limit(target);
    float spread = Math.max(0.35F, reach);
    int span = (int) Math.ceil(spread * 3.0F);
    for (int copy = 0; copy < copies; copy++) {
      int centre = index + copy * step;
      for (int offset = -span; offset <= span; offset++) {
        float weight = (float) Math.exp(-(offset * offset) / (2.0F * spread * spread));
        int at = Math.floorMod(centre + offset, KNOTS);
        next[at] = limit(next[at] + (goal - next[at]) * weight);
      }
    }
    return new Contour(next);
  }

  public Contour mirrored(int symmetry) {
    int copies = Math.max(1, symmetry);
    if (copies == 1) {
      return this;
    }
    int step = KNOTS / copies;
    float[] next = new float[KNOTS];
    for (int i = 0; i < KNOTS; i++) {
      float sum = 0.0F;
      for (int copy = 0; copy < copies; copy++) {
        sum += knots[(i + copy * step) % KNOTS];
      }
      next[i] = sum / copies;
    }
    return new Contour(next);
  }

  public Contour smooth() {
    float[] next = new float[KNOTS];
    for (int i = 0; i < KNOTS; i++) {
      next[i] = limit((knot(i - 1) + knot(i) * 2.0F + knot(i + 1)) * 0.25F);
    }
    return new Contour(next);
  }

  public Contour blend(Contour to, float amount) {
    if (amount <= 0.0F) {
      return this;
    }
    if (amount >= 1.0F) {
      return to;
    }
    float[] next = new float[KNOTS];
    for (int i = 0; i < KNOTS; i++) {
      next[i] = knots[i] + (to.knots[i] - knots[i]) * amount;
    }
    return new Contour(next);
  }

  public boolean near(Contour other, float tolerance) {
    for (int i = 0; i < KNOTS; i++) {
      if (Math.abs(knots[i] - other.knots[i]) > tolerance) {
        return false;
      }
    }
    return true;
  }

  public static float angle(int index) {
    return index * TURN / KNOTS;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Contour contour
        && hash == contour.hash
        && Arrays.equals(knots, contour.knots);
  }

  @Override
  public int hashCode() {
    return hash;
  }

  private static float limit(float radius) {
    return Float.isNaN(radius) ? 1.0F : Math.clamp(radius, LOWEST, HIGHEST);
  }

  private static float[] filled(float radius) {
    float[] knots = new float[KNOTS];
    Arrays.fill(knots, radius);
    return knots;
  }

  private static Contour polygon(int corners, float outer, float inner, float twist) {
    boolean star = inner != outer;
    int points = star ? corners * 2 : corners;
    float[] xs = new float[points];
    float[] ys = new float[points];
    for (int i = 0; i < points; i++) {
      float angle = (i / (float) points + twist) * TURN;
      float radius = star && (i & 1) == 1 ? inner : outer;
      xs[i] = (float) Math.cos(angle) * radius;
      ys[i] = (float) Math.sin(angle) * radius;
    }
    return trace(xs, ys, 0.0F, 0.0F);
  }

  private static Contour flower() {
    float[] knots = new float[KNOTS];
    for (int i = 0; i < KNOTS; i++) {
      knots[i] = 0.74F + 0.26F * (float) Math.cos(angle(i) * 6.0F);
    }
    return normalized(knots);
  }

  private static Contour heart() {
    float[] xs = new float[TRACE];
    float[] ys = new float[TRACE];
    for (int i = 0; i < TRACE; i++) {
      double t = i * Math.PI * 2.0 / TRACE;
      double s = Math.sin(t);
      xs[i] =
          (float)
              (13.0 * Math.cos(t)
                  - 5.0 * Math.cos(2.0 * t)
                  - 2.0 * Math.cos(3.0 * t)
                  - Math.cos(4.0 * t));
      ys[i] = (float) (-16.0 * s * s * s);
    }
    return trace(xs, ys, 1.5F, 0.0F);
  }

  private static Contour trace(float[] xs, float[] ys, float centreX, float centreY) {
    float[] knots = new float[KNOTS];
    int count = xs.length;
    for (int k = 0; k < KNOTS; k++) {
      float dx = (float) Math.cos(angle(k));
      float dy = (float) Math.sin(angle(k));
      float far = 0.0F;
      for (int i = 0; i < count; i++) {
        int j = (i + 1) % count;
        float ax = xs[i] - centreX;
        float ay = ys[i] - centreY;
        float ex = xs[j] - xs[i];
        float ey = ys[j] - ys[i];
        float cross = dx * ey - dy * ex;
        if (Math.abs(cross) < 1.0e-7F) {
          continue;
        }
        float along = (ax * ey - ay * ex) / cross;
        float side = (ax * dy - ay * dx) / cross;
        if (along > 0.0F && side >= 0.0F && side <= 1.0F) {
          far = Math.max(far, along);
        }
      }
      knots[k] = far;
    }
    return normalized(knots);
  }

  private static Contour normalized(float[] knots) {
    float peak = 0.0F;
    for (float knot : knots) {
      peak = Math.max(peak, knot);
    }
    for (int i = 0; i < KNOTS; i++) {
      knots[i] = limit(knots[i] / peak);
    }
    return new Contour(knots);
  }
}
