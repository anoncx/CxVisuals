package me.cxiy.cxvisuals.modules.swings;

public final class Ribbon {
  public static final int CAPACITY = 128;

  private static final int MASK = CAPACITY - 1;
  private static final long BREAK = 120_000_000L;
  private static final int CHANNELS = 6;
  private static final int TIP = 3;
  private static final double STILL = 4.0e-4;
  private static final double SLOWEST = 4.0e-3;
  private static final double TURN = -0.25;
  private static final float RESTART = 0.2F;
  private static final float RETURN_WEIGHT = 0.3F;

  private final double[] points = new double[CAPACITY * CHANNELS];
  private final long[] born = new long[CAPACITY];
  private final boolean[] linked = new boolean[CAPACITY];
  private final float[] weights = new float[CAPACITY];
  private int head;
  private int count;
  private long touched;
  private float progress;
  private boolean returning;

  public int count() {
    return count;
  }

  public boolean empty() {
    return count == 0;
  }

  public long touched() {
    return touched;
  }

  public double at(int i, int channel) {
    return points[((head - i) & MASK) * CHANNELS + channel];
  }

  public float age(int i, long nanos) {
    return (nanos - born[(head - i) & MASK]) / 1.0e9F;
  }

  public boolean linked(int i) {
    return linked[(head - i) & MASK];
  }

  public float weight(int i) {
    return weights[(head - i) & MASK];
  }

  public void add(
      double baseX,
      double baseY,
      double baseZ,
      double tipX,
      double tipY,
      double tipZ,
      float swing,
      long nanos,
      long spacing) {
    boolean fresh = count == 0 || nanos - touched > BREAK || swing < progress - RESTART;
    touched = nanos;
    progress = swing;
    if (fresh) {
      returning = false;
    } else if (stationary(baseX, baseY, baseZ, tipX, tipY, tipZ)) {
      return;
    } else if (!returning && reverses(tipX, tipY, tipZ)) {
      returning = true;
    }
    if (!fresh && count > 1 && linked[head] && nanos - born[head] < spacing) {
      store(head, baseX, baseY, baseZ, tipX, tipY, tipZ, nanos, true);
      return;
    }
    head = (head + 1) & MASK;
    store(head, baseX, baseY, baseZ, tipX, tipY, tipZ, nanos, !fresh);
    count = Math.min(count + 1, CAPACITY);
  }

  public void prune(long nanos, float lifeSeconds) {
    long life = (long) (lifeSeconds * 1.0e9F);
    while (count > 0 && nanos - born[(head - count + 1) & MASK] > life) {
      count--;
    }
  }

  public void clear() {
    count = 0;
    returning = false;
  }

  private boolean stationary(
      double baseX, double baseY, double baseZ, double tipX, double tipY, double tipZ) {
    int at = head * CHANNELS;
    double dx = Math.abs(points[at] - baseX) + Math.abs(points[at + TIP] - tipX);
    double dy = Math.abs(points[at + 1] - baseY) + Math.abs(points[at + TIP + 1] - tipY);
    double dz = Math.abs(points[at + 2] - baseZ) + Math.abs(points[at + TIP + 2] - tipZ);
    return dx + dy + dz < STILL;
  }

  private boolean reverses(double tipX, double tipY, double tipZ) {
    if (count < 2 || !linked[head]) {
      return false;
    }
    int now = head * CHANNELS + TIP;
    int before = ((head - 1) & MASK) * CHANNELS + TIP;
    double ax = points[now] - points[before];
    double ay = points[now + 1] - points[before + 1];
    double az = points[now + 2] - points[before + 2];
    double bx = tipX - points[now];
    double by = tipY - points[now + 1];
    double bz = tipZ - points[now + 2];
    double lengths = Math.sqrt((ax * ax + ay * ay + az * az) * (bx * bx + by * by + bz * bz));
    return lengths > SLOWEST * SLOWEST && ax * bx + ay * by + az * bz < TURN * lengths;
  }

  private void store(
      int slot,
      double baseX,
      double baseY,
      double baseZ,
      double tipX,
      double tipY,
      double tipZ,
      long nanos,
      boolean joined) {
    int at = slot * CHANNELS;
    points[at] = baseX;
    points[at + 1] = baseY;
    points[at + 2] = baseZ;
    points[at + TIP] = tipX;
    points[at + TIP + 1] = tipY;
    points[at + TIP + 2] = tipZ;
    born[slot] = nanos;
    linked[slot] = joined;
    weights[slot] = returning ? RETURN_WEIGHT : 1.0F;
  }
}
