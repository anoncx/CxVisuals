package me.cxiy.cxvisuals.modules.hud.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MediaNet {
  private static final String AGENT = "CxVisuals (https://github.com/anoncx/CxVisuals)";
  private static final String LRCLIB = "https://lrclib.net/api/";
  private static final String DEEZER = "https://api.deezer.com/search?limit=12&q=";
  private static final Duration CONNECT = Duration.ofSeconds(6);
  private static final Duration REQUEST = Duration.ofSeconds(10);
  private static final long DURATION_SLACK = 30L;
  private static final int MAX_BYTES = 16 * 1024 * 1024;
  private static final String[] COVERS = {"cover_big", "cover_medium", "cover_xl"};

  private static HttpClient client;

  private MediaNet() {}

  private static synchronized HttpClient client() {
    if (client == null) {
      client =
          HttpClient.newBuilder()
              .connectTimeout(CONNECT)
              .followRedirects(HttpClient.Redirect.NORMAL)
              .build();
    }
    return client;
  }

  public static String lyrics(MediaSnapshot snapshot) {
    String title = snapshot.title();
    String artist = snapshot.artist();
    String plain = LyricsMatch.cleanTitle(title);
    String lead = LyricsMatch.primaryArtist(artist);
    boolean altered = !plain.equals(title) || !lead.equals(artist);
    long duration = snapshot.durationMs();
    String found = duration > 0L ? exact(title, artist, snapshot.album(), duration) : null;
    if (found == null && altered && duration > 0L) {
      found = exact(plain, lead, snapshot.album(), duration);
    }
    if (found == null) {
      found = search(title, artist, duration);
    }
    if (found == null && altered) {
      found = search(plain, lead, duration);
    }
    return found;
  }

  private static String exact(String title, String artist, String album, long duration) {
    JsonElement row =
        json(
            LRCLIB
                + "get?track_name="
                + encode(title)
                + "&artist_name="
                + encode(artist)
                + "&album_name="
                + encode(album)
                + "&duration="
                + duration / 1000L);
    return row != null && row.isJsonObject()
        ? LyricsMatch.pick(List.of(hit(row.getAsJsonObject())), duration)
        : null;
  }

  private static String search(String title, String artist, long duration) {
    JsonElement rows =
        json(LRCLIB + "search?track_name=" + encode(title) + "&artist_name=" + encode(artist));
    if (rows == null || !rows.isJsonArray()) {
      return null;
    }
    List<LyricsMatch.Hit> hits = new ArrayList<>();
    for (JsonElement row : rows.getAsJsonArray()) {
      if (row.isJsonObject()) {
        hits.add(hit(row.getAsJsonObject()));
      }
    }
    return LyricsMatch.pick(hits, duration);
  }

  private static LyricsMatch.Hit hit(JsonObject row) {
    double seconds =
        row.has("duration") && row.get("duration").isJsonPrimitive()
            ? row.get("duration").getAsDouble()
            : 0.0;
    return new LyricsMatch.Hit(seconds, text(row, "syncedLyrics"));
  }

  public static String coverUrl(MediaSnapshot snapshot) {
    if (snapshot.title().isEmpty() || snapshot.artist().isEmpty()) {
      return null;
    }
    String title = LyricsMatch.cleanTitle(snapshot.title());
    String artist = LyricsMatch.primaryArtist(snapshot.artist());
    JsonElement found = json(DEEZER + encode(artist + " " + title));
    if (found == null || !found.isJsonObject() || !found.getAsJsonObject().has("data")) {
      return null;
    }
    long seconds = snapshot.durationMs() / 1000L;
    JsonArray rows = found.getAsJsonObject().getAsJsonArray("data");
    for (JsonElement element : rows) {
      if (!element.isJsonObject()) {
        continue;
      }
      JsonObject row = element.getAsJsonObject();
      JsonObject by = object(row, "artist");
      JsonObject album = object(row, "album");
      if (album == null || !matches(row, "title", title) || !matches(by, "name", artist)) {
        continue;
      }
      long length =
          row.has("duration") && row.get("duration").isJsonPrimitive()
              ? row.get("duration").getAsLong()
              : 0L;
      if (seconds > 0L && length > 0L && Math.abs(length - seconds) > DURATION_SLACK) {
        continue;
      }
      for (String field : COVERS) {
        String url = text(album, field);
        if (!url.isBlank()) {
          return url;
        }
      }
    }
    return null;
  }

  public static byte[] bytes(String source) {
    if (source == null || source.isEmpty()) {
      return null;
    }
    try {
      if (source.startsWith("http://") || source.startsWith("https://")) {
        HttpRequest request =
            HttpRequest.newBuilder(URI.create(source))
                .timeout(REQUEST)
                .header("User-Agent", AGENT)
                .GET()
                .build();
        HttpResponse<InputStream> response =
            client().send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
          if (response.statusCode() != 200) {
            return null;
          }
          byte[] read = body.readNBytes(MAX_BYTES + 1);
          return read.length > MAX_BYTES ? null : read;
        }
      }
      Path file = source.startsWith("file:") ? Path.of(URI.create(source)) : Path.of(source);
      return Files.isRegularFile(file) && Files.size(file) <= MAX_BYTES
          ? Files.readAllBytes(file)
          : null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  private static JsonElement json(String url) {
    try {
      HttpRequest request =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(REQUEST)
              .header("User-Agent", AGENT)
              .header("Accept", "application/json")
              .GET()
              .build();
      HttpResponse<String> response = client().send(request, HttpResponse.BodyHandlers.ofString());
      return response.statusCode() == 200 ? JsonParser.parseString(response.body()) : null;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  private static JsonObject object(JsonObject row, String field) {
    return row.has(field) && row.get(field).isJsonObject() ? row.getAsJsonObject(field) : null;
  }

  private static String text(JsonObject row, String field) {
    return row != null && row.has(field) && row.get(field).isJsonPrimitive()
        ? row.get(field).getAsString()
        : "";
  }

  private static boolean matches(JsonObject row, String field, String wanted) {
    String candidate = normalize(text(row, field));
    String expected = normalize(wanted);
    return !candidate.isEmpty()
        && !expected.isEmpty()
        && (candidate.contains(expected) || expected.contains(candidate));
  }

  private static String normalize(String value) {
    StringBuilder out = new StringBuilder(value.length());
    boolean gap = false;
    String lower = value.toLowerCase(Locale.ROOT);
    for (int i = 0; i < lower.length(); i++) {
      char ch = lower.charAt(i);
      if (Character.isLetterOrDigit(ch)) {
        if (gap && out.length() > 0) {
          out.append(' ');
        }
        gap = false;
        out.append(ch);
      } else {
        gap = true;
      }
    }
    return out.toString();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
