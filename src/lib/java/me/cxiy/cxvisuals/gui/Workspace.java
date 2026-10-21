package me.cxiy.cxvisuals.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class Workspace {
  record Slot(String id, float x, float y) {
    boolean placed() {
      return !Float.isNaN(x) && !Float.isNaN(y);
    }
  }

  record Layout(String main, List<Slot> slots) {
    static final Layout EMPTY = new Layout("", List.of());
  }

  private static final String MAIN = "main=";
  private static final String WINDOW = "win=";
  private static final char SEPARATOR = ';';
  private static final char AT = '@';
  private static final char COMMA = ',';

  private Workspace() {}

  static String encode(String main, List<Slot> slots) {
    StringBuilder text = new StringBuilder();
    if (!main.isEmpty()) {
      text.append(MAIN).append(main);
    }
    for (int i = 0; i < slots.size(); i++) {
      Slot slot = slots.get(i);
      if (text.length() > 0) {
        text.append(SEPARATOR);
      }
      text.append(WINDOW).append(slot.id());
      if (slot.placed()) {
        text.append(AT).append(format(slot.x())).append(COMMA).append(format(slot.y()));
      }
    }
    return text.toString();
  }

  static Layout decode(String text) {
    if (text == null || text.isEmpty()) {
      return Layout.EMPTY;
    }
    String main = "";
    List<Slot> slots = new ArrayList<>();
    for (String part : text.split(String.valueOf(SEPARATOR))) {
      if (part.startsWith(MAIN)) {
        main = part.substring(MAIN.length());
      } else if (part.startsWith(WINDOW)) {
        Slot slot = slot(part.substring(WINDOW.length()));
        if (slot != null) {
          slots.add(slot);
        }
      }
    }
    return new Layout(main, slots);
  }

  private static Slot slot(String text) {
    int at = text.indexOf(AT);
    if (at < 0) {
      return text.isEmpty() ? null : new Slot(text, Float.NaN, Float.NaN);
    }
    String id = text.substring(0, at);
    int comma = text.indexOf(COMMA, at);
    if (id.isEmpty() || comma < 0) {
      return id.isEmpty() ? null : new Slot(id, Float.NaN, Float.NaN);
    }
    try {
      float x = Float.parseFloat(text.substring(at + 1, comma));
      float y = Float.parseFloat(text.substring(comma + 1));
      return Float.isFinite(x) && Float.isFinite(y)
          ? new Slot(id, x, y)
          : new Slot(id, Float.NaN, Float.NaN);
    } catch (NumberFormatException e) {
      return new Slot(id, Float.NaN, Float.NaN);
    }
  }

  private static String format(float value) {
    return String.format(Locale.ROOT, "%.4f", value);
  }
}
