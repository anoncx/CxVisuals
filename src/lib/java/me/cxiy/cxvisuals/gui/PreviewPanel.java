package me.cxiy.cxvisuals.gui;

import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.module.ClientSettings.WindowMotion;

public final class PreviewPanel {
  public static final int COLUMNS = 9;
  private static final float TITLE_GAP = 14.0F;
  private static final float DISSOLVE_PAD = 4.0F;
  private static final float PAD_FRACTION = 0.28F;
  private static final float HEAD_FRACTION = 0.52F;
  private static final float ITEM_FRACTION = 0.8889F;
  private static final float TINT_MIX = 0.30F;
  private static final float SEE_THROUGH_MIX = 0.62F;
  private static final int SEE_THROUGH_ALPHA = 0x80;
  private static final float WELL_SHADE = 0.26F;
  private static final float MIN_PAD = 8.0F;
  private static final float MIN_HEAD = 20.0F;
  private static final int NO_HIT = -1;

  private final Presence presence = new Presence();
  private final Supplier<WindowMotion> motion;
  private String title = "";
  private String subtitle = "";
  private int slots = COLUMNS * 3;
  private int columns = COLUMNS;
  private int tint;
  private int base;
  private float zoom = 1.0F;
  private int[] counts = new int[0];
  private int[] handles;
  private boolean mapFace;
  private float x;
  private float y;
  private float w;
  private float h;
  private float cell;
  private int hovered = NO_HIT;
  private int focus = NO_HIT;

  public PreviewPanel(Supplier<WindowMotion> motion) {
    this.motion = motion;
  }

  public void content(
      String name,
      String note,
      int slotCount,
      int columnCount,
      int tintArgb,
      int[] stackCounts,
      int firstIndex,
      float textScale) {
    content(
        name, note, slotCount, columnCount, tintArgb, stackCounts, firstIndex, textScale, false);
  }

  public void content(
      String name,
      String note,
      int slotCount,
      int columnCount,
      int tintArgb,
      int[] stackCounts,
      int firstIndex,
      float textScale,
      boolean map) {
    columns = Math.max(1, Math.min(COLUMNS, columnCount));
    title = name;
    subtitle = note;
    slots = Math.max(1, slotCount);
    tint = tintArgb;
    counts = stackCounts;
    base = firstIndex;
    zoom = textScale;
    mapFace = map;
    handles = null;
  }

  public void focus(int index) {
    focus = index;
  }

  public void stacks(int[] stackHandles) {
    handles = stackHandles;
  }

  private float pad(float cellSize) {
    return Math.max(MIN_PAD * zoom, cellSize * PAD_FRACTION);
  }

  private float head(float cellSize) {
    return Math.max(MIN_HEAD * zoom, cellSize * HEAD_FRACTION);
  }

  public float width(Canvas c, float cellSize) {
    float grid = columns * cellSize + pad(cellSize) * 2.0F;
    float titles =
        (c.textWidth(FontStyle.LABEL, title)
                    + (subtitle.isEmpty()
                        ? 0.0F
                        : c.textWidth(FontStyle.CAPTION, subtitle) + TITLE_GAP))
                * zoom
            + pad(cellSize) * 2.0F;
    return Math.max(grid, titles);
  }

  public float height(float cellSize) {
    return head(cellSize) + rows() * cellSize + pad(cellSize);
  }

  private int rows() {
    return (slots + columns - 1) / columns;
  }

  public void place(float left, float top, float cellSize, float cardWidth) {
    x = left;
    y = top;
    cell = cellSize;
    w = cardWidth;
    h = height(cellSize);
  }

  public boolean contains(float mx, float my) {
    return presence.phase() > 0.5F
        && !presence.closing()
        && mx >= x
        && mx < x + w
        && my >= y
        && my < y + h;
  }

  public boolean visible() {
    return presence.phase() > 0.01F;
  }

  public int hovered() {
    return hovered;
  }

  public void draw(
      Canvas c, Theme t, boolean show, boolean highlight, float mx, float my, float dt) {
    WindowMotion style = motion.get();
    presence.step(style, show, dt);
    hovered = NO_HIT;
    if (presence.phase() < 0.01F) {
      return;
    }
    float radius = Math.min(t.radius(), 14.0F) * Math.max(0.6F, zoom);
    int surface = t.surface();
    boolean seeThrough = t.translucent();
    int alphaBits =
        seeThrough ? Math.max(surface >>> 24, SEE_THROUGH_ALPHA) << 24 : surface & 0xFF000000;
    int plate =
        tint == 0
            ? surface
            : alphaBits
                | (Theme.mix(
                        surface | 0xFF000000,
                        tint | 0xFF000000,
                        seeThrough ? SEE_THROUGH_MIX : TINT_MIX)
                    & 0x00FFFFFF);
    int rim = tint == 0 ? Depth.rimColor(t) : Theme.withAlpha(tint | 0xFF000000, 0.55F);
    float lift = presence.rise(style);
    float pad = pad(cell);
    float head = head(cell);
    float cardW = presence.width(style, w);
    float cardH = presence.height(style, h);
    float cardX = x + (w - cardW) * 0.5F;
    float cardY = y + lift + (h - cardH) * 0.5F;
    boolean folded = cardW < w - 0.5F || cardH < h - 0.5F;
    c.pushAlpha(presence.alpha(style));
    c.pushTransform(presence.scale(style), x + w * 0.5F, y + h * 0.5F);
    c.beginDissolve(
        x - DISSOLVE_PAD,
        y - DISSOLVE_PAD,
        w + DISSOLVE_PAD * 2.0F,
        h + DISSOLVE_PAD * 2.0F,
        presence.erosion(style));
    c.pushLayer(10.0F);
    c.shadow(cardX, cardY, cardW, cardH, radius, 22.0F * zoom, 8.0F * zoom, t.cardShadow());
    if (t.glass()) {
      c.glass(cardX, cardY, cardW, cardH, radius, plate);
    } else {
      c.roundRect(cardX, cardY, cardW, cardH, radius, plate);
    }
    c.rim(cardX, cardY, cardW, cardH, radius, 1.0F, Math.min(cardH, radius * 1.4F), rim);
    if (highlight) {
      c.roundRectBorder(cardX, cardY, cardW, cardH, radius, 1.5F, Theme.withAlpha(t.ink(), 0.7F));
    }
    if (folded) {
      c.pushClip(cardX, cardY, cardW, cardH);
    }
    c.pushAlpha(presence.content(style));
    float titleX = x + pad;
    float titleY = y + lift + (head - c.lineHeight(FontStyle.LABEL) * zoom) * 0.5F;
    c.pushClip(x, y + lift, w, head);
    c.pushTransform(zoom, titleX, titleY);
    c.text(FontStyle.LABEL, title, titleX, titleY, t.ink());
    c.popTransform();
    if (!subtitle.isEmpty()) {
      float noteWidth = c.textWidth(FontStyle.CAPTION, subtitle) * zoom;
      float noteX = x + w - pad - noteWidth;
      float noteY = y + lift + (head - c.lineHeight(FontStyle.CAPTION) * zoom) * 0.5F;
      c.pushTransform(zoom, noteX, noteY);
      c.text(FontStyle.CAPTION, subtitle, noteX, noteY, Theme.withAlpha(t.ink(), 0.6F));
      c.popTransform();
    }
    c.popClip();
    float item = cell * ITEM_FRACTION;
    float inset = (cell - item) * 0.5F;
    float gridX = x + (w - columns * cell) * 0.5F;
    float gridY = y + lift + head;
    int well = Theme.withAlpha(0xFF000000, WELL_SHADE);
    float cellRadius = Math.max(3.0F, cell * 0.11F);
    if (mapFace) {
      float side = columns * cell;
      c.roundRect(gridX + 1.0F, gridY + 1.0F, side - 2.0F, side - 2.0F, cellRadius, well);
      if (mx >= gridX && mx < gridX + side && my >= gridY && my < gridY + side) {
        hovered = 0;
        c.roundRect(
            gridX + 1.0F,
            gridY + 1.0F,
            side - 2.0F,
            side - 2.0F,
            cellRadius,
            Theme.withAlpha(0xFFFFFFFF, 0.14F));
      }
      c.map(base, gridX + 1.0F, gridY + 1.0F, side - 2.0F);
    } else {
      for (int i = 0; i < slots; i++) {
        float sx = gridX + (i % columns) * cell;
        float sy = gridY + (i / columns) * cell;
        c.roundRect(sx + 1.0F, sy + 1.0F, cell - 2.0F, cell - 2.0F, cellRadius, well);
        boolean filled = i < counts.length && counts[i] > 0;
        if (filled && mx >= sx && mx < sx + cell && my >= sy && my < sy + cell) {
          hovered = i;
          c.roundRect(
              sx + 1.0F,
              sy + 1.0F,
              cell - 2.0F,
              cell - 2.0F,
              cellRadius,
              Theme.withAlpha(0xFFFFFFFF, 0.14F));
        }
        if (filled && i == focus) {
          c.roundRect(
              sx + 1.0F,
              sy + 1.0F,
              cell - 2.0F,
              cell - 2.0F,
              cellRadius,
              Theme.withAlpha(t.ink(), 0.16F));
          c.roundRectBorder(
              sx + 1.0F,
              sy + 1.0F,
              cell - 2.0F,
              cell - 2.0F,
              cellRadius,
              1.2F,
              Theme.withAlpha(t.ink(), 0.7F));
        }
        if (filled) {
          if (handles != null) {
            c.stack(handles[i], sx + inset, sy + inset, item);
          } else {
            c.held(base + i, sx + inset, sy + inset, item);
          }
          if (counts[i] > 1) {
            Counts.draw(c, t, counts[i], sx + cell - 2.0F, sy + cell - 1.0F, item);
          }
        }
      }
    }
    c.popAlpha();
    if (folded) {
      c.popClip();
    }
    c.popLayer();
    c.endDissolve();
    c.popTransform();
    c.popAlpha();
  }
}
