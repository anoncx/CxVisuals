package me.cxiy.cxvisuals.core.settings;

import java.util.Locale;
import me.cxiy.cxvisuals.core.i18n.Lang;

public record Palette(int preset, int customFirst, int customSecond) {
  public static final int CUSTOM = -1;

  private static final String[] IDS = {"ice", "violet", "ember", "lime", "rose", "mono"};
  private static final String[] NAMES = Lang.keys("Ice", "Violet", "Ember", "Lime", "Rose", "Mono");
  private static final int[] FIRST = {
    0xFF8FE3FF, 0xFFC59BFF, 0xFFFFB347, 0xFFC8FF5A, 0xFFFF8FB3, 0xFFFFFFFF
  };
  private static final int[] SECOND = {
    0xFF3D7BFF, 0xFFFF5BD1, 0xFFFF3B2F, 0xFF2FE6A6, 0xFFFF4D6D, 0xFF9A9AA0
  };
  private static final String CUSTOM_ID = "custom";
  private static final int OPAQUE = 0xFF000000;

  public Palette {
    preset = preset < 0 ? CUSTOM : Math.min(preset, IDS.length - 1);
    customFirst |= OPAQUE;
    customSecond |= OPAQUE;
  }

  public static Palette of(int preset) {
    return new Palette(preset, FIRST[0], SECOND[0]);
  }

  public static int count() {
    return IDS.length;
  }

  public static String name(int preset) {
    return Lang.tr(preset == CUSTOM ? Lang.key("Custom") : NAMES[preset]);
  }

  public static int first(int preset) {
    return FIRST[preset];
  }

  public static int second(int preset) {
    return SECOND[preset];
  }

  public int first() {
    return preset == CUSTOM ? customFirst : FIRST[preset];
  }

  public int second() {
    return preset == CUSTOM ? customSecond : SECOND[preset];
  }

  public Palette choose(int next) {
    return new Palette(next, customFirst, customSecond);
  }

  public Palette custom(int first, int second) {
    return new Palette(CUSTOM, first, second);
  }

  public String id() {
    return preset == CUSTOM ? CUSTOM_ID : IDS[preset];
  }

  public static int presetOf(String id) {
    if (CUSTOM_ID.equals(id)) {
      return CUSTOM;
    }
    for (int i = 0; i < IDS.length; i++) {
      if (IDS[i].equals(id)) {
        return i;
      }
    }
    throw new IllegalArgumentException("unknown palette " + id);
  }

  public static int parse(String hex) {
    String digits = hex.startsWith("#") ? hex.substring(1) : hex;
    if (digits.length() != 6) {
      throw new IllegalArgumentException("expected a colour like #8FE3FF");
    }
    try {
      return OPAQUE | Integer.parseInt(digits, 16);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("expected a colour like #8FE3FF");
    }
  }

  public static String hex(int argb) {
    return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
  }
}
