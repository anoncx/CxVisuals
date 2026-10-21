package me.cxiy.cxvisuals.modules.accounts;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class Ids {
  private static final int MIN_NAME = 3;
  private static final int MAX_NAME = 16;

  private Ids() {}

  public static String plain(String uuid) {
    return uuid == null ? "" : uuid.replace("-", "").trim().toLowerCase(Locale.ROOT);
  }

  public static String dashed(String uuid) {
    String plain = plain(uuid);
    if (plain.length() != 32) {
      return plain;
    }
    return plain.substring(0, 8)
        + "-"
        + plain.substring(8, 12)
        + "-"
        + plain.substring(12, 16)
        + "-"
        + plain.substring(16, 20)
        + "-"
        + plain.substring(20);
  }

  public static UUID parse(String uuid) {
    String plain = plain(uuid);
    if (plain.length() != 32) {
      return null;
    }
    try {
      return new UUID(
          Long.parseUnsignedLong(plain.substring(0, 16), 16),
          Long.parseUnsignedLong(plain.substring(16), 16));
    } catch (NumberFormatException e) {
      return null;
    }
  }

  public static String offline(String name) {
    UUID id = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    return plain(id.toString());
  }

  public static boolean validName(String name) {
    if (name == null || name.length() < MIN_NAME || name.length() > MAX_NAME) {
      return false;
    }
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      boolean ok = c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9' || c == '_';
      if (!ok) {
        return false;
      }
    }
    return true;
  }

  public static int minName() {
    return MIN_NAME;
  }

  public static int maxName() {
    return MAX_NAME;
  }
}
