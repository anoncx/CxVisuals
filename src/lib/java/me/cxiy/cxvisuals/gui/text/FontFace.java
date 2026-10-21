package me.cxiy.cxvisuals.gui.text;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public final class FontFace {
  private static final FontRenderContext CONTEXT =
      new FontRenderContext(new AffineTransform(), true, true);
  private static final int PADDING = 1;
  private static final int SCALE_STEPS = 4;
  private static final int MIN_STEP = SCALE_STEPS;
  private static final int MAX_STEP = SCALE_STEPS * 4;
  private static final int MAX_LAYOUTS = 512;
  private static final int MAX_WIDTHS = 2048;
  private static final int OWN = 0;
  private static final int FALLBACK = 1;
  private static final int SYSTEM = 2;
  private static final int TIERS = 3;
  private static final TextRun EMPTY =
      new TextRun(new GlyphAtlas.Glyph[0], new float[0], new float[0], 0, 1.0F, 0.0F);

  private final class Strike {
    private final Font[] fonts = new Font[TIERS];
    private final float scale;

    @SuppressWarnings("unchecked")
    private final Map<Integer, GlyphAtlas.Glyph>[] glyphs = new Map[TIERS];

    private final Map<String, TextRun> layouts =
        new LinkedHashMap<>(128, 0.75F, true) {
          @Override
          protected boolean removeEldestEntry(Map.Entry<String, TextRun> eldest) {
            return size() > MAX_LAYOUTS;
          }
        };

    private Strike(float scale) {
      this.scale = scale;
      for (int tier = 0; tier < TIERS; tier++) {
        fonts[tier] = tiers[tier].deriveFont(logicalSize * scale);
        glyphs[tier] = new HashMap<>();
      }
    }

    private TextRun layout(String text) {
      TextRun cached = layouts.get(text);
      if (cached == null) {
        cached = build(text);
        layouts.put(text, cached);
      }
      return cached;
    }

    private TextRun build(String text) {
      int total = text.length();
      GlyphAtlas.Glyph[] placed = new GlyphAtlas.Glyph[total * 2];
      float[] xs = new float[total * 2];
      float[] ys = new float[total * 2];
      float inverse = 1.0F / scale;
      float pen = 0.0F;
      int count = 0;
      int start = 0;
      while (start < total) {
        int tier = tier(text.codePointAt(start));
        int end = start;
        while (end < total && tier(text.codePointAt(end)) == tier) {
          end += Character.charCount(text.codePointAt(end));
        }
        Font face = fonts[tier];
        Map<Integer, GlyphAtlas.Glyph> known = glyphs[tier];
        GlyphVector vector = shape(face, text.substring(start, end));
        int glyphCount = vector.getNumGlyphs();
        if (count + glyphCount > placed.length) {
          int room = Math.max(placed.length * 2, count + glyphCount);
          placed = java.util.Arrays.copyOf(placed, room);
          xs = java.util.Arrays.copyOf(xs, room);
          ys = java.util.Arrays.copyOf(ys, room);
        }
        for (int i = 0; i < glyphCount; i++) {
          GlyphAtlas.Glyph glyph = glyph(face, known, vector.getGlyphCode(i));
          if (!glyph.renderable()) {
            continue;
          }
          Point2D position = vector.getGlyphPosition(i);
          placed[count] = glyph;
          xs[count] = pen + ((float) position.getX() + glyph.offsetX()) * inverse;
          ys[count] = ((float) position.getY() + glyph.offsetY()) * inverse;
          count++;
        }
        pen += (float) vector.getGlyphPosition(glyphCount).getX() * inverse;
        start = end;
      }
      return new TextRun(placed, xs, ys, count, inverse, pen);
    }

    private GlyphAtlas.Glyph glyph(Font face, Map<Integer, GlyphAtlas.Glyph> known, int code) {
      GlyphAtlas.Glyph cached = known.get(code);
      if (cached == null) {
        cached = rasterize(face, code);
        known.put(code, cached);
      }
      return cached;
    }

    private GlyphAtlas.Glyph rasterize(Font face, int code) {
      GlyphVector single = face.createGlyphVector(CONTEXT, new int[] {code});
      Rectangle bounds = single.getGlyphPixelBounds(0, CONTEXT, 0.0F, 0.0F);
      if (bounds.width <= 0 || bounds.height <= 0) {
        return GlyphAtlas.EMPTY;
      }
      int width = bounds.width + PADDING * 2;
      int height = bounds.height + PADDING * 2;
      BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g = image.createGraphics();
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      g.setColor(Color.WHITE);
      g.translate(PADDING - bounds.x, PADDING - bounds.y);
      g.fill(single.getGlyphOutline(0));
      g.dispose();

      int[] argb = image.getRGB(0, 0, width, height, null, 0, width);
      byte[] coverage = new byte[argb.length];
      for (int i = 0; i < argb.length; i++) {
        coverage[i] = (byte) (argb[i] >>> 24);
      }
      return atlas.place(coverage, width, height, bounds.x - PADDING, bounds.y - PADDING);
    }
  }

  private final Font base;
  private final Font[] tiers = new Font[TIERS];
  private final float logicalSize;
  private final GlyphAtlas atlas;
  private final float capHeight;
  private final Strike[] strikes = new Strike[MAX_STEP + 1];
  private final Map<String, Float> widths = new HashMap<>();

  FontFace(Font loaded, Font fallback, int logicalSize, GlyphAtlas atlas) {
    Map<TextAttribute, Object> attributes = new HashMap<>();
    attributes.put(TextAttribute.KERNING, TextAttribute.KERNING_ON);
    this.base = loaded.deriveFont(attributes).deriveFont((float) logicalSize);
    this.tiers[OWN] = base;
    this.tiers[FALLBACK] = fallback.deriveFont(attributes).deriveFont((float) logicalSize);
    this.tiers[SYSTEM] =
        new Font(Font.SANS_SERIF, Font.PLAIN, logicalSize)
            .deriveFont(attributes)
            .deriveFont((float) logicalSize);
    this.logicalSize = logicalSize;
    this.atlas = atlas;
    this.capHeight = (float) base.createGlyphVector(CONTEXT, "H").getVisualBounds().getHeight();
  }

  public float capHeight() {
    return capHeight;
  }

  public boolean covers(int codepoint) {
    return base.canDisplay(codepoint);
  }

  public boolean bundled(int codepoint) {
    return tier(codepoint) != SYSTEM;
  }

  private int tier(int codepoint) {
    return base.canDisplay(codepoint)
        ? OWN
        : tiers[FALLBACK].canDisplay(codepoint) ? FALLBACK : SYSTEM;
  }

  public boolean covers(String text) {
    return base.canDisplayUpTo(text) == -1;
  }

  public float width(String text) {
    if (text.isEmpty()) {
      return 0.0F;
    }
    Float cached = widths.get(text);
    if (cached != null) {
      return cached;
    }
    float width = 0.0F;
    int total = text.length();
    int start = 0;
    while (start < total) {
      int tier = tier(text.codePointAt(start));
      int end = start;
      while (end < total && tier(text.codePointAt(end)) == tier) {
        end += Character.charCount(text.codePointAt(end));
      }
      GlyphVector vector = shape(tiers[tier], text.substring(start, end));
      width += (float) vector.getGlyphPosition(vector.getNumGlyphs()).getX();
      start = end;
    }
    if (widths.size() >= MAX_WIDTHS) {
      widths.clear();
    }
    widths.put(text, width);
    return width;
  }

  public TextRun layout(String text, float rasterScale) {
    if (text.isEmpty()) {
      return EMPTY;
    }
    int step = Math.max(MIN_STEP, Math.min(MAX_STEP, Math.round(rasterScale * SCALE_STEPS)));
    Strike strike = strikes[step];
    if (strike == null) {
      strike = new Strike(step / (float) SCALE_STEPS);
      strikes[step] = strike;
    }
    return strike.layout(text);
  }

  private static GlyphVector shape(Font font, String text) {
    char[] chars = text.toCharArray();
    return font.layoutGlyphVector(CONTEXT, chars, 0, chars.length, Font.LAYOUT_LEFT_TO_RIGHT);
  }
}
