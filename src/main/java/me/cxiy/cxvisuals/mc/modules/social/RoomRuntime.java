package me.cxiy.cxvisuals.mc.modules.social;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.modules.map.Maps;
import me.cxiy.cxvisuals.modules.map.MapShare;
import me.cxiy.cxvisuals.modules.map.Region;
import me.cxiy.cxvisuals.modules.map.share.Backfill;
import me.cxiy.cxvisuals.modules.map.share.DeltaBatcher;
import me.cxiy.cxvisuals.modules.map.share.RemoteQueue;
import me.cxiy.cxvisuals.modules.map.share.RoomBook;
import me.cxiy.cxvisuals.modules.map.share.RoomSync;
import me.cxiy.cxvisuals.modules.map.share.TokenBucket;

final class RoomRuntime {
  private static final long FLUSH_MILLIS = 2_000L;
  private static final int BATCH_CHUNKS = 256;
  private static final long UPLOAD_BYTES_PER_SECOND = 250_000L;
  private static final long UPLOAD_BURST = 1L << 20;
  private static final long IDLE_MILLIS = 2_000L;
  private static final long BACKFILL_MILLIS = 250L;
  private static final long MIN_BACKOFF_MILLIS = 2_000L;
  private static final long MAX_BACKOFF_MILLIS = 60_000L;
  private static final long STALE_BACKOFF_MILLIS = 15_000L;

  private final String id;
  private final RoomBook book;
  private final Runnable touched;
  private final Runnable flush;
  private final Consumer<String> gone;
  private final DeltaBatcher batcher =
      new DeltaBatcher(System::currentTimeMillis, FLUSH_MILLIS, BATCH_CHUNKS);
  private final RemoteQueue queue = new RemoteQueue();
  private final Semaphore wake = new Semaphore(0);
  private final RoomSync sync;
  private final ArrayDeque<Long> todo = new ArrayDeque<>();
  private final Map<Long, Long> inflight = new HashMap<>();
  private volatile boolean stopped;
  private volatile boolean stale;
  private volatile boolean rescan;
  private volatile int nearX;
  private volatile int nearZ;
  private volatile String dimension = "";
  private String todoDimension = "";
  private String inflightDimension = "";

  RoomRuntime(RoomBook book, String id, Runnable touched, Runnable flush, Consumer<String> gone) {
    this.id = id;
    this.book = book;
    this.touched = touched;
    this.flush = flush;
    this.gone = gone;
    this.sync =
        new RoomSync(
            () -> book.get(id),
            room -> {
              book.put(room);
              touched.run();
            },
            new RoomApi(id),
            Maps::readChunks,
            Maps.palette(),
            batcher,
            queue,
            new TokenBucket(System::nanoTime, UPLOAD_BYTES_PER_SECOND, UPLOAD_BURST));
  }

  String id() {
    return id;
  }

  RemoteQueue queue() {
    return queue;
  }

  boolean stale() {
    return stale;
  }

  void near(String nextDimension, int chunkX, int chunkZ) {
    dimension = nextDimension;
    nearX = chunkX;
    nearZ = chunkZ;
    queue.focus(chunkX, chunkZ);
  }

  void mark(String chunkDimension, int chunkX, int chunkZ, long stamp) {
    batcher.mark(chunkDimension, chunkX, chunkZ, stamp);
  }

  void wake() {
    wake.release();
  }

  void fresh() {
    stale = false;
    rescan = true;
    wake();
  }

  void start() {
    Thread thread = new Thread(this::run, "CxVisuals Shared Map");
    thread.setDaemon(true);
    thread.setPriority(Thread.NORM_PRIORITY - 2);
    thread.start();
  }

  void stop() {
    stopped = true;
    wake();
  }

  private void run() {
    long backoff = 0L;
    while (!stopped) {
      try {
        boolean more;
        do {
          more = sync.pull();
        } while (more && !stopped);
        if (!stale) {
          sync.push(nearX, nearZ);
          if (batcher.pending() == 0) {
            commit();
          }
          backfillStep();
        }
        backoff = 0L;
      } catch (RoomSync.StaleEpoch e) {
        stale = true;
        backoff = STALE_BACKOFF_MILLIS;
      } catch (Cloud.Refused e) {
        if (e.status == 403 || e.status == 404) {
          stopped = true;
          gone.accept(id);
          return;
        }
        backoff = next(backoff);
      } catch (IOException | RuntimeException e) {
        CxVisualsClient.LOG.debug("Shared map sync failed: {}", e.getMessage());
        backoff = next(backoff);
      }
      flush.run();
      long pause = backoff > 0L ? backoff : todo.isEmpty() ? IDLE_MILLIS : BACKFILL_MILLIS;
      try {
        wake.tryAcquire(pause, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }
      wake.drainPermits();
    }
    flush.run();
  }

  private void backfillStep() {
    String dim = dimension;
    if (!book.backfills(id) || dim.isEmpty() || batcher.pending() > BATCH_CHUNKS) {
      return;
    }
    if (!inflight.isEmpty() && !dim.equals(inflightDimension)) {
      return;
    }
    if (rescan || !dim.equals(todoDimension)) {
      rescan = false;
      todo.clear();
      todo.addAll(Backfill.order(Maps.world().knownRegions(), nearX, nearZ));
      todoDimension = dim;
    }
    Long key = todo.poll();
    if (key == null) {
      return;
    }
    List<MapShare.Chunk> chunks = Maps.readRegion(dim, Region.keyX(key), Region.keyZ(key));
    if (!Backfill.needed(chunks, book.regionHash(id, dim, key))) {
      return;
    }
    for (MapShare.Chunk chunk : chunks) {
      batcher.mark(dim, chunk.x(), chunk.z(), chunk.stamp());
    }
    inflightDimension = dim;
    inflight.put(key, Backfill.stampHash(chunks));
  }

  private void commit() {
    if (inflight.isEmpty()) {
      return;
    }
    for (Map.Entry<Long, Long> entry : inflight.entrySet()) {
      book.regionDone(id, inflightDimension, entry.getKey(), entry.getValue());
    }
    inflight.clear();
    touched.run();
  }

  private static long next(long backoff) {
    return backoff == 0L ? MIN_BACKOFF_MILLIS : Math.min(MAX_BACKOFF_MILLIS, backoff * 2L);
  }
}
