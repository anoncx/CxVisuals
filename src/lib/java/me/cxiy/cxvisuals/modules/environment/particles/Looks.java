package me.cxiy.cxvisuals.modules.environment.particles;

import me.cxiy.cxvisuals.modules.environment.View;

final class Looks {
  private static final float GONE = 0.004F;
  private static final float BEHIND = -0.5F;
  private static final float SEEDS = 32767.0F;

  private Looks() {}

  static void emit(
      Scene scene,
      Mote m,
      View view,
      Sink sink,
      float fade,
      float scale,
      float glow,
      Climate climate,
      float time) {
    Kind kind = scene.kind();
    float dx = (float) (m.x - view.x);
    float dy = (float) (m.y - view.y);
    float dz = (float) (m.z - view.z);
    float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    if (dx * view.fx + dy * view.fy + dz * view.fz < BEHIND && distance > 1.0F) {
      return;
    }
    float flat = (float) Math.sqrt(dx * dx + dz * dz);
    float shown =
        fade
            * (1.0F - Waves.smooth(kind.reach * 0.55F, kind.reach * 0.98F, flat))
            * Waves.smooth(0.25F, 0.9F, distance)
            * lifetime(kind, m);
    if (shown <= GONE) {
      return;
    }
    int seed = (int) (m.seed * SEEDS);
    Tint tint = scene.tint();
    float light = climate.lit(m.sky, m.lamp);
    switch (kind) {
      case FIREFLIES -> firefly(m, view, sink, dx, dy, dz, shown, scale, glow, time, tint, seed);
      case PETALS -> petal(m, sink, dx, dy, dz, distance, shown, scale, light, tint, seed);
      case SNOW -> flake(m, view, sink, dx, dy, dz, distance, shown, scale, light, tint, seed);
      case EMBERS -> ember(m, view, sink, dx, dy, dz, shown, scale, glow, time, tint, seed);
      case MOTES -> mote(m, view, sink, dx, dy, dz, shown, scale, glow, time, tint, seed);
      case BUTTERFLIES -> butterfly(m, sink, dx, dy, dz, shown, scale, light, time, tint, seed);
      case BUBBLES -> bubble(m, view, sink, dx, dy, dz, shown, scale, light, tint, seed);
      case WISPS -> wisp(m, view, sink, dx, dy, dz, shown, scale, glow, time, tint, seed);
    }
  }

  private static float lifetime(Kind kind, Mote m) {
    float in = Waves.smooth(0.0F, 0.8F, m.age);
    if (m.life == Float.MAX_VALUE || kind == Kind.BUBBLES) {
      return in;
    }
    return in * (1.0F - Waves.smooth(m.life - 1.2F, m.life, m.age));
  }

  private static float vary(Mote m, float salt) {
    float v = m.seed * salt;
    return v - (float) Math.floor(v);
  }

  private static void billboard(
      View view,
      Sink sink,
      float dx,
      float dy,
      float dz,
      float half,
      int argb,
      Shape shape,
      float detail,
      int seed) {
    sink.quad(
        dx,
        dy,
        dz,
        view.rx * half,
        view.ry * half,
        view.rz * half,
        view.ux * half,
        view.uy * half,
        view.uz * half,
        argb,
        shape,
        detail,
        seed);
  }

  private static void firefly(
      Mote m,
      View view,
      Sink sink,
      float dx,
      float dy,
      float dz,
      float shown,
      float scale,
      float glow,
      float time,
      Tint tint,
      int seed) {
    float beat = time * m.rate + m.phase;
    float pulse = beat - (float) Math.floor(beat);
    float flash = Waves.smooth(0.0F, 0.08F, pulse) * (1.0F - Waves.smooth(0.18F, 0.62F, pulse));
    float bright = 0.22F + 0.78F * flash * (float) Math.sqrt(flash);
    float alpha = Math.min(1.0F, shown * bright * glow);
    if (alpha <= GONE) {
      return;
    }
    float half = m.size * 7.0F * scale * (0.75F + 0.25F * glow) * (0.85F + 0.25F * flash);
    int edge = Waves.mix(tint.edge, tint.core, vary(m, 6.1F) * 0.45F);
    billboard(
        view,
        sink,
        dx,
        dy,
        dz,
        half,
        Waves.argb(Waves.mix(edge, tint.core, flash * 0.6F), alpha),
        Shape.GLOW,
        0.0F,
        seed);
  }

  private static void mote(
      Mote m,
      View view,
      Sink sink,
      float dx,
      float dy,
      float dz,
      float shown,
      float scale,
      float glow,
      float time,
      Tint tint,
      int seed) {
    float wave = Math.max(0.0F, Waves.sin(time * m.rate + m.seed * 40.0F));
    float glint = wave * wave * wave * wave;
    glint *= glint * glint;
    float alpha = Math.min(1.0F, shown * (0.22F + 0.78F * glint) * glow * 0.85F);
    if (alpha <= GONE) {
      return;
    }
    float half = m.size * 6.0F * scale * (1.0F + glint * 0.6F);
    billboard(
        view,
        sink,
        dx,
        dy,
        dz,
        half,
        Waves.argb(Waves.mix(tint.edge, tint.core, 0.5F + 0.5F * glint), alpha),
        Shape.GLOW,
        glint,
        seed);
  }

  private static void ember(
      Mote m,
      View view,
      Sink sink,
      float dx,
      float dy,
      float dz,
      float shown,
      float scale,
      float glow,
      float time,
      Tint tint,
      int seed) {
    float heat = Math.clamp(1.0F - m.age / m.life, 0.0F, 1.0F);
    float flicker = 0.75F + 0.25F * Waves.sin(time * (18.0F + m.seed * 14.0F) + m.seed * 50.0F);
    float alpha = Math.min(1.0F, shown * flicker * glow * (0.35F + 0.65F * heat));
    if (alpha <= GONE) {
      return;
    }
    float sx = m.vx * view.rx + m.vy * view.ry + m.vz * view.rz;
    float sy = m.vx * view.ux + m.vy * view.uy + m.vz * view.uz;
    float across = (float) Math.sqrt(sx * sx + sy * sy);
    if (across < 1.0e-3F) {
      sx = 0.0F;
      sy = 1.0F;
      across = 1.0F;
    }
    sx /= across;
    sy /= across;
    float speed = (float) Math.sqrt(m.vx * m.vx + m.vy * m.vy + m.vz * m.vz);
    float half = m.size * 3.2F * scale * (0.6F + 0.4F * heat);
    float along = half * (1.4F + speed * 1.6F);
    float ax = (view.rx * sx + view.ux * sy) * along;
    float ay = (view.ry * sx + view.uy * sy) * along;
    float az = (view.rz * sx + view.uz * sy) * along;
    float bx = (view.ux * sx - view.rx * sy) * half;
    float by = (view.uy * sx - view.ry * sy) * half;
    float bz = (view.uz * sx - view.rz * sy) * half;
    sink.quad(
        dx,
        dy,
        dz,
        ax,
        ay,
        az,
        bx,
        by,
        bz,
        Waves.argb(Waves.mix(tint.edge, tint.core, heat * heat), alpha),
        Shape.SPARK,
        0.0F,
        seed);
  }

  private static void petal(
      Mote m,
      Sink sink,
      float dx,
      float dy,
      float dz,
      float distance,
      float shown,
      float scale,
      float light,
      Tint tint,
      int seed) {
    float cs = Waves.cos(m.spin);
    float ss = Waves.sin(m.spin);
    float ax;
    float ay;
    float az;
    float bx;
    float by;
    float bz;
    float alpha = shown;
    if (m.landed) {
      ax = cs;
      ay = 0.0F;
      az = ss;
      bx = -ss;
      by = 0.0F;
      bz = cs;
      alpha *= Math.min(1.0F, m.rest / 1.4F);
    } else {
      float tilt = Waves.sin(m.tumble) * 1.15F;
      float roll = Waves.cos(m.tumble * 0.83F + m.seed * 6.0F) * 0.95F;
      float ct = Waves.cos(tilt);
      float st = Waves.sin(tilt);
      ax = cs * ct;
      ay = st;
      az = ss * ct;
      float nx = ay * cs;
      float ny = az * -ss - ax * cs;
      float nz = ay * ss;
      float cr = Waves.cos(roll);
      float sr = Waves.sin(roll);
      bx = -ss * cr + nx * sr;
      by = ny * sr;
      bz = cs * cr + nz * sr;
    }
    if (alpha <= GONE) {
      return;
    }
    float normalY = az * bx - ax * bz;
    float lit = light * (0.78F + 0.22F * Math.abs(normalY));
    float length = m.size * scale;
    float width = length * (0.62F + 0.3F * vary(m, 3.7F));
    float blur = 1.0F - Waves.smooth(0.35F, 1.4F, distance);
    float hue = vary(m, 7.3F);
    int colour =
        hue < 0.22F
            ? Waves.shade(tint.edge, 0.82F)
            : Waves.mix(tint.edge, tint.core, (hue - 0.22F) * 0.7F);
    sink.quad(
        dx,
        dy,
        dz,
        ax * length,
        ay * length,
        az * length,
        bx * width,
        by * width,
        bz * width,
        Waves.argb(Waves.shade(colour, lit), alpha),
        Shape.PETAL,
        blur,
        seed);
  }

  private static void butterfly(
      Mote m,
      Sink sink,
      float dx,
      float dy,
      float dz,
      float shown,
      float scale,
      float light,
      float time,
      Tint tint,
      int seed) {
    float glide = Motion.glide(m, time);
    float flap = 0.3F + 1.0F * Waves.sin(m.tumble);
    float angle = flap + (0.22F - flap) * glide;
    float speed = (float) Math.sqrt(m.vx * m.vx + m.vz * m.vz);
    float hx = speed > 1.0e-3F ? m.vx / speed : Waves.cos(m.spin);
    float hz = speed > 1.0e-3F ? m.vz / speed : Waves.sin(m.spin);
    float hy = Math.clamp(m.vy * 0.4F, -0.4F, 0.4F);
    float level = (float) Math.sqrt(1.0F - hy * hy);
    hx *= level;
    hz *= level;
    float span = m.size * scale;
    float length = span * 0.85F;
    float out = Waves.cos(angle) * span;
    float up = Waves.sin(angle) * span;
    int colour = Waves.argb(Waves.shade(tint.core, Math.max(light, 0.5F)), shown);
    float sx = -hz;
    float sz = hx;
    sink.quad(
        dx + sx * out,
        dy + up,
        dz + sz * out,
        sx * out,
        up,
        sz * out,
        hx * length,
        hy * length,
        hz * length,
        colour,
        Shape.WING,
        0.0F,
        seed);
    sink.quad(
        dx - sx * out,
        dy + up,
        dz - sz * out,
        -sx * out,
        up,
        -sz * out,
        hx * length,
        hy * length,
        hz * length,
        colour,
        Shape.WING,
        0.0F,
        seed);
  }

  private static void bubble(
      Mote m,
      View view,
      Sink sink,
      float dx,
      float dy,
      float dz,
      float shown,
      float scale,
      float light,
      Tint tint,
      int seed) {
    float popping = Math.clamp((m.age - m.life) / Motion.POP, 0.0F, 1.0F);
    float breathe = 1.0F + 0.04F * Waves.sin(m.age * 5.0F + m.seed * 30.0F);
    float half = m.size * scale * breathe * (1.0F + popping * 0.5F);
    float film = m.phase - (float) Math.floor(m.phase);
    float detail = popping > 0.0F ? 0.5F + popping * 0.5F : film * 0.499F;
    billboard(
        view,
        sink,
        dx,
        dy,
        dz,
        half,
        Waves.argb(Waves.shade(tint.core, Math.max(light, 0.55F)), shown),
        Shape.BUBBLE,
        detail,
        seed);
  }

  private static void wisp(
      Mote m,
      View view,
      Sink sink,
      float dx,
      float dy,
      float dz,
      float shown,
      float scale,
      float glow,
      float time,
      Tint tint,
      int seed) {
    float pulse = 0.75F + 0.25F * Waves.sin(time * 1.7F + m.seed * 20.0F);
    float head = m.size * 3.2F * scale * (0.9F + 0.1F * pulse);
    billboard(
        view,
        sink,
        dx,
        dy,
        dz,
        m.size * 7.0F * scale,
        Waves.argb(tint.edge, Math.min(1.0F, shown * 0.35F * glow)),
        Shape.HALO,
        0.0F,
        seed);
    for (int k = Motion.TRAIL - 1; k >= 1; k--) {
      int at = Math.floorMod(m.trailHead - k, Motion.TRAIL) * 3;
      float fade = 1.0F - (float) k / Motion.TRAIL;
      float alpha = Math.min(1.0F, shown * 0.55F * fade * fade * glow);
      if (alpha > GONE) {
        billboard(
            view,
            sink,
            (float) (m.trail[at] - view.x),
            (float) (m.trail[at + 1] - view.y),
            (float) (m.trail[at + 2] - view.z),
            m.size * 2.2F * scale * fade,
            Waves.argb(Waves.mix(tint.edge, tint.core, fade * 0.5F), alpha),
            Shape.GLOW,
            0.0F,
            seed);
      }
    }
    billboard(
        view,
        sink,
        dx,
        dy,
        dz,
        head,
        Waves.argb(Waves.mix(tint.edge, tint.core, 0.6F), Math.min(1.0F, shown * pulse * glow)),
        Shape.GLOW,
        0.15F,
        seed);
  }

  private static void flake(
      Mote m,
      View view,
      Sink sink,
      float dx,
      float dy,
      float dz,
      float distance,
      float shown,
      float scale,
      float light,
      Tint tint,
      int seed) {
    float blur = 1.0F - Waves.smooth(0.5F, 2.8F, distance);
    float alpha = shown * (1.0F - blur * 0.55F);
    if (m.landed) {
      alpha *= Math.min(1.0F, m.rest / 0.8F);
    }
    if (alpha <= GONE) {
      return;
    }
    float half = m.size * scale * (1.0F + blur * 1.4F);
    int colour = Waves.shade(Waves.mix(tint.edge, tint.core, light), Math.max(light, 0.62F));
    billboard(view, sink, dx, dy, dz, half, Waves.argb(colour, alpha), Shape.FLAKE, blur, seed);
  }
}
