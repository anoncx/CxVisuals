package me.cxiy.cxvisuals.modules.donutsmp.auction;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

public final class VariantKey {
  private VariantKey() {}

  public static String of(String id, Variant variant) {
    if (id == null || id.isEmpty() || variant == null || variant.plain()) {
      return id == null ? "" : id;
    }
    List<String> parts = new ArrayList<>(6);
    if (!variant.enchants().isEmpty()) {
      parts.add("e=" + enchants(variant.enchants()));
    }
    if (!variant.potion().isEmpty()) {
      parts.add("p=" + variant.potion());
    }
    if (!variant.effects().isEmpty()) {
      parts.add("x=" + effects(variant.effects()));
    }
    if (!variant.contents().isEmpty()) {
      parts.add("c=" + hash(contents(variant.contents())));
    }
    if (!variant.name().isEmpty()) {
      parts.add("n=" + hash(variant.name()));
    }
    if (variant.damage() > 0) {
      parts.add("d=" + variant.damage());
    }
    if (!variant.trim().isEmpty()) {
      parts.add("t=" + hash(variant.trim()));
    }
    String suffix = String.join("&", parts);
    if (suffix.length() > 512) {
      return id + "~h=" + hash(suffix);
    }
    return id + "~" + suffix;
  }

  private static String enchants(List<Variant.Enchant> values) {
    List<Variant.Enchant> sorted = new ArrayList<>(values);
    sorted.sort(Comparator.comparing(Variant.Enchant::id).thenComparingInt(Variant.Enchant::level));
    List<String> parts = new ArrayList<>(sorted.size());
    for (Variant.Enchant enchant : sorted) {
      parts.add(enchant.id() + ":" + enchant.level());
    }
    return String.join("+", parts);
  }

  private static String effects(List<Variant.Effect> values) {
    List<Variant.Effect> sorted = new ArrayList<>(values);
    sorted.sort(
        Comparator.comparing(Variant.Effect::id)
            .thenComparingInt(Variant.Effect::level)
            .thenComparingInt(Variant.Effect::duration));
    List<String> parts = new ArrayList<>(sorted.size());
    for (Variant.Effect effect : sorted) {
      parts.add(effect.id() + ":" + effect.level() + ":" + effect.duration());
    }
    return String.join("+", parts);
  }

  private static String contents(List<Variant.Content> values) {
    List<Variant.Content> sorted = new ArrayList<>(values);
    sorted.sort(
        Comparator.comparing(Variant.Content::key)
            .thenComparing(Variant.Content::id)
            .thenComparingInt(Variant.Content::count));
    StringBuilder out = new StringBuilder();
    for (Variant.Content content : sorted) {
      out.append(content.id())
          .append('x')
          .append(Math.max(1, content.count()))
          .append(':')
          .append(content.key())
          .append(':')
          .append(enchants(content.enchants()))
          .append(':')
          .append(content.name())
          .append('|');
    }
    return out.toString();
  }

  private static String hash(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-1");
      byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(bytes).substring(0, 16);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 is not available", e);
    }
  }
}
