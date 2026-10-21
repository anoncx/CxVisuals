package me.cxiy.cxvisuals.modules.shaders.circles;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import me.cxiy.cxvisuals.core.settings.Setting;

public final class ContourSetting extends Setting<Contour> {
  private static final float PRECISION = 1000.0F;

  public ContourSetting(String id, String title) {
    super(id, title, Contour.circle());
  }

  @Override
  public JsonElement toJson() {
    JsonArray json = new JsonArray(Contour.KNOTS);
    Contour contour = get();
    for (int i = 0; i < Contour.KNOTS; i++) {
      json.add(Math.round(contour.knot(i) * PRECISION) / PRECISION);
    }
    return json;
  }

  @Override
  public void fromJson(JsonElement json) {
    if (!json.isJsonArray() || json.getAsJsonArray().size() != Contour.KNOTS) {
      throw new IllegalArgumentException("expected " + Contour.KNOTS + " radii");
    }
    float[] radii = new float[Contour.KNOTS];
    JsonArray array = json.getAsJsonArray();
    for (int i = 0; i < Contour.KNOTS; i++) {
      JsonElement entry = array.get(i);
      if (!entry.isJsonPrimitive() || !entry.getAsJsonPrimitive().isNumber()) {
        throw new IllegalArgumentException("expected a list of numbers");
      }
      radii[i] = entry.getAsFloat();
    }
    set(Contour.of(radii));
  }
}
