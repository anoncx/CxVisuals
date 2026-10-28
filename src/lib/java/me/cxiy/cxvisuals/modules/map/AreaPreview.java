package me.cxiy.cxvisuals.modules.map;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.cxiy.cxvisuals.modules.map.Relief.Terrain;

public final class AreaPreview {
  public static final int MAX_SIDE = 160;
  private static final int MIN_SIDE = 64;
  private static final int LEVEL_ZERO_LIMIT = 384;
  private static final int LEVEL_ONE_LIMIT = 768;

  private record Bounds(int minX, int minZ, int width, int depth) {}

  private AreaPreview() {}

  public static byte[] render(List<MapShare.Chunk> chunks, Terrain terrain) {
    if (chunks.isEmpty()) {
      return null;
    }
    Map<Long, Region> regions = new HashMap<>();
    int minChunkX = Integer.MAX_VALUE;
    int minChunkZ = Integer.MAX_VALUE;
    int maxChunkX = Integer.MIN_VALUE;
    int maxChunkZ = Integer.MIN_VALUE;
    for (MapShare.Chunk chunk : chunks) {
      minChunkX = Math.min(minChunkX, chunk.x());
      minChunkZ = Math.min(minChunkZ, chunk.z());
      maxChunkX = Math.max(maxChunkX, chunk.x());
      maxChunkZ = Math.max(maxChunkZ, chunk.z());
      int regionX = chunk.x() >> 5;
      int regionZ = chunk.z() >> 5;
      regions
          .computeIfAbsent(Region.key(regionX, regionZ), key -> new Region(regionX, regionZ))
          .write(chunk.x() & 31, chunk.z() & 31, chunk.column(), chunk.stamp());
    }
    Bounds bounds =
        new Bounds(
            minChunkX << 4,
            minChunkZ << 4,
            ((maxChunkX + 1) << 4) - (minChunkX << 4),
            ((maxChunkZ + 1) << 4) - (minChunkZ << 4));
    int size = Math.max(bounds.width(), bounds.depth());
    int level = size <= LEVEL_ZERO_LIMIT ? 0 : size <= LEVEL_ONE_LIMIT ? 1 : 2;
    int canvasW = (bounds.width() + (1 << level) - 1) >> level;
    int canvasH = (bounds.depth() + (1 << level) - 1) >> level;
    int[] canvas = new int[canvasW * canvasH];
    TileBaker baker = new TileBaker(source(regions));
    paint(baker, terrain, canvas, canvasW, canvasH, bounds, level);
    int factor = Math.max(1, (Math.max(canvasW, canvasH) + MAX_SIDE - 1) / MAX_SIDE);
    int smallW = (canvasW + factor - 1) / factor;
    int smallH = (canvasH + factor - 1) / factor;
    int[] small = shrink(canvas, canvasW, canvasH, factor);
    int side = Math.max(MIN_SIDE, Math.max(smallW, smallH));
    int[] square = new int[side * side];
    int offsetX = (side - smallW) / 2;
    int offsetZ = (side - smallH) / 2;
    for (int y = 0; y < smallH; y++) {
      System.arraycopy(small, y * smallW, square, (y + offsetZ) * side + offsetX, smallW);
    }
    return PngWriter.encode(square, side, side);
  }

  private static TileBaker.Source source(Map<Long, Region> regions) {
    return new TileBaker.Source() {
      @Override
      public Region region(int layer, int regionX, int regionZ) {
        return layer == TileKey.SURFACE ? regions.get(Region.key(regionX, regionZ)) : null;
      }

      @Override
      public int[] thumbnail(int regionX, int regionZ) {
        return null;
      }
    };
  }

  private static void paint(
      TileBaker baker,
      Terrain terrain,
      int[] canvas,
      int canvasW,
      int canvasH,
      Bounds bounds,
      int level) {
    int span = TileKey.span(level);
    int[] tile = new int[TileBaker.PIXELS];
    int lastZ = Math.floorDiv(bounds.minZ() + bounds.depth() - 1, span);
    int lastX = Math.floorDiv(bounds.minX() + bounds.width() - 1, span);
    for (int tz = Math.floorDiv(bounds.minZ(), span); tz <= lastZ; tz++) {
      for (int tx = Math.floorDiv(bounds.minX(), span); tx <= lastX; tx++) {
        if (baker.bake(TileKey.of(TileKey.SURFACE, level, tx, tz), terrain, tile)) {
          copy(tile, canvas, canvasW, canvasH, bounds, level, tx * span, tz * span);
        }
      }
    }
  }

  private static void copy(
      int[] tile,
      int[] canvas,
      int canvasW,
      int canvasH,
      Bounds bounds,
      int level,
      int originX,
      int originZ) {
    for (int py = 0; py < TileKey.EDGE; py++) {
      int cy = (originZ + (py << level) - bounds.minZ()) >> level;
      if (cy < 0 || cy >= canvasH) {
        continue;
      }
      for (int px = 0; px < TileKey.EDGE; px++) {
        int cx = (originX + (px << level) - bounds.minX()) >> level;
        int pixel = tile[(py << TileKey.EDGE_SHIFT) | px];
        if (cx >= 0 && cx < canvasW && (pixel >>> 24) != 0) {
          canvas[cy * canvasW + cx] = pixel;
        }
      }
    }
  }

  private static int[] shrink(int[] from, int width, int height, int factor) {
    if (factor == 1) {
      return from;
    }
    int outW = (width + factor - 1) / factor;
    int outH = (height + factor - 1) / factor;
    int[] out = new int[outW * outH];
    for (int y = 0; y < outH; y++) {
      for (int x = 0; x < outW; x++) {
        out[y * outW + x] = average(from, width, height, x * factor, y * factor, factor);
      }
    }
    return out;
  }

  private static int average(int[] from, int width, int height, int left, int top, int factor) {
    long alpha = 0;
    long red = 0;
    long green = 0;
    long blue = 0;
    int count = 0;
    for (int dy = 0; dy < factor && top + dy < height; dy++) {
      for (int dx = 0; dx < factor && left + dx < width; dx++) {
        int pixel = from[(top + dy) * width + left + dx];
        long a = pixel >>> 24;
        alpha += a;
        red += ((pixel >> 16) & 0xFF) * a;
        green += ((pixel >> 8) & 0xFF) * a;
        blue += (pixel & 0xFF) * a;
        count++;
      }
    }
    if (alpha == 0) {
      return 0;
    }
    return ((int) (alpha / count) << 24)
        | ((int) (red / alpha) << 16)
        | ((int) (green / alpha) << 8)
        | (int) (blue / alpha);
  }
}
