package me.cxiy.cxvisuals.core.settings;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import me.cxiy.cxvisuals.core.i18n.Lang;

public abstract class Setting<T> {
  private final String id;
  private final String title;
  private final T defaultValue;
  private final List<Runnable> listeners = new ArrayList<>(2);
  private String description = "";
  private String context;
  private String[] former = {};
  private boolean kept;
  private T value;

  protected Setting(String id, String title, T defaultValue) {
    this.id = id;
    this.title = title;
    this.defaultValue = defaultValue;
    this.value = defaultValue;
  }

  public String id() {
    return id;
  }

  public String title() {
    return context == null ? Lang.tr(title) : Lang.tr(context, title);
  }

  public String description() {
    return Lang.tr(description);
  }

  @SuppressWarnings("unchecked")
  public <S extends Setting<T>> S context(String key) {
    context = key;
    return (S) this;
  }

  @SuppressWarnings("unchecked")
  public <S extends Setting<T>> S describe(String text) {
    description = text;
    return (S) this;
  }

  @SuppressWarnings("unchecked")
  public <S extends Setting<T>> S formerly(String... keys) {
    former = keys;
    return (S) this;
  }

  public String[] formerKeys() {
    return former;
  }

  @SuppressWarnings("unchecked")
  public <S extends Setting<T>> S keepOnReset() {
    kept = true;
    return (S) this;
  }

  public boolean keptOnReset() {
    return kept;
  }

  public T get() {
    return value;
  }

  public void set(T next) {
    T normalized = normalize(next);
    if (Objects.equals(normalized, value)) {
      return;
    }
    value = normalized;
    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).run();
    }
  }

  public T defaultValue() {
    return defaultValue;
  }

  public void reset() {
    set(defaultValue);
  }

  public void onChange(Runnable listener) {
    listeners.add(listener);
  }

  protected final void changed() {
    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).run();
    }
  }

  public boolean persisted() {
    return true;
  }

  protected T normalize(T next) {
    return next;
  }

  public abstract JsonElement toJson();

  public abstract void fromJson(JsonElement json);
}
