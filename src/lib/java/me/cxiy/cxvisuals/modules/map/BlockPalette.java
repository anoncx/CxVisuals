package me.cxiy.cxvisuals.modules.map;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BlockPalette {
  public static final int NONE = 0;
  public static final int LIMIT = Character.MAX_VALUE;
  public static final int TABLE = 1 << 14;

  private final Map<String, Integer> ids = new ConcurrentHashMap<>(512);
  private volatile String[] keys = new String[256];
  private volatile int[] sprites = new int[256];
  public static final int DIRECTIONS = 4;

  private volatile int[][] sides = new int[DIRECTIONS][256];
  private volatile int[] overlays = new int[256];
  private volatile int size = 1;
  private volatile int revision;

  public synchronized int id(String key) {
    if (key == null || key.isEmpty()) {
      return NONE;
    }
    Integer known = ids.get(key);
    if (known != null) {
      return known;
    }
    if (size >= LIMIT) {
      return NONE;
    }
    int id = size;
    if (id >= keys.length) {
      keys = Arrays.copyOf(keys, keys.length * 2);
      sprites = Arrays.copyOf(sprites, sprites.length * 2);
      int[][] grown = new int[DIRECTIONS][];
      for (int d = 0; d < DIRECTIONS; d++) {
        grown[d] = Arrays.copyOf(sides[d], keys.length);
      }
      sides = grown;
      overlays = Arrays.copyOf(overlays, overlays.length * 2);
    }
    keys[id] = key;
    size = id + 1;
    ids.put(key, id);
    return id;
  }

  public String key(int id) {
    String[] all = keys;
    return id > NONE && id < size && id < all.length ? all[id] : "";
  }

  public int size() {
    return size;
  }

  public int sprite(int id) {
    int[] all = sprites;
    return id > NONE && id < all.length ? all[id] : 0;
  }

  public synchronized void sprite(int id, int code) {
    if (id > NONE && id < sprites.length && sprites[id] != code) {
      sprites[id] = code;
      revision++;
    }
  }

  public synchronized void side(int id, int direction, int code) {
    int[] all = sides[direction];
    if (id > NONE && id < all.length && all[id] != code) {
      all[id] = code;
      revision++;
    }
  }

  public synchronized void overlay(int id, int code) {
    if (id > NONE && id < overlays.length && overlays[id] != code) {
      overlays[id] = code;
      revision++;
    }
  }

  public void overlays(int[] out) {
    int[] all = overlays;
    int count = Math.min(Math.min(size, all.length), Math.min(out.length, TABLE));
    Arrays.fill(out, 0);
    System.arraycopy(all, 0, out, 0, count);
  }

  public int side(int id, int direction) {
    int[] all = sides[direction];
    return id > NONE && id < all.length ? all[id] : 0;
  }

  public void sides(int direction, int[] out) {
    int[] all = sides[direction];
    int count = Math.min(Math.min(size, all.length), Math.min(out.length, TABLE));
    Arrays.fill(out, 0);
    System.arraycopy(all, 0, out, 0, count);
  }

  public synchronized void forgetSprites() {
    sprites = new int[sprites.length];
    sides = new int[DIRECTIONS][sides[0].length];
    overlays = new int[overlays.length];
    revision++;
  }

  public int revision() {
    return revision;
  }

  public void table(int[] out) {
    int[] all = sprites;
    int count = Math.min(Math.min(size, all.length), Math.min(out.length, TABLE));
    Arrays.fill(out, 0);
    System.arraycopy(all, 0, out, 0, count);
  }
}
