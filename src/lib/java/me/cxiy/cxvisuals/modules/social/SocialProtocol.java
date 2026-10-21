package me.cxiy.cxvisuals.modules.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.cxiy.cxvisuals.modules.avatar.anim.ClipMap;
import me.cxiy.cxvisuals.modules.avatar.anim.ClipSet;
import me.cxiy.cxvisuals.modules.avatar.bake.Tune;

public final class SocialProtocol {
  public static final int TRACK_LIMIT = 120;
  public static final int NAME_LIMIT = 16;
  public static final int ROW_LIMIT = 28;

  private SocialProtocol() {}

  public static String normalizeBase(String value) {
    if (value == null) {
      return "";
    }
    String base = value.trim();
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    String low = base.toLowerCase(Locale.ROOT);
    if (low.startsWith("https://")
        || low.startsWith("http://localhost")
        || low.startsWith("http://127.0.0.1")) {
      return base;
    }
    return "";
  }

  public static boolean validUuid(String value) {
    if (value == null || value.length() != 36) {
      return false;
    }
    for (int i = 0; i < 36; i++) {
      char c = value.charAt(i);
      boolean dash = i == 8 || i == 13 || i == 18 || i == 23;
      if (dash ? c != '-' : !hex(c)) {
        return false;
      }
    }
    return true;
  }

  public static boolean validHash(String value) {
    if (value == null || value.isEmpty()) {
      return true;
    }
    if (value.length() != 64) {
      return false;
    }
    for (int i = 0; i < 64; i++) {
      if (!hex(value.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  public static String tuneUp(String value) {
    return "Z".equalsIgnoreCase(value) ? "Z" : "Y";
  }

  public static int clampTurn(long value) {
    if (value < 0L || value > 3L) {
      return 0;
    }
    return (int) value;
  }

  public static double clampSize(double value) {
    if (!(value >= 25.0) || !(value <= 400.0)) {
      return 100.0;
    }
    return value;
  }

  public static double clampLift(double value) {
    if (!(value >= -1.0) || !(value <= 1.0)) {
      return 0.0;
    }
    return value;
  }

  public static String trackLine(String title, String artist, int limit) {
    String head = clip(title == null ? "" : title.trim(), limit);
    String by = clip(artist == null ? "" : artist.trim(), limit);
    if (head.isEmpty()) {
      return by;
    }
    if (by.isEmpty()) {
      return head;
    }
    String line = head + " — " + by;
    if (line.length() <= limit) {
      return line;
    }
    return line.substring(0, Math.max(0, limit - 1)).trim() + "…";
  }

  public static String clip(String value, int limit) {
    if (value == null) {
      return "";
    }
    String clean = value.replace('\u0000', ' ').trim();
    if (clean.length() <= limit) {
      return clean;
    }
    return clean.substring(0, limit);
  }

  public static String lower(String value) {
    return value == null ? "" : value.toLowerCase(Locale.ROOT);
  }

  public static String text(JsonObject json, String key, String fallback) {
    if (json.has(key)
        && json.get(key).isJsonPrimitive()
        && json.get(key).getAsJsonPrimitive().isString()) {
      return json.get(key).getAsString();
    }
    return fallback;
  }

  public static JsonObject object(JsonObject json, String key, JsonObject fallback) {
    JsonElement element = json.get(key);
    return element != null && element.isJsonObject() ? element.getAsJsonObject() : fallback;
  }

  public static final int CLIP_NAMES = 16;
  public static final int CLIP_NAME_LIMIT = 48;
  public static final int CLIP_MAP_LIMIT = 2048;

  public static JsonObject clipMap(ClipMap map) {
    if (map == null || map.allAuto()) {
      return null;
    }
    JsonObject out = new JsonObject();
    for (int event = 0; event < ClipSet.EVENTS; event++) {
      if (map.isAuto(event)) {
        continue;
      }
      String[] chosen = map.names(event);
      JsonArray names = new JsonArray(chosen.length);
      for (int i = 0; i < chosen.length && i < CLIP_NAMES; i++) {
        names.add(clip(chosen[i], CLIP_NAME_LIMIT));
      }
      JsonObject choice = new JsonObject();
      choice.add("n", names);
      if (map.random(event)) {
        choice.addProperty("r", true);
      }
      out.add(Integer.toString(event), choice);
    }
    return out.toString().length() > CLIP_MAP_LIMIT ? null : out;
  }

  public static ClipMap clipMap(JsonObject json) {
    ClipMap map = ClipMap.AUTO;
    if (json == null) {
      return map;
    }
    for (var entry : json.entrySet()) {
      int event;
      try {
        event = Integer.parseInt(entry.getKey());
      } catch (NumberFormatException e) {
        continue;
      }
      if (event < 0 || event >= ClipSet.EVENTS || !entry.getValue().isJsonObject()) {
        continue;
      }
      JsonObject choice = entry.getValue().getAsJsonObject();
      JsonElement listed = choice.get("n");
      if (listed == null || !listed.isJsonArray()) {
        continue;
      }
      List<String> names = new ArrayList<>();
      for (JsonElement element : listed.getAsJsonArray()) {
        if (names.size() >= CLIP_NAMES) {
          break;
        }
        if (element.isJsonPrimitive()
            && element.getAsJsonPrimitive().isString()
            && !element.getAsString().isBlank()) {
          names.add(clip(element.getAsString(), CLIP_NAME_LIMIT));
        }
      }
      map = map.with(event, names.toArray(new String[0]), flag(choice, "r", false));
    }
    return map;
  }

  public static String syncBody(
      String avatarHash,
      Tune tune,
      ClipMap clips,
      String title,
      String artist,
      boolean playing,
      List<String> nearby,
      String mode,
      String status,
      String server,
      JsonObject spot) {
    JsonObject body = new JsonObject();
    body.addProperty("avatar", avatarHash);
    JsonObject shape = new JsonObject();
    shape.addProperty("up", tune.up().name());
    shape.addProperty("turn", tune.turn());
    shape.addProperty("size", tune.size());
    shape.addProperty("lift", tune.lift());
    shape.addProperty("clips", tune.clips());
    JsonObject mapping = clipMap(clips);
    if (mapping != null) {
      shape.add("map", mapping);
    }
    body.add("tune", shape);
    if (!title.isEmpty() || !artist.isEmpty()) {
      JsonObject track = new JsonObject();
      track.addProperty("title", clip(title, TRACK_LIMIT));
      track.addProperty("artist", clip(artist, TRACK_LIMIT));
      track.addProperty("playing", playing);
      body.add("track", track);
    }
    JsonArray around = new JsonArray(nearby.size());
    for (int i = 0; i < nearby.size(); i++) {
      around.add(nearby.get(i));
    }
    body.add("nearby", around);
    body.addProperty("mode", mode);
    if (!status.isEmpty()) {
      body.addProperty("status", status);
    }
    if (!server.isEmpty()) {
      body.addProperty("server", server);
    }
    if (spot != null) {
      body.add("spot", spot);
    }
    return body.toString();
  }

  public static long number(JsonObject json, String key, long fallback) {
    try {
      if (json.has(key)
          && json.get(key).isJsonPrimitive()
          && json.get(key).getAsJsonPrimitive().isNumber()) {
        return json.get(key).getAsLong();
      }
    } catch (ArithmeticException | NumberFormatException e) {
      return fallback;
    }
    return fallback;
  }

  public static double decimal(JsonObject json, String key, double fallback) {
    try {
      if (json.has(key)
          && json.get(key).isJsonPrimitive()
          && json.get(key).getAsJsonPrimitive().isNumber()) {
        return json.get(key).getAsDouble();
      }
    } catch (ArithmeticException | NumberFormatException e) {
      return fallback;
    }
    return fallback;
  }

  public static boolean flag(JsonObject json, String key, boolean fallback) {
    JsonElement element = json.get(key);
    if (element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isBoolean()) {
      return element.getAsBoolean();
    }
    return fallback;
  }

  public static String sha256Hex(byte[] blob) {
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(blob);
      StringBuilder out = new StringBuilder(64);
      for (byte b : digest) {
        out.append(Character.forDigit((b >> 4) & 0xF, 16));
        out.append(Character.forDigit(b & 0xF, 16));
      }
      return out.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  public static byte[] utf8(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }

  private static boolean hex(char c) {
    return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
  }
}
