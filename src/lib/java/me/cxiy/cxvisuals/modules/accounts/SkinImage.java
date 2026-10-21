package me.cxiy.cxvisuals.modules.accounts;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public final class SkinImage {
  public static final int FIGURE = 32;
  public static final int HEAD = 8;

  private static final int LARGEST = 1024;

  private SkinImage() {}

  public static byte[] head(byte[] skin, int scale) throws IOException {
    BufferedImage source = read(skin);
    BufferedImage out = new BufferedImage(HEAD, HEAD, BufferedImage.TYPE_INT_ARGB);
    copy(source, 8, 8, HEAD, HEAD, out, 0, 0, false);
    copy(source, 40, 8, HEAD, HEAD, out, 0, 0, true);
    return png(scaled(out, scale));
  }

  public static byte[] figure(byte[] skin, boolean slim, int scale) throws IOException {
    BufferedImage source = read(skin);
    boolean modern = source.getHeight() >= 64;
    int arm = slim ? 3 : 4;
    int left = (FIGURE - (8 + arm * 2)) / 2;
    BufferedImage out = new BufferedImage(FIGURE, FIGURE, BufferedImage.TYPE_INT_ARGB);
    int bodyX = left + arm;
    copy(source, 8, 8, 8, 8, out, bodyX, 0, false);
    copy(source, 20, 20, 8, 12, out, bodyX, 8, false);
    copy(source, 4, 20, 4, 12, out, bodyX, 20, false);
    copy(source, modern ? 20 : 4, modern ? 52 : 20, 4, 12, out, bodyX + 4, 20, false, !modern);
    copy(source, 44, 20, arm, 12, out, left, 8, false);
    copy(source, modern ? 36 : 44, modern ? 52 : 20, arm, 12, out, bodyX + 8, 8, false, !modern);
    if (modern) {
      copy(source, 20, 36, 8, 12, out, bodyX, 8, true);
      copy(source, 4, 36, 4, 12, out, bodyX, 20, true);
      copy(source, 4, 52, 4, 12, out, bodyX + 4, 20, true);
      copy(source, 44, 36, arm, 12, out, left, 8, true);
      copy(source, 52, 52, arm, 12, out, bodyX + 8, 8, true);
    }
    copy(source, 40, 8, 8, 8, out, bodyX, 0, true);
    return png(scaled(out, scale));
  }

  private static BufferedImage read(byte[] skin) throws IOException {
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(skin));
    if (image == null
        || image.getWidth() != 64
        || image.getHeight() != 64 && image.getHeight() != 32
        || image.getWidth() > LARGEST) {
      throw new IOException("Not a skin texture");
    }
    return image;
  }

  private static void copy(
      BufferedImage from,
      int sx,
      int sy,
      int w,
      int h,
      BufferedImage to,
      int dx,
      int dy,
      boolean overlay) {
    copy(from, sx, sy, w, h, to, dx, dy, overlay, false);
  }

  private static void copy(
      BufferedImage from,
      int sx,
      int sy,
      int w,
      int h,
      BufferedImage to,
      int dx,
      int dy,
      boolean overlay,
      boolean mirror) {
    for (int y = 0; y < h; y++) {
      for (int x = 0; x < w; x++) {
        int pixel = from.getRGB(mirror ? sx + w - 1 - x : sx + x, sy + y);
        int alpha = pixel >>> 24;
        if (alpha == 0) {
          continue;
        }
        if (!overlay) {
          to.setRGB(dx + x, dy + y, pixel | 0xFF000000);
        } else {
          to.setRGB(dx + x, dy + y, blend(to.getRGB(dx + x, dy + y), pixel));
        }
      }
    }
  }

  private static int blend(int below, int above) {
    int a = above >>> 24;
    int base = below >>> 24;
    if (a == 255 || base == 0) {
      return above;
    }
    int outAlpha = a + base * (255 - a) / 255;
    int r = mix(below >> 16 & 0xFF, above >> 16 & 0xFF, a);
    int g = mix(below >> 8 & 0xFF, above >> 8 & 0xFF, a);
    int b = mix(below & 0xFF, above & 0xFF, a);
    return outAlpha << 24 | r << 16 | g << 8 | b;
  }

  private static int mix(int below, int above, int alpha) {
    return (above * alpha + below * (255 - alpha)) / 255;
  }

  private static BufferedImage scaled(BufferedImage image, int scale) {
    if (scale <= 1) {
      return image;
    }
    BufferedImage out =
        new BufferedImage(
            image.getWidth() * scale, image.getHeight() * scale, BufferedImage.TYPE_INT_ARGB);
    for (int y = 0; y < out.getHeight(); y++) {
      for (int x = 0; x < out.getWidth(); x++) {
        out.setRGB(x, y, image.getRGB(x / scale, y / scale));
      }
    }
    return out;
  }

  private static byte[] png(BufferedImage image) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ImageIO.write(image, "png", out);
    return out.toByteArray();
  }
}
