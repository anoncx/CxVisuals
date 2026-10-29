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
  private final float[] corners = new float[8];

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
    if (size >= NUMBER_SIZE * 3.0F) {
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
    if (x0 < x || y0 < y || x0 + 40.0F > x + w || y0 + 24.0F > y + h) {
      return;
    }
    float fade = smooth((size - NUMBER_SIZE * 3.0F) / (NUMBER_SIZE * 2.0F));
    if (fade <= 0.0F) {
      return;
    }
    c.text(
        FontStyle.CAPTION,
        Integer.toString(cell),
        Math.round(x0 + 10.0F),
        Math.round(y0 + 8.0F),
        Theme.withAlpha(t.ink() | 0xFF000000, 0.38F * fade));
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
    float grow = smooth((size - GROUP_SIZE * 0.6F) / (GROUP_SIZE * 0.4F));
    if (grow <= 0.0F) {
      return;
    }
    double[] box = DonutRegions.groupBounds(group);
    for (int i = 0; i < 4; i++) {
      double cornerX = box[(i & 1) == 0 ? 0 : 2];
      double cornerZ = box[(i & 2) == 0 ? 1 : 3];
      corners[i] = view.screenX(cornerX, cornerZ);
      corners[i + 4] = view.screenY(cornerX, cornerZ);
    }
    float left = Math.max(x, low(corners, 0));
    float right = Math.min(x + w, high(corners, 0));
    float top = Math.max(y, low(corners, 4));
    float bottom = Math.min(y + h, high(corners, 4));
    float visible = Math.min(right - left, bottom - top);
    float show = grow * smooth((visible - 24.0F) / 40.0F);
    if (show <= 0.0F) {
      return;
    }
    DonutRegions.Region region = DonutRegions.region(DonutRegions.groupCells(group)[0]);
    float halfWidth = (c.textWidth(FontStyle.CAPTION, region.label()) + 20.0F) * 0.5F;
    float halfHeight = (c.lineHeight(FontStyle.CAPTION) + 8.0F) * 0.5F;
    float cx =
        Math.round(settle(view.screenX(box[4], box[5]), left + halfWidth, right - halfWidth));
    float cy =
        Math.round(settle(view.screenY(box[4], box[5]), top + halfHeight, bottom - halfHeight));
    float away =
        Math.max(
            Math.abs(hoverX - cx) / (halfWidth + 6.0F),
            Math.abs(hoverY - cy) / (halfHeight + 6.0F));
    float clear = 0.12F + 0.88F * smooth((away - 0.9F) / 0.5F);
    chip(c, t, region.label(), cx, cy, region.color(), show * clear);
  }

  private static float low(float[] values, int from) {
    return Math.min(
        Math.min(values[from], values[from + 1]), Math.min(values[from + 2], values[from + 3]));
  }

  private static float high(float[] values, int from) {
    return Math.max(
        Math.max(values[from], values[from + 1]), Math.max(values[from + 2], values[from + 3]));
  }

  private static float settle(float value, float low, float high) {
    return low > high ? (low + high) * 0.5F : Math.max(low, Math.min(high, value));
  }

  private static float smooth(float v) {
    float k = Math.max(0.0F, Math.min(1.0F, v));
    return k * k * (3.0F - 2.0F * k);
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
