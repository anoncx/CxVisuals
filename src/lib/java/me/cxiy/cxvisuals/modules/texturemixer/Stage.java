package me.cxiy.cxvisuals.modules.texturemixer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import me.cxiy.cxvisuals.gui.TileSheet;

public final class Stage {
  public static final int BIG = 576;
  public static final int SMALL = 192;
  public static final int SMALL_COLUMNS = 6;
  public static final int SMALL_ROWS = 6;
  public static final int CANDIDATES = SMALL_COLUMNS * SMALL_ROWS;

  private record Frame(Entry entry, int key, int[] pixels, boolean animated) {}

  private record Batch(Entry entry, int generation, int[][] pixels, List<Pack> packs) {}

  private static final float ZOOM_BLOCK = 1.0F;
  private static final float ZOOM_CARD = 0.95F;
  private static final float ZOOM_ITEM = 0.7F;
  private static final long TICK_NANOS = 50_000_000L;
  private static final long ORIGIN = System.nanoTime();

  private final Studio studio;
  private final TileSheet big = new TileSheet(BIG, 1, 1);
  private final TileSheet small = new TileSheet(SMALL, SMALL_COLUMNS, SMALL_ROWS);
  private final Raster bigRaster = new Raster();
  private final Raster smallRaster = new Raster();
  private final AtomicReference<Frame> bigDone = new AtomicReference<>();
  private final AtomicReference<Batch> smallDone = new AtomicReference<>();
  private final List<Pack> candidates = new ArrayList<>(CANDIDATES);
  private Entry entry;
  private Pack focus;
  private int state;
  private boolean flying;
  private boolean batching;
  private boolean animated;
  private int shownKey = Integer.MIN_VALUE;
  private int requestedKey = Integer.MIN_VALUE;
  private int generation;
  private int batchedKey = Integer.MIN_VALUE;
  private int[] ready = new int[0];
  private boolean has;

  public Stage(Studio studio) {
    this.studio = studio;
  }

  public TileSheet big() {
    return big;
  }

  public TileSheet small() {
    return small;
  }

  public Entry entry() {
    return entry;
  }

  public boolean has() {
    return has;
  }

  public int state() {
    return state;
  }

  public List<Pack> candidates() {
    return candidates;
  }

  public void focus(Pack pack) {
    focus = pack;
  }

  public void state(int index) {
    if (entry != null && index >= 0 && index < entry.states().size()) {
      state = index;
    }
  }

  public void inspect(Entry next) {
    if (next == entry) {
      return;
    }
    entry = next;
    focus = null;
    state = 0;
    animated = false;
    generation++;
    has = false;
    shownKey = Integer.MIN_VALUE;
    requestedKey = Integer.MIN_VALUE;
    batchedKey = Integer.MIN_VALUE;
    candidates.clear();
    ready = new int[0];
  }

  public int cell(int index) {
    return index < ready.length && ready[index] == generation ? index : -1;
  }

  private float zoom(Entry target, float base) {
    return target.kind() == Entry.Kind.ITEM ? ZOOM_ITEM : base * target.zoom();
  }

  public void frame(float yaw, float pitch, boolean perspective) {
    collect();
    if (entry == null) {
      return;
    }
    int tick = (int) ((System.nanoTime() - ORIGIN) / TICK_NANOS);
    int key = Float.floatToIntBits(yaw) * 31 + Float.floatToIntBits(pitch);
    key = key * 31 + studio.mix().signature(entry);
    key = key * 31 + (perspective ? 1 : 0);
    key = key * 31 + (focus == null ? 0 : focus.id().hashCode() + 1);
    key = key * 31 + studio.packRevision();
    key = key * 31 + state;
    key = key * 31 + (animated ? tick / 2 : 0);
    if (!flying && key != shownKey && key != requestedKey) {
      flying = true;
      requestedKey = key;
      renderBig(key, tick, yaw, pitch, perspective);
    }
    int sig = studio.packRevision() * 31 + state;
    if (!batching && batchedKey != sig) {
      batching = true;
      batchedKey = sig;
      renderCards();
    }
  }

  private void collect() {
    Frame frame = bigDone.getAndSet(null);
    if (frame != null) {
      flying = false;
      if (frame.entry() == entry) {
        big.write(0, frame.pixels());
        shownKey = frame.key();
        animated = frame.animated();
        has = true;
      }
    }
    Batch batch = smallDone.getAndSet(null);
    if (batch != null) {
      batching = false;
      if (batch.entry() == entry && batch.generation() == generation) {
        candidates.clear();
        candidates.addAll(batch.packs());
        ready = new int[batch.pixels().length];
        for (int i = 0; i < batch.pixels().length; i++) {
          small.write(i, batch.pixels()[i]);
          ready[i] = generation;
        }
      }
    }
  }

  private void renderBig(int key, int tick, float yaw, float pitch, boolean perspective) {
    Entry target = entry;
    String[] chosen = studio.chosen(target);
    Pack preview = focus;
    Shape shape = target.states().get(Math.min(state, target.states().size() - 1)).shape();
    studio.run(
        () -> {
          int[] out = new int[BIG * BIG];
          boolean[] moving = new boolean[1];
          Function<String, Pixels> source =
              texture -> {
                String id = chosen[target.textures().indexOf(texture)];
                if (preview != null) {
                  id = preview.has(texture) ? preview.id() : "";
                }
                Pixels pixels = studio.texture(texture, id);
                if (pixels != null && pixels.animated()) {
                  moving[0] = true;
                }
                return pixels == null ? null : pixels.at(tick / 2 * 2);
              };
          bigRaster.render(
              shape,
              source,
              Raster.tint(target.id(), ""),
              BIG,
              1,
              zoom(target, ZOOM_BLOCK),
              yaw,
              pitch,
              perspective,
              out);
          bigDone.set(new Frame(target, key, out, moving[0]));
        });
  }

  private void renderCards() {
    Entry target = entry;
    int gen = generation;
    Shape shape = target.states().get(Math.min(state, target.states().size() - 1)).shape();
    studio.run(
        () -> {
          List<Pack> found = new ArrayList<>(CANDIDATES);
          found.add(studio.vanilla());
          for (Pack pack : studio.packs()) {
            if (found.size() < CANDIDATES && pack.coverage(target.textures()) > 0) {
              found.add(pack);
            }
          }
          int[][] images = new int[found.size()][];
          boolean item = target.kind() == Entry.Kind.ITEM;
          for (int i = 0; i < images.length; i++) {
            Pack pack = found.get(i);
            int[] out = new int[SMALL * SMALL];
            smallRaster.render(
                shape,
                texture -> {
                  Pixels pixels = pack.pixels(texture);
                  return pixels != null ? pixels : studio.vanilla().pixels(texture);
                },
                Raster.tint(target.id(), ""),
                SMALL,
                2,
                zoom(target, ZOOM_CARD),
                item ? 0.0F : Raster.TURN,
                item ? 0.0F : Raster.TILT,
                false,
                out);
            images[i] = out;
          }
          smallDone.set(new Batch(target, gen, images, found));
        });
  }
}
