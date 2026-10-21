package me.cxiy.cxvisuals.modules.hud.media;

public final class MediaProtocol {
  public static final char US = 31;

  private static final String SPLIT = String.valueOf(US);
  private static final int FIELDS = 9;
  private static final long JUMP = 1200L;
  private static final double BLEND = 0.25;

  private long lastReported = Long.MIN_VALUE;
  private String error = "";

  public String error() {
    return error;
  }

  public MediaSnapshot accept(String line, MediaSnapshot previous, long nowNanos) {
    if (line.isEmpty()) {
      return previous;
    }
    String[] parts = line.split(SPLIT, -1);
    try {
      return switch (parts[0]) {
        case "T" -> windowsTrack(parts, previous, nowNanos);
        case "P" -> windowsPosition(parts, previous, nowNanos);
        case "M" -> mac(parts, previous, nowNanos);
        case "L" -> playerctl(parts, previous, nowNanos);
        case "D" -> dbus(parts, previous, nowNanos);
        case "N" -> MediaSnapshot.EMPTY;
        case "E" -> {
          error = parts.length > 1 ? parts[1] : "unknown error";
          yield previous;
        }
        default -> previous;
      };
    } catch (NumberFormatException e) {
      return previous;
    }
  }

  private MediaSnapshot windowsTrack(String[] parts, MediaSnapshot previous, long nowNanos) {
    if (parts.length < 7) {
      return previous;
    }
    String title = parts[1].strip();
    String artist = parts[2].strip();
    String album = parts[3].strip();
    if (title.isEmpty() && artist.isEmpty()) {
      return MediaSnapshot.EMPTY;
    }
    long duration = Math.max(0L, Long.parseLong(parts[5].strip()));
    error = "";
    MediaSnapshot next =
        new MediaSnapshot(
            title,
            artist,
            album,
            parts[4].strip(),
            duration,
            0L,
            nowNanos,
            false,
            parts[6].strip());
    if (!next.trackKey().equals(previous.trackKey())) {
      lastReported = Long.MIN_VALUE;
      return next;
    }
    return new MediaSnapshot(
        title,
        artist,
        album,
        next.app(),
        duration > 0L ? duration : previous.durationMs(),
        previous.livePositionMs(nowNanos),
        nowNanos,
        previous.playing(),
        next.art());
  }

  private MediaSnapshot windowsPosition(String[] parts, MediaSnapshot previous, long nowNanos) {
    if (parts.length < 4 || !previous.present()) {
      return previous;
    }
    long reported = Long.parseLong(parts[1].strip());
    long duration = Long.parseLong(parts[3].strip());
    boolean playing = "1".equals(parts[2]);
    return previous.withPosition(
        smooth(previous, reported, playing, true, nowNanos), playing, duration, nowNanos);
  }

  private MediaSnapshot mac(String[] parts, MediaSnapshot previous, long nowNanos) {
    if (parts.length != FIELDS) {
      return previous;
    }
    String app = parts[1].strip();
    double length = decimal(parts[7]);
    long duration = "spotify".equals(app) ? (long) length : (long) (length * 1000.0);
    long position = (long) (decimal(parts[3]) * 1000.0);
    return publish(
        parts[4],
        parts[5],
        parts[6],
        app,
        duration,
        position,
        "playing".equalsIgnoreCase(parts[2].strip()),
        parts[8],
        previous,
        nowNanos);
  }

  private MediaSnapshot playerctl(String[] parts, MediaSnapshot previous, long nowNanos) {
    if (parts.length != FIELDS) {
      return previous;
    }
    return publish(
        parts[5],
        parts[6],
        parts[7],
        parts[1].strip(),
        micros(parts[3]),
        micros(parts[4]),
        "Playing".equalsIgnoreCase(parts[2].strip()),
        parts[8],
        previous,
        nowNanos);
  }

  private MediaSnapshot dbus(String[] parts, MediaSnapshot previous, long nowNanos) {
    if (parts.length != FIELDS) {
      return previous;
    }
    String bus = parts[1].strip();
    return publish(
        parts[4],
        parts[5],
        parts[6],
        bus.substring(bus.lastIndexOf('.') + 1),
        micros(parts[8]),
        micros(parts[3]),
        "Playing".equalsIgnoreCase(parts[2].strip()),
        parts[7],
        previous,
        nowNanos);
  }

  private static double decimal(String value) {
    return value.isBlank() ? 0.0 : Double.parseDouble(value.strip().replace(',', '.'));
  }

  private static long micros(String value) {
    return value.isBlank() ? 0L : Long.parseLong(value.strip()) / 1000L;
  }

  private MediaSnapshot publish(
      String rawTitle,
      String rawArtist,
      String rawAlbum,
      String app,
      long duration,
      long position,
      boolean playing,
      String art,
      MediaSnapshot previous,
      long nowNanos) {
    String title = rawTitle.strip();
    String artist = rawArtist.strip();
    String album = rawAlbum.strip();
    if (title.isEmpty() && artist.isEmpty()) {
      return MediaSnapshot.EMPTY;
    }
    error = "";
    boolean sameTrack =
        previous.title().equals(title)
            && previous.artist().equals(artist)
            && previous.album().equals(album);
    long smoothed = Math.max(0L, smooth(previous, position, playing, sameTrack, nowNanos));
    return new MediaSnapshot(
        title,
        artist,
        album,
        app,
        Math.max(0L, duration),
        smoothed,
        nowNanos,
        playing,
        art.strip());
  }

  private long smooth(
      MediaSnapshot previous, long reported, boolean playing, boolean sameTrack, long nowNanos) {
    long before = lastReported;
    lastReported = reported;
    if (!sameTrack || !previous.present() || !playing || !previous.playing()) {
      return reported;
    }
    long projected = previous.livePositionMs(nowNanos);
    if (reported == before) {
      return projected;
    }
    long drift = reported - projected;
    return Math.abs(drift) > JUMP ? reported : projected + Math.round(drift * BLEND);
  }
}
