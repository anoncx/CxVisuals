package me.cxiy.cxvisuals.modules.shaders.trails;

public final class Runner {
  public static final float RADIUS = 1.5F;
  public static final float PACE = 5.4F;
  public static final float WIDTH = 0.6F;
  public static final float HEIGHT = 1.8F;
  public static final float STRIDE = 18.0F;

  private static final float RATE = PACE / RADIUS;
  private static final float PERIOD = (float) (Math.PI * 2.0) / RATE;
  private static final float LAPS = 64.0F;
  private static final float STEP = 0.02F;
  private static final int HISTORY = 120;

  private Runner() {}

  public static float wrap() {
    return PERIOD * LAPS;
  }

  public static float x(float clock) {
    return RADIUS * (float) Math.cos(RATE * clock);
  }

  public static float z(float clock) {
    return RADIUS * (float) Math.sin(RATE * clock);
  }

  public static float heading(float clock) {
    return (float) Math.toDegrees(RATE * clock);
  }

  public static long nanos(float clock) {
    return (long) (clock * 1.0e9);
  }

  public static void fill(Wake wake, float clock, float life) {
    float step = Math.max(STEP, life / HISTORY);
    long newest = (long) Math.floor(clock / step);
    int count = Math.min(Wake.CAPACITY, (int) Math.ceil(life / step) + 1);
    wake.clear();
    wake.shape(WIDTH, HEIGHT);
    for (int k = count - 1; k >= 0; k--) {
      long index = newest - k;
      float time = index * step;
      wake.push(x(time), 0.0, z(time), nanos(time), PACE, (int) index);
    }
  }
}
