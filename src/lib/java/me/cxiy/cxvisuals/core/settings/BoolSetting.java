package me.cxiy.cxvisuals.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BoolSetting extends Setting<Boolean> {
  BoolSetting(String id, String title, boolean defaultValue) {
    super(id, title, defaultValue);
  }

  @Override
  public JsonElement toJson() {
    return new JsonPrimitive(get());
  }

  @Override
  public void fromJson(JsonElement json) {
    if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isBoolean()) {
      throw new IllegalArgumentException("expected true or false");
    }
    set(json.getAsBoolean());
  }
}
