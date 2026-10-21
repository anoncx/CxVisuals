package me.cxiy.cxvisuals.modules.inventory;

public final class Catalog {
  private static final int[] NONE = new int[0];

  private final Handles handles;
  private int[] all = NONE;
  private int[] shown = NONE;
  private Query applied = Query.ALL;
  private int revision;

  public Catalog(Handles handles) {
    this.handles = handles;
  }

  public void replace(int[] entries) {
    all = entries;
    refilter(applied);
    revision++;
  }

  public int revision() {
    return revision;
  }

  public int size() {
    return shown.length;
  }

  public int total() {
    return all.length;
  }

  public int at(int index) {
    return shown[index];
  }

  public int indexOf(int handle) {
    for (int i = 0; i < shown.length; i++) {
      if (shown[i] == handle) {
        return i;
      }
    }
    return -1;
  }

  public void search(Query query) {
    if (query.source().equals(applied.source())) {
      return;
    }
    refilter(query);
  }

  private void refilter(Query query) {
    applied = query;
    if (query.empty()) {
      shown = all;
      return;
    }
    int[] buffer = new int[all.length];
    int count = 0;
    for (int handle : all) {
      if (query.matches(handles, handle)) {
        buffer[count++] = handle;
      }
    }
    shown = java.util.Arrays.copyOf(buffer, count);
  }
}
