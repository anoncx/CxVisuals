package me.cxiy.cxvisuals.menu.views;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Theme;

final class DotArt {
  static final int GRID = 14;

  private static final float SOLID = 0.7F;
  private static final float DARK_BACKDROP = 0.55F;
  private static final float FLAT = 0.06F;
  private static final float CURVE = 0.8F;
  private static final float SMALLEST = 0.22F;
  private static final float FLOOR = 0.08F;
  private static final float OFF = 0.5F;
  private static final float RIPPLE = 0.45F;
  private static final float SWING = 0.5F;
  private static final float DOT = 0.78F;

  private final float[] levels = new float[GRID * GRID];

  private DotArt() {}

  static DotArt of(byte[] png) {
    if (png == null || png.length == 0) {
      return null;
    }
    BufferedImage image;
    try {
      image = ImageIO.read(new ByteArrayInputStream(png));
    } catch (IOException | RuntimeException e) {
      return null;
    }
    if (image == null || image.getWidth() < 1 || image.getHeight() < 1) {
      return null;
    }
    DotArt art = new DotArt();
    art.sample(image);
    art.balance();
    return art;
  }

  float level(int column, int row) {
    return levels[row * GRID + column];
  }

  private void sample(BufferedImage image) {
    int width = image.getWidth();
    int height = image.getHeight();
    for (int row = 0; row < GRID; row++) {
      int top = row * height / GRID;
      int bottom = Math.max(top + 1, (row + 1) * height / GRID);
      for (int column = 0; column < GRID; column++) {
        int left = column * width / GRID;
        int right = Math.max(left + 1, (column + 1) * width / GRID);
        float sum = 0.0F;
        int count = 0;
        for (int y = top; y < bottom; y++) {
          for (int x = left; x < right; x++) {
            int argb = image.getRGB(x, y);
            float alpha = (argb >>> 24) / 255.0F;
            float luma =
                (0.2126F * ((argb >> 16) & 0xFF)
                        + 0.7152F * ((argb >> 8) & 0xFF)
                        + 0.0722F * (argb & 0xFF))
                    / 255.0F;
            sum += luma * alpha;
            count++;
          }
        }
        levels[row * GRID + column] = sum / count;
      }
    }
  }

  private void balance() {
    float low = 1.0F;
    float high = 0.0F;
    float mean = 0.0F;
    for (float value : levels) {
      low = Math.min(low, value);
      high = Math.max(high, value);
      mean += value;
    }
    mean /= levels.length;
    boolean invert = mean > DARK_BACKDROP;
    float span = high - low;
    for (int i = 0; i < levels.length; i++) {
      float value = span < FLAT ? levels[i] : (levels[i] - low) / span;
      value = (float) Math.pow(invert ? 1.0F - value : value, CURVE);
      levels[i] = Math.max(0.0F, Math.min(1.0F, value));
    }
  }

  void draw(Canvas c, Theme t, float x, float y, float size, int lit, boolean matrix, float wave) {
    float cell = size / GRID;
    float full = cell * DOT;
    int off = Theme.withAlpha(t.dotOff(), OFF);
    for (int i = 0; i < levels.length; i++) {
      float level = levels[i];
      float left = x + i % GRID * cell;
      float top = y + i / GRID * cell;
      if (level < FLOOR) {
        if (matrix) {
          float dot = full * SMALLEST;
          c.roundRect(
              left + (cell - dot) * 0.5F, top + (cell - dot) * 0.5F, dot, dot, dot * 0.5F, off);
        }
        continue;
      }
      float dot = full * (SMALLEST + (1.0F - SMALLEST) * level);
      float alpha = SOLID + (1.0F - SOLID) * level;
      if (wave != Pictograms.NO_WAVE) {
        alpha *=
            SWING
                + (1.0F - SWING)
                    * (0.5F + 0.5F * (float) Math.sin(wave - (i % GRID + i / GRID) * RIPPLE));
      }
      c.roundRect(
          left + (cell - dot) * 0.5F,
          top + (cell - dot) * 0.5F,
          dot,
          dot,
          dot * 0.5F,
          Theme.withAlpha(lit, alpha));
    }
  }
}
