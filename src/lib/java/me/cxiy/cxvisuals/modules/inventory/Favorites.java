package me.cxiy.cxvisuals.modules.inventory;

import java.util.ArrayList;
import java.util.List;
import me.cxiy.cxvisuals.core.settings.TextSetting;

public final class Favorites {
  private static final int LIMIT = 64;

  private final TextSetting store;
  private final Handles handles;
  private final List<String> keys = new ArrayList<>();
  private int[] resolved = new int[0];
  private boolean stale = true;

  public Favorites(TextSetting store, Handles handles) {
    this.store = store;
    this.handles = handles;
    for (String line : store.get().split("\n")) {
      if (!line.isBlank()) {
        keys.add(line);
      }
    }
  }

  public boolean has(int handle) {
    return keys.contains(handles.key(handle));
  }

  public void toggle(int handle) {
    String key = handles.key(handle);
    if (!keys.remove(key)) {
      if (keys.size() >= LIMIT) {
        keys.remove(0);
      }
      keys.add(key);
    }
    stale = true;
    store.set(String.join("\n", keys));
  }

  public int[] handles() {
    if (stale) {
      int[] buffer = new int[keys.size()];
      int count = 0;
      for (String key : keys) {
        int handle = handles.find(key);
        if (handle != Handles.NONE) {
          buffer[count++] = handle;
        }
      }
      resolved = java.util.Arrays.copyOf(buffer, count);
      stale = false;
    }
    return resolved;
  }

  public void refresh() {
    stale = true;
  }
}
