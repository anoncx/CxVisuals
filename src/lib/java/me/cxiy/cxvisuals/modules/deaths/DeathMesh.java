package me.cxiy.cxvisuals.modules.deaths;

import me.cxiy.cxvisuals.modules.environment.View;

public final class DeathMesh {
  public static final int SIGIL = 0;
  public static final int EMBER = 1;
  public static final int BEAM = 2;
  public static final int HALO = 3;
  public static final int CORE = 4;
  public static final int FLECK = 5;
  public static final int EMBERS = 28;
  public static final int FLECKS = 40;
  public static final int MOTES = 20;
  public static final int QUADS =
      Corpses.MOST * (2 + Math.max(EMBERS, Math.max(FLECKS, MOTES + 2)));

  private static final float LIFT = 0.02F;
  private static final float BEAM_HEIGHT = 18.0F;
  private static final float HOLE_REACH = 2.0F;

  private final Posture posture = new Posture();
  private int quads;

  public int emit(Corpses corpses, View view, float seconds, long nanos, FxSink out) {
    quads = 0;
    for (int i = 0; i < corpses.count(); i++) {
      Corpses.Corpse corpse = corpses.get(i);
      if (corpse.body == null) {
        continue;
      }
      float t = Math.min(1.0F, corpse.progress(nanos, seconds));
      float x = (float) (corpse.x - view.x);
      float y = (float) (corpse.y - view.y);
      float z = (float) (corpse.z - view.z);
      corpse.fate.pose(t, corpse.height, corpse.seed, posture);
      switch (corpse.fate) {
        case RITUAL -> ritual(out, view, x, y, z, corpse, t);
        case DISSOLVE -> dissolve(out, view, x, y, z, corpse, t);
        case ASCEND -> ascend(out, view, x, y, z, corpse, t);
        case VOID -> hole(out, view, x, y, z, corpse, t);
        default -> {}
      }
    }
    return quads;
  }

  private void ritual(
      FxSink out, View view, float x, float y, float z, Corpses.Corpse corpse, float t) {
    float radius = Math.max(1.2F, corpse.width * 1.6F + 0.6F);
    float shown = Fate.smooth(t / 0.18F) * (1.0F - Fate.smooth((t - 0.82F) / 0.18F));
    if (shown <= 0.0F) {
      return;
    }
    flat(out, x, y + LIFT, z, radius * 1.15F, SIGIL, t, shown, corpse.seed);
    for (int k = 0; k < EMBERS; k++) {
      int grain = mix(corpse.seed * 97 + k);
      float phase = fract(t * 1.6F + unit(grain));
      float angle = unit(grain >>> 8) * 6.2831855F;
      float reach = radius * (0.25F + 0.75F * unit(grain >>> 16));
      float px = x + (float) Math.cos(angle) * reach;
      float pz = z + (float) Math.sin(angle) * reach;
      float py = y + phase * (1.4F + 1.4F * unit(grain >>> 24));
      float alpha = (float) Math.sin(phase * Math.PI) * shown;
      billboard(
          out,
          view,
          px,
          py,
          pz,
          0.035F + 0.03F * unit(grain >>> 4),
          EMBER,
          phase,
          alpha,
          grain & 0xFFF);
    }
  }

  private void dissolve(
      FxSink out, View view, float x, float y, float z, Corpses.Corpse corpse, float t) {
    float lying = Math.max(corpse.width, corpse.height * 0.9F);
    for (int k = 0; k < FLECKS; k++) {
      int grain = mix(corpse.seed * 131 + k);
      float birth = unit(grain) * 0.75F;
      float age = (t - birth) / 0.3F;
      if (age <= 0.0F || age >= 1.0F) {
        continue;
      }
      float px = x + (unit(grain >>> 8) - 0.5F) * lying;
      float pz = z + (unit(grain >>> 16) - 0.5F) * lying;
      float py = y + unit(grain >>> 24) * Math.min(corpse.height, 0.6F) + age * 0.9F;
      billboard(
          out,
          view,
          px,
          py,
          pz,
          0.05F * (1.0F - age * 0.6F),
          FLECK,
          age,
          1.0F - age,
          grain & 0xFFF);
    }
  }

  private void ascend(
      FxSink out, View view, float x, float y, float z, Corpses.Corpse corpse, float t) {
    float shown = Fate.smooth(t / 0.2F) * (1.0F - Fate.smooth((t - 0.72F) / 0.28F));
    if (shown <= 0.0F) {
      return;
    }
    float width = Math.max(corpse.width, 0.5F) * 0.9F + 0.35F;
    column(out, view, x, y - 0.4F, z, width, BEAM_HEIGHT, t, shown, corpse.seed);
    float head = y + posture.rise + corpse.height + 0.25F;
    flat(
        out,
        x,
        head,
        z,
        Math.max(corpse.width, 0.5F) * 0.55F,
        HALO,
        t,
        shown * Fate.smooth((t - 0.08F) / 0.2F),
        corpse.seed);
    for (int k = 0; k < MOTES; k++) {
      int grain = mix(corpse.seed * 59 + k);
      float phase = fract(t * 1.3F + unit(grain));
      float angle = unit(grain >>> 8) * 6.2831855F;
      float reach = width * 0.5F * unit(grain >>> 16);
      float py = y + phase * (corpse.height + 3.0F);
      billboard(
          out,
          view,
          x + (float) Math.cos(angle) * reach,
          py,
          z + (float) Math.sin(angle) * reach,
          0.025F + 0.025F * unit(grain >>> 4),
          EMBER,
          phase,
          (float) Math.sin(phase * Math.PI) * shown,
          grain & 0xFFF);
    }
  }

  private void hole(
      FxSink out, View view, float x, float y, float z, Corpses.Corpse corpse, float t) {
    if (Fate.horizon(t) <= 0.0F) {
      return;
    }
    float size = Fate.holeRadius(corpse.height, corpse.width) * HOLE_REACH;
    billboard(out, view, x, y + corpse.height * 0.5F, z, size, CORE, t, 1.0F, corpse.seed & 0xFFF);
  }

  private void flat(
      FxSink out, float x, float y, float z, float half, int kind, float t, float alpha, int seed) {
    int grain = seed & 0xFFF;
    out.vertex(x - half, y, z - half, -1.0F, -1.0F, kind, t, alpha, grain, half);
    out.vertex(x + half, y, z - half, 1.0F, -1.0F, kind, t, alpha, grain, half);
    out.vertex(x + half, y, z + half, 1.0F, 1.0F, kind, t, alpha, grain, half);
    out.vertex(x - half, y, z - half, -1.0F, -1.0F, kind, t, alpha, grain, half);
    out.vertex(x + half, y, z + half, 1.0F, 1.0F, kind, t, alpha, grain, half);
    out.vertex(x - half, y, z + half, -1.0F, 1.0F, kind, t, alpha, grain, half);
    quads++;
  }

  private void column(
      FxSink out,
      View view,
      float x,
      float y,
      float z,
      float width,
      float tall,
      float t,
      float alpha,
      int seed) {
    float sideX = view.rx;
    float sideZ = view.rz;
    float length = (float) Math.sqrt(sideX * sideX + sideZ * sideZ);
    if (length < 1.0e-4F) {
      sideX = 1.0F;
      sideZ = 0.0F;
    } else {
      sideX = sideX / length * width;
      sideZ = sideZ / length * width;
    }
    int grain = seed & 0xFFF;
    float top = y + tall;
    out.vertex(x - sideX, y, z - sideZ, -1.0F, 0.0F, BEAM, t, alpha, grain, tall);
    out.vertex(x + sideX, y, z + sideZ, 1.0F, 0.0F, BEAM, t, alpha, grain, tall);
    out.vertex(x + sideX, top, z + sideZ, 1.0F, 1.0F, BEAM, t, alpha, grain, tall);
    out.vertex(x - sideX, y, z - sideZ, -1.0F, 0.0F, BEAM, t, alpha, grain, tall);
    out.vertex(x + sideX, top, z + sideZ, 1.0F, 1.0F, BEAM, t, alpha, grain, tall);
    out.vertex(x - sideX, top, z - sideZ, -1.0F, 1.0F, BEAM, t, alpha, grain, tall);
    quads++;
  }

  private void billboard(
      FxSink out,
      View view,
      float x,
      float y,
      float z,
      float size,
      int kind,
      float t,
      float alpha,
      int seed) {
    if (alpha <= 0.002F) {
      return;
    }
    float rx = view.rx * size;
    float ry = view.ry * size;
    float rz = view.rz * size;
    float ux = view.ux * size;
    float uy = view.uy * size;
    float uz = view.uz * size;
    out.vertex(x - rx - ux, y - ry - uy, z - rz - uz, -1.0F, -1.0F, kind, t, alpha, seed, size);
    out.vertex(x + rx - ux, y + ry - uy, z + rz - uz, 1.0F, -1.0F, kind, t, alpha, seed, size);
    out.vertex(x + rx + ux, y + ry + uy, z + rz + uz, 1.0F, 1.0F, kind, t, alpha, seed, size);
    out.vertex(x - rx - ux, y - ry - uy, z - rz - uz, -1.0F, -1.0F, kind, t, alpha, seed, size);
    out.vertex(x + rx + ux, y + ry + uy, z + rz + uz, 1.0F, 1.0F, kind, t, alpha, seed, size);
    out.vertex(x - rx + ux, y - ry + uy, z - rz + uz, -1.0F, 1.0F, kind, t, alpha, seed, size);
    quads++;
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

  private static float fract(float value) {
    return value - (float) Math.floor(value);
  }
}
