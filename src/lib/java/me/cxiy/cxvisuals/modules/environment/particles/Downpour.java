package me.cxiy.cxvisuals.modules.environment.particles;

import me.cxiy.cxvisuals.modules.environment.View;

public final class Downpour {
  public static final int CAPACITY = 3200;

  private static final float REACH = 15.0F;
  private static final float TILE = REACH * 2.0F;
  private static final float RAISE = 16.0F;
  private static final float DEPTH = 34.0F;
  private static final float SPEED = 17.0F;
  private static final float SPEED_SPREAD = 0.3F;
  private static final float BLUR = 0.05F;
  private static final float WIDTH = 0.011F;
  private static final float WIDTH_PER_BLOCK = 0.0052F;
  private static final float SLANT = 2.2F;
  private static final float SLANT_RESPONSE = 0.6F;
  private static final float NEAR_FROM = 0.5F;
  private static final float NEAR_TO = 2.2F;
  private static final float SIDE_FROM = REACH * 0.55F;
  private static final float GROUND_FADE = 0.7F;
  private static final float SPAWN_FADE = 0.06F;
  private static final float STRENGTH = 1.0F;
  private static final float DARKEST = 0.3F;
  private static final float GONE = 0.004F;
  private static final float REFRESH = 1.1F;
  private static final int CACHE = 2048;
  private static final int RAIN = 0xDCE8F6;
  private static final int SALT = 0x2545F491;

  private final long[] cacheKey = new long[CACHE];
  private final float[] cacheTop = new float[CACHE];
  private final double[] cacheAt = new double[CACHE];
  private Ground ground;
  private double clock;
  private float intensity;
  private float slantX;
  private float slantZ;
  private int count;

  public int count() {
    return count;
  }

  public void clear() {
    count = 0;
    intensity = 0.0F;
    slantX = 0.0F;
    slantZ = 0.0F;
    java.util.Arrays.fill(cacheAt, -1.0E9);
  }

  public void update(float rain, float amount, Wind wind, Ground ground, float dt) {
    this.ground = ground;
    clock += dt;
    intensity = Waves.approach(intensity, rain, 2.2F, dt);
    slantX = Waves.approach(slantX, wind.x * SLANT, SLANT_RESPONSE, dt);
    slantZ = Waves.approach(slantZ, wind.z * SLANT, SLANT_RESPONSE, dt);
    count = Math.clamp(Math.round(CAPACITY * intensity * amount), 0, CAPACITY);
  }

  public void emit(View view, Sink sink, Climate climate, float streak) {
    if (count == 0 || ground == null) {
      return;
    }
    float light = Math.clamp(DARKEST + (1.0F - DARKEST) * climate.light, 0.0F, 1.0F);
    int colour = Waves.shade(RAIN, light);
    for (int i = 0; i < count; i++) {
      draw(i, view, sink, colour, streak);
    }
  }

  private void draw(int index, View view, Sink sink, int colour, float streak) {
    int h0 = mix(index * 0x9E3779B1 + SALT);
    int h1 = mix(h0 + 1);
    int h2 = mix(h0 + 2);
    int h3 = mix(h0 + 3);
    float scale = 1.0F - SPEED_SPREAD * 0.5F + SPEED_SPREAD * unit(h2);
    float period = DEPTH / (SPEED * scale);
    float phase = (float) ((clock / period + unit(h3)) % 1.0);
    float elapsed = phase * period;
    double x = wrap(unit(h0) * TILE + slantX * elapsed - view.x) + view.x;
    double z = wrap(unit(h1) * TILE + slantZ * elapsed - view.z) + view.z;
    double y = view.y + RAISE - phase * DEPTH;
    float dx = (float) (x - view.x);
    float dz = (float) (z - view.z);
    float side = (float) Math.sqrt(dx * dx + dz * dz);
    if (side >= REACH) {
      return;
    }
    float top = top(x, z, view.y);
    float dy = (float) (y - view.y);
    float distance = (float) Math.sqrt(side * side + dy * dy);
    float alpha =
        STRENGTH
            * (1.0F - Waves.smooth(SIDE_FROM, REACH, side))
            * Waves.smooth(NEAR_FROM, NEAR_TO, distance)
            * Waves.smooth(top, top + GROUND_FADE, (float) y)
            * Waves.smooth(0.0F, SPAWN_FADE, phase)
            * (0.55F + 0.45F * unit(mix(h3 + 4)));
    if (alpha <= GONE) {
      return;
    }
    float velocityX = slantX;
    float velocityY = -SPEED * scale;
    float velocityZ = slantZ;
    float speed =
        (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY + velocityZ * velocityZ);
    float length = speed * BLUR * streak * (0.8F + 0.4F * unit(mix(h3 + 5)));
    float ax = velocityX / speed * length * 0.5F;
    float ay = velocityY / speed * length * 0.5F;
    float az = velocityZ / speed * length * 0.5F;
    float inverse = 1.0F / distance;
    float cx = -dx * inverse;
    float cy = -dy * inverse;
    float cz = -dz * inverse;
    float bx = ay * cz - az * cy;
    float by = az * cx - ax * cz;
    float bz = ax * cy - ay * cx;
    float span = (float) Math.sqrt(bx * bx + by * by + bz * bz);
    if (span < 1.0e-5F) {
      return;
    }
    float width = Math.max(WIDTH * streak, distance * WIDTH_PER_BLOCK) / span;
    sink.quad(
        dx,
        dy,
        dz,
        ax,
        ay,
        az,
        bx * width,
        by * width,
        bz * width,
        Waves.argb(colour, Math.min(1.0F, alpha)),
        Shape.STREAK,
        unit(h2),
        h3 & 0x7FFF);
  }

  private float top(double x, double z, double eyes) {
    int cellX = (int) Math.floor(x);
    int cellZ = (int) Math.floor(z);
    long key = (long) cellX << 32 | cellZ & 0xFFFFFFFFL;
    int slot = mix((int) (key ^ key >>> 32)) & (CACHE - 1);
    double age = clock - cacheAt[slot];
    if (cacheKey[slot] != key || age < 0.0 || age > REFRESH) {
      cacheKey[slot] = key;
      cacheTop[slot] = ground.floor(cellX + 0.5, eyes + RAISE, cellZ + 0.5);
      cacheAt[slot] = clock;
    }
    return cacheTop[slot];
  }

  private static double wrap(double offset) {
    return offset - TILE * Math.floor(offset / TILE + 0.5);
  }

  private static int mix(int value) {
    int x = value;
    x ^= x >>> 16;
    x *= 0x7FEB352D;
    x ^= x >>> 15;
    x *= 0x846CA68B;
    x ^= x >>> 16;
    return x;
  }

  private static float unit(int hash) {
    return (hash >>> 8 & 0xFFFFFF) * (1.0F / 16777216.0F);
  }
}
