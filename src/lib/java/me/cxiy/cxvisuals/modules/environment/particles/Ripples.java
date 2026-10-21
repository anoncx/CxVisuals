package me.cxiy.cxvisuals.modules.environment.particles;

import java.util.SplittableRandom;
import me.cxiy.cxvisuals.modules.environment.View;

public final class Ripples {
  public static final int CAPACITY = 260;

  private static final float REACH = 15.0F;
  private static final float LEAVE = 1.25F;
  private static final float NEAREST = 0.9F;
  private static final float RATE = 150.0F;
  private static final float SPAWNS_PER_FRAME = 14.0F;
  private static final float LIFE_LEAST = 0.95F;
  private static final float LIFE_SPREAD = 0.95F;
  private static final float RADIUS_LEAST = 0.3F;
  private static final float RADIUS_SPREAD = 0.4F;
  private static final float LIFT = 0.02F;
  private static final float LIFT_PER_BLOCK = 0.0007F;
  private static final float CEILING = 24.0F;
  private static final float VERTICAL_LIMIT = 30.0F;
  private static final float VOID = 30.0F;
  private static final float FADE_NEAR_FROM = 0.5F;
  private static final float FADE_NEAR_TO = 1.6F;
  private static final float FADE_FAR_FROM = 9.0F;
  private static final float GONE = 0.004F;
  private static final float SEEDS = 32767.0F;
  private static final float DARKEST = 0.25F;
  private static final int SKY = 0xE4F0FF;

  private static final class Ripple {
    double x;
    double y;
    double z;
    float age;
    float life;
    float radius;
    float strength;
    int seed;
    boolean alive;
  }

  private final Ripple[] pool = new Ripple[CAPACITY];
  private final SplittableRandom random;
  private float owed;
  private int alive;

  public Ripples(long seed) {
    random = new SplittableRandom(seed);
    for (int i = 0; i < CAPACITY; i++) {
      pool[i] = new Ripple();
    }
  }

  public int alive() {
    return alive;
  }

  public void clear() {
    for (Ripple ripple : pool) {
      ripple.alive = false;
    }
    alive = 0;
    owed = 0.0F;
  }

  public void update(View view, float wet, float amount, Ground ground, float dt) {
    alive = 0;
    double leave = REACH * LEAVE;
    for (Ripple ripple : pool) {
      if (!ripple.alive) {
        continue;
      }
      ripple.age += dt;
      double dx = ripple.x - view.x;
      double dz = ripple.z - view.z;
      ripple.alive = ripple.age < ripple.life && dx * dx + dz * dz < leave * leave;
      if (ripple.alive) {
        alive++;
      }
    }
    if (wet <= 0.0F || amount <= 0.0F) {
      owed = 0.0F;
      return;
    }
    owed = Math.min(owed + RATE * amount * wet * dt, SPAWNS_PER_FRAME);
    while (owed >= 1.0F) {
      owed -= 1.0F;
      if (!spawn(view, wet, ground)) {
        owed = 0.0F;
        break;
      }
    }
  }

  private boolean spawn(View view, float wet, Ground ground) {
    Ripple ripple = free();
    if (ripple == null) {
      return false;
    }
    float angle = random.nextFloat() * Waves.TURN;
    float distance = Math.max(NEAREST, REACH * (float) Math.sqrt(random.nextFloat()));
    double x = view.x + Waves.cos(angle) * distance;
    double z = view.z + Waves.sin(angle) * distance;
    double from = view.y + CEILING;
    float floor = ground.floor(x, from, z);
    if (floor <= from - CEILING - VOID || Math.abs(floor - view.y) > VERTICAL_LIMIT) {
      return true;
    }
    int cellX = (int) Math.floor(x);
    int cellY = (int) Math.floor(floor) - 1;
    int cellZ = (int) Math.floor(z);
    float height = ground.top(cellX, cellY, cellZ);
    ripple.x = x;
    ripple.z = z;
    ripple.y = height > Ground.AIR ? cellY + height : floor;
    ripple.age = 0.0F;
    ripple.life = LIFE_LEAST + random.nextFloat() * LIFE_SPREAD;
    ripple.radius = RADIUS_LEAST + random.nextFloat() * RADIUS_SPREAD;
    ripple.strength = Math.min(1.0F, 0.55F + random.nextFloat() * 0.45F) * Math.max(0.35F, wet);
    ripple.seed = (int) (random.nextFloat() * SEEDS);
    ripple.alive = true;
    alive++;
    return true;
  }

  private Ripple free() {
    for (Ripple ripple : pool) {
      if (!ripple.alive) {
        return ripple;
      }
    }
    return null;
  }

  public void emit(View view, Sink sink, Climate climate, float size, float sheen) {
    if (alive == 0) {
      return;
    }
    int colour =
        Waves.shade(SKY, Math.clamp(DARKEST + (1.0F - DARKEST) * climate.light, 0.0F, 1.0F));
    for (Ripple ripple : pool) {
      if (ripple.alive) {
        draw(ripple, view, sink, colour, size, sheen);
      }
    }
  }

  private static void draw(
      Ripple ripple, View view, Sink sink, int colour, float size, float sheen) {
    float dx = (float) (ripple.x - view.x);
    float dy = (float) (ripple.y - view.y);
    float dz = (float) (ripple.z - view.z);
    float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    float visible =
        Waves.smooth(FADE_NEAR_FROM, FADE_NEAR_TO, distance)
            * (1.0F - Waves.smooth(FADE_FAR_FROM, REACH, distance));
    float alpha = ripple.strength * visible * sheen;
    if (alpha <= GONE) {
      return;
    }
    float radius = ripple.radius * size * (1.0F + distance * 0.01F);
    float lift = LIFT + distance * LIFT_PER_BLOCK;
    float progress = Math.min(1.0F, ripple.age / ripple.life);
    sink.quad(
        dx,
        dy + lift,
        dz,
        radius,
        0.0F,
        0.0F,
        0.0F,
        0.0F,
        radius,
        Waves.argb(colour, Math.min(1.0F, alpha)),
        Shape.RIPPLE,
        progress,
        ripple.seed);
  }
}
