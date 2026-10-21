package me.cxiy.cxvisuals.modules.texturemixer;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

public final class Modrinth {
  public enum Sort {
    DOWNLOADS("downloads"),
    FOLLOWS("follows"),
    NEWEST("newest"),
    UPDATED("updated");

    final String index;

    Sort(String index) {
      this.index = index;
    }
  }

  public record Hit(
      String id,
      String slug,
      String title,
      String author,
      String summary,
      int downloads,
      String icon) {}

  public record Page(List<Hit> hits, int total) {}

  public static final int PAGE = 24;
  private static final String API = "https://api.modrinth.com/v2";
  private static final String AGENT = "CxVisuals/texture-mixer";
  private static final Duration CONNECT = Duration.ofSeconds(8);
  private static final Duration READ = Duration.ofSeconds(25);
  private static final long ICON_LIMIT = 512L * 1024L;
  private static final long PACK_LIMIT = 512L * 1024L * 1024L;

  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(CONNECT)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  public Page search(String query, Sort sort, int offset) throws IOException {
    String facets = "[[\"project_type:resourcepack\"]]";
    String url =
        API
            + "/search?query="
            + encode(query)
            + "&facets="
            + encode(facets)
            + "&index="
            + (query.isBlank() ? sort.index : "relevance")
            + "&limit="
            + PAGE
            + "&offset="
            + offset;
    JsonObject root = JsonParser.parseString(text(url)).getAsJsonObject();
    List<Hit> hits = new ArrayList<>(PAGE);
    for (JsonElement element : root.getAsJsonArray("hits")) {
      JsonObject hit = element.getAsJsonObject();
      hits.add(
          new Hit(
              string(hit, "project_id"),
              string(hit, "slug"),
              string(hit, "title"),
              string(hit, "author"),
              string(hit, "description"),
              hit.has("downloads") ? hit.get("downloads").getAsInt() : 0,
              string(hit, "icon_url")));
    }
    return new Page(hits, root.has("total_hits") ? root.get("total_hits").getAsInt() : hits.size());
  }

  public byte[] icon(String url) throws IOException {
    return bytes(url, ICON_LIMIT);
  }

  public Path download(Hit hit, String gameVersion, Path folder, IntConsumer progress)
      throws IOException {
    JsonArray versions =
        JsonParser.parseString(text(API + "/project/" + encode(hit.id()) + "/version"))
            .getAsJsonArray();
    JsonObject chosen = null;
    for (JsonElement element : versions) {
      JsonObject version = element.getAsJsonObject();
      if (contains(version.getAsJsonArray("game_versions"), gameVersion)) {
        chosen = version;
        break;
      }
    }
    if (chosen == null && !versions.isEmpty()) {
      chosen = versions.get(0).getAsJsonObject();
    }
    if (chosen == null) {
      throw new IOException("no versions");
    }
    JsonObject file = null;
    for (JsonElement element : chosen.getAsJsonArray("files")) {
      JsonObject candidate = element.getAsJsonObject();
      if (file == null || candidate.has("primary") && candidate.get("primary").getAsBoolean()) {
        file = candidate;
      }
    }
    if (file == null) {
      throw new IOException("no files");
    }
    String name = safe(hit.slug()) + ".zip";
    Files.createDirectories(folder);
    Path target = folder.resolve(name);
    Path part = folder.resolve(name + ".part");
    long expected = file.has("size") ? file.get("size").getAsLong() : 0L;
    HttpRequest request = request(file.get("url").getAsString()).GET().build();
    try {
      HttpResponse<InputStream> response =
          client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() / 100 != 2) {
        response.body().close();
        throw new IOException("HTTP " + response.statusCode());
      }
      try (InputStream in = response.body();
          OutputStream out = Files.newOutputStream(part)) {
        byte[] buffer = new byte[64 * 1024];
        long total = 0L;
        int last = -1;
        for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
          out.write(buffer, 0, read);
          total += read;
          if (total > PACK_LIMIT) {
            throw new IOException("pack too large");
          }
          int percent = expected > 0L ? (int) Math.min(99L, total * 100L / expected) : 50;
          if (percent != last) {
            last = percent;
            progress.accept(percent);
          }
        }
      }
      Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
      progress.accept(100);
      return target;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted", e);
    } finally {
      Files.deleteIfExists(part);
    }
  }

  private static boolean contains(JsonArray array, String value) {
    for (JsonElement element : array) {
      if (element.getAsString().equals(value)) {
        return true;
      }
    }
    return false;
  }

  private static String safe(String name) {
    StringBuilder out = new StringBuilder(name.length());
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      out.append(Character.isLetterOrDigit(c) || c == '-' || c == '_' ? c : '-');
    }
    return out.isEmpty() ? "pack" : out.toString();
  }

  private static String string(JsonObject object, String key) {
    JsonElement element = object.get(key);
    return element == null || element.isJsonNull() ? "" : element.getAsString();
  }

  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private HttpRequest.Builder request(String url) {
    return HttpRequest.newBuilder(URI.create(url)).timeout(READ).header("User-Agent", AGENT);
  }

  private String text(String url) throws IOException {
    try {
      HttpResponse<String> response =
          client.send(
              request(url).GET().build(),
              HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      if (response.statusCode() / 100 != 2) {
        throw new IOException("HTTP " + response.statusCode());
      }
      return response.body();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted", e);
    }
  }

  private byte[] bytes(String url, long limit) throws IOException {
    try {
      HttpResponse<InputStream> response =
          client.send(request(url).GET().build(), HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() / 100 != 2) {
        response.body().close();
        throw new IOException("HTTP " + response.statusCode());
      }
      try (InputStream in = response.body()) {
        byte[] data = in.readNBytes((int) limit + 1);
        if (data.length > limit) {
          throw new IOException("too large");
        }
        return data;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted", e);
    }
  }
}
