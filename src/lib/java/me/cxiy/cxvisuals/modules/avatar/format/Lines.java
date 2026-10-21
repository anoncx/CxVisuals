package me.cxiy.cxvisuals.modules.avatar.format;

import java.nio.charset.StandardCharsets;
import me.cxiy.cxvisuals.core.i18n.Lang;

final class Lines {
  private final String text;
  private final String[] tokens = new String[64];
  private int position;
  private int count;
  private String line = "";

  Lines(byte[] data, int offset) {
    this.text = new String(data, offset, data.length - offset, StandardCharsets.ISO_8859_1);
  }

  Lines(String text) {
    this.text = text;
  }

  int position() {
    return position;
  }

  boolean next() {
    while (position < text.length()) {
      int end = text.indexOf('\n', position);
      if (end < 0) {
        end = text.length();
      }
      line = text.substring(position, end);
      position = end + 1;
      split();
      if (count > 0) {
        return true;
      }
    }
    return false;
  }

  String line() {
    return line;
  }

  int count() {
    return count;
  }

  String token(int index) {
    return tokens[index];
  }

  float number(int index) throws ImportError {
    if (index >= count) {
      throw new ImportError(Lang.format("A line is missing numbers: {0}", shortLine()));
    }
    try {
      return Float.parseFloat(tokens[index]);
    } catch (NumberFormatException e) {
      throw new ImportError(Lang.format("Couldn't read the number \"{0}\".", tokens[index]));
    }
  }

  String rest(int from) {
    int seen = 0;
    int i = 0;
    int length = line.length();
    while (i < length && seen < from) {
      while (i < length && Character.isWhitespace(line.charAt(i))) {
        i++;
      }
      while (i < length && !Character.isWhitespace(line.charAt(i))) {
        i++;
      }
      seen++;
    }
    return line.substring(Math.min(i, length)).strip();
  }

  private String shortLine() {
    String trimmed = line.strip();
    return trimmed.length() > 40 ? trimmed.substring(0, 40) : trimmed;
  }

  private void split() {
    count = 0;
    int length = line.length();
    int i = 0;
    while (i < length && count < tokens.length) {
      char c = line.charAt(i);
      if (c == '#' && count == 0) {
        return;
      }
      if (c <= ' ') {
        i++;
        continue;
      }
      int start = i;
      while (i < length && line.charAt(i) > ' ') {
        i++;
      }
      tokens[count++] = line.substring(start, i);
    }
  }
}
