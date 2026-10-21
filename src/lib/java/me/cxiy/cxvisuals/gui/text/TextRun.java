package me.cxiy.cxvisuals.gui.text;

public final class TextRun {
  public final GlyphAtlas.Glyph[] glyphs;
  public final float[] xs;
  public final float[] ys;
  public final int count;
  public final float glyphScale;
  public final float advance;
  public final float minX;
  public final float minY;
  public final float maxX;
  public final float maxY;
  public final int firstPage;
  public final int lastPage;

  TextRun(
      GlyphAtlas.Glyph[] glyphs,
      float[] xs,
      float[] ys,
      int count,
      float glyphScale,
      float advance) {
    this.glyphs = glyphs;
    this.xs = xs;
    this.ys = ys;
    this.count = count;
    this.glyphScale = glyphScale;
    this.advance = advance;

    float left = 0.0F;
    float top = 0.0F;
    float right = 0.0F;
    float bottom = 0.0F;
    int low = Integer.MAX_VALUE;
    int high = -1;
    for (int i = 0; i < count; i++) {
      GlyphAtlas.Glyph glyph = glyphs[i];
      float glyphRight = xs[i] + glyph.width() * glyphScale;
      float glyphBottom = ys[i] + glyph.height() * glyphScale;
      if (i == 0) {
        left = xs[i];
        top = ys[i];
        right = glyphRight;
        bottom = glyphBottom;
      } else {
        left = Math.min(left, xs[i]);
        top = Math.min(top, ys[i]);
        right = Math.max(right, glyphRight);
        bottom = Math.max(bottom, glyphBottom);
      }
      low = Math.min(low, glyph.page());
      high = Math.max(high, glyph.page());
    }
    this.minX = left;
    this.minY = top;
    this.maxX = right;
    this.maxY = bottom;
    this.firstPage = count == 0 ? 0 : low;
    this.lastPage = high;
  }

  public boolean isEmpty() {
    return count == 0;
  }
}
