package me.cxiy.cxvisuals.core.i18n;

import java.util.Locale;

public enum Language {
  ENGLISH("en", "English", "en_us", Locale.ENGLISH),
  GERMAN("de", "Deutsch", "de_de", Locale.GERMAN),
  RUSSIAN("ru", "Русский", "ru_ru", Locale.forLanguageTag("ru"));

  private static final Language[] ALL = values();

  public final String code;
  public final String nativeName;
  public final String minecraft;
  public final Locale locale;

  Language(String code, String nativeName, String minecraft, Locale locale) {
    this.code = code;
    this.nativeName = nativeName;
    this.minecraft = minecraft;
    this.locale = locale;
  }

  public int form(int count) {
    int n = Math.abs(count);
    if (this != RUSSIAN) {
      return n == 1 ? 0 : 1;
    }
    int ones = n % 10;
    int tens = n % 100;
    if (ones == 1 && tens != 11) {
      return 0;
    }
    return ones >= 2 && ones <= 4 && (tens < 12 || tens > 14) ? 1 : 2;
  }

  public static Language ofMinecraft(String languageCode) {
    if (languageCode != null && languageCode.length() >= 2) {
      String prefix = languageCode.substring(0, 2).toLowerCase(Locale.ROOT);
      for (Language language : ALL) {
        if (language.code.equals(prefix)) {
          return language;
        }
      }
    }
    return ENGLISH;
  }
}
