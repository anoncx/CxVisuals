package me.cxiy.cxvisuals.modules.environment.particles;

import java.util.SplittableRandom;
import me.cxiy.cxvisuals.modules.environment.View;

public final class Lanterns {
  public static final int CAPACITY = 40;
  public static final int QUADS = CAPACITY * 3;

  private static final float NEAREST = 30.0F;
  private static final float SPREAD = 75.0F;
  private static final float RISE = 0.4F;
  private static final float RISE_SPREAD = 0.35F;
  private static final float CEILING = 75.0F;
  private static final float CEILING_SPREAD = 35.0F;
  private static final float FADE_TOP = 14.0F;
  private static final float FAR = 170.0F;
  private static final float HIDDEN = 1.6F;
  private static final float SHOWN = 3.5F;
  private static final float HAZE_SHARE = 0.75F;
  private static final float SHORTEST = 18.0F;
  private static final float DUSK = 0.2F;
  private static final float GONE = 0.004F;
  private static final float BODY = 0.85F;
  private static final float GROWTH = 40.0F;
  private static final float FLAME_DROP = 0.3F;
  private static final float CROWN = 0.45F;
  private static final float TRAPPED = 10.0F;
  private static final float SEEDS = 32767.0F;
  private static final int HALO = 0xFF8A3C;
  private static final int GLEAM = 0xFFC27A;
  private static final int PAPER = 0xFF9A3C;
  private static final int PAPER_WARM = 0xFFBC5C;

  private static final class Lantern {
    double x;
    double y;
    double z;
    double top;
    float rise;
    float seed;
    float age;
    float turn;
    float stuck;
    int cellX;
    int cellY;
    int cellZ;
    boolean capped;
    boolean alive;
  }

  private final Lantern[] pool = new Lantern[CAPACITY];
  private final SplittableRandom random;
  private float presence;
  private float wait;
  private int alive;

  public Lanterns(long seed) {
    random = new SplittableRandom(seed);
    for (int i = 0; i < CAPACITY; i++) {
      pool[i] = new Lantern();
    }
  }

  public int alive() {
    return alive;
  }

  public void clear() {
    for (Lantern lantern : pool) {
      lantern.alive = false;
    }
    alive = 0;
    presence = 0.0F;
    wait = 0.0F;
  }

  public void release(View view, float distance) {
    Lantern lantern = free();
    if (lantern == null) {
      return;
    }
    float flat = (float) Math.sqrt(view.fx * view.fx + view.fz * view.fz);
    float fx = flat < 1.0e-3F ? 0.0F : view.fx / flat;
    float fz = flat < 1.0e-3F ? 1.0F : view.fz / flat;
    prepare(
        lantern, view.x + fx * distance, view.y - 0.6, view.z + fz * distance, view.y + CEILING);
    presence = Math.max(presence, 1.0F);
    lantern.age = 3.0F;
    alive++;
  }

  public void update(
      View view,
      int wanted,
      float allow,
      float horizon,
      float dt,
      float time,
      Wind wind,
      Ground ground) {
    presence = Waves.approach(presence, allow, 0.4F, dt);
    int target = allow > DUSK ? Math.min(wanted, CAPACITY) : 0;
    if (target == 0 && presence <= GONE) {
      clear();
      return;
    }
    alive = 0;
    for (Lantern lantern : pool) {
      if (!lantern.alive) {
        continue;
      }
      lantern.age += dt;
      float sway = 0.25F + 0.15F * lantern.seed;
      rise(lantern, dt, ground);
      lantern.x += (wind.x * 0.3F + Waves.sin(time * 0.5F + lantern.seed * 30.0F) * sway) * dt;
      lantern.z += (wind.z * 0.3F + Waves.cos(time * 0.43F + lantern.seed * 17.0F) * sway) * dt;
      lantern.turn += (0.12F + 0.1F * lantern.seed) * (lantern.seed < 0.5F ? -dt : dt);
      double dx = lantern.x - view.x;
      double dz = lantern.z - view.z;
      if (lantern.y > lantern.top || lantern.stuck > TRAPPED || dx * dx + dz * dz > FAR * FAR) {
        lantern.alive = false;
      } else {
        alive++;
      }
    }
    wait -= dt;
    boolean sparse = alive < target / 2;
    while (alive < target && (sparse || wait <= 0.0F)) {
      if (!launch(view, ground, horizon, sparse)) {
        break;
      }
      alive++;
      wait = 0.6F + random.nextFloat() * 1.8F;
      sparse = alive < target / 2;
    }
  }

  private static void rise(Lantern lantern, float dt, Ground ground) {
    double next = lantern.y + lantern.rise * dt;
    int x = (int) Math.floor(lantern.x);
    int y = (int) Math.floor(next + CROWN);
    int z = (int) Math.floor(lantern.z);
    if (x != lantern.cellX || y != lantern.cellY || z != lantern.cellZ) {
      lantern.cellX = x;
      lantern.cellY = y;
      lantern.cellZ = z;
      lantern.capped = ground.top(x, y, z) > Ground.AIR;
    }
    if (lantern.capped) {
      lantern.y = Math.max(lantern.y - lantern.rise * dt, Math.min(next, y - CROWN));
      lantern.stuck += dt;
    } else {
      lantern.y = next;
      lantern.stuck = Math.max(0.0F, lantern.stuck - dt);
    }
  }

  private Lantern free() {
    for (Lantern lantern : pool) {
      if (!lantern.alive) {
        return lantern;
      }
    }
    return null;
  }

  private boolean launch(View view, Ground ground, float horizon, boolean anywhere) {
    Lantern lantern = free();
    if (lantern == null) {
      return false;
    }
    float angle = random.nextFloat() * Waves.TURN;
    float farthest = Math.max(SHORTEST, Math.min(NEAREST + SPREAD, horizon * HAZE_SHARE));
    float nearest = Math.min(NEAREST, farthest * 0.5F);
    float distance = nearest + random.nextFloat() * (farthest - nearest);
    double x = view.x + Waves.cos(angle) * distance;
    double z = view.z + Waves.sin(angle) * distance;
    double base = Math.clamp(ground.floor(x, view.y + 40.0, z) + 1.5, view.y - 25.0, view.y + 12.0);
    double top = view.y + CEILING + random.nextFloat() * CEILING_SPREAD;
    prepare(lantern, x, anywhere ? base + random.nextFloat() * (top - base) * 0.8 : base, z, top);
    return true;
  }

  private void prepare(Lantern lantern, double x, double y, double z, double top) {
    lantern.x = x;
    lantern.y = y;
    lantern.z = z;
    lantern.top = top;
    lantern.rise = RISE + random.nextFloat() * RISE_SPREAD;
    lantern.seed = random.nextFloat();
    lantern.turn = random.nextFloat() * Waves.TURN;
    lantern.age = 0.0F;
    lantern.stuck = 0.0F;
    lantern.cellY = Integer.MIN_VALUE;
    lantern.capped = false;
    lantern.alive = true;
  }

  public void emit(View view, Sink sink, float fogStart, float fogEnd, float glow, float time) {
    if (presence <= GONE) {
      return;
    }
    for (Lantern lantern : pool) {
      if (lantern.alive) {
        draw(lantern, view, sink, fogStart, fogEnd, glow, time);
      }
    }
  }

  private void draw(
      Lantern lantern, View view, Sink sink, float fogStart, float fogEnd, float glow, float time) {
    float dx = (float) (lantern.x - view.x);
    float dy = (float) (lantern.y - view.y);
    float dz = (float) (lantern.z - view.z);
    float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (distance < HIDDEN || dx * view.fx + dy * view.fy + dz * view.fz < -BODY) {
      return;
    }
    float alpha =
        presence
            * Waves.smooth(HIDDEN, SHOWN, distance)
            * (1.0F - Waves.smooth(fogStart, fogEnd, distance))
            * Waves.smooth(0.0F, 3.0F, lantern.age)
            * (1.0F - Waves.smooth(TRAPPED - 3.0F, TRAPPED, lantern.stuck))
            * (1.0F
                - Waves.smooth(
                    (float) (lantern.top - FADE_TOP), (float) lantern.top, (float) lantern.y));
    if (alpha <= GONE) {
      return;
    }
    float flicker = 0.88F + 0.12F * Waves.wave(time * 6.5F + lantern.seed * 40.0F);
    float size = BODY * (1.0F + distance / GROWTH);
    float flat = (float) Math.sqrt(dx * dx + dz * dz);
    float rx = flat < 1.0e-3F ? 1.0F : dz / flat;
    float rz = flat < 1.0e-3F ? 0.0F : -dx / flat;
    float inverse = 1.0F / distance;
    float tx = dx * inverse;
    float ty = dy * inverse;
    float tz = dz * inverse;
    float ux = ty * rz;
    float uy = tz * rx - tx * rz;
    float uz = -ty * rx;
    float tilt = Waves.sin(time * 0.9F + lantern.seed * 20.0F) * 0.07F;
    float ct = Waves.cos(tilt);
    float st = Waves.sin(tilt);
    float ax = rx * ct + ux * st;
    float ay = uy * st;
    float az = rz * ct + uz * st;
    float bx = ux * ct - rx * st;
    float by = uy * ct;
    float bz = uz * ct - rz * st;
    float drop = size * FLAME_DROP;
    float fx = dx - bx * drop;
    float fy = dy - by * drop;
    float fz = dz - bz * drop;
    int seed = (int) (lantern.seed * SEEDS);
    float gleam = 1.6F * size;
    sink.quad(
        fx,
        fy,
        fz,
        view.rx * gleam,
        view.ry * gleam,
        view.rz * gleam,
        view.ux * gleam,
        view.uy * gleam,
        view.uz * gleam,
        Waves.argb(GLEAM, Math.min(1.0F, alpha * 0.55F * flicker * glow)),
        Shape.HALO,
        0.0F,
        seed);
    float halo = 3.4F * size;
    sink.quad(
        fx,
        fy,
        fz,
        view.rx * halo,
        view.ry * halo,
        view.rz * halo,
        view.ux * halo,
        view.uy * halo,
        view.uz * halo,
        Waves.argb(HALO, Math.min(1.0F, alpha * 0.2F * flicker * glow)),
        Shape.HALO,
        0.0F,
        seed);
    float spin = (lantern.turn + (float) Math.atan2(rz, rx)) / Waves.TURN;
    int paper = Waves.shade(Waves.mix(PAPER, PAPER_WARM, lantern.seed), flicker);
    sink.quad(
        dx,
        dy,
        dz,
        ax * size,
        ay * size,
        az * size,
        bx * size,
        by * size,
        bz * size,
        Waves.argb(paper, alpha),
        Shape.LANTERN,
        ty * 0.5F + 0.5F,
        Math.round((spin - (float) Math.floor(spin)) * SEEDS));
  }
}
