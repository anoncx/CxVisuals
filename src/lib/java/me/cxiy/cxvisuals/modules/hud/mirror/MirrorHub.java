package me.cxiy.cxvisuals.modules.hud.mirror;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MirrorHub {
  public static final String PORTAL = "portal";

  private static final long TICK_MILLIS = 400L;
  private static final long RETRY_NANOS = 1_000_000_000L;

  private final Supplier<MirrorBackend> factory;
  private final Consumer<String> log;
  private final FrameStore store = new FrameStore();
  private final Object lock = new Object();

  private Thread manager;
  private boolean wanted;
  private String wantedKey = "";
  private boolean listRequested;
  private boolean fresh;
  private boolean closing;
  private volatile List<MirrorWindow> windows = List.of();
  private MirrorBackend backend;
  private boolean resolved;
  private MirrorCapture capture;
  private String openedKey = "";
  private long lastTry;

  public MirrorHub(Supplier<MirrorBackend> factory, Consumer<String> log) {
    this.factory = factory;
    this.log = log;
  }

  public FrameStore store() {
    return store;
  }

  public List<MirrorWindow> windows() {
    return windows;
  }

  public boolean chooser() {
    MirrorBackend active = backend();
    return active != null && active.chooser();
  }

  public void want(boolean on, String key) {
    synchronized (lock) {
      if (on == wanted && key.equals(wantedKey)) {
        return;
      }
      wanted = on;
      wantedKey = key;
      if (on && manager == null) {
        closing = false;
        manager = new Thread(this::run, "CxVisuals mirror");
        manager.setDaemon(true);
        manager.start();
      }
      lock.notifyAll();
    }
  }

  public void refresh() {
    synchronized (lock) {
      listRequested = true;
      if (manager == null) {
        closing = false;
        manager = new Thread(this::run, "CxVisuals mirror");
        manager.setDaemon(true);
        manager.start();
      }
      lock.notifyAll();
    }
  }

  public void choose() {
    synchronized (lock) {
      fresh = true;
      lock.notifyAll();
    }
  }

  public void shutdown() {
    synchronized (lock) {
      wanted = false;
      closing = true;
      lock.notifyAll();
    }
  }

  private void run() {
    try {
      while (true) {
        boolean on;
        String key;
        boolean list;
        boolean again;
        synchronized (lock) {
          while (!closing && !wanted && !listRequested && !fresh && capture == null) {
            lock.wait();
          }
          if (closing) {
            break;
          }
          on = wanted;
          key = wantedKey;
          list = listRequested;
          again = fresh;
          listRequested = false;
          fresh = false;
        }
        MirrorBackend active = backend();
        if (active == null) {
          store.status(FrameStore.Status.UNSUPPORTED);
        } else {
          step(active, on, key, list, again);
        }
        synchronized (lock) {
          if (!closing && !listRequested && !fresh) {
            lock.wait(TICK_MILLIS);
          }
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } finally {
      stop();
      store.status(FrameStore.Status.OFF);
      synchronized (lock) {
        manager = null;
      }
    }
  }

  private synchronized MirrorBackend backend() {
    if (!resolved) {
      resolved = true;
      try {
        backend = factory.get();
      } catch (RuntimeException | LinkageError e) {
        log.accept("Window capture unavailable: " + e);
      }
    }
    return backend;
  }

  private void step(MirrorBackend active, boolean on, String key, boolean list, boolean again) {
    if (list || (on && !active.chooser())) {
      try {
        windows = List.copyOf(active.windows());
      } catch (RuntimeException e) {
        log.accept("Window list failed: " + e);
      }
    }
    if (!on || key.isEmpty()) {
      stop();
      store.clear();
      store.status(FrameStore.Status.OFF);
      return;
    }
    if (again) {
      stop();
      lastTry = 0L;
    }
    if (capture != null && !key.equals(openedKey)) {
      stop();
      store.clear();
    }
    if (capture != null && capture.alive()) {
      return;
    }
    if (capture != null) {
      stop();
      store.status(FrameStore.Status.LOST);
    }
    long now = System.nanoTime();
    if (lastTry != 0L && now - lastTry < RETRY_NANOS) {
      return;
    }
    lastTry = now;
    try {
      capture = active.chooser() ? active.choose(store, again) : open(active, key);
    } catch (RuntimeException | LinkageError e) {
      log.accept("Window capture failed: " + e);
      capture = null;
    }
    if (capture == null) {
      store.status(FrameStore.Status.LOST);
      return;
    }
    openedKey = key;
    store.status(FrameStore.Status.WAITING);
  }

  private MirrorCapture open(MirrorBackend active, String key) {
    MirrorWindow match = null;
    String app = MirrorWindow.appOf(key);
    for (MirrorWindow window : windows) {
      if (window.key().equals(key)) {
        match = window;
        break;
      }
      if (match == null && window.app().equals(app)) {
        match = window;
      }
    }
    return match == null ? null : active.open(match, store);
  }

  private void stop() {
    if (capture != null) {
      capture.close();
      capture = null;
    }
    openedKey = "";
  }
}
