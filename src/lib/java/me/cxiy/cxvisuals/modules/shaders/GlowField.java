package me.cxiy.cxvisuals.modules.shaders;

public final class GlowField {
  public static final int LEVELS = 6;
  public static final float MIX = 0.5F;
  public static final float STEP = 0.25F;
  public static final float[] TABLE = {
    0.000F, 0.496F, 1.034F, 1.454F, 2.209F, 3.293F,
    4.638F, 6.123F, 7.931F, 10.337F, 13.321F, 16.902F,
    21.390F, 26.777F, 32.884F, 39.836F, 47.491F, 55.426F,
    63.405F, 71.390F, 79.336F, 87.144F, 94.702F, 101.940F,
    108.823F, 115.356F, 121.560F, 127.462F, 133.094F
  };

  private static final int EDGE_WIDTH = 1024;
  private static final int EDGE_HEIGHT = 2;
  private static final int EDGE_FROM = 480;
  private static final int EDGE_SHIFTS = 64;
  private static final int EDGE_SPAN = 400;
  private static final float FURTHEST = 128.0F;

  private GlowField() {}

  public static float[] blur(float[] mask, int width, int height) {
    float[][] down = new float[LEVELS + 1][];
    int[] widths = new int[LEVELS + 1];
    int[] heights = new int[LEVELS + 1];
    for (int level = 1; level <= LEVELS; level++) {
      widths[level] = ScreenBounds.size(width, level);
      heights[level] = ScreenBounds.size(height, level);
    }
    down[1] = first(mask, width, height, widths[1], heights[1]);
    for (int level = 2; level <= LEVELS; level++) {
      down[level] =
          down(
              down[level - 1],
              widths[level - 1],
              heights[level - 1],
              widths[level],
              heights[level]);
    }
    float[] up = down[LEVELS];
    for (int level = LEVELS - 1; level >= 1; level--) {
      up =
          up(up, widths[level + 1], heights[level + 1], down[level], widths[level], heights[level]);
    }
    return up;
  }

  private static float[] first(float[] mask, int width, int height, int toWidth, int toHeight) {
    float[] to = new float[toWidth * toHeight];
    for (int j = 0; j < toHeight; j++) {
      for (int i = 0; i < toWidth; i++) {
        float sum = 0.0F;
        for (int corner = 0; corner < 4; corner++) {
          int x = Math.min(width - 1, i * 2 + (corner & 1));
          int y = Math.min(height - 1, j * 2 + (corner >> 1));
          sum += mask[y * width + x];
        }
        to[j * toWidth + i] = sum * 0.25F;
      }
    }
    return to;
  }

  private static float[] down(float[] from, int width, int height, int toWidth, int toHeight) {
    float[] to = new float[toWidth * toHeight];
    for (int j = 0; j < toHeight; j++) {
      for (int i = 0; i < toWidth; i++) {
        float x = i * 2.0F + 1.0F;
        float y = j * 2.0F + 1.0F;
        float sum = texel(from, width, height, x, y) * 4.0F;
        sum += texel(from, width, height, x - 1.0F, y - 1.0F);
        sum += texel(from, width, height, x + 1.0F, y - 1.0F);
        sum += texel(from, width, height, x - 1.0F, y + 1.0F);
        sum += texel(from, width, height, x + 1.0F, y + 1.0F);
        to[j * toWidth + i] = sum * 0.125F;
      }
    }
    return to;
  }

  private static float[] up(
      float[] from, int width, int height, float[] same, int toWidth, int toHeight) {
    float[] to = new float[toWidth * toHeight];
    for (int j = 0; j < toHeight; j++) {
      for (int i = 0; i < toWidth; i++) {
        float x = (i + 0.5F) * 0.5F;
        float y = (j + 0.5F) * 0.5F;
        float sum = texel(from, width, height, x - 1.0F, y);
        sum += texel(from, width, height, x + 1.0F, y);
        sum += texel(from, width, height, x, y - 1.0F);
        sum += texel(from, width, height, x, y + 1.0F);
        sum += texel(from, width, height, x - 0.5F, y - 0.5F) * 2.0F;
        sum += texel(from, width, height, x + 0.5F, y - 0.5F) * 2.0F;
        sum += texel(from, width, height, x - 0.5F, y + 0.5F) * 2.0F;
        sum += texel(from, width, height, x + 0.5F, y + 0.5F) * 2.0F;
        float wide = sum / 12.0F;
        to[j * toWidth + i] = wide + (same[j * toWidth + i] - wide) * MIX;
      }
    }
    return to;
  }

  private static float texel(float[] grid, int width, int height, float x, float y) {
    float fx = Math.max(0.0F, Math.min(width - 1.0F, x - 0.5F));
    float fy = Math.max(0.0F, Math.min(height - 1.0F, y - 0.5F));
    int x0 = (int) fx;
    int y0 = (int) fy;
    int x1 = Math.min(width - 1, x0 + 1);
    int y1 = Math.min(height - 1, y0 + 1);
    float tx = fx - x0;
    float ty = fy - y0;
    float top = grid[y0 * width + x0] + (grid[y0 * width + x1] - grid[y0 * width + x0]) * tx;
    float bottom = grid[y1 * width + x0] + (grid[y1 * width + x1] - grid[y1 * width + x0]) * tx;
    return top + (bottom - top) * ty;
  }

  public static float lookup(float[] field, int width, int height, float x, float y) {
    return texel(
        field,
        ScreenBounds.size(width, 1),
        ScreenBounds.size(height, 1),
        (x + 0.5F) * 0.5F,
        (y + 0.5F) * 0.5F);
  }

  public static float distance(float coverage) {
    if (coverage == 0.5F) {
      return 0.0F;
    }
    return coverage > 0.5F ? -span(1.0F - coverage) : span(coverage);
  }

  public static float span(float share) {
    if (!(share > 0.0F)) {
      return Float.NaN;
    }
    float at = (float) (-Math.log(2.0F * share) / STEP);
    int last = TABLE.length - 1;
    if (at >= last) {
      return Float.NaN;
    }
    int slot = Math.max(0, (int) at);
    return TABLE[slot] + (TABLE[slot + 1] - TABLE[slot]) * Math.max(0.0F, at - slot);
  }

  private static float[] profile() {
    float[] mean = new float[EDGE_SPAN];
    for (int shift = 0; shift < EDGE_SHIFTS; shift++) {
      int edge = EDGE_FROM + shift;
      float[] mask = new float[EDGE_WIDTH * EDGE_HEIGHT];
      for (int y = 0; y < EDGE_HEIGHT; y++) {
        for (int x = edge; x < EDGE_WIDTH; x++) {
          mask[y * EDGE_WIDTH + x] = 1.0F;
        }
      }
      float[] field = blur(mask, EDGE_WIDTH, EDGE_HEIGHT);
      for (int out = 0; out < EDGE_SPAN; out++) {
        mean[out] += lookup(field, EDGE_WIDTH, EDGE_HEIGHT, edge - 1 - out, 0) / EDGE_SHIFTS;
      }
    }
    return mean;
  }

  public static float[] table() {
    float[] mean = profile();
    int count = 1;
    while (crossing(mean, count) < FURTHEST) {
      count++;
    }
    float[] table = new float[count + 1];
    for (int i = 1; i <= count; i++) {
      table[i] = crossing(mean, i);
    }
    return table;
  }

  private static float crossing(float[] mean, int entry) {
    float wanted = (float) (0.5 * Math.exp(-entry * (double) STEP));
    float before = 0.5F;
    float beforeAt = 0.0F;
    for (int out = 0; out < mean.length; out++) {
      float here = mean[out];
      float hereAt = out + 0.5F;
      if (here <= wanted) {
        return beforeAt
            + (hereAt - beforeAt) * (before - wanted) / Math.max(before - here, 1.0e-12F);
      }
      before = here;
      beforeAt = hereAt;
    }
    return Float.MAX_VALUE;
  }
}
