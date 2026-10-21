package me.cxiy.cxvisuals.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.AbstractList;
import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class EnumSetting<E extends Enum<E>> extends Setting<E> implements Choice {
  private final List<E> values;
  private final List<String> labels;
  private final List<String> shown =
      new AbstractList<>() {
        @Override
        public String get(int index) {
          return Lang.tr(labels.get(index));
        }

        @Override
        public int size() {
          return labels.size();
        }
      };

  EnumSetting(String id, String title, E defaultValue, String... labels) {
    super(id, title, defaultValue);
    this.values = List.of(defaultValue.getDeclaringClass().getEnumConstants());
    if (labels.length != values.size()) {
      throw new IllegalArgumentException("one label per enum constant is required for " + id);
    }
    this.labels = List.of(labels);
  }

  public List<E> values() {
    return values;
  }

  @Override
  public List<String> labels() {
    return shown;
  }

  @Override
  public int index() {
    return get().ordinal();
  }

  @Override
  public void setIndex(int index) {
    set(values.get(Math.max(0, Math.min(values.size() - 1, index))));
  }

  @Override
  public JsonElement toJson() {
    return new JsonPrimitive(get().name());
  }

  @Override
  public void fromJson(JsonElement json) {
    if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("expected a name");
    }
    String name = json.getAsString();
    for (int i = 0; i < values.size(); i++) {
      if (values.get(i).name().equals(name)) {
        set(values.get(i));
        return;
      }
    }
    throw new IllegalArgumentException("unknown option " + name);
  }
}
