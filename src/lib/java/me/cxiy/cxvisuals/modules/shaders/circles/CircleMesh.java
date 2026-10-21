package me.cxiy.cxvisuals.modules.shaders.circles;

import me.cxiy.cxvisuals.modules.environment.View;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;

public final class CircleMesh {
  public static final int FLOOR = 0;
  public static final int WALL = 1;
  public static final int SPARK = 2;
  public static final int SEGMENTS = 64;
  public static final int SPARKS = 18;
  public static final int MOST_ECHOES = 3;
  public static final int QUADS = Circles.CAPACITY * (1 + MOST_ECHOES + SEGMENTS + SPARKS);

  private static final float TURN = (float) (Math.PI * 2.0);
  private static final float FADE_FROM = 32.0F;
  private static final float FADE_TO = 48.0F;
  private static final float ECHO_DIM = 0.55F;
  private static final float GRAVITY = 9.0F;
  private static final float SPARK_LIFE = 0.85F;
  private static final float LIFT = 0.02F;
  private static final float LIFT_PER_BLOCK = 0.0015F;
  private static final float SMALLEST = 0.03F;
  private static final float FAINTEST = 0.004F;

  private final float[] rimX = new float[SEGMENTS + 1];
  private final float[] rimY = new float[SEGMENTS + 1];
  private Contour sampled;
  private float peak = 1.0F;
  private int quads;

  public int emit(Circles circles, View view, ShadersModule module, long nanos, CircleSink out) {
    quads = 0;
    sample(module.ringContour());
    float seconds = Math.max(0.05F, module.ringSeconds());
    float spin = module.ringSpinRate();
    float size = module.ringRadius();
    float line = module.ringLineBlocks();
    float glow = module.ringGlowScale();
    float wall = module.wallBlocks();
    int echoes = Math.min(MOST_ECHOES, module.echoCount());
    float delay = module.echoDelay();
    boolean sparks = module.sparks.get();
    Spread spread = module.spread.get();
    for (int i = 0; i < circles.count(); i++) {
      float age = circles.age(i, nanos);
      float strength = circles.strength(i);
      float dx = (float) (circles.x(i) - view.x);
      float dy = (float) (circles.y(i) - view.y);
      float dz = (float) (circles.z(i) - view.z);
      float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
      float reach = size * strength * Math.max(peak, 1.0F) * 2.2F + wall + 1.0F;
      if (distance > FADE_TO + reach || dx * view.fx + dy * view.fy + dz * view.fz < -reach) {
        continue;
      }
      float near = 1.0F - smooth((distance - FADE_FROM) / (FADE_TO - FADE_FROM));
      float angle = circles.yaw(i) + spin * age;
      float fx = (float) -Math.sin(angle);
      float fz = (float) Math.cos(angle);
      float lift = LIFT + distance * LIFT_PER_BLOCK;
      int seed = circles.seed(i);
      for (int echo = 0; echo <= echoes; echo++) {
        float progress = (age - echo * delay) / seconds;
        if (progress < 0.0F || progress >= 1.0F) {
          continue;
        }
        float radius = size * strength * spread.scale(progress);
        float alpha = spread.fade(progress) * near * (float) Math.pow(ECHO_DIM, echo);
        if (radius < SMALLEST || alpha < FAINTEST) {
          continue;
        }
        floor(out, dx, dy + lift, dz, fx, fz, radius, line, glow, progress, alpha, seed + echo * 7);
        if (echo == 0 && wall > 0.0F) {
          float height = wall * strength * smooth(progress / 0.22F) * (1.0F - 0.35F * progress);
          if (height > SMALLEST) {
            wall(out, dx, dy + lift, dz, fx, fz, radius, height, progress, alpha, seed);
          }
        }
      }
      if (sparks) {
        sparks(
            out,
            view,
            dx,
            dy + lift,
            dz,
            fx,
            fz,
            size * strength,
            age,
            seconds * SPARK_LIFE,
            near,
            seed);
      }
    }
    return quads;
  }

  public float peak() {
    return peak;
  }

  private void sample(Contour contour) {
    if (contour == sampled) {
      return;
    }
    sampled = contour;
    peak = contour.peak();
    for (int j = 0; j <= SEGMENTS; j++) {
      float turn = (float) j / SEGMENTS;
      float radius = contour.at(turn);
      rimX[j] = (float) Math.cos(turn * TURN) * radius;
      rimY[j] = (float) Math.sin(turn * TURN) * radius;
    }
  }

  private void floor(
      CircleSink out,
      float cx,
      float cy,
      float cz,
      float fx,
      float fz,
      float radius,
      float line,
      float glow,
      float progress,
      float alpha,
      int seed) {
    float local = peak + (line * 4.0F + 1.15F * glow) / radius + 0.15F;
    float extent = local * radius;
    corner(out, cx, cy, cz, -extent, -extent, fx, fz, radius, progress, alpha, seed);
    corner(out, cx, cy, cz, extent, -extent, fx, fz, radius, progress, alpha, seed);
    corner(out, cx, cy, cz, extent, extent, fx, fz, radius, progress, alpha, seed);
    corner(out, cx, cy, cz, -extent, -extent, fx, fz, radius, progress, alpha, seed);
    corner(out, cx, cy, cz, extent, extent, fx, fz, radius, progress, alpha, seed);
    corner(out, cx, cy, cz, -extent, extent, fx, fz, radius, progress, alpha, seed);
    quads++;
  }

  private static void corner(
      CircleSink out,
      float cx,
      float cy,
      float cz,
      float ox,
      float oz,
      float fx,
      float fz,
      float radius,
      float progress,
      float alpha,
      int seed) {
    float u = (ox * fx + oz * fz) / radius;
    float v = (ox * fz - oz * fx) / radius;
    out.vertex(cx + ox, cy, cz + oz, u, v, FLOOR, progress, alpha, seed, radius);
  }

  private void wall(
      CircleSink out,
      float cx,
      float cy,
      float cz,
      float fx,
      float fz,
      float radius,
      float height,
      float progress,
      float alpha,
      int seed) {
    float top = cy + height;
    for (int j = 0; j < SEGMENTS; j++) {
      float ax = cx + (rimX[j] * fx + rimY[j] * fz) * radius;
      float az = cz + (rimX[j] * fz - rimY[j] * fx) * radius;
      float bx = cx + (rimX[j + 1] * fx + rimY[j + 1] * fz) * radius;
      float bz = cz + (rimX[j + 1] * fz - rimY[j + 1] * fx) * radius;
      float u0 = (float) j / SEGMENTS;
      float u1 = (float) (j + 1) / SEGMENTS;
      out.vertex(ax, cy, az, u0, 0.0F, WALL, progress, alpha, seed, radius);
      out.vertex(bx, cy, bz, u1, 0.0F, WALL, progress, alpha, seed, radius);
      out.vertex(bx, top, bz, u1, 1.0F, WALL, progress, alpha, seed, radius);
      out.vertex(ax, cy, az, u0, 0.0F, WALL, progress, alpha, seed, radius);
      out.vertex(bx, top, bz, u1, 1.0F, WALL, progress, alpha, seed, radius);
      out.vertex(ax, top, az, u0, 1.0F, WALL, progress, alpha, seed, radius);
      quads++;
    }
  }

  private void sparks(
      CircleSink out,
      View view,
      float cx,
      float cy,
      float cz,
      float fx,
      float fz,
      float radius,
      float age,
      float life,
      float near,
      int seed) {
    if (age >= life) {
      return;
    }
    float t = age / life;
    float travel = 1.0F - (1.0F - t) * (1.0F - t);
    float fade = (float) Math.pow(1.0F - t, 1.4F) * smooth(t / 0.05F) * near;
    if (fade < FAINTEST) {
      return;
    }
    for (int k = 0; k < SPARKS; k++) {
      int grain = mix(seed * 131 + k);
      float turn = (k + unit(grain)) / SPARKS;
      float speed = unit(grain >>> 8);
      float rise = unit(grain >>> 16);
      int index = Math.round(turn * SEGMENTS);
      float reach = 0.3F + (0.8F + 0.9F * speed) * travel;
      float lx = rimX[index] * reach * radius;
      float ly = rimY[index] * reach * radius;
      float px = cx + lx * fx + ly * fz;
      float pz = cz + lx * fz - ly * fx;
      float height = Math.max(0.03F, (1.6F + 2.2F * rise) * age - 0.5F * GRAVITY * age * age);
      float py = cy + height;
      float scale = (0.035F + 0.035F * unit(grain >>> 4)) * (1.0F - 0.4F * t);
      float rx = view.rx * scale;
      float ry = view.ry * scale;
      float rz = view.rz * scale;
      float ux = view.ux * scale;
      float uy = view.uy * scale;
      float uz = view.uz * scale;
      int tone = grain & 0xFFF;
      out.vertex(
          px - rx - ux, py - ry - uy, pz - rz - uz, -1.0F, -1.0F, SPARK, t, fade, tone, radius);
      out.vertex(
          px + rx - ux, py + ry - uy, pz + rz - uz, 1.0F, -1.0F, SPARK, t, fade, tone, radius);
      out.vertex(
          px + rx + ux, py + ry + uy, pz + rz + uz, 1.0F, 1.0F, SPARK, t, fade, tone, radius);
      out.vertex(
          px - rx - ux, py - ry - uy, pz - rz - uz, -1.0F, -1.0F, SPARK, t, fade, tone, radius);
      out.vertex(
          px + rx + ux, py + ry + uy, pz + rz + uz, 1.0F, 1.0F, SPARK, t, fade, tone, radius);
      out.vertex(
          px - rx + ux, py - ry + uy, pz - rz + uz, -1.0F, 1.0F, SPARK, t, fade, tone, radius);
      quads++;
    }
  }

  private static int mix(int value) {
    int h = value * 0x9E3779B1;
    h ^= h >>> 15;
    h *= 0x85EBCA77;
    h ^= h >>> 13;
    return h;
  }

  private static float unit(int bits) {
    return (bits & 0xFF) / 255.0F;
  }

  private static float smooth(float t) {
    float c = t < 0.0F ? 0.0F : Math.min(t, 1.0F);
    return c * c * (3.0F - 2.0F * c);
  }
}
