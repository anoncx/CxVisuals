package me.cxiy.cxvisuals.modules.environment.particles;

import java.util.SplittableRandom;
import me.cxiy.cxvisuals.modules.environment.View;

public final class Meteors {
  public static final int CAPACITY = 10;
  public static final int QUADS = CAPACITY * 7;

  private static final float MEAN_WAIT = 9.0F;
  private static final float LEAST_WAIT = 1.0F;
  private static final float SHOWER_CHANCE = 0.08F;
  private static final float FIREBALL_CHANCE = 0.12F;
  private static final float CLEAR = 0.3F;
  private static final float WIDTH = 0.0045F;
  private static final float FLARE = 0.02F;
  private static final float HAZE = 0.06F;
  private static final float TRAIN = 0.022F;
  private static final float TRAIN_ARC = 0.16F;
  private static final float TRAIN_LIFE = 2.6F;
  private static final float FRAGMENT = 0.007F;
  private static final float GONE = 0.004F;
  private static final float RADIANS = (float) (Math.PI / 180.0);
  private static final int WHITE = 0xE6EEFF;
  private static final int GREEN = 0xC4FFDC;
  private static final int AMBER = 0xFFD9A8;
  private static final int VIOLET = 0xE8D4FF;

  private static final class Meteor {
    float dx;
    float dy;
    float dz;
    float tx;
    float ty;
    float tz;
    float speed;
    float length;
    float age;
    float life;
    float bright;
    float scale;
    float train;
    int colour;
    int seed;
    boolean fireball;
    boolean alive;
  }

  private final Meteor[] pool = new Meteor[CAPACITY];
  private final SplittableRandom random;
  private final float[] start = new float[3];
  private final float[] travel = new float[3];
  private final float[] from = new float[3];
  private final float[] heading = new float[3];
  private final float[] point = new float[3];
  private float wait = 4.0F;
  private int burst;
  private float burstWait;
  private int alive;

  public Meteors(long seed) {
    random = new SplittableRandom(seed);
    for (int i = 0; i < CAPACITY; i++) {
      pool[i] = new Meteor();
    }
  }

  public int alive() {
    return alive;
  }

  public void clear() {
    for (Meteor meteor : pool) {
      meteor.alive = false;
    }
    alive = 0;
    burst = 0;
  }

  public void shower() {
    aim(start, travel);
    burst = 3 + random.nextInt(4);
    burstWait = 0.0F;
  }

  public void update(float dt, float allow, float rate) {
    alive = 0;
    for (Meteor meteor : pool) {
      if (meteor.alive) {
        meteor.age += dt;
        meteor.alive = meteor.age < meteor.life + meteor.train;
        if (meteor.alive) {
          alive++;
        }
      }
    }
    if (allow < CLEAR) {
      burst = 0;
      return;
    }
    if (burst > 0) {
      burstWait -= dt;
      if (burstWait <= 0.0F) {
        launch(true);
        burst--;
        burstWait = 0.2F + random.nextFloat() * 0.7F;
      }
    }
    wait -= dt * rate * allow;
    if (wait <= 0.0F) {
      wait = LEAST_WAIT - (float) Math.log(1.0 - random.nextDouble()) * MEAN_WAIT;
      if (random.nextFloat() < SHOWER_CHANCE) {
        shower();
      } else {
        launch(false);
      }
    }
  }

  private void aim(float[] origin, float[] course) {
    float elevation = (18.0F + random.nextFloat() * 50.0F) * RADIANS;
    float azimuth = random.nextFloat() * Waves.TURN;
    origin[0] = Waves.cos(elevation) * Waves.sin(azimuth);
    origin[1] = Waves.sin(elevation);
    origin[2] = Waves.cos(elevation) * Waves.cos(azimuth);
    float bearing = random.nextFloat() * Waves.TURN;
    float dip = (15.0F + random.nextFloat() * 45.0F) * RADIANS;
    course[0] = Waves.cos(dip) * Waves.sin(bearing);
    course[1] = -Waves.sin(dip);
    course[2] = Waves.cos(dip) * Waves.cos(bearing);
  }

  private void launch(boolean fromShower) {
    Meteor meteor = null;
    for (Meteor candidate : pool) {
      if (!candidate.alive) {
        meteor = candidate;
        break;
      }
    }
    if (meteor == null) {
      return;
    }
    float dx;
    float dy;
    float dz;
    float tx;
    float ty;
    float tz;
    if (fromShower) {
      dx = start[0] + (random.nextFloat() - 0.5F) * 0.5F;
      dy = start[1] + (random.nextFloat() - 0.5F) * 0.3F;
      dz = start[2] + (random.nextFloat() - 0.5F) * 0.5F;
      tx = travel[0] + (random.nextFloat() - 0.5F) * 0.15F;
      ty = travel[1] + (random.nextFloat() - 0.5F) * 0.15F;
      tz = travel[2] + (random.nextFloat() - 0.5F) * 0.15F;
    } else {
      aim(from, heading);
      dx = from[0];
      dy = from[1];
      dz = from[2];
      tx = heading[0];
      ty = heading[1];
      tz = heading[2];
    }
    float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    dx /= length;
    dy /= length;
    dz /= length;
    float along = tx * dx + ty * dy + tz * dz;
    tx -= dx * along;
    ty -= dy * along;
    tz -= dz * along;
    length = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
    if (length < 1.0e-3F) {
      return;
    }
    meteor.dx = dx;
    meteor.dy = dy;
    meteor.dz = dz;
    meteor.tx = tx / length;
    meteor.ty = ty / length;
    meteor.tz = tz / length;
    meteor.fireball = random.nextFloat() < FIREBALL_CHANCE;
    if (meteor.fireball) {
      meteor.speed = 0.2F + random.nextFloat() * 0.1F;
      meteor.life = 1.3F + random.nextFloat() * 0.7F;
      meteor.length = 0.12F + random.nextFloat() * 0.08F;
      meteor.bright = 1.0F;
      meteor.scale = 2.0F + random.nextFloat() * 0.6F;
      meteor.train = TRAIN_LIFE * (0.7F + random.nextFloat() * 0.6F);
    } else {
      meteor.speed = 0.32F + random.nextFloat() * 0.3F;
      meteor.life = 0.55F + random.nextFloat() * 0.75F;
      meteor.length = 0.07F + random.nextFloat() * 0.1F;
      meteor.bright = 0.6F + random.nextFloat() * 0.4F;
      meteor.scale = 0.85F + random.nextFloat() * 0.35F;
      meteor.train = 0.0F;
    }
    float hue = random.nextFloat();
    meteor.colour = hue < 0.12F ? GREEN : hue < 0.24F ? AMBER : hue < 0.3F ? VIOLET : WHITE;
    meteor.seed = random.nextInt(32768);
    meteor.age = 0.0F;
    meteor.alive = true;
    alive++;
  }

  public void emit(View view, Sink sink, float distance, float glow) {
    for (Meteor meteor : pool) {
      if (meteor.alive) {
        if (meteor.train > 0.0F) {
          train(meteor, sink, distance, glow);
        }
        if (meteor.age < meteor.life) {
          draw(meteor, view, sink, distance, glow);
        }
      }
    }
  }

  private void at(Meteor meteor, float angle, float distance) {
    float c = Waves.cos(angle);
    float s = Waves.sin(angle);
    point[0] = (meteor.dx * c + meteor.tx * s) * distance;
    point[1] = (meteor.dy * c + meteor.ty * s) * distance;
    point[2] = (meteor.dz * c + meteor.tz * s) * distance;
  }

  private void streak(
      Meteor meteor,
      Sink sink,
      float head,
      float tail,
      float distance,
      float width,
      int argb,
      float detail) {
    at(meteor, head, distance);
    float hx = point[0];
    float hy = point[1];
    float hz = point[2];
    at(meteor, tail, distance);
    float mx = (hx + point[0]) * 0.5F;
    float my = (hy + point[1]) * 0.5F;
    float mz = (hz + point[2]) * 0.5F;
    float ax = (hx - point[0]) * 0.5F;
    float ay = (hy - point[1]) * 0.5F;
    float az = (hz - point[2]) * 0.5F;
    float cx = ay * mz - az * my;
    float cy = az * mx - ax * mz;
    float cz = ax * my - ay * mx;
    float across = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
    if (across > 1.0e-4F) {
      float scale = distance * width / across;
      sink.quad(
          mx,
          my,
          mz,
          ax,
          ay,
          az,
          cx * scale,
          cy * scale,
          cz * scale,
          argb,
          Shape.METEOR,
          detail,
          meteor.seed);
    }
  }

  private void train(Meteor meteor, Sink sink, float distance, float glow) {
    float head = meteor.speed * Math.min(meteor.age, meteor.life);
    float kindle = Waves.smooth(0.2F, 0.6F, meteor.age);
    float after = Math.max(0.0F, meteor.age - meteor.life) / meteor.train;
    float alpha = Math.min(1.0F, kindle * (1.0F - after) * (1.0F - after) * 0.55F * glow);
    if (alpha > GONE) {
      streak(
          meteor,
          sink,
          head,
          Math.max(0.0F, head - TRAIN_ARC),
          distance,
          TRAIN * (1.0F + after * 1.5F),
          Waves.argb(Waves.mix(meteor.colour, GREEN, 0.5F), alpha),
          0.5F + after * 0.5F);
    }
  }

  private void draw(Meteor meteor, View view, Sink sink, float distance, float glow) {
    float progress = meteor.age / meteor.life;
    float envelope =
        Waves.smooth(0.0F, 0.1F, meteor.age) * (1.0F - Waves.smooth(0.55F, 1.0F, progress));
    float flare =
        meteor.fireball
            ? 1.0F + 1.6F * (float) Math.exp(-(progress - 0.8F) * (progress - 0.8F) * 180.0F)
            : 1.0F;
    float alpha = Math.min(1.0F, envelope * meteor.bright * glow);
    if (alpha <= GONE) {
      return;
    }
    float head = meteor.speed * meteor.age;
    float tail =
        Math.max(0.0F, head - meteor.length * Math.min(1.0F, meteor.age / (meteor.life * 0.4F)));
    int argb = Waves.argb(meteor.colour, alpha);
    streak(meteor, sink, head, tail, distance, WIDTH * meteor.scale, argb, 0.0F);
    if (meteor.fireball && progress > 0.6F) {
      float split = (progress - 0.6F) * 2.5F;
      for (int k = 1; k <= 2; k++) {
        at(meteor, head - meteor.length * split * 0.18F * k, distance);
        float size = distance * FRAGMENT * (1.0F - 0.3F * k);
        float sideways = distance * 0.004F * split * (k == 1 ? 1.0F : -1.0F);
        sink.quad(
            point[0] + view.ux * sideways,
            point[1] + view.uy * sideways,
            point[2] + view.uz * sideways,
            view.rx * size,
            view.ry * size,
            view.rz * size,
            view.ux * size,
            view.uy * size,
            view.uz * size,
            Waves.argb(Waves.mix(meteor.colour, AMBER, 0.5F), alpha * split),
            Shape.GLOW,
            0.2F,
            meteor.seed + k);
      }
    }
    at(meteor, head, distance);
    float haze = distance * HAZE * meteor.scale * flare;
    sink.quad(
        point[0],
        point[1],
        point[2],
        view.rx * haze,
        view.ry * haze,
        view.rz * haze,
        view.ux * haze,
        view.uy * haze,
        view.uz * haze,
        Waves.argb(meteor.colour, Math.min(1.0F, alpha * 0.22F * flare)),
        Shape.HALO,
        0.0F,
        meteor.seed);
    float spark = distance * FLARE * meteor.scale * (0.8F + 0.2F * flare);
    sink.quad(
        point[0],
        point[1],
        point[2],
        view.rx * spark,
        view.ry * spark,
        view.rz * spark,
        view.ux * spark,
        view.uy * spark,
        view.uz * spark,
        Waves.argb(meteor.colour, alpha),
        Shape.GLOW,
        meteor.fireball ? 0.6F : 0.35F,
        meteor.seed);
  }
}
