package me.cxiy.cxvisuals.modules.chat;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Speakers {
  private static final Pattern LINE =
      Pattern.compile(
          "^(?:\\s|[\\[(][^\\])]{1,32}[\\])]|[^\\p{L}\\p{N}_\\s:])*(?:[\\p{L}\\p{N}_.]{1,24}\\s+){0,2}?([A-Za-z0-9_]{2,24})\\s?[:»›➤>≫→|]{1,2}\\s");
  private static final Set<String> NOT_NAMES =
      Set.of(
          "error",
          "usage",
          "warning",
          "warn",
          "info",
          "note",
          "tip",
          "server",
          "broadcast",
          "announcement",
          "alert",
          "success",
          "failed",
          "fail",
          "invalid",
          "unknown",
          "debug",
          "notice",
          "reminder",
          "hint",
          "result",
          "status",
          "example",
          "syntax",
          "help",
          "reason",
          "time",
          "ping",
          "tps",
          "online",
          "players");
  private static final int LOOKAHEAD = 96;
  private static final char CODE = '§';

  private Speakers() {}

  public static String plain(String text) {
    if (text.indexOf(CODE) < 0) {
      return text;
    }
    StringBuilder out = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == CODE) {
        i++;
      } else {
        out.append(text.charAt(i));
      }
    }
    return out.toString();
  }

  public static String speaker(String text) {
    Matcher match = LINE.matcher(text.length() > LOOKAHEAD ? text.substring(0, LOOKAHEAD) : text);
    if (!match.find()) {
      return null;
    }
    String name = match.group(1);
    return NOT_NAMES.contains(name.toLowerCase(Locale.ROOT)) ? null : name;
  }
}
