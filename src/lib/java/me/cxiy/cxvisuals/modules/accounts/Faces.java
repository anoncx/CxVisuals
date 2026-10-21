package me.cxiy.cxvisuals.modules.accounts;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BooleanSupplier;

public final class Faces {
  public static final int HEAD_SCALE = 8;
  public static final int FIGURE_SCALE = 8;

  private static final long FRESH_MILLIS = 6L * 60L * 60L * 1000L;
  private static final long RETRY_MILLIS = 5L * 60L * 1000L;
  private static final String SKIN = ".png";
  private static final String META = ".meta";

  public static final class Face {
    private volatile byte[] head;
    private volatile byte[] figure;
    private volatile Profiles.Known known;
    private volatile boolean loading;
    private volatile long attempted;

    public byte[] head() {
      return head;
    }

    public byte[] figure() {
      return figure;
    }

    public Profiles.Known known() {
      return known;
    }

    public boolean loading() {
      return loading;
    }
  }

  private final Path directory;
  private final Profiles profiles;
  private final Executor io;
  private final BooleanSupplier lookups;
  private final Map<String, Face> faces = new ConcurrentHashMap<>();

  public Faces(Path directory, Profiles profiles, Executor io, BooleanSupplier lookups) {
    this.directory = directory;
    this.profiles = profiles;
    this.io = io;
    this.lookups = lookups;
  }

  public static String key(Entry entry) {
    if (entry.kind() != Kind.OFFLINE && entry.uuid.length() == 32) {
      return entry.uuid;
    }
    return "o_" + entry.username.toLowerCase(Locale.ROOT);
  }

  public Face of(Entry entry) {
    String key = key(entry);
    Face face = faces.computeIfAbsent(key, ignored -> new Face());
    long now = System.currentTimeMillis();
    if (!face.loading && now - face.attempted > (face.head == null ? RETRY_MILLIS : FRESH_MILLIS)) {
      face.loading = true;
      face.attempted = now;
      String lookup =
          entry.kind() == Kind.OFFLINE
              ? entry.username
              : entry.uuid.isEmpty() ? entry.username : entry.uuid;
      io.execute(() -> load(key, lookup, face));
    }
    return face;
  }

  public void supply(Entry entry, String skinUrl, boolean slim) {
    String key = key(entry);
    Face face = faces.computeIfAbsent(key, ignored -> new Face());
    io.execute(
        () -> {
          try {
            download(key, skinUrl, slim, face);
          } catch (IOException | RuntimeException ignored) {
            face.attempted = 0L;
          }
        });
  }

  public void forget(Entry entry) {
    String key = key(entry);
    faces.remove(key);
    try {
      Files.deleteIfExists(directory.resolve(key + SKIN));
      Files.deleteIfExists(directory.resolve(key + META));
    } catch (IOException ignored) {
      return;
    }
  }

  private void load(String key, String lookup, Face face) {
    try {
      restore(key, face);
      if (!lookups.getAsBoolean()) {
        return;
      }
      Profiles.Known known = profiles.known(lookup);
      if (known == null) {
        return;
      }
      face.known = known;
      if (known.skin() != null) {
        download(key, known.skin(), known.slim(), face);
      }
    } catch (IOException | RuntimeException ignored) {
      return;
    } finally {
      face.loading = false;
    }
  }

  private void restore(String key, Face face) {
    Path skin = directory.resolve(key + SKIN);
    try {
      if (face.head == null && Files.isRegularFile(skin)) {
        boolean slim =
            Files.isRegularFile(directory.resolve(key + META))
                && Files.readString(directory.resolve(key + META), StandardCharsets.UTF_8)
                    .contains("slim");
        build(Files.readAllBytes(skin), slim, face);
      }
    } catch (IOException | RuntimeException ignored) {
      return;
    }
  }

  private void download(String key, String url, boolean slim, Face face) throws IOException {
    byte[] data = profiles.texture(url);
    build(data, slim, face);
    Files.createDirectories(directory);
    Files.write(directory.resolve(key + SKIN), data);
    Files.writeString(
        directory.resolve(key + META), slim ? "slim" : "wide", StandardCharsets.UTF_8);
  }

  private static void build(byte[] data, boolean slim, Face face) throws IOException {
    byte[] head = SkinImage.head(data, HEAD_SCALE);
    byte[] figure = SkinImage.figure(data, slim, FIGURE_SCALE);
    face.head = head;
    face.figure = figure;
  }
}
