package me.cxiy.cxvisuals.modules.hud.media;

public record MediaSnapshot(
    String title,
    String artist,
    String album,
    String app,
    long durationMs,
    long positionMs,
    long positionAtNanos,
    boolean playing,
    String art) {
  public static final String ART_PENDING = "*";
  public static final MediaSnapshot EMPTY =
      new MediaSnapshot("", "", "", "", 0L, 0L, 0L, false, "");

  public boolean present() {
    return !title.isEmpty() || !artist.isEmpty();
  }

  public String trackKey() {
    return title + '\u0000' + artist + '\u0000' + album;
  }

  public long livePositionMs(long nowNanos) {
    if (!playing) {
      return positionMs;
    }
    long live = positionMs + Math.max(0L, (nowNanos - positionAtNanos) / 1_000_000L);
    return durationMs > 0L ? Math.min(live, durationMs) : live;
  }

  public float progress(long nowNanos) {
    if (durationMs <= 0L) {
      return 0.0F;
    }
    return Math.max(0.0F, Math.min(1.0F, livePositionMs(nowNanos) / (float) durationMs));
  }

  public MediaSnapshot withPosition(
      long position, boolean nowPlaying, long duration, long nowNanos) {
    return new MediaSnapshot(
        title,
        artist,
        album,
        app,
        duration > 0L ? duration : durationMs,
        Math.max(0L, position),
        nowNanos,
        nowPlaying,
        art);
  }
}
