package me.cxiy.cxvisuals.modules.donutsmp.auction;

import java.util.Locale;

public final class Host {
  public static final String NAME = "donutsmp.net";

  private Host() {}

  public static boolean donut(String address) {
    return matches(address, NAME);
  }

  public static boolean matches(String address, String allowed) {
    String host = host(address);
    if (host.isEmpty() || allowed == null) {
      return false;
    }
    for (String each : allowed.split("[,;\\s]+")) {
      String name = each.strip().toLowerCase(Locale.ROOT);
      if (!name.isEmpty() && (host.equals(name) || host.endsWith("." + name))) {
        return true;
      }
    }
    return false;
  }

  public static String host(String address) {
    if (address == null) {
      return "";
    }
    String host = address.strip().toLowerCase(Locale.ROOT);
    int scheme = host.indexOf("://");
    if (scheme >= 0) {
      host = host.substring(scheme + 3);
    }
    int slash = host.indexOf('/');
    if (slash >= 0) {
      host = host.substring(0, slash);
    }
    int colon = host.lastIndexOf(':');
    if (colon >= 0 && host.indexOf(']') < colon) {
      host = host.substring(0, colon);
    }
    while (host.endsWith(".")) {
      host = host.substring(0, host.length() - 1);
    }
    return host;
  }
}
