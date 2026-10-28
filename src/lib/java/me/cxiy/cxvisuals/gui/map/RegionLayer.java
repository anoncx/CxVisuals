package me.cxiy.cxvisuals.gui.map;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.modules.map.regions.DonutRegions;

final class RegionLayer {
  private static final float TINT = 0.2F;
  private static final float EDGE = 2.4F;
  private static final float HAIR = 1.0F;
  private static final float NAME_SIZE = 150.0F;
  private static final float NUMBER_SIZE = 34.0F;
  private static final float LEGEND_WIDTH = 158.0F;
  private static final float LEGEND_ROW = 24.0F;
  private static final int WHITE = 0xFFFFFFFF;

  private final Anim fade = Anim.reveal(0.0F);
  private final float[] xs = new float[2];
  private final float[] ys = new float[2];
  private final float[] projected = new float[3];

  boolean active() {
    return fade.get() > 0.003F || fade.target() > 0.5F;
  }

  void set(boolean on, float dt) {
    fade.set(on ? 1.0F : 0.0F);
    fade.update(dt);
  }

  void flat(
      Canvas c,
      Theme t,
      Viewport view,
      float x,
      float y,
      float w,
      float h,
      float hoverX,
      float hoverY) {
    float alpha = fade.get();
    if (alpha <= 0.003F) {
      return;
    }
    double west = Math.min(view.worldX(x, y), view.worldX(x + w, y + h));
    double east = Math.max(view.worldX(x, y), view.worldX(x + w, y + h));
    double north = Math.min(view.worldZ(x, y), view.worldZ(x + w, y + h));
    double south = Math.max(view.worldZ(x, y), view.worldZ(x + w, y + h));
    int firstColumn = clamp((int) Math.floor((west + DonutRegions.HALF) / DonutRegions.CELL));
    int lastColumn = clamp((int) Math.floor((east + DonutRegions.HALF) / DonutRegions.CELL));
    int firstRow = clamp((int) Math.floor((north + DonutRegions.HALF) / DonutRegions.CELL));
    int lastRow = clamp((int) Math.floor((south + DonutRegions.HALF) / DonutRegions.CELL));
    int hover = DonutRegions.cellAt(view.worldX(hoverX, hoverY), view.worldZ(hoverX, hoverY));
    c.pushAlpha(alpha);
    for (int row = firstRow; row <= lastRow; row++) {
      for (int column = firstColumn; column <= lastColumn; column++) {
        fill(c, view, row * DonutRegions.GRID + column + 1, x, y, w, h);
      }
    }
    for (int row = firstRow; row <= lastRow; row++) {
      for (int column = firstColumn; column <= lastColumn; column++) {
        edges(c, view, row * DonutRegions.GRID + column + 1, x, y, w, h, hover);
      }
    }
    for (int row = firstRow; row <= lastRow; row++) {
      for (int column = firstColumn; column <= lastColumn; column++) {
        label(c, t, view, row * DonutRegions.GRID + column + 1, x, y, w, h, hover);
      }
    }
    c.popAlpha();
  }

  void solid(Canvas c, Theme t, Camera3D camera, double plane, float w, float h) {
    float alpha = fade.get();
    if (alpha <= 0.003F || !c.curves()) {
      return;
    }
    c.pushAlpha(alpha);
    for (int cell = 1; cell <= DonutRegions.CELLS; cell++) {
      double x0 = DonutRegions.minX(cell);
      double z0 = DonutRegions.minZ(cell);
      double x1 = x0 + DonutRegions.CELL;
      double z1 = z0 + DonutRegions.CELL;
      boolean edge = DonutRegions.borderEast(cell);
      if (edge) {
        line(c, camera, plane, x1, z0, x1, z1, edgeColor(cell, EDGE));
      }
      if (DonutRegions.borderSouth(cell)) {
        line(c, camera, plane, x0, z1, x1, z1, edgeColor(cell, EDGE));
      }
      if (DonutRegions.column(cell) == 0) {
        line(c, camera, plane, x0, z0, x0, z1, edgeColor(cell, EDGE));
      }
      if (DonutRegions.row(cell) == 0) {
        line(c, camera, plane, x0, z0, x1, z0, edgeColor(cell, EDGE));
      }
      if (camera.pixelsPerBlock() * DonutRegions.CELL >= NUMBER_SIZE
          && camera.project(
              x0 + DonutRegions.CELL / 2.0, plane, z0 + DonutRegions.CELL / 2.0, projected)
          && projected[0] > 0.0F
          && projected[0] < w
          && projected[1] > 0.0F
          && projected[1] < h) {
        chip(
            c,
            t,
            DonutRegions.region(cell).label(),
            projected[0],
            projected[1],
            DonutRegions.region(cell).color());
      }
    }
    c.popAlpha();
  }

  void legend(Canvas c, Theme t, MapChrome.Frost frost, float x, float y, int current) {
    float alpha = fade.get();
    if (alpha <= 0.003F) {
      return;
    }
    DonutRegions.Region[] all = DonutRegions.Region.values();
    float height = 42.0F + all.length * LEGEND_ROW;
    c.pushAlpha(alpha);
    MapChrome.panel(c, t, frost, x, y, LEGEND_WIDTH, height, 16.0F);
    c.text(FontStyle.CAPTION, Lang.tr("Regions"), x + 16.0F, y + 14.0F, t.grey());
    DonutRegions.Region here = current > 0 ? DonutRegions.region(current) : null;
    for (int i = 0; i < all.length; i++) {
      float rowY = y + 34.0F + i * LEGEND_ROW;
      boolean mine = all[i] == here;
      if (mine) {
        c.roundRect(
            x + 8.0F,
            rowY - 3.0F,
            LEGEND_WIDTH - 16.0F,
            LEGEND_ROW - 2.0F,
            9.0F,
            Theme.withAlpha(all[i].color(), 0.22F));
      }
      c.roundRect(
          x + 18.0F,
          rowY + (LEGEND_ROW - 2.0F - 9.0F) * 0.5F - 3.0F,
          9.0F,
          9.0F,
          4.5F,
          all[i].color());
      c.text(
          FontStyle.LABEL,
          all[i].label(),
          x + 34.0F,
          rowY + (LEGEND_ROW - 2.0F - c.lineHeight(FontStyle.LABEL)) * 0.5F - 3.0F,
          mine ? t.ink() : t.ink2());
    }
    c.popAlpha();
  }

  private static int clamp(int index) {
    return Math.max(0, Math.min(DonutRegions.GRID - 1, index));
  }

  private void fill(Canvas c, Viewport view, int cell, float x, float y, float w, float h) {
    float x0 = view.screenX(DonutRegions.minX(cell), DonutRegions.minZ(cell));
    float y0 = view.screenY(DonutRegions.minX(cell), DonutRegions.minZ(cell));
    float x1 =
        view.screenX(
            DonutRegions.minX(cell) + DonutRegions.CELL,
            DonutRegions.minZ(cell) + DonutRegions.CELL);
    float y1 =
        view.screenY(
            DonutRegions.minX(cell) + DonutRegions.CELL,
            DonutRegions.minZ(cell) + DonutRegions.CELL);
    float cx0 = Math.max(x, Math.min(x0, x1));
    float cy0 = Math.max(y, Math.min(y0, y1));
    float cx1 = Math.min(x + w, Math.max(x0, x1));
    float cy1 = Math.min(y + h, Math.max(y0, y1));
    if (cx1 > cx0 && cy1 > cy0) {
      c.roundRect(
          cx0,
          cy0,
          cx1 - cx0,
          cy1 - cy0,
          0.0F,
          Theme.withAlpha(DonutRegions.region(cell).color(), TINT));
    }
  }

  private void edges(
      Canvas c, Viewport view, int cell, float x, float y, float w, float h, int hover) {
    double x0 = DonutRegions.minX(cell);
    double z0 = DonutRegions.minZ(cell);
    double x1 = x0 + DonutRegions.CELL;
    double z1 = z0 + DonutRegions.CELL;
    DonutRegions.Region region = DonutRegions.region(cell);
    boolean east = DonutRegions.borderEast(cell);
    boolean south = DonutRegions.borderSouth(cell);
    rule(
        c,
        view,
        x0,
        z1,
        x1,
        z1,
        south ? EDGE : HAIR,
        south ? region.color() : WHITE,
        south ? 0.95F : 0.14F,
        x,
        y,
        w,
        h);
    rule(
        c,
        view,
        x1,
        z0,
        x1,
        z1,
        east ? EDGE : HAIR,
        east ? region.color() : WHITE,
        east ? 0.95F : 0.14F,
        x,
        y,
        w,
        h);
    if (DonutRegions.column(cell) == 0) {
      rule(c, view, x0, z0, x0, z1, EDGE, region.color(), 0.95F, x, y, w, h);
    }
    if (DonutRegions.row(cell) == 0) {
      rule(c, view, x0, z0, x1, z0, EDGE, region.color(), 0.95F, x, y, w, h);
    }
    if (cell == hover) {
      rule(c, view, x0, z0, x1, z0, EDGE, WHITE, 0.9F, x, y, w, h);
      rule(c, view, x0, z1, x1, z1, EDGE, WHITE, 0.9F, x, y, w, h);
      rule(c, view, x0, z0, x0, z1, EDGE, WHITE, 0.9F, x, y, w, h);
      rule(c, view, x1, z0, x1, z1, EDGE, WHITE, 0.9F, x, y, w, h);
    }
  }

  private void rule(
      Canvas c,
      Viewport view,
      double ax,
      double az,
      double bx,
      double bz,
      float thickness,
      int color,
      float strength,
      float x,
      float y,
      float w,
      float h) {
    float sx0 = view.screenX(ax, az);
    float sy0 = view.screenY(ax, az);
    float sx1 = view.screenX(bx, bz);
    float sy1 = view.screenY(bx, bz);
    float half = thickness * 0.5F;
    float left = Math.max(x, Math.min(sx0, sx1) - half);
    float top = Math.max(y, Math.min(sy0, sy1) - half);
    float right = Math.min(x + w, Math.max(sx0, sx1) + half);
    float bottom = Math.min(y + h, Math.max(sy0, sy1) + half);
    if (right > left && bottom > top) {
      c.roundRect(
          left,
          top,
          right - left,
          bottom - top,
          0.0F,
          Theme.withAlpha(color | 0xFF000000, strength));
    }
  }

  private void label(
      Canvas c, Theme t, Viewport view, int cell, float x, float y, float w, float h, int hover) {
    float size = (float) (DonutRegions.CELL * view.scale);
    boolean named = size >= NAME_SIZE;
    boolean numbered = size >= NUMBER_SIZE;
    if (!numbered && cell != hover) {
      return;
    }
    float x0 = view.screenX(DonutRegions.minX(cell), DonutRegions.minZ(cell));
    float y0 = view.screenY(DonutRegions.minX(cell), DonutRegions.minZ(cell));
    float left = Math.max(x, x0);
    float top = Math.max(y, y0);
    float right = Math.min(x + w, x0 + size);
    float bottom = Math.min(y + h, y0 + size);
    if (right - left < 30.0F || bottom - top < 24.0F) {
      return;
    }
    DonutRegions.Region region = DonutRegions.region(cell);
    String text =
        named || cell == hover
            ? region.label() + " · " + Lang.format("Region {0}", cell)
            : Integer.toString(cell);
    chip(c, t, text, (left + right) * 0.5F, (top + bottom) * 0.5F, region.color());
  }

  private void chip(Canvas c, Theme t, String text, float cx, float cy, int color) {
    float width = c.textWidth(FontStyle.CAPTION, text) + 22.0F;
    float height = c.lineHeight(FontStyle.CAPTION) + 10.0F;
    c.roundRect(
        cx - width * 0.5F,
        cy - height * 0.5F,
        width,
        height,
        height * 0.5F,
        Theme.withAlpha(t.paper() | 0xFF000000, 0.78F));
    c.roundRect(cx - width * 0.5F + 7.0F, cy - 3.0F, 6.0F, 6.0F, 3.0F, color);
    c.text(
        FontStyle.CAPTION,
        text,
        cx - width * 0.5F + 17.0F,
        cy - c.lineHeight(FontStyle.CAPTION) * 0.5F,
        t.ink());
  }

  private int edgeColor(int cell, float width) {
    return Theme.withAlpha(DonutRegions.region(cell).color() | 0xFF000000, 0.95F);
  }

  private void line(
      Canvas c,
      Camera3D camera,
      double plane,
      double ax,
      double az,
      double bx,
      double bz,
      int color) {
    if (!camera.project(ax, plane, az, projected)) {
      return;
    }
    xs[0] = projected[0];
    ys[0] = projected[1];
    if (!camera.project(bx, plane, bz, projected)) {
      return;
    }
    xs[1] = projected[0];
    ys[1] = projected[1];
    c.stroke(xs, ys, 2, EDGE, 0.5F, color);
  }
}
