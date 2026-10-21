package me.cxiy.cxvisuals.modules.donutsmp;

import java.util.Locale;

public final class Money {
  private static final String[] SUFFIXES = {"", "K", "M", "B", "T"};
  private static final double STEP = 1000.0;

  private Money() {}

  public static String format(double value) {
    double scaled = Math.abs(value);
    int tier = 0;
    while (scaled >= STEP && tier < SUFFIXES.length - 1) {
      scaled /= STEP;
      tier++;
    }
    return String.format(Locale.ROOT, "%.2f%s", Math.copySign(scaled, value), SUFFIXES[tier]);
  }
}
