package me.cxiy.cxvisuals.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

abstract class TransientSetting extends Setting<Void> {
  TransientSetting(String id, String title) {
    super(id, title, null);
  }

  @Override
  public final boolean persisted() {
    return false;
  }

  @Override
  public final JsonElement toJson() {
    return JsonNull.INSTANCE;
  }

  @Override
  public final void fromJson(JsonElement json) {}
}
