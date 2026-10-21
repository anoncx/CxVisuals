package me.cxiy.cxvisuals.modules.hud.media;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
  private static final Pattern STAMP =
      Pattern.compile("\\G\\s*\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");

  private record Row(long time, String text) {}

  private LrcParser() {}

  public static Lyrics parse(String raw) {
    List<Row> rows = new ArrayList<>();
    List<Long> stamps = new ArrayList<>(2);
    for (String entry : raw.split("\\r?\\n")) {
      Matcher matcher = STAMP.matcher(entry);
      stamps.clear();
      int end = 0;
      while (matcher.find()) {
        long fraction = 0L;
        String digits = matcher.group(3);
        if (digits != null) {
          fraction = Long.parseLong(digits);
          if (digits.length() == 1) {
            fraction *= 100L;
          } else if (digits.length() == 2) {
            fraction *= 10L;
          }
        }
        stamps.add(
            Long.parseLong(matcher.group(1)) * 60_000L
                + Long.parseLong(matcher.group(2)) * 1000L
                + fraction);
        end = matcher.end();
      }
      if (stamps.isEmpty()) {
        continue;
      }
      String text = entry.substring(end).strip();
      for (int i = 0; i < stamps.size(); i++) {
        rows.add(new Row(stamps.get(i), text));
      }
    }
    if (rows.isEmpty()) {
      return Lyrics.MISSING;
    }
    rows.sort((a, b) -> Long.compare(a.time(), b.time()));
    long[] times = new long[rows.size()];
    String[] lines = new String[rows.size()];
    for (int i = 0; i < times.length; i++) {
      times[i] = rows.get(i).time();
      lines[i] = rows.get(i).text();
    }
    return new Lyrics(Lyrics.State.FOUND, times, lines);
  }
}
