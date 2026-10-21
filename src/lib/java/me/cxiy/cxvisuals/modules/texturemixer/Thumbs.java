package me.cxiy.cxvisuals.modules.texturemixer;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.function.Function;
import me.cxiy.cxvisuals.gui.TileSheet;

public final class Thumbs {
  public static final int EDGE = 128;
  public static final int COLUMNS = 12;
  public static final int ROWS = 12;

  private static final int SAMPLES = 2;
  private static final float ZOOM_BLOCK = 0.95F;
  private static final float ZOOM_ITEM = 0.64F;
  private static final int IN_FLIGHT = 96;
  private static final int PUMP_BUDGET = 24;
  private static final int STALE_FRAMES = 3;

  private static final class Slot {
    int cell = -1;
    int shown = Integer.MIN_VALUE;
    int requested = Integer.MIN_VALUE;
    long seen;
  }

  private record Done(Entry entry, int signature, int[] pixels, Slot slot) {}

  private final TileSheet sheet = new TileSheet(EDGE, COLUMNS, ROWS);
  private final Executor pool;
  private final Function<String, Pack> packs;
  private final Map<Entry, Slot> slots = new HashMap<>(512);
  private final ConcurrentLinkedQueue<Done> done = new ConcurrentLinkedQueue<>();
  private final ArrayDeque<Integer> free = new ArrayDeque<>(COLUMNS * ROWS);
  private final ThreadLocal<Raster> rasters = ThreadLocal.withInitial(Raster::new);
  private final ThreadLocal<int[]> buffers = ThreadLocal.withInitial(() -> new int[EDGE * EDGE]);
  private volatile long frame;
  private int flying;

  public Thumbs(Executor pool, Function<String, Pack> packs) {
    this.pool = pool;
    this.packs = packs;
    for (int i = 0; i < sheet.cells(); i++) {
      free.add(i);
    }
  }

  public TileSheet sheet() {
    return sheet;
  }

  public void frame() {
    frame++;
    int budget = PUMP_BUDGET;
    for (Done result = done.poll(); result != null; result = done.poll()) {
      flying--;
      Slot slot = result.slot();
      if (result.pixels() == null) {
        slot.requested = Integer.MIN_VALUE;
        continue;
      }
      if (slot.cell < 0) {
        slot.cell = claim();
      }
      if (slot.cell >= 0) {
        sheet.write(slot.cell, result.pixels());
        slot.shown = result.signature();
      }
      if (--budget <= 0) {
        break;
      }
    }
  }

  public int cell(Entry entry, int signature, Function<Entry, String[]> chosen) {
    Slot slot = slots.computeIfAbsent(entry, e -> new Slot());
    slot.seen = frame;
    if (slot.shown != signature && slot.requested != signature && flying < IN_FLIGHT) {
      slot.requested = signature;
      flying++;
      String[] ids = chosen.apply(entry);
      pool.execute(() -> render(entry, signature, ids, slot));
    }
    return slot.shown == Integer.MIN_VALUE ? -1 : slot.cell;
  }

  public void forget() {
    for (Slot slot : slots.values()) {
      slot.shown = Integer.MIN_VALUE;
      slot.requested = Integer.MIN_VALUE;
    }
  }

  private int claim() {
    Integer spare = free.poll();
    if (spare != null) {
      return spare;
    }
    Slot oldest = null;
    for (Slot slot : slots.values()) {
      boolean idle = slot.cell >= 0 && frame - slot.seen > STALE_FRAMES;
      if (idle && (oldest == null || slot.seen < oldest.seen)) {
        oldest = slot;
      }
    }
    if (oldest == null) {
      return -1;
    }
    int cell = oldest.cell;
    oldest.cell = -1;
    oldest.shown = Integer.MIN_VALUE;
    oldest.requested = Integer.MIN_VALUE;
    return cell;
  }

  private void render(Entry entry, int signature, String[] ids, Slot slot) {
    if (frame - slot.seen > STALE_FRAMES) {
      done.add(new Done(entry, signature, null, slot));
      return;
    }
    int[] out = buffers.get();
    Shape shape = entry.shape();
    Function<String, Pixels> source =
        key -> {
          int index = entry.textures().indexOf(key);
          Pack pack = index >= 0 && ids[index] != null ? packs.apply(ids[index]) : null;
          Pixels pixels = pack == null ? null : pack.pixels(key);
          if (pixels == null) {
            Pack base = packs.apply(Pack.VANILLA);
            pixels = base == null ? null : base.pixels(key);
          }
          return pixels;
        };
    boolean item = entry.kind() == Entry.Kind.ITEM;
    rasters
        .get()
        .render(
            shape,
            source,
            Raster.tint(entry.id(), ""),
            EDGE,
            SAMPLES,
            item ? ZOOM_ITEM : ZOOM_BLOCK * entry.zoom(),
            item ? 0.0F : Raster.TURN,
            item ? 0.0F : Raster.TILT,
            false,
            out);
    done.add(new Done(entry, signature, out.clone(), slot));
  }
}
