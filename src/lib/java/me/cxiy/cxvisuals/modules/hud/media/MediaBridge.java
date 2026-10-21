package me.cxiy.cxvisuals.modules.hud.media;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class MediaBridge {
  private static final long RESTART_MILLIS = 5000L;
  private static final long STALE_NANOS = 15_000_000_000L;
  private static final long PROBE_SECONDS = 3L;
  private static final long ABSENT_MILLIS = 30_000L;
  private static final int QUEUED = 32;
  private static final String ABSENT = "no media helper for this system";
  private static final String SILENT = "helper exited without output";

  private final Supplier<List<String>> command;
  private final Consumer<String> log;
  private final Object lock = new Object();
  private final ThreadPoolExecutor writer;

  private int users;
  private Thread supervisor;
  private Process process;
  private volatile BufferedWriter input;
  private String logged = "";
  private volatile boolean running;
  private volatile MediaSnapshot snapshot = MediaSnapshot.EMPTY;
  private volatile long lastLine;

  public MediaBridge(Supplier<List<String>> command, Consumer<String> log) {
    this.command = command;
    this.log = log;
    this.writer =
        new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(QUEUED),
            task -> {
              Thread thread = new Thread(task, "CxVisuals media commands");
              thread.setDaemon(true);
              return thread;
            },
            new ThreadPoolExecutor.DiscardPolicy());
  }

  public void acquire() {
    synchronized (lock) {
      if (users++ > 0) {
        return;
      }
      running = true;
      supervisor = new Thread(this::supervise, "CxVisuals media");
      supervisor.setDaemon(true);
      supervisor.start();
    }
  }

  public void release() {
    synchronized (lock) {
      if (users == 0 || --users > 0) {
        return;
      }
      stop();
    }
  }

  public void shutdown() {
    synchronized (lock) {
      users = 0;
      stop();
    }
  }

  private void stop() {
    running = false;
    if (supervisor != null) {
      supervisor.interrupt();
      supervisor = null;
    }
    if (process != null) {
      process.destroy();
      process = null;
    }
    input = null;
    lastLine = 0L;
    snapshot = MediaSnapshot.EMPTY;
  }

  public MediaSnapshot snapshot() {
    return stale() ? MediaSnapshot.EMPTY : snapshot;
  }

  public boolean stale() {
    long seen = lastLine;
    return seen != 0L && System.nanoTime() - seen > STALE_NANOS;
  }

  public void send(MediaCommand order, long argument) {
    BufferedWriter target = input;
    if (target == null) {
      return;
    }
    String line = order.line(argument);
    writer.execute(() -> transmit(target, line));
    MediaSnapshot current = snapshot;
    if (order == MediaCommand.PLAY_PAUSE && current.present()) {
      long now = System.nanoTime();
      snapshot =
          current.withPosition(
              current.livePositionMs(now), !current.playing(), current.durationMs(), now);
    }
  }

  private void transmit(BufferedWriter target, String line) {
    try {
      synchronized (target) {
        target.write(line);
        target.write('\n');
        target.flush();
      }
    } catch (IOException e) {
      if (input == target) {
        input = null;
      }
    }
  }

  private void supervise() {
    Thread self = Thread.currentThread();
    while (alive(self)) {
      long pause = RESTART_MILLIS;
      try {
        List<String> arguments = command.get();
        if (arguments == null) {
          pause = ABSENT_MILLIS;
          report(ABSENT);
        } else if (!pump(self, arguments)) {
          report(SILENT);
        }
      } catch (IOException | RuntimeException e) {
        report(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
      }
      if (!alive(self)) {
        return;
      }
      snapshot = MediaSnapshot.EMPTY;
      try {
        Thread.sleep(pause);
      } catch (InterruptedException e) {
        return;
      }
    }
  }

  private boolean alive(Thread self) {
    synchronized (lock) {
      return running && supervisor == self;
    }
  }

  private boolean pump(Thread self, List<String> arguments) throws IOException {
    Process started =
        new ProcessBuilder(arguments).redirectError(ProcessBuilder.Redirect.DISCARD).start();
    synchronized (lock) {
      if (!running || supervisor != self) {
        started.destroy();
        return true;
      }
      process = started;
      input =
          new BufferedWriter(
              new OutputStreamWriter(started.getOutputStream(), StandardCharsets.UTF_8));
    }
    MediaProtocol protocol = new MediaProtocol();
    boolean heard = false;
    try (BufferedReader reader =
        new BufferedReader(
            new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8), 1 << 14)) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!alive(self)) {
          return true;
        }
        heard = true;
        long now = System.nanoTime();
        MediaSnapshot next = protocol.accept(line, snapshot, now);
        synchronized (lock) {
          if (running && supervisor == self) {
            snapshot = next;
            lastLine = now;
          }
        }
        if (!protocol.error().isEmpty()) {
          report(protocol.error());
        }
      }
    } finally {
      started.destroy();
      synchronized (lock) {
        if (process == started) {
          process = null;
          input = null;
        }
      }
    }
    return heard || !alive(self);
  }

  private void report(String message) {
    synchronized (lock) {
      if (message.equals(logged)) {
        return;
      }
      logged = message;
    }
    log.accept("Media helper unavailable: " + message);
  }

  public static List<String> platformCommand(Path folder) {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    try {
      if (os.contains("win")) {
        return List.of(
            "powershell",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            MediaScripts.materialize(folder, "cxvisuals-media-win.ps1", MediaScripts.WINDOWS)
                .toString());
      }
      if (os.contains("mac") || os.contains("darwin")) {
        return List.of(
            "sh",
            MediaScripts.materialize(folder, "cxvisuals-media-mac.sh", MediaScripts.MACOS)
                .toString(),
            MediaScripts.stateFile(folder, "cxvisuals-media-mac.state").toString());
      }
      if (os.contains("nux") || os.contains("nix") || os.contains("bsd")) {
        String mode = exists("playerctl") ? "playerctl" : exists("dbus-send") ? "dbus" : null;
        if (mode == null) {
          return null;
        }
        return List.of(
            "sh",
            MediaScripts.materialize(folder, "cxvisuals-media-linux.sh", MediaScripts.LINUX)
                .toString(),
            MediaScripts.stateFile(folder, "cxvisuals-media-linux.state").toString(),
            mode);
      }
    } catch (IOException e) {
      return null;
    }
    return null;
  }

  private static boolean exists(String name) {
    try {
      Process probe =
          new ProcessBuilder("sh", "-c", "command -v " + name).redirectErrorStream(true).start();
      if (!probe.waitFor(PROBE_SECONDS, TimeUnit.SECONDS)) {
        probe.destroy();
        return false;
      }
      return probe.exitValue() == 0;
    } catch (IOException e) {
      return false;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
