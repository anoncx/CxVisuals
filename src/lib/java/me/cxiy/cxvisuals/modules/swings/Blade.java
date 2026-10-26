package me.cxiy.cxvisuals.modules.swings;

public final class Blade {
  private static final float START = 0.205F;
  private static final float END = 0.974F;
  private static final float BAND = 0.04F;
  private static final float SHORTEST = 0.0025F;

  public float baseX;
  public float baseY;
  public float baseZ;
  public float tipX;
  public float tipY;
  public float tipZ;

  public boolean measure(float[] xyz, int vertices) {
    float min = Float.POSITIVE_INFINITY;
    float max = Float.NEGATIVE_INFINITY;
    for (int i = 0; i < vertices; i++) {
      float p = xyz[i * 3] + xyz[i * 3 + 1];
      min = Math.min(min, p);
      max = Math.max(max, p);
    }
    float band = (max - min) * BAND;
    float tx = 0.0F;
    float ty = 0.0F;
    float tz = 0.0F;
    float bx = 0.0F;
    float by = 0.0F;
    float bz = 0.0F;
    int tips = 0;
    int butts = 0;
    for (int i = 0; i < vertices; i++) {
      float x = xyz[i * 3];
      float y = xyz[i * 3 + 1];
      float z = xyz[i * 3 + 2];
      float p = x + y;
      if (p >= max - band) {
        tx += x;
        ty += y;
        tz += z;
        tips++;
      }
      if (p <= min + band) {
        bx += x;
        by += y;
        bz += z;
        butts++;
      }
    }
    if (tips == 0 || butts == 0) {
      return false;
    }
    float dx = tx / tips - bx / butts;
    float dy = ty / tips - by / butts;
    float dz = tz / tips - bz / butts;
    if (dx * dx + dy * dy + dz * dz < SHORTEST) {
      return false;
    }
    span(bx / butts, by / butts, bz / butts, dx, dy, dz);
    return true;
  }

  public void fallback() {
    span(0.025F, 0.025F, 0.5F, 0.95F, 0.95F, 0.0F);
  }

  private void span(float x, float y, float z, float dx, float dy, float dz) {
    baseX = x + dx * START;
    baseY = y + dy * START;
    baseZ = z + dz * START;
    tipX = x + dx * END;
    tipY = y + dy * END;
    tipZ = z + dz * END;
  }
}
