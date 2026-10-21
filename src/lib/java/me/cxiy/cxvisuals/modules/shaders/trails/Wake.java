package me.cxiy.cxvisuals.modules.shaders.trails;

public final class Wake {
  public static final int CAPACITY = 128;

  private static final int MASK = CAPACITY - 1;
  private static final double GAP = 0.35e9;
  private static final double TELEPORT = 6.0;
  private static final double CLOSEST = 0.1;
  private static final double SPEED_GAIN = 0.025;
  private static final double WIDEST = 1.0;

  private final double[] x = new double[CAPACITY];
  private final double[] y = new double[CAPACITY];
  private final double[] z = new double[CAPACITY];
  private final long[] born = new long[CAPACITY];
  private final float[] speed = new float[CAPACITY];
  private final int[] serial = new int[CAPACITY];
  private int head;
  private int count;
  private int serials;
  private boolean seeded;
  private double refX;
  private double refY;
  private double refZ;
  private long refTime;
  private double pace;
  private long touched;
  private float width = 0.6F;
  private float height = 1.8F;
  private int identity;

  public int count() {
    return count;
  }

  public int identity() {
    return identity;
  }

  public void identity(int value) {
    identity = value;
  }

  public long touched() {
    return touched;
  }

  public float width() {
    return width;
  }

  public float height() {
    return height;
  }

  public double x(int i) {
    return x[(head - i) & MASK];
  }

  public double y(int i) {
    return y[(head - i) & MASK];
  }

  public double z(int i) {
    return z[(head - i) & MASK];
  }

  public float speed(int i) {
    return speed[(head - i) & MASK];
  }

  public int serial(int i) {
    return serial[(head - i) & MASK];
  }

  public float age(int i, long nanos) {
    return (nanos - born[(head - i) & MASK]) / 1.0e9F;
  }

  public void step(
      double atX,
      double atY,
      double atZ,
      float bodyWidth,
      float bodyHeight,
      long nanos,
      float minSpeed) {
    width = bodyWidth;
    height = bodyHeight;
    boolean fresh = !seeded || nanos - touched > GAP || distance(atX, atY, atZ) > TELEPORT;
    touched = nanos;
    if (fresh) {
      count = 0;
      seeded = true;
      pace = 0.0;
      reference(atX, atY, atZ, nanos);
      return;
    }
    double gap = distance(atX, atY, atZ);
    double spacing = Math.clamp(pace * SPEED_GAIN, CLOSEST, WIDEST);
    if (gap < spacing) {
      return;
    }
    double seconds = Math.max((nanos - refTime) / 1.0e9, 1.0e-3);
    double rate = gap / seconds;
    pace = rate;
    if (rate >= minSpeed) {
      push(atX, atY, atZ, nanos, (float) rate);
    }
    reference(atX, atY, atZ, nanos);
  }

  public void push(double atX, double atY, double atZ, long nanos, float rate) {
    push(atX, atY, atZ, nanos, rate, serials++);
  }

  public void push(double atX, double atY, double atZ, long nanos, float rate, int id) {
    head = (head + 1) & MASK;
    x[head] = atX;
    y[head] = atY;
    z[head] = atZ;
    born[head] = nanos;
    speed[head] = rate;
    serial[head] = id;
    count = Math.min(count + 1, CAPACITY);
  }

  public void shape(float bodyWidth, float bodyHeight) {
    width = bodyWidth;
    height = bodyHeight;
  }

  public void prune(long nanos, float lifeSeconds) {
    long life = (long) (lifeSeconds * 1.0e9F);
    while (count > 0 && nanos - born[(head - count + 1) & MASK] > life) {
      count--;
    }
  }

  public void clear() {
    count = 0;
    seeded = false;
  }

  private void reference(double atX, double atY, double atZ, long nanos) {
    refX = atX;
    refY = atY;
    refZ = atZ;
    refTime = nanos;
  }

  private double distance(double atX, double atY, double atZ) {
    double dx = atX - refX;
    double dy = atY - refY;
    double dz = atZ - refZ;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }
}
