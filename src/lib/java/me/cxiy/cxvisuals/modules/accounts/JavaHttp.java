package me.cxiy.cxvisuals.modules.accounts;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

public final class JavaHttp implements Http {
  private static final String AGENT = "CxVisuals";
  private static final Duration CONNECT = Duration.ofSeconds(10);
  private static final Duration READ = Duration.ofSeconds(20);

  private final HttpClient client =
      HttpClient.newBuilder()
          .connectTimeout(CONNECT)
          .followRedirects(HttpClient.Redirect.NORMAL)
          .build();

  @Override
  public Reply postForm(String url, Map<String, String> form) throws IOException {
    StringBuilder encoded = new StringBuilder();
    for (Map.Entry<String, String> entry : form.entrySet()) {
      if (!encoded.isEmpty()) {
        encoded.append('&');
      }
      encoded
          .append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
    }
    return send(
        request(url, "application/x-www-form-urlencoded", null)
            .POST(HttpRequest.BodyPublishers.ofString(encoded.toString())));
  }

  @Override
  public Reply postJson(String url, String body, String bearer) throws IOException {
    return send(
        request(url, "application/json", bearer).POST(HttpRequest.BodyPublishers.ofString(body)));
  }

  @Override
  public Reply get(String url, String bearer) throws IOException {
    return send(request(url, null, bearer).GET());
  }

  @Override
  public byte[] bytes(String url, long limit) throws IOException {
    HttpRequest request = request(url, null, null).GET().build();
    try {
      HttpResponse<InputStream> response =
          client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      if (response.statusCode() / 100 != 2) {
        response.body().close();
        throw new IOException("HTTP " + response.statusCode());
      }
      try (InputStream in = response.body()) {
        byte[] data = in.readNBytes((int) limit + 1);
        if (data.length > limit) {
          throw new IOException("Response too large");
        }
        return data;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted", e);
    }
  }

  private HttpRequest.Builder request(String url, String contentType, String bearer) {
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(url))
            .timeout(READ)
            .header("Accept", "application/json")
            .header("User-Agent", AGENT);
    if (contentType != null) {
      builder.header("Content-Type", contentType);
    }
    if (bearer != null) {
      builder.header("Authorization", "Bearer " + bearer);
    }
    return builder;
  }

  private Reply send(HttpRequest.Builder builder) throws IOException {
    try {
      HttpResponse<String> response =
          client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      return new Reply(response.statusCode(), response.body());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IOException("Interrupted", e);
    }
  }
}
