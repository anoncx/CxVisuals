package me.cxiy.cxvisuals.gui;

public final class StyledText {
  public static final int BOLD = 1;
  public static final int ITALIC = 2;
  public static final int UNDERLINE = 4;
  public static final int STRIKE = 8;
  public static final int OBFUSCATED = 16;
  public static final char MARK = '§';
  public static final StyledText EMPTY =
      new StyledText("", new String[0], new int[0], new int[0], 0);

  private static final int HEX_LENGTH = 14;
  private static final int[] PALETTE = {
    0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA,
    0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF
  };

  public final String plain;
  public final String[] parts;
  public final int[] colors;
  public final int[] flags;
  public final int count;

  private StyledText(String plain, String[] parts, int[] colors, int[] flags, int count) {
    this.plain = plain;
    this.parts = parts;
    this.colors = colors;
    this.flags = flags;
    this.count = count;
  }

  public static StyledText of(String raw) {
    if (raw.isEmpty()) {
      return EMPTY;
    }
    if (raw.indexOf(MARK) < 0) {
      return new StyledText(raw, new String[] {raw}, new int[1], new int[1], 1);
    }
    int length = raw.length();
    int room = 1;
    for (int i = 0; i < length; i++) {
      if (raw.charAt(i) == MARK) {
        room++;
      }
    }
    String[] parts = new String[room];
    int[] colors = new int[room];
    int[] flags = new int[room];
    StringBuilder plain = new StringBuilder(length);
    StringBuilder part = new StringBuilder(length);
    int count = 0;
    int color = 0;
    int flag = 0;
    for (int i = 0; i < length; i++) {
      char ch = raw.charAt(i);
      if (ch != MARK) {
        part.append(ch);
        continue;
      }
      if (i + 1 >= length) {
        break;
      }
      char code = Character.toLowerCase(raw.charAt(++i));
      int nextColor = color;
      int nextFlag = flag;
      int digit = Character.digit(code, 16);
      if (digit >= 0) {
        nextColor = PALETTE[digit];
        nextFlag = 0;
      } else if (code == 'x') {
        int rgb = hex(raw, i + 1);
        if (rgb >= 0) {
          nextColor = 0xFF000000 | rgb;
          nextFlag = 0;
          i += HEX_LENGTH - 2;
        }
      } else if (code == 'r') {
        nextColor = 0;
        nextFlag = 0;
      } else if (code == 'l') {
        nextFlag |= BOLD;
      } else if (code == 'o') {
        nextFlag |= ITALIC;
      } else if (code == 'n') {
        nextFlag |= UNDERLINE;
      } else if (code == 'm') {
        nextFlag |= STRIKE;
      } else if (code == 'k') {
        nextFlag |= OBFUSCATED;
      }
      if ((nextColor != color || nextFlag != flag) && part.length() > 0) {
        parts[count] = part.toString();
        colors[count] = color;
        flags[count] = flag;
        count++;
        plain.append(part);
        part.setLength(0);
      }
      color = nextColor;
      flag = nextFlag;
    }
    if (part.length() > 0) {
      parts[count] = part.toString();
      colors[count] = color;
      flags[count] = flag;
      count++;
      plain.append(part);
    }
    return count == 0 ? EMPTY : new StyledText(plain.toString(), parts, colors, flags, count);
  }

  public static String strip(String raw) {
    return raw.indexOf(MARK) < 0 ? raw : of(raw).plain;
  }

  private static int hex(String raw, int from) {
    if (from + HEX_LENGTH - 2 > raw.length()) {
      return -1;
    }
    int rgb = 0;
    for (int k = 0; k < 6; k++) {
      int digit = Character.digit(raw.charAt(from + k * 2 + 1), 16);
      if (raw.charAt(from + k * 2) != MARK || digit < 0) {
        return -1;
      }
      rgb = (rgb << 4) | digit;
    }
    return rgb;
  }
}
