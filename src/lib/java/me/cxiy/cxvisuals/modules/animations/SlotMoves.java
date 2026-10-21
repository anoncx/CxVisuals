package me.cxiy.cxvisuals.modules.animations;

public final class SlotMoves {
  public static final int CAPACITY = 16;
  public static final int MAX_CHANGES = 12;

  private final int[] from = new int[CAPACITY];
  private final int[] to = new int[CAPACITY];
  private final float[] progress = new float[CAPACITY];
  private final boolean[] live = new boolean[CAPACITY];
  private final boolean[] fresh = new boolean[CAPACITY];
  private final int[] losers = new int[MAX_CHANGES];
  private int[] keys = new int[0];
  private int[] counts = new int[0];
  private int size = -1;

  public void reset() {
    size = -1;
    for (int i = 0; i < CAPACITY; i++) {
      live[i] = false;
      fresh[i] = false;
    }
  }

  public void observe(int[] nextKeys, int[] nextCounts, int nextSize) {
    for (int i = 0; i < CAPACITY; i++) {
      fresh[i] = false;
    }
    if (nextSize != size) {
      reset();
      if (keys.length < nextSize) {
        keys = new int[nextSize];
        counts = new int[nextSize];
      }
      size = nextSize;
      remember(nextKeys, nextCounts);
      return;
    }
    int lost = 0;
    int changed = 0;
    for (int slot = 0; slot < size; slot++) {
      boolean same = nextKeys[slot] == keys[slot];
      if (same && nextCounts[slot] == counts[slot]) {
        continue;
      }
      changed++;
      if (keys[slot] != 0 && (!same || nextCounts[slot] < counts[slot]) && lost < MAX_CHANGES) {
        losers[lost++] = slot;
      }
    }
    if (changed > 0 && changed <= MAX_CHANGES) {
      for (int slot = 0; slot < size; slot++) {
        int key = nextKeys[slot];
        if (key == 0 || (key == keys[slot] && nextCounts[slot] <= counts[slot])) {
          continue;
        }
        for (int i = 0; i < lost; i++) {
          int source = losers[i];
          if (source >= 0 && source != slot && keys[source] == key) {
            launch(source, slot);
            losers[i] = -1;
            break;
          }
        }
      }
    }
    remember(nextKeys, nextCounts);
  }

  private void remember(int[] nextKeys, int[] nextCounts) {
    System.arraycopy(nextKeys, 0, keys, 0, size);
    System.arraycopy(nextCounts, 0, counts, 0, size);
  }

  private void launch(int source, int target) {
    for (int i = 0; i < CAPACITY; i++) {
      if (live[i] && to[i] == target) {
        live[i] = false;
      }
    }
    for (int i = 0; i < CAPACITY; i++) {
      if (!live[i]) {
        live[i] = true;
        fresh[i] = true;
        from[i] = source;
        to[i] = target;
        progress[i] = 0.0F;
        return;
      }
    }
  }

  public void update(float dt, float seconds) {
    for (int i = 0; i < CAPACITY; i++) {
      if (!live[i]) {
        continue;
      }
      progress[i] = seconds > 0.0F && dt >= 0.0F ? progress[i] + dt / seconds : 1.0F;
      if (!(progress[i] < 1.0F)) {
        live[i] = false;
      }
    }
  }

  public boolean hides(int slot) {
    for (int i = 0; i < CAPACITY; i++) {
      if (live[i] && to[i] == slot) {
        return true;
      }
    }
    return false;
  }

  public boolean live(int flight) {
    return live[flight];
  }

  public boolean fresh(int flight) {
    return fresh[flight];
  }

  public int from(int flight) {
    return from[flight];
  }

  public int to(int flight) {
    return to[flight];
  }

  public float progress(int flight) {
    return progress[flight];
  }
}
