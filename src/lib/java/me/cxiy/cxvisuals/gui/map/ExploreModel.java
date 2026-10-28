package me.cxiy.cxvisuals.gui.map;

import java.util.ArrayList;
import java.util.List;

public final class ExploreModel {
  public enum Sort {
    NEW,
    TOP,
    BIG
  }

  public record Entry(
      String id,
      String title,
      String author,
      String server,
      String dimension,
      int chunks,
      long size,
      int downloads,
      long created,
      boolean mine) {}

  public record Page(List<Entry> maps, boolean more) {}

  public record Request(int generation, Sort sort, String text, String server, int page) {}

  private final List<Entry> entries = new ArrayList<>();
  private Sort sort = Sort.NEW;
  private String text = "";
  private String server = "";
  private int generation;
  private int page;
  private boolean more;
  private boolean loading;
  private boolean failed;
  private boolean dirty = true;

  public List<Entry> entries() {
    return List.copyOf(entries);
  }

  public Sort sort() {
    return sort;
  }

  public String text() {
    return text;
  }

  public String server() {
    return server;
  }

  public boolean more() {
    return more;
  }

  public boolean loading() {
    return loading;
  }

  public boolean failed() {
    return failed;
  }

  public boolean wantsLoad() {
    return dirty && !loading && !failed;
  }

  public boolean wantsMore() {
    return !dirty && more && !loading && !failed;
  }

  public Request begin() {
    dirty = false;
    loading = true;
    page = 0;
    return new Request(++generation, sort, text, server, 0);
  }

  public Request beginMore() {
    loading = true;
    return new Request(++generation, sort, text, server, page + 1);
  }

  public void receive(Request request, Page result) {
    if (request.generation() != generation) {
      return;
    }
    loading = false;
    page = request.page();
    if (request.page() == 0) {
      entries.clear();
    }
    entries.addAll(result.maps());
    more = result.more();
  }

  public void fail(Request request) {
    if (request.generation() == generation) {
      loading = false;
      failed = true;
    }
  }

  public void retry() {
    failed = false;
    if (entries.isEmpty()) {
      dirty = true;
    }
  }

  public void sort(Sort next) {
    if (next != sort) {
      sort = next;
      reset();
    }
  }

  public void text(String next) {
    String clean = next == null ? "" : next.strip();
    if (!clean.equals(text)) {
      text = clean;
      reset();
    }
  }

  public void server(String next) {
    String chosen = next.equals(server) ? "" : next;
    server = chosen;
    reset();
  }

  public Entry find(String id) {
    for (Entry entry : entries) {
      if (entry.id().equals(id)) {
        return entry;
      }
    }
    return null;
  }

  public void remove(String id) {
    entries.removeIf(entry -> entry.id().equals(id));
  }

  private void reset() {
    entries.clear();
    more = false;
    failed = false;
    loading = false;
    generation++;
    dirty = true;
  }
}
