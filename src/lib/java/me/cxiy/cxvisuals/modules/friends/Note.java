package me.cxiy.cxvisuals.modules.friends;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.modules.social.SocialProtocol;

public record Note(
    Kind kind,
    String text,
    String parcel,
    String key,
    String hash,
    int x,
    int y,
    int z,
    String place) {
  public enum Kind {
    TEXT,
    WAVE,
    INVITE,
    MODEL,
    CONFIG,
    WAYPOINT
  }

  public static final int TEXT_LIMIT = 500;
  public static final int NAME_LIMIT = 40;
  public static final int SERVER_LIMIT = 64;
  public static final int PLACE_LIMIT = 48;
  public static final int WORLD_EDGE = 30_000_000;
  public static final Note UNREADABLE =
      new Note(
          Kind.TEXT,
          Lang.key("Earlier messages here were locked with a different key and can't be read."),
          "",
          "",
          "",
          0,
          0,
          0,
          "");
  private static final Kind[] KINDS = Kind.values();

  private static Note plain(Kind kind, String text) {
    return new Note(kind, text, "", "", "", 0, 0, 0, "");
  }

  public static Note text(String text) {
    return plain(Kind.TEXT, clean(text, TEXT_LIMIT));
  }

  public static Note wave() {
    return plain(Kind.WAVE, "");
  }

  public static Note invite(String server) {
    return plain(Kind.INVITE, clean(server, SERVER_LIMIT));
  }

  public static Note model(String name, String parcel, String key, String hash) {
    return new Note(Kind.MODEL, clean(name, NAME_LIMIT), parcel, key, hash, 0, 0, 0, "");
  }

  public static Note model(
      String name,
      String parcel,
      String key,
      String hash,
      String mini,
      String miniHash,
      int triangles,
      int clips,
      int kilobytes) {
    boolean previewed = mini.matches("[0-9a-f]{32}") && miniHash.matches("[0-9a-f]{64}");
    return new Note(
        Kind.MODEL,
        clean(name, NAME_LIMIT),
        parcel,
        key,
        hash,
        Math.max(0, triangles),
        Math.max(0, clips),
        Math.max(0, kilobytes),
        previewed ? mini + ":" + miniHash : "");
  }

  public String mini() {
    return kind == Kind.MODEL && place.length() == 97 ? place.substring(0, 32) : "";
  }

  public String miniHash() {
    return kind == Kind.MODEL && place.length() == 97 ? place.substring(33) : "";
  }

  public String facts() {
    if (kind != Kind.MODEL || (x == 0 && z == 0)) {
      return "";
    }
    StringBuilder out = new StringBuilder(32);
    if (x > 0) {
      out.append(x < 1000 ? Integer.toString(x) : x / 1000 + "." + x % 1000 / 100 + "k")
          .append(' ')
          .append(Lang.tr("tris"));
    }
    if (y > 0) {
      out.append(out.isEmpty() ? "" : " · ")
          .append(y)
          .append(' ')
          .append(Lang.count(y, "clip", "clips"));
    }
    if (z > 0) {
      out.append(out.isEmpty() ? "" : " · ")
          .append(z < 1024 ? z + " KB" : z / 1024 + "." + z % 1024 * 10 / 1024 + " MB");
    }
    return out.toString();
  }

  public static Note config(String name, String parcel, String key, String hash) {
    return new Note(Kind.CONFIG, clean(name, NAME_LIMIT), parcel, key, hash, 0, 0, 0, "");
  }

  public static Note waypoint(String name, int x, int y, int z, String dimension) {
    return spot(name, x, y, z, dimension, "", false);
  }

  public static Note spot(
      String name, int x, int y, int z, String dimension, String server, boolean live) {
    return new Note(
        Kind.WAYPOINT,
        clean(name, NAME_LIMIT),
        clean(server, SERVER_LIMIT),
        live ? "1" : "",
        "",
        x,
        y,
        z,
        clean(dimension, PLACE_LIMIT));
  }

  public String server() {
    return kind == Kind.WAYPOINT ? parcel : "";
  }

  public boolean live() {
    return kind == Kind.WAYPOINT && !key.isEmpty();
  }

  public static String dimensionName(String place) {
    String path = place.substring(place.indexOf(':') + 1);
    return switch (path) {
      case "" -> Lang.tr("Unknown");
      case "overworld" -> Lang.tr("Overworld");
      case "the_nether" -> Lang.tr("Nether");
      case "the_end" -> Lang.tr("The End");
      default -> {
        StringBuilder out = new StringBuilder(path.length());
        boolean upper = true;
        for (int i = 0; i < path.length(); i++) {
          char c = path.charAt(i) == '_' ? ' ' : path.charAt(i);
          out.append(upper ? Character.toUpperCase(c) : c);
          upper = c == ' ';
        }
        yield out.toString();
      }
    };
  }

  public static int dimensionColor(String place) {
    return place.endsWith("the_nether")
        ? 0xFFD71921
        : place.endsWith("the_end") ? 0xFFB388FF : 0xFF30D158;
  }

  public boolean sealed() {
    return kind == Kind.MODEL || kind == Kind.CONFIG;
  }

  public String coordinates() {
    return x + " " + y + " " + z;
  }

  public String preview() {
    return switch (kind) {
      case TEXT -> text;
      case WAVE -> Lang.tr("Waved");
      case INVITE -> Lang.format("Invite to {0}", text);
      case MODEL -> Lang.tr("Avatar") + " · " + text;
      case CONFIG -> Lang.tr("Config") + " · " + text;
      case WAYPOINT -> Lang.tr("Waypoint") + " · " + text;
    };
  }

  public byte[] encode() {
    JsonObject json = new JsonObject();
    json.addProperty("k", kind.ordinal());
    if (!text.isEmpty()) {
      json.addProperty("t", text);
    }
    if (sealed()) {
      json.addProperty("p", parcel);
      json.addProperty("s", key);
      json.addProperty("h", hash);
    }
    if (kind == Kind.MODEL && (x > 0 || z > 0 || !place.isEmpty())) {
      json.addProperty("tr", x);
      json.addProperty("cl", y);
      json.addProperty("kb", z);
      if (!place.isEmpty()) {
        json.addProperty("mp", mini());
        json.addProperty("mh", miniHash());
      }
    }
    if (kind == Kind.WAYPOINT) {
      json.addProperty("x", x);
      json.addProperty("y", y);
      json.addProperty("z", z);
      json.addProperty("d", place);
      if (!parcel.isEmpty()) {
        json.addProperty("sv", parcel);
      }
      if (!key.isEmpty()) {
        json.addProperty("lv", true);
      }
    }
    return json.toString().getBytes(StandardCharsets.UTF_8);
  }

  public static Note decode(byte[] plain) {
    if (plain == null) {
      return null;
    }
    try {
      JsonObject json =
          JsonParser.parseString(new String(plain, StandardCharsets.UTF_8)).getAsJsonObject();
      long ordinal = SocialProtocol.number(json, "k", -1L);
      if (ordinal < 0L || ordinal >= KINDS.length) {
        return null;
      }
      Kind kind = KINDS[(int) ordinal];
      String text = SocialProtocol.text(json, "t", "");
      return switch (kind) {
        case TEXT -> text.isBlank() ? null : text(text);
        case WAVE -> wave();
        case INVITE -> clean(text, SERVER_LIMIT).isEmpty() ? null : invite(text);
        case MODEL, CONFIG -> {
          String parcel = SocialProtocol.text(json, "p", "");
          String hash = SocialProtocol.lower(SocialProtocol.text(json, "h", ""));
          String key = SocialProtocol.text(json, "s", "");
          boolean valid =
              parcel.matches("[0-9a-f]{32}")
                  && hash.length() == 64
                  && SocialProtocol.validHash(hash)
                  && key.length() <= 64;
          yield !valid
              ? null
              : kind == Kind.CONFIG
                  ? config(text, parcel, key, hash)
                  : model(
                      text,
                      parcel,
                      key,
                      hash,
                      SocialProtocol.text(json, "mp", ""),
                      SocialProtocol.lower(SocialProtocol.text(json, "mh", "")),
                      (int) Math.min(10_000_000L, SocialProtocol.number(json, "tr", 0L)),
                      (int) Math.min(1000L, SocialProtocol.number(json, "cl", 0L)),
                      (int) Math.min(1L << 20, SocialProtocol.number(json, "kb", 0L)));
        }
        case WAYPOINT -> {
          long x = SocialProtocol.number(json, "x", Long.MAX_VALUE);
          long y = SocialProtocol.number(json, "y", Long.MAX_VALUE);
          long z = SocialProtocol.number(json, "z", Long.MAX_VALUE);
          boolean valid =
              Math.abs(x) <= WORLD_EDGE && Math.abs(y) <= WORLD_EDGE && Math.abs(z) <= WORLD_EDGE;
          yield valid
              ? spot(
                  text.isBlank() ? Lang.tr("Waypoint") : text,
                  (int) x,
                  (int) y,
                  (int) z,
                  SocialProtocol.text(json, "d", ""),
                  SocialProtocol.text(json, "sv", ""),
                  SocialProtocol.flag(json, "lv", false))
              : null;
        }
      };
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static String clean(String value, int limit) {
    if (value == null) {
      return "";
    }
    StringBuilder out = new StringBuilder(Math.min(value.length(), limit));
    for (int i = 0; i < value.length() && out.length() < limit; i++) {
      char c = value.charAt(i);
      out.append(c < 0x20 || c == 0x7F ? ' ' : c);
    }
    return out.toString().strip();
  }
}
