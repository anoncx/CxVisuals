package me.cxiy.cxvisuals.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import java.util.Locale;

public final class NumberSetting extends Setting<Double> {
  private final double min;
  private final double max;
  private final double step;
  private final String unit;
  private final int decimals;
  private final double scale;
  private String formatted;
  private double formattedFor = Double.NaN;

  NumberSetting(
      String id,
      String title,
      double defaultValue,
      double min,
      double max,
      double step,
      String unit) {
    super(id, title, defaultValue);
    this.min = min;
    this.max = max;
    this.step = step;
    this.unit = unit;
    this.decimals = decimalsOf(step);
    this.scale = Math.pow(10, decimals);
  }

  public double min() {
    return min;
  }

  public double max() {
    return max;
  }

  public double step() {
    return step;
  }

  public String unit() {
    return unit;
  }

  public double fraction() {
    return max <= min ? 0.0 : (get() - min) / (max - min);
  }

  public void setFraction(double fraction) {
    double clamped = fraction < 0.0 ? 0.0 : Math.min(fraction, 1.0);
    set(min + (max - min) * clamped);
  }

  public String format() {
    double value = get();
    if (value != formattedFor) {
      String number = String.format(Locale.ROOT, "%." + decimals + "f", value);
      boolean spaced = !unit.isEmpty() && Character.isLetter(unit.charAt(0));
      formatted = spaced ? number + " " + unit : number + unit;
      formattedFor = value;
    }
    return formatted;
  }

  @Override
  protected Double normalize(Double next) {
    double clamped = Math.max(min, Math.min(max, next));
    double snapped = min + Math.round((clamped - min) / step) * step;
    double rounded = Math.round(snapped * scale) / scale;
    return Math.max(min, Math.min(max, rounded));
  }

  @Override
  public JsonElement toJson() {
    return new JsonPrimitive(get());
  }

  @Override
  public void fromJson(JsonElement json) {
    if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) {
      throw new IllegalArgumentException("expected a number");
    }
    double value = json.getAsDouble();
    if (!Double.isFinite(value) || value < min || value > max) {
      throw new IllegalArgumentException("expected " + min + " to " + max);
    }
    set(value);
  }

  private static int decimalsOf(double step) {
    for (int d = 0; d < 4; d++) {
      double scaled = step * Math.pow(10, d);
      if (Math.abs(scaled - Math.round(scaled)) < 1e-9) {
        return d;
      }
    }
    return 4;
  }
}
