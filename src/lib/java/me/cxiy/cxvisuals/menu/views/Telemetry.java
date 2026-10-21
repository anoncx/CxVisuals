package me.cxiy.cxvisuals.menu.views;

final class Telemetry {
  private static final long STARTED = System.nanoTime();
  private static final long SECOND = 1_000_000_000L;
  private static final long GIGABYTE = 1L << 30;
  private static final float FULL = 0.85F;

  private final Timekeeper clock = new Timekeeper();
  private final StringBuilder scratch = new StringBuilder(24);
  private long second = Long.MIN_VALUE;
  private String session = "";
  private String memory = "";
  private float memoryUse;

  String time() {
    return clock.time();
  }

  String day() {
    return clock.day();
  }

  String session() {
    return session;
  }

  String memory() {
    return memory;
  }

  float memoryUse() {
    return memoryUse;
  }

  boolean memoryFull() {
    return memoryUse > FULL;
  }

  void update() {
    clock.update();
    long elapsed = (System.nanoTime() - STARTED) / SECOND;
    if (elapsed != second) {
      second = elapsed;
      scratch.setLength(0);
      pair(elapsed / 3600L);
      scratch.append(':');
      pair(elapsed / 60L % 60L);
      scratch.append(':');
      pair(elapsed % 60L);
      session = scratch.toString();
      Runtime runtime = Runtime.getRuntime();
      long used = runtime.totalMemory() - runtime.freeMemory();
      long most = Math.max(1L, runtime.maxMemory());
      memoryUse = Math.min(1.0F, (float) used / most);
      scratch.setLength(0);
      tenths(used);
      scratch.append(" / ");
      tenths(most);
      scratch.append(" GB");
      memory = scratch.toString();
    }
  }

  private void pair(long value) {
    if (value < 10L) {
      scratch.append('0');
    }
    scratch.append(value);
  }

  private void tenths(long bytes) {
    long value = bytes * 10L / GIGABYTE;
    scratch.append(value / 10L).append('.').append(value % 10L);
  }
}
