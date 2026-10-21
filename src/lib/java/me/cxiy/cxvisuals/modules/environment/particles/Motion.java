package me.cxiy.cxvisuals.modules.environment.particles;

import java.util.SplittableRandom;
import me.cxiy.cxvisuals.modules.environment.View;

final class Motion {
  private static final float SHY = 2.4F;
  private static final float SHY_PUSH = 1.8F;
  private static final float REFRESH = 0.75F;
  private static final float LIFT = 0.02F;
  private static final float HOVER_LOOK = 6.0F;
  private static final float TOP = 0.7F;
  private static final float BOTTOM = 0.75F;
  private static final float PETAL_SWAY = 0.42F;
  private static final float SNOW_SWAY = 0.26F;
  private static final float PETAL_REST = 4.5F;
  private static final float SNOW_REST = 1.4F;
  static final int TRAIL = 14;
  static final float POP = 0.3F;
  private static final float TRAIL_STEP = 0.035F;
  private static final float BOUNCE = 0.3F;
  private static final float FULL = 0.99F;

  private static final float[] PUSH = new float[3];

  private Motion() {}

  static void spawn(
      Kind kind, Mote m, View view, SplittableRandom random, Ground ground, boolean anywhere) {
    float reach = kind.reach;
    m.x = view.x + (random.nextFloat() * 2.0F - 1.0F) * reach;
    m.z = view.z + (random.nextFloat() * 2.0F - 1.0F) * reach;
    m.vx = 0.0F;
    m.vy = 0.0F;
    m.vz = 0.0F;
    m.seed = random.nextFloat();
    m.phase = random.nextFloat();
    m.spin = random.nextFloat() * Waves.TURN;
    m.tumble = random.nextFloat() * Waves.TURN;
    m.age = 0.0F;
    m.life = Float.MAX_VALUE;
    m.landed = false;
    m.rest = 0.0F;
    m.floorX = Double.NaN;
    switch (kind) {
      case FIREFLIES -> {
        m.size = 0.04F + random.nextFloat() * 0.03F;
        m.rate = 1.0F / (2.2F + random.nextFloat() * 4.0F);
        m.hover = 0.3F + (float) Math.pow(random.nextFloat(), 1.6) * 3.2F;
        settle(m, ground, view.y + HOVER_LOOK);
        m.y = m.floor + m.hover;
      }
      case PETALS -> {
        m.size = 0.1F + random.nextFloat() * 0.06F;
        m.rate = 1.4F + random.nextFloat() * 2.2F;
        m.drop = 0.42F + random.nextFloat() * 0.32F;
        aloft(m, view, reach, random, ground, anywhere);
      }
      case SNOW -> {
        m.size = 0.035F + random.nextFloat() * 0.04F;
        m.rate = 0.8F + random.nextFloat() * 1.4F;
        m.drop = 0.65F + random.nextFloat() * 0.5F;
        aloft(m, view, reach, random, ground, anywhere);
      }
      case EMBERS -> {
        m.size = 0.016F + random.nextFloat() * 0.026F;
        m.drop = 0.55F + random.nextFloat() * 0.8F;
        m.life = 2.2F + random.nextFloat() * 3.6F;
        m.age = anywhere ? random.nextFloat() * m.life : 0.0F;
        m.y =
            anywhere
                ? view.y + (random.nextFloat() * 2.0F - 1.0F) * reach * 0.5F
                : view.y - reach * 0.55F + random.nextFloat() * reach * 0.6F;
      }
      case MOTES -> {
        m.size = 0.009F + random.nextFloat() * 0.014F;
        m.rate = 1.2F + random.nextFloat() * 2.4F;
        m.life = 6.0F + random.nextFloat() * 8.0F;
        m.age = anywhere ? random.nextFloat() * m.life : 0.0F;
        m.y = view.y + (random.nextFloat() * 2.0F - 1.0F) * reach * 0.55F;
      }
      case BUTTERFLIES -> {
        m.size = 0.15F + random.nextFloat() * 0.08F;
        m.rate = 6.0F + random.nextFloat() * 4.0F;
        m.hover = 0.4F + random.nextFloat() * 2.2F;
        m.drop = 0.8F + random.nextFloat() * 0.6F;
        settle(m, ground, view.y + HOVER_LOOK);
        m.y = m.floor + m.hover;
      }
      case BUBBLES -> {
        m.size = 0.08F + random.nextFloat() * 0.14F;
        m.drop = 0.12F + random.nextFloat() * 0.25F;
        m.rate = 0.05F + random.nextFloat() * 0.12F;
        m.life = 6.0F + random.nextFloat() * 9.0F;
        m.age = anywhere ? random.nextFloat() * m.life : 0.0F;
        m.y =
            anywhere
                ? view.y + (random.nextFloat() * 2.0F - 1.0F) * reach * 0.5F
                : view.y - reach * 0.4F + random.nextFloat() * reach * 0.5F;
      }
      case WISPS -> {
        m.size = 0.14F + random.nextFloat() * 0.08F;
        m.rate = 0.5F + random.nextFloat() * 0.6F;
        m.hover = 0.8F + random.nextFloat() * 2.6F;
        settle(m, ground, view.y + HOVER_LOOK);
        m.y = m.floor + m.hover;
        trace(m);
      }
    }
    locate(m, ground);
  }

  static void locate(Mote m, Ground ground) {
    m.cellX = (int) Math.floor(m.x);
    m.cellY = (int) Math.floor(m.y);
    m.cellZ = (int) Math.floor(m.z);
    m.ledge = ground.top(m.cellX, m.cellY, m.cellZ);
    m.buried = blocks(m.ledge, m.y - m.cellY);
    sense(m, ground);
  }

  private static void sense(Mote m, Ground ground) {
    int light = ground.light(m.cellX, m.cellY, m.cellZ);
    m.sky = (light >> 4) / Climate.LEVELS;
    m.lamp = (light & 0xF) / Climate.LEVELS;
  }

  private static boolean blocks(float top, double height) {
    return top >= FULL || top >= 0.0F && height < top;
  }

  private static void trace(Mote m) {
    if (m.trail == null) {
      m.trail = new double[TRAIL * 3];
    }
    for (int i = 0; i < TRAIL; i++) {
      m.trail[i * 3] = m.x;
      m.trail[i * 3 + 1] = m.y;
      m.trail[i * 3 + 2] = m.z;
    }
    m.trailHead = 0;
    m.trailClock = 0.0F;
  }

  static float glide(Mote m, float time) {
    return Waves.smooth(0.35F, 0.75F, 0.5F + 0.5F * Waves.wave(time * 0.45F + m.seed * 19.0F));
  }

  private static void aloft(
      Mote m, View view, float reach, SplittableRandom random, Ground ground, boolean anywhere) {
    if (anywhere) {
      m.y = view.y + (random.nextFloat() * 2.0F - 1.0F) * reach * 0.6F;
      settle(m, ground, m.y + 1.0);
      m.y = Math.max(m.y, m.floor + 0.5);
    } else {
      m.y = view.y + reach * (0.3F + random.nextFloat() * 0.35F);
    }
  }

  private static void settle(Mote m, Ground ground, double top) {
    m.floor = ground.floor(m.x, top, m.z);
    m.floorX = m.x;
    m.floorZ = m.z;
  }

  private static boolean strayed(Mote m) {
    return Double.isNaN(m.floorX) || Math.abs(m.x - m.floorX) + Math.abs(m.z - m.floorZ) > REFRESH;
  }

  static void wrapped(Kind kind, Mote m, View view, Ground ground) {
    if (kind == Kind.FIREFLIES || kind == Kind.BUTTERFLIES || kind == Kind.WISPS) {
      settle(m, ground, view.y + HOVER_LOOK);
      m.y = m.floor + m.hover;
    }
    if (kind == Kind.WISPS) {
      trace(m);
    }
    locate(m, ground);
  }

  static boolean step(
      Kind kind, Mote m, float dt, float time, Wind wind, View view, Ground ground) {
    m.age += dt;
    return switch (kind) {
      case FIREFLIES -> firefly(m, dt, time, wind, view, ground);
      case PETALS -> fall(m, kind, dt, time, wind, view, ground, PETAL_SWAY, PETAL_REST);
      case SNOW -> fall(m, kind, dt, time, wind, view, ground, SNOW_SWAY, SNOW_REST);
      case EMBERS -> ember(m, kind, dt, time, wind, view, ground);
      case MOTES -> mote(m, kind, dt, time, wind, view, ground);
      case BUTTERFLIES -> butterfly(m, dt, time, wind, view, ground);
      case BUBBLES -> bubble(m, kind, dt, time, wind, view, ground);
      case WISPS -> wisp(m, dt, time, wind, view, ground);
    };
  }

  private static void shy(Mote m, View view, float[] push) {
    float dx = (float) (m.x - view.x);
    float dy = (float) (m.y - view.y);
    float dz = (float) (m.z - view.z);
    float near = dx * dx + dy * dy + dz * dz;
    push[0] = 0.0F;
    push[1] = 0.0F;
    push[2] = 0.0F;
    if (near < SHY * SHY && near > 1.0e-4F) {
      float d = (float) Math.sqrt(near);
      float strength = (SHY - d) / SHY * SHY_PUSH / d;
      push[0] = dx * strength;
      push[1] = dy * strength * 0.5F;
      push[2] = dz * strength;
    }
  }

  private static boolean butterfly(
      Mote m, float dt, float time, Wind wind, View view, Ground ground) {
    if (strayed(m)) {
      settle(m, ground, view.y + HOVER_LOOK);
    }
    float glide = glide(m, time);
    m.spin += Waves.wave(time * 0.8F + m.seed * 30.0F) * 2.4F * dt;
    m.tumble += dt * m.rate * Waves.TURN * (1.0F - 0.85F * glide);
    float cruise = m.drop * (0.75F + 0.25F * Waves.wave(time * 1.3F + m.seed * 7.0F));
    shy(m, view, PUSH);
    float tx = Waves.cos(m.spin) * cruise + wind.x * 0.25F + PUSH[0];
    float tz = Waves.sin(m.spin) * cruise + wind.z * 0.25F + PUSH[2];
    float ty =
        (float) (m.floor + m.hover - m.y) * 1.2F
            + Waves.sin(m.tumble) * 0.35F * (1.0F - glide)
            + Waves.wave(time * 0.9F + m.seed * 50.0F) * 0.3F
            - glide * 0.2F
            + PUSH[1];
    m.vx = Waves.approach(m.vx, tx, 3.0F, dt);
    m.vy = Waves.approach(m.vy, ty, 3.0F, dt);
    m.vz = Waves.approach(m.vz, tz, 3.0F, dt);
    if (move(m, dt, ground)) {
      m.spin += Waves.TURN * 0.5F;
    }
    return true;
  }

  private static boolean bubble(
      Mote m, Kind kind, float dt, float time, Wind wind, View view, Ground ground) {
    if (m.age > m.life + POP) {
      return false;
    }
    if (m.age > m.life) {
      return true;
    }
    float tx = wind.x * 0.35F + Waves.wave(time * 0.6F + m.seed * 40.0F) * 0.25F;
    float tz = wind.z * 0.35F + Waves.wave(time * 0.55F + m.seed * 70.0F + 2.0F) * 0.25F;
    float ty = m.drop + Waves.wave(time * 0.8F + m.seed * 20.0F) * 0.12F;
    m.vx = Waves.approach(m.vx, tx, 1.2F, dt);
    m.vy = Waves.approach(m.vy, ty, 1.2F, dt);
    m.vz = Waves.approach(m.vz, tz, 1.2F, dt);
    m.phase += dt * m.rate;
    move(m, dt, ground);
    return Math.abs(m.y - view.y) < kind.reach * 0.7F;
  }

  private static boolean wisp(Mote m, float dt, float time, Wind wind, View view, Ground ground) {
    if (strayed(m)) {
      settle(m, ground, view.y + HOVER_LOOK);
    }
    float t = time * m.rate + m.phase * Waves.TURN;
    float radius = 0.9F + m.seed;
    shy(m, view, PUSH);
    float tx =
        -Waves.sin(t) * radius * m.rate * 1.6F
            + Waves.wave(time * 0.13F + m.seed * 40.0F) * 0.4F
            + wind.x * 0.08F
            + PUSH[0];
    float tz =
        Waves.cos(t) * radius * m.rate * 1.6F
            + Waves.wave(time * 0.11F + m.seed * 60.0F) * 0.4F
            + wind.z * 0.08F
            + PUSH[2];
    float ty = (float) (m.floor + m.hover - m.y) * 0.7F + Waves.cos(t * 1.6F) * 0.35F + PUSH[1];
    m.vx = Waves.approach(m.vx, tx, 2.0F, dt);
    m.vy = Waves.approach(m.vy, ty, 2.0F, dt);
    m.vz = Waves.approach(m.vz, tz, 2.0F, dt);
    move(m, dt, ground);
    m.trailClock += dt;
    while (m.trailClock >= TRAIL_STEP) {
      m.trailClock -= TRAIL_STEP;
      m.trailHead = (m.trailHead + 1) % TRAIL;
      m.trail[m.trailHead * 3] = m.x;
      m.trail[m.trailHead * 3 + 1] = m.y;
      m.trail[m.trailHead * 3 + 2] = m.z;
    }
    return true;
  }

  private static boolean firefly(
      Mote m, float dt, float time, Wind wind, View view, Ground ground) {
    if (strayed(m)) {
      settle(m, ground, view.y + HOVER_LOOK);
    }
    float t = time * (0.3F + m.seed * 0.3F);
    float tx = Waves.wave(t + m.seed * 40.0F) * 0.55F + wind.x * 0.1F;
    float tz = Waves.wave(t * 1.13F + m.seed * 70.0F + 2.1F) * 0.55F + wind.z * 0.1F;
    float ty =
        Waves.wave(t * 0.8F + m.seed * 90.0F + 4.3F) * 0.22F
            + (float) (m.floor + m.hover - m.y) * 0.8F;
    shy(m, view, PUSH);
    tx += PUSH[0];
    ty += PUSH[1];
    tz += PUSH[2];
    m.vx = Waves.approach(m.vx, tx, 1.6F, dt);
    m.vy = Waves.approach(m.vy, ty, 1.6F, dt);
    m.vz = Waves.approach(m.vz, tz, 1.6F, dt);
    move(m, dt, ground);
    return true;
  }

  private static boolean fall(
      Mote m,
      Kind kind,
      float dt,
      float time,
      Wind wind,
      View view,
      Ground ground,
      float sway,
      float rest) {
    if (m.landed) {
      m.rest -= dt;
      return m.rest > 0.0F;
    }
    float flutter = Waves.wave(time * 1.9F + m.seed * 30.0F);
    float drift = 0.85F + m.seed * 0.5F;
    float tx = wind.x * drift + Waves.sin(time * m.rate * 0.9F + m.seed * 20.0F) * sway;
    float tz = wind.z * drift + Waves.cos(time * m.rate * 0.7F + m.seed * 11.0F) * sway;
    float ty = -m.drop * (0.85F + 0.3F * flutter);
    m.vx = Waves.approach(m.vx, tx, 2.2F, dt);
    m.vy = Waves.approach(m.vy, ty, 2.2F, dt);
    m.vz = Waves.approach(m.vz, tz, 2.2F, dt);
    m.spin += dt * m.rate * (0.7F + 0.3F * flutter);
    m.tumble += dt * m.rate * 0.65F;
    float sinking = m.vy;
    if (move(m, dt, ground) && sinking < 0.0F && m.vy >= 0.0F) {
      land(m, m.cellY + Math.max(0.0F, m.ledge), rest);
      return true;
    }
    if (strayed(m)) {
      settle(m, ground, m.y + 1.0);
    }
    double bottom = view.y - kind.reach * BOTTOM;
    if (m.y <= m.floor + LIFT && m.floor > bottom) {
      land(m, m.floor, rest);
      return true;
    }
    return m.y > bottom && m.y < view.y + kind.reach * (TOP + 0.4F);
  }

  private static boolean ember(
      Mote m, Kind kind, float dt, float time, Wind wind, View view, Ground ground) {
    if (m.age > m.life) {
      return false;
    }
    float heat = 1.0F - m.age / m.life;
    float tx =
        wind.x * 0.3F
            + Waves.wave(time * 1.7F + m.seed * 50.0F + (float) (m.y - view.y) * 0.35F) * 0.8F;
    float tz =
        wind.z * 0.3F
            + Waves.wave(time * 1.5F + m.seed * 80.0F + (float) (m.x - view.x) * 0.35F) * 0.8F;
    float ty = m.drop * (0.45F + 0.55F * heat) + Waves.wave(time * 2.3F + m.seed * 13.0F) * 0.25F;
    m.vx = Waves.approach(m.vx, tx, 2.6F, dt);
    m.vy = Waves.approach(m.vy, ty, 2.6F, dt);
    m.vz = Waves.approach(m.vz, tz, 2.6F, dt);
    move(m, dt, ground);
    return m.y < view.y + kind.reach * BOTTOM;
  }

  private static boolean mote(
      Mote m, Kind kind, float dt, float time, Wind wind, View view, Ground ground) {
    if (m.age > m.life) {
      return false;
    }
    float tx = wind.x * 0.07F + Waves.wave(time * 0.31F + m.seed * 60.0F) * 0.09F;
    float tz = wind.z * 0.07F + Waves.wave(time * 0.29F + m.seed * 90.0F + 1.7F) * 0.09F;
    float ty = Waves.wave(time * 0.27F + m.seed * 80.0F) * 0.06F + 0.015F;
    m.vx = Waves.approach(m.vx, tx, 0.8F, dt);
    m.vy = Waves.approach(m.vy, ty, 0.8F, dt);
    m.vz = Waves.approach(m.vz, tz, 0.8F, dt);
    move(m, dt, ground);
    return Math.abs(m.y - view.y) < kind.reach * 0.6F;
  }

  private static void land(Mote m, double surface, float rest) {
    m.y = surface + LIFT;
    m.landed = true;
    m.rest = rest * (0.6F + 0.8F * m.seed);
    m.vx = 0.0F;
    m.vy = 0.0F;
    m.vz = 0.0F;
  }

  private static boolean move(Mote m, float dt, Ground ground) {
    double nx = m.x + m.vx * dt;
    double ny = m.y + m.vy * dt;
    double nz = m.z + m.vz * dt;
    int bx = (int) Math.floor(nx);
    int by = (int) Math.floor(ny);
    int bz = (int) Math.floor(nz);
    boolean hit = false;
    if (bx != m.cellX || by != m.cellY || bz != m.cellZ) {
      float top = ground.top(bx, by, bz);
      if (m.buried) {
        m.buried = blocks(top, ny - by);
      } else if (blocks(top, ny - by)) {
        hit = true;
        if (bx != m.cellX && blocks(ground.top(bx, m.cellY, m.cellZ), m.y - m.cellY)) {
          m.vx *= -BOUNCE;
          nx = m.x;
          bx = m.cellX;
        }
        if (by != m.cellY && blocks(ground.top(bx, by, m.cellZ), ny - by)) {
          m.vy *= -BOUNCE;
          ny = m.y;
          by = m.cellY;
        }
        if (bz != m.cellZ && blocks(ground.top(bx, by, bz), ny - by)) {
          m.vz *= -BOUNCE;
          nz = m.z;
          bz = m.cellZ;
        }
        boolean moved = bx != m.cellX || by != m.cellY || bz != m.cellZ;
        top = moved ? ground.top(bx, by, bz) : m.ledge;
        if (moved && blocks(top, ny - by)) {
          m.vx *= -BOUNCE;
          m.vy *= -BOUNCE;
          m.vz *= -BOUNCE;
          return true;
        }
      }
      if (bx != m.cellX || by != m.cellY || bz != m.cellZ) {
        m.cellX = bx;
        m.cellY = by;
        m.cellZ = bz;
        m.ledge = top;
        sense(m, ground);
      }
    }
    if (!m.buried && m.ledge >= 0.0F && ny < m.cellY + m.ledge) {
      ny = m.cellY + m.ledge;
      m.vy *= -BOUNCE;
      hit = true;
    }
    m.x = nx;
    m.y = ny;
    m.z = nz;
    return hit;
  }
}
