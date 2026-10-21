package me.cxiy.cxvisuals.modules.accounts;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class Machine {
  private static final String SALT = "cxvisuals.accounts.device.v1";

  private Machine() {}

  public static char[] fingerprint() {
    String joined =
        String.join(
            "|",
            SALT,
            System.getProperty("os.name", ""),
            System.getProperty("user.name", ""),
            host());
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(joined.getBytes(StandardCharsets.UTF_8)))
          .toCharArray();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is unavailable", e);
    }
  }

  private static String host() {
    String name = System.getenv("COMPUTERNAME");
    if (name != null && !name.isBlank()) {
      return name;
    }
    name = System.getenv("HOSTNAME");
    if (name != null && !name.isBlank()) {
      return name;
    }
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (java.io.IOException | RuntimeException e) {
      return "";
    }
  }
}
