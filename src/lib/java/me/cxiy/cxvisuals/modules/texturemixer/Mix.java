package me.cxiy.cxvisuals.modules.texturemixer;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Mix {
  private final Map<String, String> picks = new HashMap<>(256);
  private final List<Runnable> listeners = new ArrayList<>(2);
  private int revision;

  public int revision() {
    return revision;
  }

  public int size() {
    return picks.size();
  }

  public Map<String, String> snapshot() {
    return new HashMap<>(picks);
  }

  public String pack(String texture) {
    return picks.getOrDefault(texture, Pack.VANILLA);
  }

  public boolean changed(Entry entry) {
    for (String texture : entry.textures()) {
      if (picks.containsKey(texture)) {
        return true;
      }
    }
    return false;
  }

  public int signature(Entry entry) {
    int hash = 17;
    for (String texture : entry.textures()) {
      hash = hash * 31 + pack(texture).hashCode();
    }
    return hash;
  }

  public boolean paint(Entry entry, Pack pack) {
    boolean touched = false;
    for (String texture : entry.textures()) {
      touched |= set(texture, pack);
    }
    if (touched) {
      bump();
    }
    return touched;
  }

  public boolean paint(Iterable<Entry> entries, Pack pack) {
    boolean touched = false;
    for (Entry entry : entries) {
      for (String texture : entry.textures()) {
        touched |= set(texture, pack);
      }
    }
    if (touched) {
      bump();
    }
    return touched;
  }

  public boolean reset(Entry entry) {
    return paint(entry, null);
  }

  public void clear() {
    if (!picks.isEmpty()) {
      picks.clear();
      bump();
    }
  }

  public void onChange(Runnable listener) {
    listeners.add(listener);
  }

  public String write() {
    JsonObject out = new JsonObject();
    for (Map.Entry<String, String> entry : picks.entrySet()) {
      out.addProperty(entry.getKey(), entry.getValue());
    }
    return out.toString();
  }

  public void read(String json) {
    picks.clear();
    if (json != null && !json.isBlank()) {
      try {
        for (Map.Entry<String, JsonElement> entry :
            JsonParser.parseString(json).getAsJsonObject().entrySet()) {
          picks.put(entry.getKey(), entry.getValue().getAsString());
        }
      } catch (RuntimeException ignored) {
        picks.clear();
      }
    }
    bump();
  }

  public void retain(Set<String> known) {
    if (picks.values().removeIf(id -> !known.contains(id))) {
      bump();
    }
  }

  private boolean set(String texture, Pack pack) {
    if (pack == null || pack.id().equals(Pack.VANILLA)) {
      return picks.remove(texture) != null;
    }
    if (!pack.has(texture) || pack.id().equals(picks.get(texture))) {
      return false;
    }
    picks.put(texture, pack.id());
    return true;
  }

  private void bump() {
    revision++;
    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).run();
    }
  }
}
