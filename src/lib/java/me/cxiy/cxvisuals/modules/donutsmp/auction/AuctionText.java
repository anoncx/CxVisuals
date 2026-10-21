package me.cxiy.cxvisuals.modules.donutsmp.auction;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AuctionText {
  public static final double NONE = -1.0;

  private static final String SYMBOL = "[$]|[^\\p{L}\\p{N}\\s.,:;()\\[\\]+*/%'\"!?<>=-]";
  private static final String NUMBER = "([0-9][0-9,]*(?:[.][0-9]+)?) *([kmbtq])?(?![\\p{L}\\p{N}])";
  private static final Pattern MONEY =
      Pattern.compile("(?:" + SYMBOL + ") *" + NUMBER, Pattern.CASE_INSENSITIVE);
  private static final Pattern AMOUNT = Pattern.compile(NUMBER, Pattern.CASE_INSENSITIVE);
  private static final Pattern BARE =
      Pattern.compile(
          "(?:" + SYMBOL + ") *[0-9][0-9,]*(?:[.][0-9]+)? *[kmbtq]?", Pattern.CASE_INSENSITIVE);
  private static final int SAMPLE_LIMIT = 140;
  private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡхʏᴢ";
  private static final char SECTION = '§';
  private static final String PRICE = "price";
  private static final String SELLER = "seller";
  private static final String[] CONTROLS = {
    "next page", "previous page", "sort", "filter", "search", "refresh"
  };

  private AuctionText() {}

  public static String strip(String raw) {
    StringBuilder out = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char ch = raw.charAt(i);
      if (ch == SECTION && i + 1 < raw.length()) {
        i++;
        continue;
      }
      int small = SMALL_CAPS.indexOf(ch);
      out.append(small >= 0 ? (char) ('a' + small) : ch);
    }
    return out.toString().trim();
  }

  public static String plain(String raw) {
    return strip(raw).toLowerCase(Locale.ROOT);
  }

  public static double money(String text) {
    return value(MONEY.matcher(text));
  }

  public static double amount(String text) {
    return value(AMOUNT.matcher(text));
  }

  private static double value(Matcher match) {
    if (!match.find()) {
      return NONE;
    }
    double value;
    try {
      value = Double.parseDouble(match.group(1).replace(",", ""));
    } catch (NumberFormatException e) {
      return NONE;
    }
    String suffix = match.group(2);
    if (suffix == null) {
      return value;
    }
    return switch (Character.toLowerCase(suffix.charAt(0))) {
      case 'k' -> value * 1.0e3;
      case 'm' -> value * 1.0e6;
      case 'b' -> value * 1.0e9;
      case 't' -> value * 1.0e12;
      default -> value * 1.0e15;
    };
  }

  public static boolean control(String name) {
    String plain = plain(name);
    for (String control : CONTROLS) {
      if (plain.equals(control)) {
        return true;
      }
    }
    return false;
  }

  public static Listing read(String id, int count, List<String> lore, int slot) {
    double price = NONE;
    String seller = "";
    for (String raw : lore) {
      String line = strip(raw);
      String lower = line.toLowerCase(Locale.ROOT);
      if (lower.startsWith(PRICE)) {
        int colon = line.indexOf(':');
        double parsed =
            amount(colon < 0 ? line.substring(PRICE.length()) : line.substring(colon + 1));
        price = parsed >= 0.0 ? parsed : price;
      } else if (lower.startsWith(SELLER)) {
        int colon = line.indexOf(':');
        seller = colon < 0 ? "" : line.substring(colon + 1).trim();
      } else if (price < 0.0 && BARE.matcher(line).matches()) {
        price = money(line);
      }
    }
    return price <= 0.0 ? null : new Listing(id, count, price, seller, slot);
  }

  public static String sample(List<String> lore) {
    StringBuilder out = new StringBuilder();
    for (String raw : lore) {
      String line = strip(raw);
      if (line.isEmpty()) {
        continue;
      }
      if (out.length() > 0) {
        out.append(" | ");
      }
      for (int i = 0; i < line.length() && out.length() < SAMPLE_LIMIT; i++) {
        char ch = line.charAt(i);
        if (ch < 127) {
          out.append(ch);
        } else {
          out.append(String.format("\\u%04X", (int) ch));
        }
      }
      if (out.length() >= SAMPLE_LIMIT) {
        break;
      }
    }
    return out.toString();
  }
}
