package me.cxiy.cxvisuals.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

public final class TextWrap {
  private TextWrap() {}

  public static List<String> lines(ToDoubleFunction<String> width, String text, float max) {
    List<String> lines = new ArrayList<>();
    StringBuilder line = new StringBuilder();
    for (String word : text.split(" ")) {
      if (line.length() == 0) {
        line.append(word);
      } else if (width.applyAsDouble(line + " " + word) <= max) {
        line.append(' ').append(word);
      } else {
        lines.add(line.toString());
        line = new StringBuilder(word);
      }
    }
    lines.add(line.toString());
    return lines;
  }
}
