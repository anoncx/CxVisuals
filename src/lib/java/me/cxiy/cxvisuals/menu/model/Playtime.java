package me.cxiy.cxvisuals.menu.model;

public final class Playtime {
  public static final int WEEK = 7;

  private static final int KEEP = 14;
  private static final long SECOND = 1_000_000_000L;
  private static final long SAVE_EVERY = 30L;

  private final TextStore store;
  private final long[] days = new long[KEEP];
  private final long[] seconds = new long[KEEP];
  private long last = -1L;
  private long carry;
  private long unsaved;

  public Playtime(TextStore store) {
    this.store = store;
    for (String entry : store.read().split(",")) {
      int split = entry.indexOf('=');
      if (split > 0) {
        try {
          add(
              Long.parseLong(entry.substring(0, split)),
              Long.parseLong(entry.substring(split + 1)));
        } catch (NumberFormatException e) {
          continue;
        }
      }
    }
  }

  public void tick(boolean playing, long nanos, long day) {
    long delta = last < 0L ? 0L : Math.min(SECOND, nanos - last);
    last = nanos;
    if (!playing) {
      return;
    }
    carry += delta;
    long whole = carry / SECOND;
    if (whole == 0L) {
      return;
    }
    carry -= whole * SECOND;
    add(day, whole);
    unsaved += whole;
    if (unsaved >= SAVE_EVERY) {
      save();
    }
  }

  public long seconds(long day) {
    int slot = slot(day);
    return days[slot] == day ? seconds[slot] : 0L;
  }

  public long week(long day) {
    long sum = 0L;
    for (int ago = 0; ago < WEEK; ago++) {
      sum += seconds(day - ago);
    }
    return sum;
  }

  public void save() {
    unsaved = 0L;
    StringBuilder out = new StringBuilder(KEEP * 12);
    for (int i = 0; i < KEEP; i++) {
      if (seconds[i] > 0L) {
        out.append(out.length() == 0 ? "" : ",").append(days[i]).append('=').append(seconds[i]);
      }
    }
    String encoded = out.toString();
    if (!encoded.equals(store.read())) {
      store.write(encoded);
    }
  }

  private void add(long day, long amount) {
    int slot = slot(day);
    if (days[slot] != day) {
      days[slot] = day;
      seconds[slot] = 0L;
    }
    seconds[slot] += amount;
  }

  private static int slot(long day) {
    return (int) Math.floorMod(day, (long) KEEP);
  }
}
