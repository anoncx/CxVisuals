package me.cxiy.cxvisuals.modules.hud.mirror.x11;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.modules.hud.mirror.FrameStore;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorCapture;
import me.cxiy.cxvisuals.modules.hud.mirror.Reduce;

final class X11Capture implements MirrorCapture {
  private static final long FRAME_NANOS = 33_000_000L;
  private static final long IDLE_NANOS = 120_000_000L;
  private static final long JOIN_MILLIS = 800L;
  private static final int MISSES = 30;

  private final int window;
  private final FrameStore store;
  private final Consumer<String> log;
  private final Thread thread;
  private volatile boolean running = true;
  private volatile boolean ended;

  X11Capture(int window, FrameStore store, Consumer<String> log) {
    this.window = window;
    this.store = store;
    this.log = log;
    this.thread = new Thread(this::run, "CxVisuals window capture");
    thread.setDaemon(true);
    thread.start();
  }

  @Override
  public boolean alive() {
    return !ended;
  }

  @Override
  public void close() {
    running = false;
    LockSupport.unpark(thread);
    try {
      thread.join(JOIN_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void run() {
    MemorySegment connection = null;
    try {
      connection = Xcb.connect();
      try (Arena setup = Arena.ofConfined()) {
        if (!Xcb.composite(connection, setup) || !Xcb.redirect(connection, setup, window)) {
          throw new IllegalStateException("the compositing extension is unavailable");
        }
      }
      pump(connection);
    } catch (RuntimeException | LinkageError e) {
      log.accept("Window capture stopped: " + e.getMessage());
    } finally {
      if (connection != null) {
        Xcb.disconnect(connection);
      }
      ended = true;
    }
  }

  private void pump(MemorySegment connection) {
    int misses = 0;
    long next = System.nanoTime();
    while (running) {
      boolean gone;
      boolean idle = false;
      try (Arena frame = Arena.ofConfined()) {
        int[] size = Xcb.geometry(connection, frame, window);
        gone = size == null;
        if (!gone) {
          idle = Xcb.mapState(connection, frame, window) != Xcb.MAP_VIEWABLE;
          if (idle) {
            store.status(FrameStore.Status.PAUSED);
          } else if (size[0] > 0 && size[1] > 0 && grab(connection, frame, size[0], size[1])) {
            misses = 0;
          } else {
            misses++;
          }
        }
      }
      if (gone || misses > MISSES) {
        return;
      }
      long now = System.nanoTime();
      next = idle ? now + IDLE_NANOS : Math.max(next + FRAME_NANOS, now);
      LockSupport.parkNanos(next - now);
    }
  }

  private boolean grab(MemorySegment connection, Arena frame, int width, int height) {
    int pixmap = Xcb.generate(connection);
    boolean named = Xcb.name(connection, frame, window, pixmap);
    MemorySegment reply = MemorySegment.NULL;
    try {
      if (!named) {
        return false;
      }
      reply = Xcb.image(connection, frame, pixmap, width, height);
      if (reply.address() == 0L) {
        return false;
      }
      int length = Xcb.imageLength(reply);
      long stride = (long) length / height;
      if (stride < width * 4L) {
        return false;
      }
      MemorySegment pixels = Xcb.imageData(reply).reinterpret(length);
      int level = Reduce.level(width, height, store.wanted());
      ByteBuffer target = store.begin(Reduce.size(width, level), Reduce.size(height, level));
      Reduce.copy(pixels, stride, width, height, level, target);
      store.publish();
      return true;
    } finally {
      Xcb.free(reply);
      if (named) {
        Xcb.freePixmap(connection, frame, pixmap);
        Xcb.flush(connection);
      }
    }
  }
}
