package me.cxiy.cxvisuals.modules.shaders.circles;

public final class Circles {
  public static final int CAPACITY = 32;

  private final double[] x = new double[CAPACITY];
  private final double[] y = new double[CAPACITY];
  private final double[] z = new double[CAPACITY];
  private final float[] yaw = new float[CAPACITY];
  private final float[] strength = new float[CAPACITY];
  private final int[] seed = new int[CAPACITY];
  private final long[] born = new long[CAPACITY];
  private int count;
  private int seeds = 0x2F6B;

  public int count() {
    return count;
  }

  public boolean empty() {
    return count == 0;
  }

  public void spawn(double atX, double atY, double atZ, float facing, float power, long nanos) {
    seeds = seeds * 0x343FD + 0x269EC3;
    spawn(atX, atY, atZ, facing, power, nanos, (seeds >>> 16) & 0xFFF);
  }

  public void spawn(
      double atX, double atY, double atZ, float facing, float power, long nanos, int grain) {
    int slot = count < CAPACITY ? count++ : oldest();
    x[slot] = atX;
    y[slot] = atY;
    z[slot] = atZ;
    yaw[slot] = facing;
    strength[slot] = power;
    seed[slot] = grain;
    born[slot] = nanos;
  }

  public void prune(long nanos, float lifeSeconds) {
    long life = (long) (lifeSeconds * 1.0e9F);
    int kept = 0;
    for (int i = 0; i < count; i++) {
      if (nanos - born[i] <= life && nanos >= born[i]) {
        if (kept != i) {
          move(i, kept);
        }
        kept++;
      }
    }
    count = kept;
  }

  public void clear() {
    count = 0;
  }

  public double x(int i) {
    return x[i];
  }

  public double y(int i) {
    return y[i];
  }

  public double z(int i) {
    return z[i];
  }

  public float yaw(int i) {
    return yaw[i];
  }

  public float strength(int i) {
    return strength[i];
  }

  public int seed(int i) {
    return seed[i];
  }

  public float age(int i, long nanos) {
    return (nanos - born[i]) / 1.0e9F;
  }

  private int oldest() {
    int slot = 0;
    for (int i = 1; i < count; i++) {
      if (born[i] < born[slot]) {
        slot = i;
      }
    }
    return slot;
  }

  private void move(int from, int to) {
    x[to] = x[from];
    y[to] = y[from];
    z[to] = z[from];
    yaw[to] = yaw[from];
    strength[to] = strength[from];
    seed[to] = seed[from];
    born[to] = born[from];
  }
}
