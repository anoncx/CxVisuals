package me.cxiy.cxvisuals.modules.swings;

public final class Ribbons {
  public static final int MOST = 24;

  private static final long LINGER = 500_000_000L;

  private final Ribbon[] ribbons = new Ribbon[MOST];
  private final int[] owners = new int[MOST];
  private int count;

  public int count() {
    return count;
  }

  public boolean empty() {
    return count == 0;
  }

  public Ribbon get(int i) {
    return ribbons[i];
  }

  public Ribbon of(int owner) {
    for (int i = 0; i < count; i++) {
      if (owners[i] == owner) {
        return ribbons[i];
      }
    }
    if (count == MOST) {
      return null;
    }
    if (ribbons[count] == null) {
      ribbons[count] = new Ribbon();
    }
    Ribbon ribbon = ribbons[count];
    ribbon.clear();
    owners[count++] = owner;
    return ribbon;
  }

  public void prune(long nanos, float lifeSeconds) {
    int kept = 0;
    for (int i = 0; i < count; i++) {
      Ribbon ribbon = ribbons[i];
      int owner = owners[i];
      ribbon.prune(nanos, lifeSeconds);
      if (!ribbon.empty() || nanos - ribbon.touched() < LINGER) {
        ribbons[i] = ribbons[kept];
        owners[i] = owners[kept];
        ribbons[kept] = ribbon;
        owners[kept++] = owner;
      }
    }
    count = kept;
  }

  public void clear() {
    count = 0;
  }
}
