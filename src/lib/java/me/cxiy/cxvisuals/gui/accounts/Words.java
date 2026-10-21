package me.cxiy.cxvisuals.gui.accounts;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;

final class Words {
  private Words() {}

  static List<String> wrap(Canvas c, FontStyle font, String text, float width) {
    List<String> lines = new ArrayList<>(4);
    for (String paragraph : text.split("\n")) {
      StringBuilder line = new StringBuilder();
      for (String word : paragraph.split(" ")) {
        String trial = line.isEmpty() ? word : line + " " + word;
        if (!line.isEmpty() && c.textWidth(font, trial) > width) {
          lines.add(line.toString());
          line.setLength(0);
          line.append(word);
        } else {
          line.setLength(0);
          line.append(trial);
        }
      }
      lines.add(line.toString());
    }
    return lines;
  }

  static String date(long millis) {
    if (millis <= 0L) {
      return Lang.tr("Never");
    }
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Lang.locale())
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
  }

  static String ago(long millis, long now) {
    if (millis <= 0L) {
      return Lang.tr("Never");
    }
    long seconds = Math.max(0L, (now - millis) / 1000L);
    if (seconds < 60L) {
      return Lang.tr("Just now");
    }
    if (seconds < 3600L) {
      return Lang.format("{0} min ago", seconds / 60L);
    }
    if (seconds < 86_400L) {
      return Lang.format("{0} h ago", seconds / 3600L);
    }
    if (seconds < 86_400L * 60L) {
      return Lang.format("{0} d ago", seconds / 86_400L);
    }
    return date(millis);
  }
}
