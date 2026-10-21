package me.cxiy.cxvisuals.modules.shaders.trails;

import me.cxiy.cxvisuals.modules.environment.View;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;

public final class TrailMesh {
  public static final int QUADS = 12288;

  private static final int POINTS = 160;
  private static final int SPUR_POINTS = 4;
  private static final float FAINTEST = 0.004F;
  private static final float NEAR_FROM = 0.7F;
  private static final float NEAR_SPAN = 1.5F;
  private static final float GHOST_GAP = 0.085F;
  private static final float GHOSTS = 9.0F;
  private static final float RING_GAP = 0.2F;
  private static final float GRAVITY = 3.2F;
  private static final float GRID = 0.075F;
  private static final long FLICKER_NANOS = 45_000_000L;
  private static final long GLITCH_NANOS = 70_000_000L;
  private static final float[] LANES = {0.2F, 0.5F, 0.82F};
  private static final float[] LANE_REACH = {0.7F, 1.0F, 0.85F};
  private static final float[] LANE_WIDTH = {0.07F, 0.1F, 0.07F};
  private static final float[] BOLTS = {0.35F, 0.72F};

  private final float[] bx = new float[POINTS];
  private final float[] by = new float[POINTS];
  private final float[] bz = new float[POINTS];
  private final float[] bw = new float[POINTS];
  private final float[] ba = new float[POINTS];
  private final float[] bu = new float[POINTS];
  private final float[] sx = new float[SPUR_POINTS];
  private final float[] sy = new float[SPUR_POINTS];
  private final float[] sz = new float[SPUR_POINTS];
  private final float[] sw = new float[SPUR_POINTS];
  private final float[] sa = new float[SPUR_POINTS];
  private final float[] su = new float[SPUR_POINTS];
  private final float[] edgeX = new float[POINTS];
  private final float[] edgeY = new float[POINTS];
  private final float[] edgeZ = new float[POINTS];

  private View view;
  private TrailSink out;
  private long now;
  private float life;
  private float size;
  private float reach;
  private int quads;

  public int emit(Wakes wakes, View eye, ShadersModule module, long nanos, TrailSink sink) {
    begin(eye, module, nanos, sink);
    for (int i = 0; i < wakes.count() && quads < QUADS; i++) {
      wake(wakes.get(i), module.trailStyle.get());
    }
    return quads;
  }

  public int emit(Wake wake, View eye, ShadersModule module, long nanos, TrailSink sink) {
    begin(eye, module, nanos, sink);
    wake(wake, module.trailStyle.get());
    return quads;
  }

  private void begin(View eye, ShadersModule module, long nanos, TrailSink sink) {
    view = eye;
    out = sink;
    now = nanos;
    life = Math.max(0.1F, module.trailSeconds());
    size = module.trailSizeScale();
    reach = module.trailReach();
    quads = 0;
  }

  private void wake(Wake w, Style style) {
    int n = w.count();
    if (n == 0) {
      return;
    }
    float hx = (float) (w.x(0) - view.x);
    float hy = (float) (w.y(0) - view.y);
    float hz = (float) (w.z(0) - view.z);
    float distance = (float) Math.sqrt(hx * hx + hy * hy + hz * hz);
    float tx = (float) (w.x(n - 1) - w.x(0));
    float ty = (float) (w.y(n - 1) - w.y(0));
    float tz = (float) (w.z(n - 1) - w.z(0));
    float spread = (float) Math.sqrt(tx * tx + ty * ty + tz * tz) + w.height() + 2.0F;
    if (distance - spread > reach || hx * view.fx + hy * view.fy + hz * view.fz < -spread) {
      return;
    }
    int stride = distance < 16.0F ? 1 : distance < 32.0F ? 2 : 3;
    switch (style) {
      case SMOKE -> smoke(w, stride);
      case STREAK -> streak(w, stride);
      case ECHO -> echo(w);
      case EMBERS -> embers(w, stride);
      case ARC -> arc(w, stride);
      case AURORA -> aurora(w, stride);
      case GLITCH -> glitch(w, stride);
      case PULSE -> pulse(w);
    }
  }

  private void smoke(Wake w, int stride) {
    float width = w.width();
    float height = w.height();
    int every = 2 * stride;
    for (int i = 0; i < w.count(); i++) {
      float age = w.age(i, now);
      float t = age / life;
      if (t >= 1.0F) {
        break;
      }
      int id = w.serial(i);
      if (id % every != 0 || !room(1)) {
        continue;
      }
      float x = (float) (w.x(i) - view.x) + (unit(id, 1) - 0.5F) * (0.3F * width + age * 0.5F);
      float y =
          (float) (w.y(i) - view.y)
              + height * (0.06F + 0.22F * unit(id, 2))
              + age * (0.18F + 0.3F * unit(id, 3));
      float z = (float) (w.z(i) - view.z) + (unit(id, 4) - 0.5F) * (0.3F * width + age * 0.5F);
      float radius = Math.max(0.1F, width * size * (0.3F + 0.8F * eased(t))) * 1.3F;
      float alpha =
          fade(x, y, z) * (1.0F - t) * (float) Math.sqrt(1.0F - t) * smooth(age / 0.07F) * 0.85F;
      if (alpha >= FAINTEST) {
        billboard(x, y, z, radius, radius, Style.SMOKE, t, alpha, unit(id, 5), 0.0F, id, radius);
      }
    }
  }

  private void streak(Wake w, int stride) {
    float height = w.height();
    for (int lane = 0; lane < LANES.length; lane++) {
      int m = 0;
      float length = LANE_REACH[lane];
      float half = LANE_WIDTH[lane] * size;
      for (int i = 0; i < w.count() && m < POINTS; i += stride) {
        float age = w.age(i, now);
        float t = age / (life * length);
        if (t >= 1.0F) {
          break;
        }
        int before = Math.max(0, i - stride);
        int after = Math.min(w.count() - 1, i + stride);
        float dx = (float) (w.x(before) - w.x(after));
        float dz = (float) (w.z(before) - w.z(after));
        float run = Math.max(1.0e-4F, (float) Math.sqrt(dx * dx + dz * dz));
        float fan = (lane - 1) * (0.1F + 0.5F * t) * w.width() * size;
        float x = (float) (w.x(i) - view.x) - dz / run * fan;
        float y = (float) (w.y(i) - view.y) + height * LANES[lane];
        float z = (float) (w.z(i) - view.z) + dx / run * fan;
        float body = 1.0F - t;
        bx[m] = x;
        by[m] = y;
        bz[m] = z;
        bw[m] = half * (float) Math.sqrt(body) * (0.35F + 0.65F * smooth(age / 0.06F));
        ba[m] = fade(x, y, z) * body * (float) Math.sqrt(body) * 0.9F;
        bu[m] = t;
        m++;
      }
      if (m > 1 && room(m - 1)) {
        ribbon(bx, by, bz, bw, ba, bu, m, Style.STREAK, lane / 2.0F, w.identity() * 3 + lane, 0.0F);
      }
    }
  }

  private void echo(Wake w) {
    float radius = w.width() * 0.5F;
    float halfX = radius * 1.9F;
    float halfY = w.height() * 0.5F + radius * 0.9F;
    float aspect = Math.min(halfY / halfX / 8.0F, 1.0F);
    float lastAge = -1.0F;
    for (int i = 0; i < w.count(); i++) {
      float age = w.age(i, now);
      float t = age / life;
      if (t >= 1.0F) {
        break;
      }
      if (age < 0.05F || age - lastAge < GHOST_GAP || !room(1)) {
        continue;
      }
      lastAge = age;
      float x = (float) (w.x(i) - view.x);
      float y = (float) (w.y(i) - view.y) + w.height() * 0.5F;
      float z = (float) (w.z(i) - view.z);
      float body = 1.0F - t;
      float alpha = fade(x, y, z) * body * (float) Math.sqrt(body) * 0.7F;
      if (alpha >= FAINTEST) {
        billboard(
            x,
            y,
            z,
            halfX,
            halfY,
            Style.ECHO,
            t,
            alpha,
            aspect,
            Math.min(1.0F, t * 1.1F),
            w.serial(i),
            halfX);
      }
    }
  }

  private void embers(Wake w, int stride) {
    float width = w.width();
    float height = w.height();
    for (int i = 0; i < w.count(); i++) {
      float age = w.age(i, now);
      if (age >= life) {
        break;
      }
      int id = w.serial(i);
      if (id % stride != 0) {
        continue;
      }
      for (int k = 0; k < 2; k++) {
        int seed = id * 2 + k;
        float span = life * (0.35F + 0.65F * unit(seed, 1));
        if (age >= span || !room(1)) {
          continue;
        }
        float t = age / span;
        float x =
            (float) (w.x(i) - view.x)
                + (unit(seed, 2) - 0.5F) * width * 0.8F
                + (unit(seed, 4) - 0.5F) * 1.4F * age;
        float y =
            (float) (w.y(i) - view.y)
                + height * (0.05F + 0.8F * unit(seed, 3))
                + (0.5F + 1.6F * unit(seed, 5)) * age
                - 0.5F * GRAVITY * age * age;
        float z =
            (float) (w.z(i) - view.z)
                + (unit(seed, 6) - 0.5F) * width * 0.8F
                + (unit(seed, 7) - 0.5F) * 1.4F * age;
        float scale = (0.03F + 0.04F * unit(seed, 8)) * size * (1.0F - 0.5F * t);
        float alpha = fade(x, y, z) * (1.0F - t) * (float) Math.sqrt(1.0F - t);
        if (alpha >= FAINTEST) {
          billboard(
              x, y, z, scale, scale, Style.EMBERS, t, alpha, unit(seed, 9), 0.0F, seed, scale);
        }
      }
    }
  }

  private void arc(Wake w, int stride) {
    int bucket = (int) (now / FLICKER_NANOS);
    float height = w.height();
    float amplitude = 0.22F * size;
    int step = 2 * stride;
    for (int bolt = 0; bolt < BOLTS.length; bolt++) {
      if (unit(bucket, 40 + bolt) < 0.12F) {
        continue;
      }
      float flicker = 0.75F + 0.5F * unit(bucket, 50 + bolt);
      int m = 0;
      int forks = 0;
      for (int i = 0; i < w.count() && m < POINTS; i += step, m++) {
        float age = w.age(i, now);
        float t = age / life;
        if (t >= 1.0F) {
          break;
        }
        int seed = bucket * 131 + bolt * 7919 + m * 13;
        float swing = amplitude * Math.min(1.0F, m / 3.0F) * (0.5F + 0.5F * t);
        float x = (float) (w.x(i) - view.x) + (unit(seed, 1) - 0.5F) * 2.0F * swing;
        float y =
            (float) (w.y(i) - view.y)
                + height * BOLTS[bolt]
                + (unit(seed, 2) - 0.5F) * 1.4F * swing;
        float z = (float) (w.z(i) - view.z) + (unit(seed, 3) - 0.5F) * 2.0F * swing;
        bx[m] = x;
        by[m] = y;
        bz[m] = z;
        bw[m] = 0.06F * size * (1.0F - t * 0.7F);
        ba[m] = fade(x, y, z) * (1.0F - t) * (float) Math.sqrt(1.0F - t) * flicker;
        bu[m] = t;
        if (forks < 3 && m > 2 && unit(seed, 4) < 0.1F && room(SPUR_POINTS)) {
          spur(x, y, z, bw[m], ba[m], t, seed, bolt);
          forks++;
        }
      }
      if (m > 1 && room(m - 1)) {
        ribbon(bx, by, bz, bw, ba, bu, m, Style.ARC, bolt, bucket + bolt, 0.0F);
      }
    }
  }

  private void spur(
      float x, float y, float z, float width, float alpha, float t, int seed, int bolt) {
    float dx = unit(seed, 5) - 0.5F;
    float dy = unit(seed, 6) - 0.3F;
    float dz = unit(seed, 7) - 0.5F;
    float length = 0.12F * size / Math.max(0.1F, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
    for (int k = 0; k < SPUR_POINTS; k++) {
      float jitter = k == 0 ? 0.0F : 0.5F * (unit(seed, 8 + k) - 0.5F) * 0.12F * size;
      sx[k] = x + dx * length * k + jitter;
      sy[k] = y + dy * length * k + jitter;
      sz[k] = z + dz * length * k - jitter;
      sw[k] = width * 0.6F * (1.0F - k / (float) SPUR_POINTS);
      sa[k] = alpha * 0.7F * (1.0F - k / (float) SPUR_POINTS);
      su[k] = t;
    }
    ribbon(sx, sy, sz, sw, sa, su, SPUR_POINTS, Style.ARC, bolt, seed, 0.0F);
  }

  private void aurora(Wake w, int stride) {
    float height = w.height();
    int m = 0;
    for (int i = 0; i < w.count() && m < POINTS; i += stride) {
      float t = w.age(i, now) / life;
      if (t >= 1.0F) {
        break;
      }
      bx[m] = (float) (w.x(i) - view.x);
      by[m] = (float) (w.y(i) - view.y);
      bz[m] = (float) (w.z(i) - view.z);
      bu[m] = t;
      bw[m] = w.serial(i);
      m++;
    }
    if (m < 2) {
      return;
    }
    for (int k = 0; k < m; k++) {
      int before = Math.max(0, k - 1);
      int after = Math.min(m - 1, k + 1);
      float dx = bx[after] - bx[before];
      float dz = bz[after] - bz[before];
      float length = Math.max(1.0e-4F, (float) Math.sqrt(dx * dx + dz * dz));
      float offset = 0.3F * size + w.width() * 0.3F;
      edgeX[k] = -dz / length * offset;
      edgeZ[k] = dx / length * offset;
      float body = 1.0F - bu[k];
      ba[k] =
          fade(bx[k], by[k], bz[k])
              * body
              * (float) Math.sqrt(body)
              * smooth(bu[k] * life / 0.08F)
              * 1.15F;
    }
    for (int lane = -1; lane <= 1 && room(2 * (m - 1)); lane += 2) {
      for (int k = 0; k + 1 < m; k++) {
        float topA = curtain(height, bu[k], bw[k], lane);
        float topB = curtain(height, bu[k + 1], bw[k + 1], lane);
        float ax = bx[k] + edgeX[k] * lane;
        float az = bz[k] + edgeZ[k] * lane;
        float bxx = bx[k + 1] + edgeX[k + 1] * lane;
        float bzz = bz[k + 1] + edgeZ[k + 1] * lane;
        float ay = by[k] + 0.04F;
        float byy = by[k + 1] + 0.04F;
        float tone = lane < 0 ? 0.0F : 1.0F;
        int seed = w.identity();
        out.vertex(
            ax, ay, az, bu[k], 0.0F, Style.AURORA.ordinal(), bu[k], ba[k], tone, 0.0F, seed, 0.0F);
        out.vertex(
            bxx,
            byy,
            bzz,
            bu[k + 1],
            0.0F,
            Style.AURORA.ordinal(),
            bu[k + 1],
            ba[k + 1],
            tone,
            0.0F,
            seed,
            0.0F);
        out.vertex(
            bxx,
            byy + topB,
            bzz,
            bu[k + 1],
            1.0F,
            Style.AURORA.ordinal(),
            bu[k + 1],
            ba[k + 1],
            tone,
            0.0F,
            seed,
            0.0F);
        out.vertex(
            ax, ay, az, bu[k], 0.0F, Style.AURORA.ordinal(), bu[k], ba[k], tone, 0.0F, seed, 0.0F);
        out.vertex(
            bxx,
            byy + topB,
            bzz,
            bu[k + 1],
            1.0F,
            Style.AURORA.ordinal(),
            bu[k + 1],
            ba[k + 1],
            tone,
            0.0F,
            seed,
            0.0F);
        out.vertex(
            ax,
            ay + topA,
            az,
            bu[k],
            1.0F,
            Style.AURORA.ordinal(),
            bu[k],
            ba[k],
            tone,
            0.0F,
            seed,
            0.0F);
        quads++;
      }
    }
  }

  private float curtain(float height, float t, float serial, int lane) {
    return height * (0.55F + 0.5F * (1.0F - t) * size)
        + (float) Math.sin(serial * 0.45F + lane * 1.7F) * 0.12F * height;
  }

  private void glitch(Wake w, int stride) {
    float width = w.width();
    float height = w.height();
    float grid = GRID * size;
    int bucket = (int) (now / GLITCH_NANOS);
    for (int i = 0; i < w.count(); i++) {
      float age = w.age(i, now);
      if (age >= life) {
        break;
      }
      int id = w.serial(i);
      if (id % stride != 0) {
        continue;
      }
      for (int k = 0; k < 3; k++) {
        int seed = id * 3 + k;
        float span = life * (0.25F + 0.5F * unit(seed, 1));
        if (age >= span || !room(1) || unit(bucket * 977 + seed, 9) < 0.3F) {
          continue;
        }
        float t = age / span;
        float x =
            snap(
                (float) (w.x(i) - view.x)
                    + (unit(seed, 2) - 0.5F) * width * 1.8F
                    + (unit(seed, 5) - 0.5F) * t * 0.9F,
                grid);
        float y = snap((float) (w.y(i) - view.y) + unit(seed, 3) * height * 1.05F, grid);
        float z = snap((float) (w.z(i) - view.z) + (unit(seed, 4) - 0.5F) * width * 1.8F, grid);
        float alpha = fade(x, y, z) * (1.0F - t * t);
        if (alpha < FAINTEST) {
          continue;
        }
        float tone = unit(seed, 7);
        if (k == 2) {
          float bar = Math.max(width * 0.9F, grid * 3.0F);
          billboard(
              x, y, z, bar, grid * 0.3F, Style.GLITCH, t, alpha * 0.8F, tone, 1.0F, seed, bar);
        } else {
          float half = grid * (0.5F + (float) Math.floor(unit(seed, 6) * 3.0F)) * 0.5F;
          billboard(x, y, z, half, half, Style.GLITCH, t, alpha, tone, 0.0F, seed, half);
        }
      }
    }
  }

  private void pulse(Wake w) {
    float width = w.width();
    float lastAge = -1.0F;
    for (int i = 0; i < w.count(); i++) {
      float age = w.age(i, now);
      float t = age / life;
      if (t >= 1.0F) {
        break;
      }
      if (age < 0.04F || age - lastAge < RING_GAP || !room(1)) {
        continue;
      }
      lastAge = age;
      float x = (float) (w.x(i) - view.x);
      float z = (float) (w.z(i) - view.z);
      float distance = (float) Math.sqrt(x * x + z * z);
      float y = (float) (w.y(i) - view.y) + 0.02F + distance * 0.0015F;
      float radius = width * size * (0.35F + 1.3F * eased(t));
      float body = 1.0F - t;
      float alpha = fade(x, y, z) * body * (float) Math.sqrt(body);
      if (alpha >= FAINTEST) {
        flat(
            x,
            y,
            z,
            radius * 1.3F,
            Style.PULSE,
            t,
            alpha,
            0.0F,
            Math.min(1.0F, t * 1.2F),
            w.serial(i),
            radius);
      }
    }
  }

  private void ribbon(
      float[] x,
      float[] y,
      float[] z,
      float[] width,
      float[] alpha,
      float[] along,
      int n,
      Style style,
      float aux,
      int seed,
      float extra) {
    for (int k = 0; k < n; k++) {
      int before = Math.max(0, k - 1);
      int after = Math.min(n - 1, k + 1);
      float tx = x[after] - x[before];
      float ty = y[after] - y[before];
      float tz = z[after] - z[before];
      float px = ty * view.fz - tz * view.fy;
      float py = tz * view.fx - tx * view.fz;
      float pz = tx * view.fy - ty * view.fx;
      float length = (float) Math.sqrt(px * px + py * py + pz * pz);
      if (length < 1.0e-4F) {
        px = view.rx;
        py = view.ry;
        pz = view.rz;
        length = 1.0F;
      }
      float scale = width[k] / length;
      edgeX[k] = px * scale;
      edgeY[k] = py * scale;
      edgeZ[k] = pz * scale;
    }
    int kind = style.ordinal();
    int id = seed & 0xFFF;
    for (int k = 0; k + 1 < n; k++) {
      int j = k + 1;
      out.vertex(
          x[k] - edgeX[k],
          y[k] - edgeY[k],
          z[k] - edgeZ[k],
          along[k],
          -1.0F,
          kind,
          along[k],
          alpha[k],
          aux,
          0.0F,
          id,
          extra);
      out.vertex(
          x[j] - edgeX[j],
          y[j] - edgeY[j],
          z[j] - edgeZ[j],
          along[j],
          -1.0F,
          kind,
          along[j],
          alpha[j],
          aux,
          0.0F,
          id,
          extra);
      out.vertex(
          x[j] + edgeX[j],
          y[j] + edgeY[j],
          z[j] + edgeZ[j],
          along[j],
          1.0F,
          kind,
          along[j],
          alpha[j],
          aux,
          0.0F,
          id,
          extra);
      out.vertex(
          x[k] - edgeX[k],
          y[k] - edgeY[k],
          z[k] - edgeZ[k],
          along[k],
          -1.0F,
          kind,
          along[k],
          alpha[k],
          aux,
          0.0F,
          id,
          extra);
      out.vertex(
          x[j] + edgeX[j],
          y[j] + edgeY[j],
          z[j] + edgeZ[j],
          along[j],
          1.0F,
          kind,
          along[j],
          alpha[j],
          aux,
          0.0F,
          id,
          extra);
      out.vertex(
          x[k] + edgeX[k],
          y[k] + edgeY[k],
          z[k] + edgeZ[k],
          along[k],
          1.0F,
          kind,
          along[k],
          alpha[k],
          aux,
          0.0F,
          id,
          extra);
      quads++;
    }
  }

  private void billboard(
      float x,
      float y,
      float z,
      float halfWidth,
      float halfHeight,
      Style style,
      float progress,
      float alpha,
      float aux,
      float tone,
      int seed,
      float extra) {
    float rx = view.rx * halfWidth;
    float ry = view.ry * halfWidth;
    float rz = view.rz * halfWidth;
    float ux = view.ux * halfHeight;
    float uy = view.uy * halfHeight;
    float uz = view.uz * halfHeight;
    int kind = style.ordinal();
    int id = seed & 0xFFF;
    out.vertex(
        x - rx - ux,
        y - ry - uy,
        z - rz - uz,
        -1.0F,
        -1.0F,
        kind,
        progress,
        alpha,
        aux,
        tone,
        id,
        extra);
    out.vertex(
        x + rx - ux,
        y + ry - uy,
        z + rz - uz,
        1.0F,
        -1.0F,
        kind,
        progress,
        alpha,
        aux,
        tone,
        id,
        extra);
    out.vertex(
        x + rx + ux,
        y + ry + uy,
        z + rz + uz,
        1.0F,
        1.0F,
        kind,
        progress,
        alpha,
        aux,
        tone,
        id,
        extra);
    out.vertex(
        x - rx - ux,
        y - ry - uy,
        z - rz - uz,
        -1.0F,
        -1.0F,
        kind,
        progress,
        alpha,
        aux,
        tone,
        id,
        extra);
    out.vertex(
        x + rx + ux,
        y + ry + uy,
        z + rz + uz,
        1.0F,
        1.0F,
        kind,
        progress,
        alpha,
        aux,
        tone,
        id,
        extra);
    out.vertex(
        x - rx + ux,
        y - ry + uy,
        z - rz + uz,
        -1.0F,
        1.0F,
        kind,
        progress,
        alpha,
        aux,
        tone,
        id,
        extra);
    quads++;
  }

  private void flat(
      float x,
      float y,
      float z,
      float half,
      Style style,
      float progress,
      float alpha,
      float aux,
      float tone,
      int seed,
      float extra) {
    int kind = style.ordinal();
    int id = seed & 0xFFF;
    out.vertex(x - half, y, z - half, -1.0F, -1.0F, kind, progress, alpha, aux, tone, id, extra);
    out.vertex(x + half, y, z - half, 1.0F, -1.0F, kind, progress, alpha, aux, tone, id, extra);
    out.vertex(x + half, y, z + half, 1.0F, 1.0F, kind, progress, alpha, aux, tone, id, extra);
    out.vertex(x - half, y, z - half, -1.0F, -1.0F, kind, progress, alpha, aux, tone, id, extra);
    out.vertex(x + half, y, z + half, 1.0F, 1.0F, kind, progress, alpha, aux, tone, id, extra);
    out.vertex(x - half, y, z + half, -1.0F, 1.0F, kind, progress, alpha, aux, tone, id, extra);
    quads++;
  }

  private boolean room(int needed) {
    return quads + needed <= QUADS;
  }

  private float fade(float x, float y, float z) {
    float distance = (float) Math.sqrt(x * x + y * y + z * z);
    float near = smooth((distance - NEAR_FROM) / NEAR_SPAN);
    float half = reach * 0.5F;
    return near * (1.0F - smooth((distance - half) / half));
  }

  private static float snap(float value, float grid) {
    return Math.round(value / grid) * grid;
  }

  private static float eased(float t) {
    return 1.0F - (1.0F - t) * (1.0F - t);
  }

  private static float smooth(float t) {
    float c = t < 0.0F ? 0.0F : Math.min(t, 1.0F);
    return c * c * (3.0F - 2.0F * c);
  }

  private static float unit(int value, int salt) {
    int h = (value + salt * 0x27D4EB2F) * 0x9E3779B1;
    h ^= h >>> 15;
    h *= 0x85EBCA77;
    h ^= h >>> 13;
    return (h >>> 8 & 0xFFFF) / 65535.0F;
  }
}
