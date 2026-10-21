package me.cxiy.cxvisuals.modules.donutsmp;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.cxiy.cxvisuals.core.settings.TextSetting;

public final class Watchlist {
  public static final int LIMIT = 12;

  private final TextSetting store;
  private final List<String> ids = new ArrayList<>(LIMIT);
  private final List<String> view = Collections.unmodifiableList(ids);
  private String synced;
  private int revision;

  public Watchlist(TextSetting store) {
    this.store = store;
  }

  private void sync() {
    String raw = store.get();
    if (raw.equals(synced)) {
      return;
    }
    synced = raw;
    ids.clear();
    for (String line : raw.split("\n")) {
      if (!line.isBlank() && ids.size() < LIMIT) {
        ids.add(line.strip());
      }
    }
    revision++;
  }

  public List<String> ids() {
    sync();
    return view;
  }

  public int size() {
    sync();
    return ids.size();
  }

  public int revision() {
    sync();
    return revision;
  }

  public boolean has(String id) {
    sync();
    return ids.contains(id);
  }

  public boolean toggle(String id) {
    sync();
    boolean added = !ids.remove(id);
    if (added) {
      if (ids.size() >= LIMIT) {
        ids.remove(0);
      }
      ids.add(id);
    }
    write();
    return added;
  }

  public void clear() {
    sync();
    ids.clear();
    write();
  }

  private void write() {
    synced = String.join("\n", ids);
    revision++;
    store.set(synced);
  }
}
