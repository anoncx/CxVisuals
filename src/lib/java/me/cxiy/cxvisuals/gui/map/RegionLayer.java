package me.cxiy.cxvisuals.gui.map;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.modules.map.regions.DonutRegions;

final class RegionLayer {
  private static final float TINT = 0.13F;
  private static final float EDGE = 2.0F;
  private static final float HAIR = 1.0F;
  private static final float GLOW = 14.0F;
  private static final float GROUP_SIZE = 12.0F;
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
    float size = (float) (DonutRegions.CELL * view.scale);
    if (size >= NUMBER_SIZE) {
      for (int row = firstRow; row <= lastRow; row++) {
        for (int column = firstColumn; column <= lastColumn; column++) {
          number(c, t, view, row * DonutRegions.GRID + column + 1, x, y, w, h, size);
        }
      }
    }
    for (int group = 0; group < DonutRegions.groupCount(); group++) {
      label(c, t, view, group, x, y, w, h, size, hoverX, hoverY);
    }
    if (hover > 0 && size >= NUMBER_SIZE) {
      DonutRegions.Region region = DonutRegions.region(hover);
      chip(
          c,
          t,
          region.label() + " · " + Lang.format("Region {0}", hover),
          hoverX,
          hoverY - 26.0F,
          region.color(),
          1.0F);
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
    }
    if (camera.pixelsPerBlock() * DonutRegions.CELL >= GROUP_SIZE) {
      for (int group = 0; group < DonutRegions.groupCount(); group++) {
        int[] cells = DonutRegions.groupCells(group);
        double sumX = 0.0;
        double sumZ = 0.0;
        for (int cell : cells) {
          sumX += DonutRegions.minX(cell) + DonutRegions.CELL / 2.0;
          sumZ += DonutRegions.minZ(cell) + DonutRegions.CELL / 2.0;
        }
        if (camera.project(sumX / cells.length, plane, sumZ / cells.length, projected)
            && projected[0] > 0.0F
            && projected[0] < w
            && projected[1] > 0.0F
            && projected[1] < h) {
          DonutRegions.Region region = DonutRegions.region(cells[0]);
          chip(c, t, region.label(), projected[0], projected[1], region.color(), 1.0F);
        }
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
    boolean west = DonutRegions.column(cell) == 0;
    boolean north = DonutRegions.row(cell) == 0;
    int color = region.color();
    if (south) {
      rule(c, view, x0, z1, x1, z1, GLOW, color, 0.1F, x, y, w, h);
      rule(c, view, x0, z1, x1, z1, GLOW * 0.4F, color, 0.16F, x, y, w, h);
    }
    if (east) {
      rule(c, view, x1, z0, x1, z1, GLOW, color, 0.1F, x, y, w, h);
      rule(c, view, x1, z0, x1, z1, GLOW * 0.4F, color, 0.16F, x, y, w, h);
    }
    rule(
        c,
        view,
        x0,
        z1,
        x1,
        z1,
        south ? EDGE : HAIR,
        south ? color : WHITE,
        south ? 0.85F : 0.08F,
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
        east ? color : WHITE,
        east ? 0.85F : 0.08F,
        x,
        y,
        w,
        h);
    if (west) {
      rule(c, view, x0, z0, x0, z1, EDGE, color, 0.85F, x, y, w, h);
    }
    if (north) {
      rule(c, view, x0, z0, x1, z0, EDGE, color, 0.85F, x, y, w, h);
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

  private void number(
      Canvas c, Theme t, Viewport view, int cell, float x, float y, float w, float h, float size) {
    float x0 = view.screenX(DonutRegions.minX(cell), DonutRegions.minZ(cell));
    float y0 = view.screenY(DonutRegions.minX(cell), DonutRegions.minZ(cell));
    if (x0 < x || y0 < y || x0 + size > x + w || y0 + size > y + h) {
      return;
    }
    c.text(
        FontStyle.CAPTION,
        Integer.toString(cell),
        x0 + 10.0F,
        y0 + 8.0F,
        Theme.withAlpha(t.ink() | 0xFF000000, 0.38F));
  }

  private void label(
      Canvas c,
      Theme t,
      Viewport view,
      int group,
      float x,
      float y,
      float w,
      float h,
      float size,
      float hoverX,
      float hoverY) {
    if (size < GROUP_SIZE) {
      return;
    }
    int[] cells = DonutRegions.groupCells(group);
    float best = 0.0F;
    float bestX = 0.0F;
    float bestY = 0.0F;
    for (int cell : cells) {
      float x0 = view.screenX(DonutRegions.minX(cell), DonutRegions.minZ(cell));
      float y0 = view.screenY(DonutRegions.minX(cell), DonutRegions.minZ(cell));
      float left = Math.max(x, x0);
      float top = Math.max(y, y0);
      float right = Math.min(x + w, x0 + size);
      float bottom = Math.min(y + h, y0 + size);
      float area = Math.min(right - left, bottom - top);
      if (area > best) {
        best = area;
        bestX = (left + right) * 0.5F;
        bestY = (top + bottom) * 0.5F;
      }
    }
    if (best < 48.0F) {
      return;
    }
    DonutRegions.Region region = DonutRegions.region(cells[0]);
    float reach = c.textWidth(FontStyle.CAPTION, region.label()) * 0.5F + 20.0F;
    boolean near = Math.abs(hoverX - bestX) < reach && Math.abs(hoverY - bestY) < 20.0F;
    chip(c, t, region.label(), bestX, bestY, region.color(), near ? 0.12F : 1.0F);
  }

  private void chip(
      Canvas c, Theme t, String text, float cx, float cy, int color, float strength) {
    float width = c.textWidth(FontStyle.CAPTION, text) + 20.0F;
    float height = c.lineHeight(FontStyle.CAPTION) + 8.0F;
    float left = cx - width * 0.5F;
    float top = cy - height * 0.5F;
    c.pushAlpha(strength);
    c.roundRect(
        left - 1.0F,
        top - 1.0F,
        width + 2.0F,
        height + 2.0F,
        height * 0.5F + 1.0F,
        Theme.withAlpha(color | 0xFF000000, 0.35F));
    c.roundRect(
        left, top, width, height, height * 0.5F, Theme.withAlpha(t.paper() | 0xFF000000, 0.86F));
    c.roundRect(left + 8.0F, cy - 2.5F, 5.0F, 5.0F, 2.5F, color);
    c.text(
        FontStyle.CAPTION,
        text,
        left + 17.0F,
        cy - c.lineHeight(FontStyle.CAPTION) * 0.5F,
        t.ink());
    c.popAlpha();
  }

  private int edgeColor(int cell, float width) {
    return Theme.withAlpha(DonutRegions.region(cell).color() | 0xFF000000, 0.85F);
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
