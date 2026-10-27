package me.cxiy.cxvisuals.gui.map;

import me.cxiy.cxvisuals.modules.map.MapAtlas;
import me.cxiy.cxvisuals.modules.map.TileKey;

public final class ChartBatch {
  public static final int MAX_QUADS = 512;
  public static final float CLOUD_CELLS = 2.0F;
  public static final int CLOUD_PERIOD = 256;

  private static final int PARENTS = 3;

  public final float[] px = new float[MAX_QUADS * 4];
  public final float[] py = new float[MAX_QUADS * 4];
  public final float[] mx = new float[MAX_QUADS * 4];
  public final float[] my = new float[MAX_QUADS * 4];
  public final float[] cx = new float[MAX_QUADS * 4];
  public final float[] cy = new float[MAX_QUADS * 4];
  public final float[] u0 = new float[MAX_QUADS];
  public final float[] v0 = new float[MAX_QUADS];
  public final float[] u1 = new float[MAX_QUADS];
  public final float[] v1 = new float[MAX_QUADS];

  public MapAtlas atlas;
  public int count;
  public float left;
  public float top;
  public float width;
  public float height;
  public float roundness;
  public boolean masked;
  public float clouds = 1.0F;
  public int fog;
  public float alpha = 1.0F;

  public void build(
      MapAtlas source, Viewport view, int layer, float x, float y, float w, float h, int pad) {
    atlas = source;
    count = 0;
    left = x;
    top = y;
    width = w;
    height = h;
    int level = view.level();
    int span = TileKey.span(level);
    double minX = Double.MAX_VALUE;
    double minZ = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    double maxZ = -Double.MAX_VALUE;
    for (int i = 0; i < 4; i++) {
      float sx = (i & 1) == 0 ? x - pad : x + w + pad;
      float sy = (i & 2) == 0 ? y - pad : y + h + pad;
      double wx = view.worldX(sx, sy);
      double wz = view.worldZ(sx, sy);
      minX = Math.min(minX, wx);
      maxX = Math.max(maxX, wx);
      minZ = Math.min(minZ, wz);
      maxZ = Math.max(maxZ, wz);
    }
    int fromX = (int) Math.floor(minX / span);
    int toX = (int) Math.floor(maxX / span);
    int fromZ = (int) Math.floor(minZ / span);
    int toZ = (int) Math.floor(maxZ / span);
    float halfW = w * 0.5F;
    float halfH = h * 0.5F;
    float midX = x + halfW;
    float midY = y + halfH;
    for (int tz = fromZ; tz <= toZ; tz++) {
      for (int tx = fromX; tx <= toX && count < MAX_QUADS; tx++) {
        long key = TileKey.of(layer, level, tx, tz);
        int slot = source.slot(key);
        float su = 0.0F;
        float sv = 0.0F;
        float size = 1.0F;
        if (slot == MapAtlas.MISSING) {
          long parent = key;
          for (int up = 1; up <= PARENTS && level + up <= TileKey.MAX_LEVEL; up++) {
            parent = TileKey.parent(parent);
            int found = source.slot(parent);
            if (found > MapAtlas.EMPTY) {
              int mask = (1 << up) - 1;
              size = 1.0F / (1 << up);
              su = (tx & mask) * size;
              sv = (tz & mask) * size;
              slot = found;
              break;
            }
            if (found == MapAtlas.EMPTY) {
              slot = MapAtlas.EMPTY;
              break;
            }
          }
          if (slot == MapAtlas.MISSING) {
            slot = MapAtlas.EMPTY;
          }
        }
        float cell = 1.0F / MapAtlas.GRID;
        float inset = 0.01F / MapAtlas.SIZE;
        u0[count] = MapAtlas.u(slot) + su * cell + inset;
        v0[count] = MapAtlas.v(slot) + sv * cell + inset;
        u1[count] = MapAtlas.u(slot) + (su + size) * cell - inset;
        v1[count] = MapAtlas.v(slot) + (sv + size) * cell - inset;
        double wx0 = (double) tx * span;
        double wz0 = (double) tz * span;
        float cloudX = Math.floorMod(tx * (int) CLOUD_CELLS, CLOUD_PERIOD);
        float cloudY = Math.floorMod(tz * (int) CLOUD_CELLS, CLOUD_PERIOD);
        for (int i = 0; i < 4; i++) {
          boolean right = i >= 2;
          boolean bottom = i == 1 || i == 2;
          double wx = wx0 + (right ? span : 0);
          double wz = wz0 + (bottom ? span : 0);
          int at = count * 4 + i;
          float sx = view.screenX(wx, wz);
          float sy = view.screenY(wx, wz);
          px[at] = sx;
          py[at] = sy;
          mx[at] = (sx - midX) / halfW;
          my[at] = (sy - midY) / halfH;
          cx[at] = cloudX + (right ? CLOUD_CELLS : 0.0F);
          cy[at] = cloudY + (bottom ? CLOUD_CELLS : 0.0F);
        }
        count++;
      }
    }
  }
}
