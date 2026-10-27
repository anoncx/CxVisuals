package me.cxiy.cxvisuals.gui.map;

import me.cxiy.cxvisuals.modules.map.MapAtlas;
import me.cxiy.cxvisuals.modules.map.TileKey;
import org.joml.FrustumIntersection;

public final class TerrainFrame {
  public static final int MAX_TILES = 192;
  public static final int DETAIL_LEVELS = TileKey.REGION_LEVEL + 1;

  private static final float NEAR_REACH = 1.1F;
  private static final float LEVEL_GROWTH = 3.0F;
  private static final float MIN_NEAR = 96.0F;
  private static final float MAX_REACH = 6144.0F;
  private static final float LOW = -64.0F;
  private static final float HIGH = 320.0F;

  public final Camera3D camera = new Camera3D();
  public final int[] slot = new int[MAX_TILES];
  public final float[] originX = new float[MAX_TILES];
  public final float[] originZ = new float[MAX_TILES];
  public final int[] level = new int[MAX_TILES];
  public final int[] stride = new int[MAX_TILES];
  public final int[] north = new int[MAX_TILES];
  public final int[] south = new int[MAX_TILES];
  public final int[] west = new int[MAX_TILES];
  public final int[] east = new int[MAX_TILES];

  public MapAtlas atlas;
  public int count;
  public float left;
  public float top;
  public float width;
  public float height;
  public float alpha = 1.0F;
  public int sky;
  public int horizon;
  public int fog;
  public int terrain;
  public float time;

  private final FrustumIntersection frustum = new FrustumIntersection();

  public void build(MapAtlas source, int layer, float x, float y, float w, float h) {
    atlas = source;
    left = x;
    top = y;
    width = w;
    height = h;
    count = 0;
    frustum.set(camera.culling, false);
    float near = Math.max(MIN_NEAR, camera.distance * NEAR_REACH);
    float reach = Math.min(MAX_REACH, near * LEVEL_GROWTH * LEVEL_GROWTH);
    int coarse = TileKey.REGION_LEVEL;
    int span = TileKey.span(coarse);
    int fromX = (int) Math.floor((camera.targetX - reach) / span);
    int toX = (int) Math.floor((camera.targetX + reach) / span);
    int fromZ = (int) Math.floor((camera.targetZ - reach) / span);
    int toZ = (int) Math.floor((camera.targetZ + reach) / span);
    for (int tz = fromZ; tz <= toZ; tz++) {
      for (int tx = fromX; tx <= toX; tx++) {
        visit(layer, coarse, tx, tz, near);
      }
    }
  }

  private void visit(int layer, int lvl, int tx, int tz, float near) {
    if (count >= MAX_TILES) {
      return;
    }
    int span = TileKey.span(lvl);
    double x0 = (double) tx * span;
    double z0 = (double) tz * span;
    float rx = (float) (x0 - camera.targetX);
    float rz = (float) (z0 - camera.targetZ);
    float ry0 = (float) (LOW - camera.targetY);
    float ry1 = (float) (HIGH - camera.targetY);
    if (!frustum.testAab(rx, ry0, rz, rx + span, ry1, rz + span)) {
      return;
    }
    float cx = Math.max(rx, Math.min(0.0F, rx + span));
    float cz = Math.max(rz, Math.min(0.0F, rz + span));
    float gap = (float) Math.sqrt(cx * cx + cz * cz);
    float limit = near;
    for (int i = 0; i < lvl; i++) {
      limit *= LEVEL_GROWTH;
    }
    if (gap > limit) {
      return;
    }
    float finer = lvl == 0 ? 0.0F : limit / LEVEL_GROWTH;
    if (lvl > 0 && gap < finer && ready(layer, lvl - 1, tx, tz)) {
      for (int c = 0; c < 4; c++) {
        visit(layer, lvl - 1, tx * 2 + (c & 1), tz * 2 + (c >> 1), near);
      }
      return;
    }
    long key = TileKey.of(layer, lvl, tx, tz);
    int found = atlas.slot(key);
    if (found <= MapAtlas.EMPTY) {
      return;
    }
    slot[count] = found;
    originX[count] = rx;
    originZ[count] = rz;
    level[count] = lvl;
    stride[count] = gap > limit * 0.6F && lvl == TileKey.REGION_LEVEL ? 2 : 1;
    north[count] = neighbour(layer, lvl, tx, tz - 1);
    south[count] = neighbour(layer, lvl, tx, tz + 1);
    west[count] = neighbour(layer, lvl, tx - 1, tz);
    east[count] = neighbour(layer, lvl, tx + 1, tz);
    count++;
  }

  private boolean ready(int layer, int lvl, int tx, int tz) {
    boolean all = true;
    for (int c = 0; c < 4; c++) {
      long key = TileKey.of(layer, lvl, tx * 2 + (c & 1), tz * 2 + (c >> 1));
      if (atlas.slot(key) == MapAtlas.MISSING) {
        all = false;
      }
    }
    return all;
  }

  private int neighbour(int layer, int lvl, int tx, int tz) {
    int found = atlas.peek(TileKey.of(layer, lvl, tx, tz));
    return found > MapAtlas.EMPTY ? found : -1;
  }
}
