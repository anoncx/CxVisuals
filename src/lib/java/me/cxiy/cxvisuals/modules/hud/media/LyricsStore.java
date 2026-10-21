package me.cxiy.cxvisuals.modules.hud.media;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.Function;

public final class LyricsStore {
  private static final int MAX_ENTRIES = 24;
  private static final String TAG = "[cx:";

  private final Path directory;
  private final Function<MediaSnapshot, String> fetcher;
  private final Executor worker;
  private final Map<String, Lyrics> cache = new ConcurrentHashMap<>();
  private final Set<String> offline = ConcurrentHashMap.newKeySet();

  public LyricsStore(Path directory, Function<MediaSnapshot, String> fetcher, Executor worker) {
    this.directory = directory;
    this.fetcher = fetcher;
    this.worker = worker;
  }

  public Lyrics get(MediaSnapshot snapshot, boolean online) {
    if (!snapshot.present()) {
      return Lyrics.MISSING;
    }
    String key = snapshot.trackKey();
    Lyrics cached = cache.get(key);
    if (cached != null && !(online && cached == Lyrics.MISSING && offline.remove(key))) {
      return cached;
    }
    if (cache.size() >= MAX_ENTRIES) {
      cache.clear();
      offline.clear();
    }
    cache.put(key, Lyrics.PENDING);
    worker.execute(
        () -> {
          Lyrics resolved = load(snapshot, key, online);
          if (resolved == Lyrics.MISSING && !online) {
            offline.add(key);
          }
          cache.put(key, resolved);
        });
    return Lyrics.PENDING;
  }

  private static long tagged(String stored) {
    int end = stored.indexOf(']');
    if (!stored.startsWith(TAG) || end < 0) {
      return -1L;
    }
    try {
      return Long.parseLong(stored.substring(TAG.length(), end));
    } catch (NumberFormatException e) {
      return -1L;
    }
  }

  private Lyrics load(MediaSnapshot snapshot, String key, boolean online) {
    try {
      Path file = directory.resolve(Integer.toHexString(key.hashCode()) + ".lrc");
      long duration = snapshot.durationMs();
      if (Files.isRegularFile(file)) {
        String stored = Files.readString(file, StandardCharsets.UTF_8);
        long tagged = tagged(stored);
        boolean fits =
            tagged == 0L
                || duration <= 0L
                || (tagged > 0L && Math.abs(tagged - duration) <= LyricsMatch.TOLERANCE_MS);
        if (!stored.isBlank() && (tagged < 0L ? !online : fits)) {
          return LrcParser.parse(stored);
        }
      }
      if (!online) {
        return Lyrics.MISSING;
      }
      String raw = fetcher.apply(snapshot);
      if (raw == null || raw.isBlank()) {
        return Lyrics.MISSING;
      }
      Lyrics parsed = LrcParser.parse(raw);
      if (parsed.state() == Lyrics.State.FOUND) {
        try {
          Files.createDirectories(directory);
          Files.writeString(
              file, TAG + Math.max(0L, duration) + "]\n" + raw, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) {
          return parsed;
        }
      }
      return parsed;
    } catch (IOException | RuntimeException e) {
      return Lyrics.MISSING;
    }
  }
}
