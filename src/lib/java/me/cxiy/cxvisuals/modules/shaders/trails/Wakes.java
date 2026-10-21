package me.cxiy.cxvisuals.modules.shaders.trails;

public final class Wakes {
  public static final int MOST = 48;

  private static final long LINGER = 500_000_000L;

  private final Wake[] wakes = new Wake[MOST];
  private int count;

  public int count() {
    return count;
  }

  public boolean empty() {
    return count == 0;
  }

  public Wake get(int i) {
    return wakes[i];
  }

  public void step(
      int id, double x, double y, double z, float width, float height, long nanos, float minSpeed) {
    Wake wake = find(id);
    if (wake == null) {
      if (count == MOST) {
        return;
      }
      if (wakes[count] == null) {
        wakes[count] = new Wake();
      }
      wake = wakes[count++];
      wake.clear();
      wake.identity(id);
    }
    wake.step(x, y, z, width, height, nanos, minSpeed);
  }

  public void prune(long nanos, float lifeSeconds) {
    int kept = 0;
    for (int i = 0; i < count; i++) {
      Wake wake = wakes[i];
      wake.prune(nanos, lifeSeconds);
      if (wake.count() > 0 || nanos - wake.touched() < LINGER) {
        wakes[i] = wakes[kept];
        wakes[kept++] = wake;
      }
    }
    count = kept;
  }

  public void clear() {
    count = 0;
  }

  private Wake find(int id) {
    for (int i = 0; i < count; i++) {
      if (wakes[i].identity() == id) {
        return wakes[i];
      }
    }
    return null;
  }
}
