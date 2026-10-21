package me.cxiy.cxvisuals.modules.texturemixer;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public record Pixels(int width, int height, int[] argb) {
  public static Pixels decode(byte[] png) throws IOException {
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
    if (image == null) {
      throw new IOException("not an image");
    }
    int w = image.getWidth();
    int h = image.getHeight();
    if (h > w && h % w == 0) {
      h = w;
    }
    int[] data = new int[w * h];
    image.getRGB(0, 0, w, h, data, 0, w);
    return new Pixels(w, h, data);
  }

  public int sample(float u, float v) {
    int x = Math.min(width - 1, Math.max(0, (int) (u * width)));
    int y = Math.min(height - 1, Math.max(0, (int) (v * height)));
    return argb[y * width + x];
  }
}
