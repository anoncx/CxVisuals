package me.cxiy.cxvisuals.modules.swings;

public final class SwingMesh {
  public static final int STRIPS = 7;
  public static final int QUADS = 1024 * STRIPS;

  private static final float SPAN = 0.05F;
  private static final int FINEST = 16;
  private static final float HALO = 0.4F;
  private static final int CHANNELS = 7;
  private static final int AGE = 6;

  private final double[] controls = new double[4 * CHANNELS];
  private final float[] stops = new float[STRIPS + 1];
  private final float[] older = new float[CHANNELS];
  private final float[] newer = new float[CHANNELS];
  private float halo;
  private float life;
  private float fade;
  private int seed;
  private float arc;
  private int emitted;
  private int budget;
  private SwingSink sink;

  public float halo(float widthScale) {
    return HALO * widthScale;
  }

  public int emit(
      Ribbon ribbon,
      double cameraX,
      double cameraY,
      double cameraZ,
      float widthScale,
      float lifeSeconds,
      float alpha,
      int identity,
      long nanos,
      int room,
      SwingSink out) {
    int n = ribbon.count();
    if (n < 2 || room <= 0 || alpha <= 0.0F) {
      return 0;
    }
    halo = halo(widthScale);
    stops[0] = -halo;
    stops[1] = -halo * 0.55F;
    stops[2] = -halo * 0.25F;
    stops[3] = -0.07F;
    stops[4] = 0.07F;
    stops[5] = 0.3F;
    stops[6] = 0.65F;
    stops[7] = 1.0F;
    life = lifeSeconds;
    fade = alpha;
    seed = identity & 0xFFF;
    arc = 0.0F;
    emitted = 0;
    budget = room;
    sink = out;
    boolean open = false;
    for (int i = 0; i < n - 1 && emitted + STRIPS <= budget; i++) {
      if (!ribbon.linked(i)) {
        open = false;
        continue;
      }
      load(ribbon, i, n, nanos, cameraX, cameraY, cameraZ);
      float dx = (float) (controls[CHANNELS + 3] - controls[CHANNELS * 2 + 3]);
      float dy = (float) (controls[CHANNELS + 4] - controls[CHANNELS * 2 + 4]);
      float dz = (float) (controls[CHANNELS + 5] - controls[CHANNELS * 2 + 5]);
      float reach = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
      int steps = Math.clamp((int) Math.ceil(reach / SPAN), 1, FINEST);
      for (int s = open ? 1 : 0; s <= steps && emitted + STRIPS <= budget; s++) {
        column(newer, s / (float) steps);
        if (s > 0 || open) {
          link(older, newer);
        }
        System.arraycopy(newer, 0, older, 0, older.length);
        open = true;
      }
    }
    sink = null;
    return emitted;
  }

  private void load(
      Ribbon ribbon, int i, int n, long nanos, double cameraX, double cameraY, double cameraZ) {
    int before = i > 0 && ribbon.linked(i - 1) ? i - 1 : i;
    int after = i + 2 < n && ribbon.linked(i + 1) ? i + 2 : i + 1;
    control(ribbon, 0, before, nanos, cameraX, cameraY, cameraZ);
    control(ribbon, 1, i, nanos, cameraX, cameraY, cameraZ);
    control(ribbon, 2, i + 1, nanos, cameraX, cameraY, cameraZ);
    control(ribbon, 3, after, nanos, cameraX, cameraY, cameraZ);
  }

  private void control(
      Ribbon ribbon, int slot, int i, long nanos, double cameraX, double cameraY, double cameraZ) {
    int at = slot * CHANNELS;
    controls[at] = ribbon.at(i, 0) - cameraX;
    controls[at + 1] = ribbon.at(i, 1) - cameraY;
    controls[at + 2] = ribbon.at(i, 2) - cameraZ;
    controls[at + 3] = ribbon.at(i, 3) - cameraX;
    controls[at + 4] = ribbon.at(i, 4) - cameraY;
    controls[at + 5] = ribbon.at(i, 5) - cameraZ;
    controls[at + AGE] = ribbon.age(i, nanos);
  }

  private void column(float[] out, float u) {
    for (int c = 0; c < CHANNELS; c++) {
      out[c] =
          (float)
              spline(
                  controls[c],
                  controls[CHANNELS + c],
                  controls[CHANNELS * 2 + c],
                  controls[CHANNELS * 3 + c],
                  u);
    }
  }

  private void link(float[] from, float[] to) {
    float dx = to[3] - from[3];
    float dy = to[4] - from[4];
    float dz = to[5] - from[5];
    float travelled = arc + (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    float t0 = Math.clamp(from[AGE] / life, 0.0F, 1.0F);
    float t1 = Math.clamp(to[AGE] / life, 0.0F, 1.0F);
    for (int s = 0; s < STRIPS; s++) {
      float near = stops[s];
      float far = stops[s + 1];
      float fromNearX = from[3] - (from[3] - from[0]) * near;
      float fromNearY = from[4] - (from[4] - from[1]) * near;
      float fromNearZ = from[5] - (from[5] - from[2]) * near;
      float fromFarX = from[3] - (from[3] - from[0]) * far;
      float fromFarY = from[4] - (from[4] - from[1]) * far;
      float fromFarZ = from[5] - (from[5] - from[2]) * far;
      float toNearX = to[3] - (to[3] - to[0]) * near;
      float toNearY = to[4] - (to[4] - to[1]) * near;
      float toNearZ = to[5] - (to[5] - to[2]) * near;
      float toFarX = to[3] - (to[3] - to[0]) * far;
      float toFarY = to[4] - (to[4] - to[1]) * far;
      float toFarZ = to[5] - (to[5] - to[2]) * far;
      sink.vertex(fromNearX, fromNearY, fromNearZ, near, arc, t0, fade, seed);
      sink.vertex(fromFarX, fromFarY, fromFarZ, far, arc, t0, fade, seed);
      sink.vertex(toFarX, toFarY, toFarZ, far, travelled, t1, fade, seed);
      sink.vertex(fromNearX, fromNearY, fromNearZ, near, arc, t0, fade, seed);
      sink.vertex(toFarX, toFarY, toFarZ, far, travelled, t1, fade, seed);
      sink.vertex(toNearX, toNearY, toNearZ, near, travelled, t1, fade, seed);
    }
    arc = travelled;
    emitted += STRIPS;
  }

  private static double spline(double a, double b, double c, double d, double u) {
    double u2 = u * u;
    return 0.5
        * (2.0 * b + (c - a) * u + (2.0 * a - 5.0 * b + 4.0 * c - d) * u2
            + (3.0 * b - a - 3.0 * c + d) * u2 * u);
  }
}
