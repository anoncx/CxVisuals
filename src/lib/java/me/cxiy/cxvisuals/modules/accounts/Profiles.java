package me.cxiy.cxvisuals.modules.accounts;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class Profiles {
  public static final String SERVICES = "https://api.minecraftservices.com/minecraft/profile";

  private static final String LOOKUP = "https://api.ashcon.app/mojang/v2/user/";
  private static final String TEXTURE_HOST = "textures.minecraft.net";
  private static final long TEXTURE_LIMIT = 256L * 1024L;

  public record Texture(String url, boolean slim, boolean active) {}

  public record Owned(String uuid, String username, List<Texture> skins, List<Texture> capes) {
    public Texture skin() {
      for (Texture texture : skins) {
        if (texture.active()) {
          return texture;
        }
      }
      return skins.isEmpty() ? null : skins.get(0);
    }
  }

  public record Name(String name, long changedAt) {}

  public record Known(
      String uuid,
      String username,
      long created,
      List<Name> names,
      String skin,
      boolean slim,
      String cape) {}

  private final Http http;

  public Profiles(Http http) {
    this.http = http;
  }

  public Owned owned(String token) throws IOException {
    if (token == null || token.isBlank()) {
      return null;
    }
    Http.Reply reply = http.get(SERVICES, token);
    if (!reply.ok()) {
      return null;
    }
    return parseOwned(reply.body());
  }

  public Known known(String lookup) throws IOException {
    if (lookup == null || lookup.isBlank()) {
      return null;
    }
    Http.Reply reply =
        http.get(LOOKUP + URLEncoder.encode(lookup.trim(), StandardCharsets.UTF_8), null);
    return reply.ok() ? parseKnown(reply.body()) : null;
  }

  public byte[] texture(String url) throws IOException {
    URI uri = secure(url);
    if (uri == null) {
      throw new IOException("Untrusted texture host");
    }
    return http.bytes(uri.toString(), TEXTURE_LIMIT);
  }

  public static URI secure(String url) {
    if (url == null) {
      return null;
    }
    try {
      URI uri = URI.create(url.trim());
      if (uri.getHost() == null || !TEXTURE_HOST.equalsIgnoreCase(uri.getHost())) {
        return null;
      }
      return new URI("https", uri.getHost(), uri.getPath(), null);
    } catch (java.net.URISyntaxException | IllegalArgumentException e) {
      return null;
    }
  }

  static Owned parseOwned(String body) {
    try {
      JsonObject json = JsonParser.parseString(body).getAsJsonObject();
      String id = text(json, "id");
      String name = text(json, "name");
      if (id == null || name == null) {
        return null;
      }
      return new Owned(
          Ids.plain(id), name, textures(json.get("skins")), textures(json.get("capes")));
    } catch (RuntimeException e) {
      return null;
    }
  }

  static Known parseKnown(String body) {
    try {
      JsonObject json = JsonParser.parseString(body).getAsJsonObject();
      if (json.has("reason")) {
        return null;
      }
      String uuid = text(json, "uuid");
      String name = text(json, "username");
      if (uuid == null || name == null) {
        return null;
      }
      List<Name> names = new ArrayList<>();
      JsonElement history = json.get("username_history");
      if (history != null && history.isJsonArray()) {
        for (JsonElement element : history.getAsJsonArray()) {
          if (element.isJsonObject()) {
            JsonObject entry = element.getAsJsonObject();
            String old = text(entry, "username");
            if (old != null) {
              names.add(new Name(old, time(text(entry, "changed_at"))));
            }
          }
        }
      }
      String skin = null;
      String cape = null;
      boolean slim = false;
      JsonElement textures = json.get("textures");
      if (textures != null && textures.isJsonObject()) {
        JsonObject block = textures.getAsJsonObject();
        slim = flag(block, "slim");
        skin = nested(block, "skin");
        cape = nested(block, "cape");
      }
      return new Known(
          Ids.plain(uuid),
          name,
          time(text(json, "created_at")),
          List.copyOf(names),
          skin,
          slim,
          cape);
    } catch (RuntimeException e) {
      return null;
    }
  }

  private static List<Texture> textures(JsonElement element) {
    List<Texture> found = new ArrayList<>();
    if (element == null || !element.isJsonArray()) {
      return found;
    }
    JsonArray array = element.getAsJsonArray();
    for (JsonElement item : array) {
      if (!item.isJsonObject()) {
        continue;
      }
      JsonObject texture = item.getAsJsonObject();
      String url = text(texture, "url");
      if (url != null) {
        found.add(
            new Texture(
                url,
                "SLIM".equalsIgnoreCase(text(texture, "variant")),
                "ACTIVE".equalsIgnoreCase(text(texture, "state"))));
      }
    }
    return found;
  }

  private static String nested(JsonObject block, String key) {
    JsonElement element = block.get(key);
    return element != null && element.isJsonObject()
        ? text(element.getAsJsonObject(), "url")
        : null;
  }

  private static String text(JsonObject json, String key) {
    JsonElement element = json.get(key);
    return element != null && element.isJsonPrimitive() ? element.getAsString() : null;
  }

  private static boolean flag(JsonObject json, String key) {
    JsonElement element = json.get(key);
    try {
      return element != null && element.isJsonPrimitive() && element.getAsBoolean();
    } catch (RuntimeException e) {
      return false;
    }
  }

  private static long time(String value) {
    if (value == null || value.isBlank()) {
      return 0L;
    }
    try {
      return Instant.parse(value.trim()).toEpochMilli();
    } catch (RuntimeException e) {
      return 0L;
    }
  }
}
