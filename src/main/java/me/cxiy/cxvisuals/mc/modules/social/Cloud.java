package me.cxiy.cxvisuals.mc.modules.social;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Iterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import me.cxiy.cxvisuals.modules.social.SocialProtocol;

final class Cloud {
  static final class Refused extends IOException {
    final int status;
    final String code;

    Refused(int status, String code) {
      super("cloud answered " + status + " " + code);
      this.status = status;
      this.code = code;
    }

    boolean signedOut() {
      return status == 401;
    }
  }

  private static final String AGENT = "CxVisuals";
  private static final Duration CONNECT = Duration.ofSeconds(8);
  private static final Duration TIMEOUT = Duration.ofSeconds(12);
  private static final Duration TRANSFER = Duration.ofSeconds(45);
  private static final int MAX_REPLY = 4 << 20;

  private final HttpClient http = HttpClient.newBuilder().connectTimeout(CONNECT).build();

  JsonObject post(String url, String token, String json) throws IOException {
    HttpRequest.Builder request =
        request(url, token, TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json));
    return parse(send(request.build()));
  }

  JsonObject get(String url, String token) throws IOException {
    return parse(send(request(url, token, TIMEOUT).GET().build()));
  }

  JsonObject send(String url, String token, byte[] blob) throws IOException {
    return parse(
        send(
            request(url, token, TRANSFER)
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(blob))
                .build()));
  }

  void listen(String url, String token, Consumer<String> events) throws IOException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(url))
            .header("User-Agent", AGENT)
            .header("Accept", "text/event-stream")
            .header("Authorization", "Bearer " + token)
            .GET()
            .build();
    try {
      HttpResponse<Stream<String>> response =
          http.send(request, HttpResponse.BodyHandlers.ofLines());
      try (Stream<String> lines = response.body()) {
        if (response.statusCode() != 200) {
          throw new Refused(response.statusCode(), "");
        }
        Iterator<String> each = lines.iterator();
        while (each.hasNext()) {
          String line = each.next();
          if (line.startsWith("event: ")) {
            events.accept(line.substring(7).strip());
          }
        }
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted");
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }

  private static JsonObject parse(byte[] reply) throws IOException {
    if (reply.length == 0) {
      return new JsonObject();
    }
    try {
      return JsonParser.parseString(new String(reply, StandardCharsets.UTF_8)).getAsJsonObject();
    } catch (RuntimeException e) {
      throw new IOException("unreadable reply");
    }
  }

  void put(String url, String token, String json) throws IOException {
    send(
        request(url, token, TIMEOUT)
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(json))
            .build());
  }

  void upload(String url, String token, byte[] blob) throws IOException {
    send(
        request(url, token, TRANSFER)
            .header("Content-Type", "application/octet-stream")
            .PUT(HttpRequest.BodyPublishers.ofByteArray(blob))
            .build());
  }

  void delete(String url, String token) throws IOException {
    send(request(url, token, TIMEOUT).DELETE().build());
  }

  byte[] download(String url, String token, int limit) throws IOException {
    HttpRequest request = request(url, token, TRANSFER).GET().build();
    try {
      HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) {
        throw refused(response);
      }
      if (response.body().length > limit) {
        throw new IOException("reply too large");
      }
      return response.body();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted");
    }
  }

  private static HttpRequest.Builder request(String url, String token, Duration timeout) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(timeout)
            .header("User-Agent", AGENT)
            .header("Accept", "application/json");
    if (!token.isEmpty()) {
      builder.header("Authorization", "Bearer " + token);
    }
    return builder;
  }

  private byte[] send(HttpRequest request) throws IOException {
    try {
      HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() < 200 || response.statusCode() > 299) {
        throw refused(response);
      }
      if (response.body().length > MAX_REPLY) {
        throw new IOException("reply too large");
      }
      return response.body();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted");
    }
  }

  private static Refused refused(HttpResponse<byte[]> response) {
    String code = "";
    try {
      if (response.body().length <= MAX_REPLY) {
        JsonObject json =
            JsonParser.parseString(new String(response.body(), StandardCharsets.UTF_8))
                .getAsJsonObject();
        code = SocialProtocol.text(json, "error", "");
      }
    } catch (RuntimeException e) {
      code = "";
    }
    return new Refused(response.statusCode(), code);
  }
}
