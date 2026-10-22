package me.cxiy.cxvisuals.modules.friends;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class LinkCode {
  public static final int LENGTH = 12;
  public static final int GROUP = 4;
  private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  private static final int ROUNDS = 60_000;
  private static final byte[] SALT = "cxvisuals/link/key".getBytes(StandardCharsets.UTF_8);
  private static final byte[] BOUND = "cxvisuals/link/1".getBytes(StandardCharsets.UTF_8);
  private static final SecureRandom RANDOM = new SecureRandom();

  private LinkCode() {}

  public static String create() {
    char[] out = new char[LENGTH];
    for (int i = 0; i < LENGTH; i++) {
      out[i] = ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length()));
    }
    return new String(out);
  }

  public static String normalize(String typed) {
    if (typed == null) {
      return "";
    }
    StringBuilder out = new StringBuilder(LENGTH);
    for (int i = 0; i < typed.length(); i++) {
      char c = Character.toUpperCase(typed.charAt(i));
      if (c == '-' || Character.isWhitespace(c)) {
        continue;
      }
      if (ALPHABET.indexOf(c) < 0) {
        return "";
      }
      out.append(c);
    }
    return out.length() == LENGTH ? out.toString() : "";
  }

  public static String pretty(String code) {
    StringBuilder out = new StringBuilder(LENGTH + LENGTH / GROUP);
    for (int i = 0; i < code.length(); i++) {
      if (i > 0 && i % GROUP == 0) {
        out.append('-');
      }
      out.append(code.charAt(i));
    }
    return out.toString();
  }

  public static String id(String code) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of()
          .formatHex(digest.digest(("cxvisuals/link/id|" + code).getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }

  public static byte[] pack(String code, Sealer sealer) {
    return Sealer.lock(key(code), sealer.stored().getBytes(StandardCharsets.UTF_8), BOUND);
  }

  public static Sealer unpack(String code, byte[] blob) {
    byte[] plain = Sealer.unlock(key(code), blob, BOUND);
    return plain == null ? null : Sealer.restore(new String(plain, StandardCharsets.UTF_8));
  }

  private static byte[] key(String code) {
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
          .generateSecret(new PBEKeySpec(code.toCharArray(), SALT, ROUNDS, Sealer.KEY_BYTES * 8))
          .getEncoded();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException(e);
    }
  }
}
