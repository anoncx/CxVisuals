package me.cxiy.cxvisuals.core.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public final class PickSetting extends Setting<Set<String>> {
  private List<String> ids = List.of();
  private List<String> labels = List.of();

  PickSetting(String id, String title) {
    super(id, title, Set.of());
  }

  public void options(List<String> ids, List<String> labels) {
    if (ids.size() != labels.size()) {
      throw new IllegalArgumentException("one label per option is required for " + id());
    }
    this.ids = List.copyOf(ids);
    this.labels = List.copyOf(labels);
  }

  public List<String> ids() {
    return ids;
  }

  public List<String> labels() {
    return labels;
  }

  public boolean picked(String option) {
    return get().contains(option);
  }

  public void toggle(String option) {
    mark(List.of(option), !picked(option));
  }

  public void mark(Collection<String> options, boolean on) {
    Set<String> next = new TreeSet<>(get());
    if (on) {
      next.addAll(options);
    } else {
      next.removeAll(options);
    }
    set(next);
  }

  @Override
  protected Set<String> normalize(Set<String> next) {
    return Collections.unmodifiableSet(new TreeSet<>(next));
  }

  @Override
  public JsonElement toJson() {
    JsonArray json = new JsonArray();
    for (String option : get()) {
      json.add(option);
    }
    return json;
  }

  @Override
  public void fromJson(JsonElement json) {
    if (!json.isJsonArray()) {
      throw new IllegalArgumentException("expected a list");
    }
    Set<String> next = new TreeSet<>();
    for (JsonElement entry : json.getAsJsonArray()) {
      if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isString()) {
        throw new IllegalArgumentException("expected a list of names");
      }
      next.add(entry.getAsString());
    }
    set(next);
  }
}
