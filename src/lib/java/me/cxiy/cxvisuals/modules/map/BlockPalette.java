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
  private volatile int[] sides = new int[256];
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
      sides = Arrays.copyOf(sides, sides.length * 2);
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

  public synchronized void side(int id, int code) {
    if (id > NONE && id < sides.length && sides[id] != code) {
      sides[id] = code;
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

  public int side(int id) {
    int[] all = sides;
    return id > NONE && id < all.length ? all[id] : 0;
  }

  public void sides(int[] out) {
    int[] all = sides;
    int count = Math.min(Math.min(size, all.length), Math.min(out.length, TABLE));
    Arrays.fill(out, 0);
    System.arraycopy(all, 0, out, 0, count);
  }

  public synchronized void forgetSprites() {
    sprites = new int[sprites.length];
    sides = new int[sides.length];
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
