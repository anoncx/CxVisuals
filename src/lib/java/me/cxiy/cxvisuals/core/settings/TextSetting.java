package me.cxiy.cxvisuals.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class TextSetting extends Setting<String> {
  private boolean concealed;

  TextSetting(String id, String title, String defaultValue) {
    super(id, title, defaultValue == null ? "" : defaultValue);
  }

  public TextSetting concealed() {
    concealed = true;
    return this;
  }

  public boolean isConcealed() {
    return concealed;
  }

  @Override
  protected String normalize(String next) {
    return next == null ? "" : next;
  }

  @Override
  public JsonElement toJson() {
    return new JsonPrimitive(get());
  }

  @Override
  public void fromJson(JsonElement json) {
    if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("expected text");
    }
    set(json.getAsString());
  }
}
