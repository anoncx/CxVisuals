package me.cxiy.cxvisuals.mc.modules.swings;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import java.util.List;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.modules.swings.Blade;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import org.joml.Vector3fc;

final class Blades {
  private static final int LIMIT = 512;
  private static final Reference2ObjectOpenHashMap<Object, Entry> CACHE =
      new Reference2ObjectOpenHashMap<>();
  private static final Blade FALLBACK = new Blade();

  private static float[] scratch = new float[256 * 3];
  private static Object lastKey;
  private static int lastSize;
  private static Entry lastEntry;

  static {
    FALLBACK.fallback();
  }

  private Blades() {}

  private record Entry(int size, Blade blade) {}

  static Blade of(List<BakedQuad> quads, Supplier<Vector3fc[]> extents) {
    if (!quads.isEmpty()) {
      return cached(quads.get(0), quads.size(), quads, null);
    }
    Vector3fc[] points = extents.get();
    return points.length == 0 ? FALLBACK : cached(points, points.length, null, points);
  }

  private static Blade cached(Object key, int size, List<BakedQuad> quads, Vector3fc[] points) {
    if (key == lastKey && size == lastSize) {
      return lastEntry.blade;
    }
    Entry entry = CACHE.get(key);
    if (entry == null || entry.size != size) {
      if (CACHE.size() >= LIMIT) {
        CACHE.clear();
      }
      entry = new Entry(size, measure(quads, points));
      CACHE.put(key, entry);
    }
    lastKey = key;
    lastSize = size;
    lastEntry = entry;
    return entry.blade;
  }

  private static Blade measure(List<BakedQuad> quads, Vector3fc[] points) {
    int count = quads != null ? gather(quads) : gather(points);
    Blade blade = new Blade();
    if (!blade.measure(scratch, count)) {
      blade.fallback();
    }
    return blade;
  }

  private static int gather(Vector3fc[] points) {
    reserve(points.length);
    for (int i = 0; i < points.length; i++) {
      put(i, points[i]);
    }
    return points.length;
  }

  private static int gather(List<BakedQuad> quads) {
    int sides = 0;
    for (int i = 0; i < quads.size(); i++) {
      if (side(quads.get(i))) {
        sides++;
      }
    }
    boolean every = sides == 0;
    reserve((every ? quads.size() : sides) * 4);
    int count = 0;
    for (int i = 0; i < quads.size(); i++) {
      BakedQuad quad = quads.get(i);
      if (every || side(quad)) {
        for (int corner = 0; corner < 4; corner++) {
          put(count++, quad.position(corner));
        }
      }
    }
    return count;
  }

  private static boolean side(BakedQuad quad) {
    Direction face = quad.direction();
    return face != Direction.NORTH && face != Direction.SOUTH;
  }

  private static void reserve(int vertices) {
    if (scratch.length < vertices * 3) {
      scratch = new float[vertices * 3];
    }
  }

  private static void put(int at, Vector3fc point) {
    scratch[at * 3] = point.x();
    scratch[at * 3 + 1] = point.y();
    scratch[at * 3 + 2] = point.z();
  }
}
