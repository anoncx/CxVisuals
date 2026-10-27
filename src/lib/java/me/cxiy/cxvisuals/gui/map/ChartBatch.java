package me.cxiy.cxvisuals.gui.map;

import me.cxiy.cxvisuals.modules.map.MapAtlas;
import me.cxiy.cxvisuals.modules.map.TileKey;

public final class ChartBatch {
  public static final int MAX_QUADS = 512;
  public static final float CLOUD_CELLS = 2.0F;
  public static final int CLOUD_PERIOD = 256;

  private static final int PARENTS = 3;
  private static final float CLIP_PAD = 2.0F;

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
  public float radius;
  public boolean masked;
  public float clouds = 1.0F;
  public int fog;
  public float alpha = 1.0F;

  public void build(
      MapAtlas source, Viewport view, int layer, float x, float y, float w, float h, int pad) {
    build(source, view, layer, x, y, w, h, pad, 0);
  }

  public void build(
      MapAtlas source,
      Viewport view,
      int layer,
      float x,
      float y,
      float w,
      float h,
      int pad,
      int coarser) {
    atlas = source;
    count = 0;
    left = x;
    top = y;
    width = w;
    height = h;
    int level = Math.min(TileKey.MAX_LEVEL, view.level() + coarser);
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
    for (int tz = fromZ; tz <= toZ; tz++) {
      for (int tx = fromX; tx <= toX && count < MAX_QUADS; tx++) {
        long key = TileKey.of(layer, level, tx, tz);
        int slot = source.slot(key);
        float cloudX = Math.floorMod(tx * (int) CLOUD_CELLS, CLOUD_PERIOD);
        float cloudY = Math.floorMod(tz * (int) CLOUD_CELLS, CLOUD_PERIOD);
        double wx0 = (double) tx * span;
        double wz0 = (double) tz * span;
        if (slot == MapAtlas.MISSING && level > 0 && children(source, layer, level, tx, tz)) {
          double half = span * 0.5;
          for (int c = 0; c < 4 && count < MAX_QUADS; c++) {
            int ox = c & 1;
            int oz = c >> 1;
            int child = source.slot(TileKey.of(layer, level - 1, tx * 2 + ox, tz * 2 + oz));
            quad(
                view,
                wx0 + ox * half,
                wz0 + oz * half,
                half,
                child,
                0.0F,
                0.0F,
                1.0F,
                cloudX + ox * CLOUD_CELLS * 0.5F,
                cloudY + oz * CLOUD_CELLS * 0.5F,
                CLOUD_CELLS * 0.5F);
          }
          continue;
        }
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
        quad(view, wx0, wz0, span, slot, su, sv, size, cloudX, cloudY, CLOUD_CELLS);
      }
    }
  }

  private static boolean children(MapAtlas source, int layer, int level, int tx, int tz) {
    for (int c = 0; c < 4; c++) {
      long key = TileKey.of(layer, level - 1, tx * 2 + (c & 1), tz * 2 + (c >> 1));
      if (source.peek(key) == MapAtlas.MISSING) {
        return false;
      }
    }
    return true;
  }

  private void quad(
      Viewport view,
      double wx0,
      double wz0,
      double span,
      int slot,
      float su,
      float sv,
      float size,
      float cloudX,
      float cloudY,
      float cloudSpan) {
    float cell = 1.0F / MapAtlas.GRID;
    float inset = 0.01F / MapAtlas.SIZE;
    float ua = MapAtlas.u(slot) + su * cell + inset;
    float va = MapAtlas.v(slot) + sv * cell + inset;
    float ub = MapAtlas.u(slot) + (su + size) * cell - inset;
    float vb = MapAtlas.v(slot) + (sv + size) * cell - inset;
    float halfW = width * 0.5F;
    float halfH = height * 0.5F;
    float midX = left + halfW;
    float midY = top + halfH;
    if (view.angle == 0.0F) {
      float x0 = view.screenX(wx0, wz0);
      float y0 = view.screenY(wx0, wz0);
      float x1 = view.screenX(wx0 + span, wz0 + span);
      float y1 = view.screenY(wx0 + span, wz0 + span);
      float cx0 = Math.max(x0, left - CLIP_PAD);
      float cy0 = Math.max(y0, top - CLIP_PAD);
      float cx1 = Math.min(x1, left + width + CLIP_PAD);
      float cy1 = Math.min(y1, top + height + CLIP_PAD);
      if (cx1 <= cx0 || cy1 <= cy0) {
        return;
      }
      float fx0 = (cx0 - x0) / (x1 - x0);
      float fy0 = (cy0 - y0) / (y1 - y0);
      float fx1 = (cx1 - x0) / (x1 - x0);
      float fy1 = (cy1 - y0) / (y1 - y0);
      u0[count] = ua + (ub - ua) * fx0;
      v0[count] = va + (vb - va) * fy0;
      u1[count] = ua + (ub - ua) * fx1;
      v1[count] = va + (vb - va) * fy1;
      for (int i = 0; i < 4; i++) {
        boolean right = i >= 2;
        boolean bottom = i == 1 || i == 2;
        int at = count * 4 + i;
        float sx = right ? cx1 : cx0;
        float sy = bottom ? cy1 : cy0;
        px[at] = sx;
        py[at] = sy;
        mx[at] = (sx - midX) / halfW;
        my[at] = (sy - midY) / halfH;
        cx[at] = cloudX + cloudSpan * (right ? fx1 : fx0);
        cy[at] = cloudY + cloudSpan * (bottom ? fy1 : fy0);
      }
      count++;
      return;
    }
    u0[count] = ua;
    v0[count] = va;
    u1[count] = ub;
    v1[count] = vb;
    for (int i = 0; i < 4; i++) {
      boolean right = i >= 2;
      boolean bottom = i == 1 || i == 2;
      double wx = wx0 + (right ? span : 0.0);
      double wz = wz0 + (bottom ? span : 0.0);
      int at = count * 4 + i;
      float sx = view.screenX(wx, wz);
      float sy = view.screenY(wx, wz);
      px[at] = sx;
      py[at] = sy;
      mx[at] = (sx - midX) / halfW;
      my[at] = (sy - midY) / halfH;
      cx[at] = cloudX + (right ? cloudSpan : 0.0F);
      cy[at] = cloudY + (bottom ? cloudSpan : 0.0F);
    }
    count++;
  }
}
