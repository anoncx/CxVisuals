package me.cxiy.cxvisuals.modules.deaths;

import java.util.Arrays;

public final class Corpses {
  public static final int MOST = 8;

  public static final class Corpse {
    public int id;
    public Fate fate;
    public int seed;
    public long born;
    public Object body;
    public double x;
    public double y;
    public double z;
    public float width;
    public float height;

    public float progress(long nanos, float seconds) {
      return Math.max(0.0F, (nanos - born) / 1.0e9F / seconds);
    }
  }

  private final Corpse[] corpses = new Corpse[MOST];
  private int count;
  private int seeds = 0x51D3;

  public Corpses() {
    Arrays.setAll(corpses, i -> new Corpse());
  }

  public int count() {
    return count;
  }

  public boolean empty() {
    return count == 0;
  }

  public Corpse get(int index) {
    return corpses[index];
  }

  public Corpse find(int id) {
    for (int i = 0; i < count; i++) {
      if (corpses[i].id == id) {
        return corpses[i];
      }
    }
    return null;
  }

  public Corpse begin(int id, Fate chosen, long nanos) {
    Corpse corpse = find(id);
    if (corpse != null) {
      return corpse;
    }
    if (count == MOST) {
      return null;
    }
    corpse = corpses[count++];
    seeds = seeds * 0x343FD + 0x269EC3;
    corpse.id = id;
    corpse.seed = (seeds >>> 16) & 0x7FFF;
    corpse.fate = chosen.resolve(corpse.seed);
    corpse.born = nanos;
    corpse.body = null;
    return corpse;
  }

  public void prune(long nanos, float seconds) {
    int kept = 0;
    for (int i = 0; i < count; i++) {
      Corpse corpse = corpses[i];
      if (corpse.progress(nanos, seconds) < 1.0F) {
        corpses[i] = corpses[kept];
        corpses[kept++] = corpse;
      } else {
        corpse.body = null;
      }
    }
    count = kept;
  }

  public void clear() {
    for (int i = 0; i < count; i++) {
      corpses[i].body = null;
    }
    count = 0;
  }
}
