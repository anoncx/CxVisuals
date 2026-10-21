package me.cxiy.cxvisuals.core.settings;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class PaletteSetting extends Setting<Palette> {
  private static final String PRESET = "preset";
  private static final String CUSTOM = "custom";

  PaletteSetting(String id, String title, int defaultPreset) {
    super(id, title, Palette.of(defaultPreset));
  }

  @Override
  public JsonElement toJson() {
    Palette palette = get();
    JsonObject json = new JsonObject();
    json.addProperty(PRESET, palette.id());
    JsonArray custom = new JsonArray(2);
    custom.add(Palette.hex(palette.customFirst()));
    custom.add(Palette.hex(palette.customSecond()));
    json.add(CUSTOM, custom);
    return json;
  }

  @Override
  public void fromJson(JsonElement json) {
    if (!json.isJsonObject()) {
      throw new IllegalArgumentException("expected a palette");
    }
    JsonObject object = json.getAsJsonObject();
    JsonElement preset = object.get(PRESET);
    if (preset == null || !preset.isJsonPrimitive() || !preset.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("expected a palette name");
    }
    Palette current = get();
    int first = current.customFirst();
    int second = current.customSecond();
    JsonElement custom = object.get(CUSTOM);
    if (custom != null) {
      if (!custom.isJsonArray() || custom.getAsJsonArray().size() != 2) {
        throw new IllegalArgumentException("expected two custom colours");
      }
      first = colour(custom.getAsJsonArray().get(0));
      second = colour(custom.getAsJsonArray().get(1));
    }
    set(new Palette(Palette.presetOf(preset.getAsString()), first, second));
  }

  private static int colour(JsonElement json) {
    if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isString()) {
      throw new IllegalArgumentException("expected a colour like #8FE3FF");
    }
    return Palette.parse(json.getAsString());
  }
}
