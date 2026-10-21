package me.cxiy.cxvisuals.modules.hud.media;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;

public final class MediaGlyphs {
  public enum Glyph {
    PREVIOUS,
    PLAY,
    PAUSE,
    NEXT,
    NOTE
  }

  private static final int SIZE = 64;
  private static final int MAX_ENTRIES = 32;
  private static final float ROUND = 6.0F;
  private static final float DOT = 6.6F;
  private static final float PITCH = 8.0F;
  private static final String[] NOTE_ROWS = {
    "..##.", "..#.#", "..#..", "..#..", ".##..", "###..", ".#..."
  };
  private static final Map<Long, byte[]> CACHE = new HashMap<>();

  private MediaGlyphs() {}

  public static synchronized byte[] png(Glyph glyph, int argb) {
    Long key = ((long) glyph.ordinal() << 32) | (argb & 0xFFFFFFFFL);
    byte[] cached = CACHE.get(key);
    if (cached != null) {
      return cached;
    }
    if (CACHE.size() >= MAX_ENTRIES) {
      CACHE.clear();
    }
    BufferedImage image =
        glyph == Glyph.PREVIOUS ? mirror(draw(Glyph.NEXT, argb)) : draw(glyph, argb);
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
      ImageIO.write(image, "png", out);
      cached = out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    CACHE.put(key, cached);
    return cached;
  }

  private static BufferedImage draw(Glyph glyph, int argb) {
    BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = image.createGraphics();
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    g.setColor(new Color(argb, true));
    g.setStroke(new BasicStroke(ROUND, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    switch (glyph) {
      case PLAY -> soft(g, triangle(21.0F, 15.0F, 49.0F, 49.0F));
      case PAUSE -> {
        g.fill(new RoundRectangle2D.Float(16.0F, 12.0F, 11.0F, 40.0F, ROUND, ROUND));
        g.fill(new RoundRectangle2D.Float(37.0F, 12.0F, 11.0F, 40.0F, ROUND, ROUND));
      }
      case NEXT -> {
        soft(g, triangle(14.0F, 16.0F, 38.0F, 48.0F));
        g.fill(new RoundRectangle2D.Float(45.0F, 12.0F, 7.0F, 40.0F, ROUND, ROUND));
      }
      default -> {
        float left = (SIZE - PITCH * 5.0F) * 0.5F + (PITCH - DOT) * 0.5F;
        float top = (SIZE - PITCH * 7.0F) * 0.5F + (PITCH - DOT) * 0.5F;
        for (int row = 0; row < NOTE_ROWS.length; row++) {
          for (int column = 0; column < 5; column++) {
            if (NOTE_ROWS[row].charAt(column) == '#') {
              g.fill(new Ellipse2D.Float(left + column * PITCH, top + row * PITCH, DOT, DOT));
            }
          }
        }
      }
    }
    g.dispose();
    return image;
  }

  private static void soft(Graphics2D g, Shape shape) {
    g.fill(shape);
    g.draw(shape);
  }

  private static Shape triangle(float left, float top, float right, float bottom) {
    Path2D.Float path = new Path2D.Float();
    path.moveTo(left, top);
    path.lineTo(right, (top + bottom) * 0.5F);
    path.lineTo(left, bottom);
    path.closePath();
    return path;
  }

  private static BufferedImage mirror(BufferedImage source) {
    BufferedImage flipped = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < SIZE; y++) {
      for (int x = 0; x < SIZE; x++) {
        flipped.setRGB(SIZE - 1 - x, y, source.getRGB(x, y));
      }
    }
    return flipped;
  }
}
