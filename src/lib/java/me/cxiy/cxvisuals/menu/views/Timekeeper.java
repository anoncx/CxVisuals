package me.cxiy.cxvisuals.menu.views;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import me.cxiy.cxvisuals.core.i18n.Lang;

final class Timekeeper {
  private static final long MINUTE = 60_000L;
  private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
  private static final String DAY = "EEEE d MMMM";

  private final DateTimeFormatter dayFormat = DateTimeFormatter.ofPattern(DAY, Lang.locale());
  private long minute = Long.MIN_VALUE;
  private String time = "";
  private String day = "";

  String time() {
    return time;
  }

  String day() {
    return day;
  }

  void update() {
    long now = System.currentTimeMillis() / MINUTE;
    if (now != minute) {
      minute = now;
      LocalDateTime local = LocalDateTime.now();
      time = CLOCK.format(local);
      day = dayFormat.format(local).toUpperCase(Lang.locale());
    }
  }
}
