package me.cxiy.cxvisuals.gui.text;

import java.util.ArrayList;
import java.util.List;

public final class GlyphAtlas {
  public static final int PAGE_SIZE = 512;

  private static final int SPACING = 1;

  public record Glyph(int page, int u, int v, int width, int height, float offsetX, float offsetY) {
    public boolean renderable() {
      return page >= 0;
    }
  }

  public static final class Page {
    private final byte[] alpha = new byte[PAGE_SIZE * PAGE_SIZE];
    private int cursorX;
    private int cursorY;
    private int rowHeight;
    private boolean dirty;

    public byte[] alpha() {
      return alpha;
    }

    public boolean dirty() {
      return dirty;
    }

    public void clean() {
      dirty = false;
    }

    private boolean place(byte[] coverage, int width, int height, int[] origin) {
      if (cursorX + width > PAGE_SIZE) {
        cursorX = 0;
        cursorY += rowHeight + SPACING;
        rowHeight = 0;
      }
      if (cursorY + height > PAGE_SIZE) {
        return false;
      }
      for (int row = 0; row < height; row++) {
        System.arraycopy(
            coverage, row * width, alpha, (cursorY + row) * PAGE_SIZE + cursorX, width);
      }
      origin[0] = cursorX;
      origin[1] = cursorY;
      cursorX += width + SPACING;
      rowHeight = Math.max(rowHeight, height);
      dirty = true;
      return true;
    }
  }

  static final Glyph EMPTY = new Glyph(-1, 0, 0, 0, 0, 0.0F, 0.0F);

  private final List<Page> pages = new ArrayList<>();
  private final int[] origin = new int[2];

  public int pageCount() {
    return pages.size();
  }

  public Page page(int index) {
    return pages.get(index);
  }

  public Glyph place(byte[] coverage, int width, int height, float offsetX, float offsetY) {
    if (width > PAGE_SIZE || height > PAGE_SIZE) {
      throw new IllegalArgumentException("glyph " + width + "x" + height + " exceeds atlas page");
    }
    for (int i = 0; i < pages.size(); i++) {
      if (pages.get(i).place(coverage, width, height, origin)) {
        return new Glyph(i, origin[0], origin[1], width, height, offsetX, offsetY);
      }
    }
    Page page = new Page();
    pages.add(page);
    page.place(coverage, width, height, origin);
    return new Glyph(pages.size() - 1, origin[0], origin[1], width, height, offsetX, offsetY);
  }
}
