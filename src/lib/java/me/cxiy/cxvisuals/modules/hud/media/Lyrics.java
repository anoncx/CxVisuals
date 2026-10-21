package me.cxiy.cxvisuals.modules.hud.media;

public final class Lyrics {
  public enum State {
    PENDING,
    MISSING,
    FOUND
  }

  public static final Lyrics PENDING = new Lyrics(State.PENDING, new long[0], new String[0]);
  public static final Lyrics MISSING = new Lyrics(State.MISSING, new long[0], new String[0]);

  private static final long LAST_LINE = 5000L;

  private final State state;
  private final long[] times;
  private final String[] lines;

  Lyrics(State state, long[] times, String[] lines) {
    this.state = state;
    this.times = times;
    this.lines = lines;
  }

  public State state() {
    return state;
  }

  public int size() {
    return times.length;
  }

  public long time(int index) {
    return times[index];
  }

  public String line(int index) {
    return lines[index];
  }

  public int indexAt(long positionMs) {
    int low = 0;
    int high = times.length - 1;
    int found = -1;
    while (low <= high) {
      int mid = (low + high) >>> 1;
      if (times[mid] <= positionMs) {
        found = mid;
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }
    return found;
  }

  public long endOf(int index, long durationMs) {
    if (index + 1 < times.length) {
      return times[index + 1];
    }
    return durationMs > times[index] ? durationMs : times[index] + LAST_LINE;
  }
}
