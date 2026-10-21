package me.cxiy.cxvisuals.modules.inventory;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class Handles {
  public static final int NONE = -1;

  private final Map<String, Integer> byKey = new HashMap<>(4096);
  private String[] keys = new String[4096];
  private String[] titles = new String[4096];
  private String[] names = new String[4096];
  private String[] spaces = new String[4096];
  private String[] tips = new String[4096];
  private int size;

  public int intern(String key, String name, String space, String tip) {
    Integer known = byKey.get(key);
    if (known != null) {
      return known;
    }
    if (size == keys.length) {
      int grown = size * 2;
      keys = java.util.Arrays.copyOf(keys, grown);
      titles = java.util.Arrays.copyOf(titles, grown);
      names = java.util.Arrays.copyOf(names, grown);
      spaces = java.util.Arrays.copyOf(spaces, grown);
      tips = java.util.Arrays.copyOf(tips, grown);
    }
    int handle = size++;
    keys[handle] = key;
    titles[handle] = name;
    names[handle] = name.toLowerCase(Locale.ROOT);
    spaces[handle] = space;
    tips[handle] = tip.toLowerCase(Locale.ROOT);
    byKey.put(key, handle);
    return handle;
  }

  public void describe(int handle, String tip) {
    tips[handle] = tip.toLowerCase(Locale.ROOT);
  }

  public int find(String key) {
    Integer handle = byKey.get(key);
    return handle == null ? NONE : handle;
  }

  public int size() {
    return size;
  }

  public String key(int handle) {
    return keys[handle];
  }

  public String title(int handle) {
    return titles[handle];
  }

  public String name(int handle) {
    return names[handle];
  }

  public String space(int handle) {
    return spaces[handle];
  }

  public String tip(int handle) {
    return tips[handle];
  }
}
