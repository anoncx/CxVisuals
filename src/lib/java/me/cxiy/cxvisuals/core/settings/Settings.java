package me.cxiy.cxvisuals.core.settings;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Settings {
  private final List<Page> pages = new ArrayList<>();
  private final List<Page> pagesView = Collections.unmodifiableList(pages);
  private final Map<String, Setting<?>> persisted = new LinkedHashMap<>();
  private final Map<String, Setting<?>> persistedView = Collections.unmodifiableMap(persisted);
  private final List<Runnable> listeners = new ArrayList<>(2);
  private final String prefix;

  public Settings() {
    this("");
  }

  public Settings(String prefix) {
    this.prefix = prefix.isEmpty() ? "" : prefix + ".";
  }

  public List<Page> pages() {
    return pagesView;
  }

  public Page page(String id, String title) {
    Page page = new Page(this, id, title);
    pages.add(page);
    return page;
  }

  public Page hidden(String id, String title) {
    return new Page(this, id, title);
  }

  public Map<String, Setting<?>> persisted() {
    return persistedView;
  }

  public void onAnyChange(Runnable listener) {
    listeners.add(listener);
  }

  public void resetAll() {
    for (Setting<?> setting : persisted.values()) {
      if (!setting.keptOnReset()) {
        setting.reset();
      }
    }
  }

  public BoolSetting toggle(String key, String title, boolean defaultValue) {
    BoolSetting setting = new BoolSetting(key, title, defaultValue);
    track(prefix + key, setting);
    return setting;
  }

  public TextSetting text(String key, String defaultValue) {
    TextSetting setting = new TextSetting(key, key, defaultValue);
    track(prefix + key, setting);
    return setting;
  }

  public TextSetting ephemeral(String key, String defaultValue) {
    TextSetting setting = new TextSetting(key, key, defaultValue);
    setting.onChange(this::changed);
    return setting;
  }

  public ActionSetting command(
      String id, String buttonLabel, ActionSetting.Kind kind, Runnable action) {
    return new ActionSetting(id, buttonLabel, buttonLabel, kind, action);
  }

  void register(String pageId, Setting<?> setting) {
    if (setting.persisted()) {
      track(prefix + pageId + "." + setting.id(), setting);
    }
  }

  private void track(String key, Setting<?> setting) {
    if (persisted.putIfAbsent(key, setting) != null) {
      throw new IllegalStateException("duplicate setting " + key);
    }
    setting.onChange(this::changed);
  }

  private void changed() {
    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).run();
    }
  }
}
