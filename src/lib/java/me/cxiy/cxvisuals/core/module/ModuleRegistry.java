package me.cxiy.cxvisuals.core.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.cxiy.cxvisuals.core.settings.Setting;

public final class ModuleRegistry {
  private final List<Module> modules = new ArrayList<>();
  private final List<Module> modulesView = Collections.unmodifiableList(modules);
  private final List<Module> entries = new ArrayList<>();
  private final List<Module> entriesView = Collections.unmodifiableList(entries);
  private final Map<String, Setting<?>> persisted = new LinkedHashMap<>();
  private final Map<String, Setting<?>> persistedView = Collections.unmodifiableMap(persisted);
  private final List<Runnable> listeners = new ArrayList<>(2);
  private final ClientSettings client;

  public ModuleRegistry(KeyBinder keys, String version) {
    client = new ClientSettings(keys, version, this::resetAll);
    client.settings.onAnyChange(this::changed);
    rebuild();
  }

  public <M extends Module> M add(M module) {
    if (byId(module.id()) != null) {
      throw new IllegalStateException("duplicate module " + module.id());
    }
    modules.add(module);
    module.settings.onAnyChange(this::changed);
    rebuild();
    return module;
  }

  public List<Module> modules() {
    return modulesView;
  }

  public List<Module> entries() {
    return entriesView;
  }

  public ClientSettings client() {
    return client;
  }

  public Module byId(String id) {
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i).id().equals(id)) {
        return entries.get(i);
      }
    }
    return null;
  }

  public Map<String, Setting<?>> persisted() {
    return persistedView;
  }

  public void onAnyChange(Runnable listener) {
    listeners.add(listener);
  }

  public void resetAll() {
    for (int i = 0; i < entries.size(); i++) {
      entries.get(i).settings.resetAll();
    }
  }

  private void rebuild() {
    entries.clear();
    entries.addAll(modules);
    entries.add(client);
    persisted.clear();
    for (int i = 0; i < entries.size(); i++) {
      Module entry = entries.get(i);
      entry.sections();
      persisted.putAll(entry.settings.persisted());
    }
  }

  private void changed() {
    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).run();
    }
  }
}
