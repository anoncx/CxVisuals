package me.cxiy.cxvisuals.modules.hud.mirror;

import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;

import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;

public final class Reduce {
  public static final int MAX_LEVEL = 4;

  private static final int MIN_EDGE = 8;

  private Reduce() {}

  public static int level(int width, int height, int wanted) {
    int level = 0;
    while (level < MAX_LEVEL
        && (width >> (level + 1)) >= wanted
        && (height >> (level + 1)) >= MIN_EDGE) {
      level++;
    }
    return level;
  }

  public static int size(int edge, int level) {
    return Math.max(1, edge >> level);
  }

  public static void copy(
      MemorySegment source, long stride, int width, int height, int level, ByteBuffer target) {
    int outWidth = size(width, level);
    int outHeight = size(height, level);
    MemorySegment sink = MemorySegment.ofBuffer(target);
    long row = outWidth * 4L;
    if (level == 0) {
      if (stride == row) {
        MemorySegment.copy(source, 0L, sink, 0L, row * outHeight);
      } else {
        for (int y = 0; y < outHeight; y++) {
          MemorySegment.copy(source, y * stride, sink, y * row, row);
        }
      }
      return;
    }
    int block = 1 << level;
    int shift = level * 2;
    int count = block * block;
    int half = count >> 1;
    for (int y = 0; y < outHeight; y++) {
      for (int x = 0; x < outWidth; x++) {
        int blue = half;
        int green = half;
        int red = half;
        for (int by = 0; by < block; by++) {
          long line = (long) (y * block + by) * stride + (long) x * block * 4L;
          for (int bx = 0; bx < block; bx++) {
            int pixel = source.get(JAVA_INT_UNALIGNED, line + bx * 4L);
            blue += pixel & 0xFF;
            green += (pixel >> 8) & 0xFF;
            red += (pixel >> 16) & 0xFF;
          }
        }
        target.putInt(
            (y * outWidth + x) * 4,
            (blue >> shift) | ((green >> shift) << 8) | ((red >> shift) << 16) | 0xFF000000);
      }
    }
  }
}
