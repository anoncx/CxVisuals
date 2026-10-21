package me.cxiy.cxvisuals.mc.menu.bridge;

import java.util.Arrays;
import java.util.Optional;
import me.cxiy.cxvisuals.gui.StyledText;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

final class Texts {
  private static final String ELLIPSIS = "...";
  private static final String SHORT_ELLIPSIS = "…";
  private static final String SEPARATOR = ": ";
  private static final String BREAK = "\n";

  private Texts() {}

  static String plain(Component component) {
    return component == null ? "" : StyledText.strip(component.getString());
  }

  static String action(Component component) {
    String text = plain(component).strip();
    if (text.endsWith(ELLIPSIS)) {
      return text.substring(0, text.length() - ELLIPSIS.length()).stripTrailing();
    }
    return text.endsWith(SHORT_ELLIPSIS)
        ? text.substring(0, text.length() - 1).stripTrailing()
        : text;
  }

  static boolean opens(Component component) {
    String text = component == null ? "" : component.getString().strip();
    return text.endsWith(ELLIPSIS) || text.endsWith(SHORT_ELLIPSIS);
  }

  static String before(String text) {
    int at = text.lastIndexOf(SEPARATOR);
    return at < 0 ? text : text.substring(0, at);
  }

  static String after(String text) {
    int at = text.lastIndexOf(SEPARATOR);
    return at < 0 ? "" : text.substring(at + SEPARATOR.length());
  }

  static StyledText styled(Component component) {
    return lines(component, 1)[0];
  }

  static StyledText[] lines(Component component, int limit) {
    StyledText[] lines = new StyledText[limit];
    Arrays.fill(lines, StyledText.EMPTY);
    if (component == null) {
      return lines;
    }
    StringBuilder out = new StringBuilder();
    component.visit(
        (style, text) -> {
          String[] pieces = text.split(BREAK, -1);
          for (int i = 0; i < pieces.length; i++) {
            if (i > 0) {
              out.append(BREAK);
            }
            codes(out, style);
            out.append(pieces[i]);
          }
          return Optional.empty();
        },
        Style.EMPTY);
    String[] split = out.toString().split(BREAK, limit + 1);
    for (int i = 0; i < Math.min(limit, split.length); i++) {
      lines[i] = StyledText.of(split[i].strip());
    }
    return lines;
  }

  private static void codes(StringBuilder out, Style style) {
    out.append(StyledText.MARK).append("r");
    TextColor color = style.getColor();
    if (color != null) {
      out.append(StyledText.MARK).append("x");
      for (int shift = 20; shift >= 0; shift -= 4) {
        out.append(StyledText.MARK)
            .append(Character.forDigit((color.getValue() >> shift) & 0xF, 16));
      }
    }
    code(out, style.isBold(), "l");
    code(out, style.isItalic(), "o");
    code(out, style.isUnderlined(), "n");
    code(out, style.isStrikethrough(), "m");
    code(out, style.isObfuscated(), "k");
  }

  private static void code(StringBuilder out, boolean on, String code) {
    if (on) {
      out.append(StyledText.MARK).append(code);
    }
  }
}
