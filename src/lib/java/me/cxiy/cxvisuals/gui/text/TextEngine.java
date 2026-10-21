package me.cxiy.cxvisuals.gui.text;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import me.cxiy.cxvisuals.gui.FontStyle;

public final class TextEngine {
  private static final FontStyle[] STYLES = FontStyle.values();
  private static final String FALLBACK = "geist-semibold.ttf";

  private final Function<String, InputStream> opener;
  private final GlyphAtlas atlas = new GlyphAtlas();
  private final FontFace[] faces = new FontFace[STYLES.length];
  private final FontFace[] bolds = new FontFace[STYLES.length];
  private final Map<String, Font> files = new HashMap<>();

  public TextEngine(Function<String, InputStream> opener) {
    this.opener = opener;
  }

  public GlyphAtlas atlas() {
    return atlas;
  }

  public FontFace face(FontStyle style) {
    FontFace face = faces[style.ordinal()];
    if (face == null) {
      face = new FontFace(load(style.file), load(FALLBACK), style.size, atlas);
      faces[style.ordinal()] = face;
    }
    return face;
  }

  public FontFace bold(FontStyle style) {
    FontFace face = bolds[style.ordinal()];
    if (face == null) {
      face =
          style.bold.equals(style.file)
              ? face(style)
              : new FontFace(load(style.bold), load(FALLBACK), style.size, atlas);
      bolds[style.ordinal()] = face;
    }
    return face;
  }

  private Font load(String file) {
    Font cached = files.get(file);
    if (cached != null) {
      return cached;
    }
    try (InputStream stream = opener.apply(file)) {
      if (stream == null) {
        throw new IllegalStateException("Font file missing: " + file);
      }
      Font font = Font.createFont(Font.TRUETYPE_FONT, stream);
      files.put(file, font);
      return font;
    } catch (IOException | FontFormatException e) {
      throw new IllegalStateException("Font file unreadable: " + file, e);
    }
  }
}
