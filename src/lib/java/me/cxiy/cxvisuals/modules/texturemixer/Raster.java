package me.cxiy.cxvisuals.modules.texturemixer;

import java.util.function.Function;

public final class Raster {
  public static final float TURN = (float) Math.toRadians(45.0);
  public static final float TILT = (float) Math.toRadians(30.0);

  private static final float CENTER = 8.0F;
  private static final float REACH = 13.9F;
  private static final float EYE = 46.0F;
  private static final float[] SHADE = {0.5F, 1.0F, 0.8F, 0.8F, 0.6F, 0.6F};
  private static final int MAX_HITS = 48;
  private static final float SPRITE_BACK = 7.5F;
  private static final float SPRITE_FRONT = 8.5F;
  private static final float SPRITE_EPS = 1.0e-3F;
  private static final float SPRITE_FACE = 0.95F;
  private static final float SPRITE_EDGE = 0.62F;
  private static final int SPRITE_STEPS = 80;
  private static final int SPRITE_ALPHA = 128;
  private static final int SOLID = 0xFF000000;
  private static final int GRASS = 0x91BD59;
  private static final int FOLIAGE = 0x59AE30;
  private static final int WATER = 0x3F76E4;

  private final float[] depth = new float[MAX_HITS];
  private final int[] colour = new int[MAX_HITS];
  private final float[] weight = new float[MAX_HITS];

  public static int tint(String entryId, String texture) {
    if (entryId.contains("leaves") || entryId.contains("vine") || entryId.contains("lily")) {
      return FOLIAGE;
    }
    if (entryId.contains("water") || entryId.contains("cauldron")) {
      return WATER;
    }
    if (entryId.contains("grass") || entryId.contains("fern") || texture.contains("grass")) {
      return GRASS;
    }
    return 0xFFFFFF;
  }

  public void render(
      Shape shape,
      Function<String, Pixels> source,
      int tint,
      int size,
      int samples,
      float zoom,
      float yaw,
      float pitch,
      boolean perspective,
      int[] out) {
    int span = size * samples;
    float cy = (float) Math.cos(yaw);
    float sy = (float) Math.sin(yaw);
    float cp = (float) Math.cos(pitch);
    float sp = (float) Math.sin(pitch);
    float fx = -sy * cp;
    float fy = -sp;
    float fz = -cy * cp;
    float rx = cy;
    float rz = -sy;
    float ux = -sy * sp;
    float uy = cp;
    float uz = -cy * sp;
    float ex = CENTER - fx * EYE;
    float ey = CENTER - fy * EYE;
    float ez = CENTER - fz * EYE;
    Shape.Box[] boxes = shape.boxes();
    Pixels[][] textures = new Pixels[boxes.length][6];
    for (int b = 0; b < boxes.length; b++) {
      for (int f = 0; f < 6; f++) {
        Shape.Face face = boxes[b].faces()[f];
        textures[b][f] = face == null ? null : source.apply(face.texture());
      }
    }
    Pixels flat = shape.extruded() ? source.apply(shape.textures().iterator().next()) : null;
    float scale = REACH * zoom * 2.0F / span;
    float half = span * 0.5F;
    for (int py = 0; py < size; py++) {
      for (int px = 0; px < size; px++) {
        int r = 0;
        int g = 0;
        int bl = 0;
        int a = 0;
        for (int sj = 0; sj < samples; sj++) {
          for (int si = 0; si < samples; si++) {
            float sx = (px * samples + si + 0.5F - half) * scale;
            float sv = (half - (py * samples + sj + 0.5F)) * scale;
            float ox;
            float oy;
            float oz;
            float dx;
            float dy;
            float dz;
            if (perspective) {
              ox = ex;
              oy = ey;
              oz = ez;
              dx = fx * EYE + rx * sx + ux * sv;
              dy = fy * EYE + uy * sv;
              dz = fz * EYE + rz * sx + uz * sv;
              float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
              dx /= len;
              dy /= len;
              dz /= len;
            } else {
              ox = ex + rx * sx + ux * sv;
              oy = ey + uy * sv;
              oz = ez + rz * sx + uz * sv;
              dx = fx;
              dy = fy;
              dz = fz;
            }
            int packed =
                flat != null
                    ? sprite(flat, ox, oy, oz, dx, dy, dz)
                    : trace(boxes, textures, tint, ox, oy, oz, dx, dy, dz);
            int pa = packed >>> 24;
            if (pa != 0) {
              r += ((packed >> 16) & 0xFF) * pa;
              g += ((packed >> 8) & 0xFF) * pa;
              bl += (packed & 0xFF) * pa;
              a += pa;
            }
          }
        }
        int count = samples * samples;
        if (a == 0) {
          out[py * size + px] = 0;
        } else {
          out[py * size + px] =
              ((a / count) << 24) | ((r / a) << 16) | ((g / a) << 8) | (bl / a);
        }
      }
    }
  }

  private static int sprite(
      Pixels pixels, float ox, float oy, float oz, float dx, float dy, float dz) {
    float vz = Math.abs(dz) < 1.0e-6F ? 1.0e-6F : dz;
    float t0 = (SPRITE_BACK - oz) / vz;
    float t1 = (SPRITE_FRONT - oz) / vz;
    if (t0 > t1) {
      float swap = t0;
      t0 = t1;
      t1 = swap;
    }
    if (t1 < 0.0F) {
      return 0;
    }
    float cell = 16.0F / pixels.width();
    float t = Math.max(t0, 0.0F);
    for (int step = 0; step < SPRITE_STEPS; step++) {
      float probe = t + SPRITE_EPS;
      if (probe >= t1) {
        break;
      }
      float x = ox + dx * probe;
      float y = oy + dy * probe;
      int cx = (int) Math.floor(x / cell);
      int cy = (int) Math.floor(y / cell);
      if (cx >= 0 && cy >= 0 && cx < pixels.width() && cy < pixels.height()) {
        int texel = pixels.argb()[(pixels.height() - 1 - cy) * pixels.width() + cx];
        if ((texel >>> 24) >= SPRITE_ALPHA) {
          float shade = t <= t0 + SPRITE_EPS ? SPRITE_FACE : SPRITE_EDGE;
          int r = Math.min(255, Math.round(((texel >> 16) & 0xFF) * shade));
          int g = Math.min(255, Math.round(((texel >> 8) & 0xFF) * shade));
          int b = Math.min(255, Math.round((texel & 0xFF) * shade));
          return SOLID | (r << 16) | (g << 8) | b;
        }
      }
      float tx = dx > 0.0F ? ((cx + 1) * cell - ox) / dx : dx < 0.0F ? (cx * cell - ox) / dx : Float.MAX_VALUE;
      float ty = dy > 0.0F ? ((cy + 1) * cell - oy) / dy : dy < 0.0F ? (cy * cell - oy) / dy : Float.MAX_VALUE;
      float next = Math.min(tx, ty);
      t = next <= t ? t + SPRITE_EPS * 4.0F : next;
    }
    return 0;
  }

  private int trace(
      Shape.Box[] boxes,
      Pixels[][] textures,
      int tint,
      float ox,
      float oy,
      float oz,
      float dx,
      float dy,
      float dz) {
    dx = Math.abs(dx) < 1.0e-6F ? 1.0e-6F : dx;
    dy = Math.abs(dy) < 1.0e-6F ? 1.0e-6F : dy;
    dz = Math.abs(dz) < 1.0e-6F ? 1.0e-6F : dz;
    int hits = 0;
    for (int b = 0; b < boxes.length && hits < MAX_HITS - 1; b++) {
      Shape.Box box = boxes[b];
      float tx0 = (box.x0() - ox) / dx;
      float tx1 = (box.x1() - ox) / dx;
      float ty0 = (box.y0() - oy) / dy;
      float ty1 = (box.y1() - oy) / dy;
      float tz0 = (box.z0() - oz) / dz;
      float tz1 = (box.z1() - oz) / dz;
      float nearX = Math.min(tx0, tx1);
      float farX = Math.max(tx0, tx1);
      float nearY = Math.min(ty0, ty1);
      float farY = Math.max(ty0, ty1);
      float nearZ = Math.min(tz0, tz1);
      float farZ = Math.max(tz0, tz1);
      float near = Math.max(nearX, Math.max(nearY, nearZ));
      float far = Math.min(farX, Math.min(farY, farZ));
      if (near > far || far < 0.0F) {
        continue;
      }
      int enter = near == nearX ? (dx > 0 ? Shape.WEST : Shape.EAST)
          : near == nearY ? (dy > 0 ? Shape.DOWN : Shape.UP)
          : (dz > 0 ? Shape.NORTH : Shape.SOUTH);
      int leave = far == farX ? (dx > 0 ? Shape.EAST : Shape.WEST)
          : far == farY ? (dy > 0 ? Shape.UP : Shape.DOWN)
          : (dz > 0 ? Shape.SOUTH : Shape.NORTH);
      hits = add(hits, box, textures[b], enter, near, tint, ox, oy, oz, dx, dy, dz);
      hits = add(hits, box, textures[b], leave, far, tint, ox, oy, oz, dx, dy, dz);
    }
    if (hits == 0) {
      return 0;
    }
    for (int i = 1; i < hits; i++) {
      float d = depth[i];
      int c = colour[i];
      float w = weight[i];
      int j = i - 1;
      while (j >= 0 && depth[j] > d) {
        depth[j + 1] = depth[j];
        colour[j + 1] = colour[j];
        weight[j + 1] = weight[j];
        j--;
      }
      depth[j + 1] = d;
      colour[j + 1] = c;
      weight[j + 1] = w;
    }
    float r = 0.0F;
    float g = 0.0F;
    float bl = 0.0F;
    float remaining = 1.0F;
    for (int i = 0; i < hits && remaining > 0.004F; i++) {
      float a = weight[i];
      int c = colour[i];
      r += ((c >> 16) & 0xFF) * a * remaining;
      g += ((c >> 8) & 0xFF) * a * remaining;
      bl += (c & 0xFF) * a * remaining;
      remaining *= 1.0F - a;
    }
    float covered = 1.0F - remaining;
    if (covered <= 0.004F) {
      return 0;
    }
    int alpha = Math.round(covered * 255.0F);
    return (alpha << 24)
        | (Math.min(255, Math.round(r / covered)) << 16)
        | (Math.min(255, Math.round(g / covered)) << 8)
        | Math.min(255, Math.round(bl / covered));
  }

  private int add(
      int hits,
      Shape.Box box,
      Pixels[] textures,
      int face,
      float t,
      int tint,
      float ox,
      float oy,
      float oz,
      float dx,
      float dy,
      float dz) {
    Shape.Face spec = box.faces()[face];
    Pixels pixels = textures[face];
    if (spec == null || pixels == null || t < 0.0F) {
      return hits;
    }
    float x = ox + dx * t;
    float y = oy + dy * t;
    float z = oz + dz * t;
    float u;
    float v;
    switch (face) {
      case Shape.DOWN, Shape.UP -> {
        u = (x - box.x0()) / Math.max(0.0001F, box.x1() - box.x0());
        v = (z - box.z0()) / Math.max(0.0001F, box.z1() - box.z0());
        if (face == Shape.DOWN) {
          v = 1.0F - v;
        }
      }
      case Shape.NORTH, Shape.SOUTH -> {
        u = (x - box.x0()) / Math.max(0.0001F, box.x1() - box.x0());
        v = 1.0F - (y - box.y0()) / Math.max(0.0001F, box.y1() - box.y0());
        if (face == Shape.NORTH) {
          u = 1.0F - u;
        }
      }
      default -> {
        u = (z - box.z0()) / Math.max(0.0001F, box.z1() - box.z0());
        v = 1.0F - (y - box.y0()) / Math.max(0.0001F, box.y1() - box.y0());
        if (face == Shape.EAST) {
          u = 1.0F - u;
        }
      }
    }
    float tu = (spec.u0() + (spec.u1() - spec.u0()) * u) / 16.0F;
    float tv = (spec.v0() + (spec.v1() - spec.v0()) * v) / 16.0F;
    tu = tu - (float) Math.floor(tu);
    tv = tv - (float) Math.floor(tv);
    int texel = pixels.sample(tu, tv);
    float alpha = (texel >>> 24) / 255.0F;
    if (alpha < 0.02F) {
      return hits;
    }
    int r = (texel >> 16) & 0xFF;
    int g = (texel >> 8) & 0xFF;
    int b = texel & 0xFF;
    if (spec.tint()) {
      r = r * ((tint >> 16) & 0xFF) / 255;
      g = g * ((tint >> 8) & 0xFF) / 255;
      b = b * (tint & 0xFF) / 255;
    }
    float shade = SHADE[face];
    r = Math.min(255, Math.round(r * shade));
    g = Math.min(255, Math.round(g * shade));
    b = Math.min(255, Math.round(b * shade));
    depth[hits] = t;
    colour[hits] = (r << 16) | (g << 8) | b;
    weight[hits] = alpha;
    return hits + 1;
  }
}
