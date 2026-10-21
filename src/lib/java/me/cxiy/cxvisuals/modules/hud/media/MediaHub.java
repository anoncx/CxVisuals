package me.cxiy.cxvisuals.modules.hud.media;

import java.util.function.BiConsumer;

public final class MediaHub {
  private static final int USERS = 8;
  private static final long RETRY_NANOS = 250_000_000L;

  private final MediaBridge bridge;
  private final Cover covers;
  private final LyricsStore store;
  private final boolean[] media = new boolean[USERS];
  private final boolean[] art = new boolean[USERS];
  private final boolean[] words = new boolean[USERS];
  private int users;
  private boolean held;
  private boolean lookups;
  private boolean wantedArt;
  private boolean wantedWords;
  private MediaSnapshot last = MediaSnapshot.EMPTY;
  private MediaSnapshot snapshot = MediaSnapshot.EMPTY;
  private byte[] cover;
  private Lyrics lyrics = Lyrics.MISSING;
  private long nanos;
  private long asked;
  private MediaSnapshot injected;
  private byte[] injectedCover;
  private Lyrics injectedLyrics;
  private BiConsumer<MediaCommand, Long> sink;

  public MediaHub(MediaBridge bridge, Cover covers, LyricsStore store) {
    this.bridge = bridge;
    this.covers = covers;
    this.store = store;
  }

  public int user() {
    return Math.min(users++, USERS - 1);
  }

  public void want(int user, boolean wantsMedia, boolean wantsCover, boolean wantsLyrics) {
    media[user] = wantsMedia;
    art[user] = wantsMedia && wantsCover;
    words[user] = wantsMedia && wantsLyrics;
  }

  public void sink(BiConsumer<MediaCommand, Long> target) {
    sink = target;
  }

  public void inject(MediaSnapshot fake, byte[] png, Lyrics text) {
    injected = fake;
    injectedCover = png;
    injectedLyrics = text;
  }

  public void poll() {
    poll(lookups);
  }

  public void poll(boolean online) {
    boolean wanted = any(media);
    boolean wantsArt = any(art);
    boolean wantsWords = any(words);
    nanos = System.nanoTime();
    if (!wanted) {
      idle();
      return;
    }
    if (injected != null) {
      snapshot = injected;
      cover = wantsArt ? injectedCover : null;
      lyrics = wantsWords && injectedLyrics != null ? injectedLyrics : Lyrics.MISSING;
      return;
    }
    if (!held) {
      held = true;
      bridge.acquire();
    }
    MediaSnapshot now = bridge.snapshot();
    boolean waiting =
        (lyrics == Lyrics.PENDING || (wantsArt && cover == null)) && nanos - asked > RETRY_NANOS;
    if (now != last
        || online != lookups
        || wantsArt != wantedArt
        || wantsWords != wantedWords
        || waiting) {
      asked = nanos;
      last = now;
      lookups = online;
      wantedArt = wantsArt;
      wantedWords = wantsWords;
      cover = now.present() && wantsArt ? covers.get(now, online) : null;
      lyrics = now.present() && wantsWords ? store.get(now, online) : Lyrics.MISSING;
    }
    snapshot = now;
  }

  private static boolean any(boolean[] flags) {
    for (int i = 0; i < USERS; i++) {
      if (flags[i]) {
        return true;
      }
    }
    return false;
  }

  private void idle() {
    if (held) {
      held = false;
      bridge.release();
    }
    last = MediaSnapshot.EMPTY;
    snapshot = MediaSnapshot.EMPTY;
    cover = null;
    lyrics = Lyrics.MISSING;
  }

  public void rest() {
    for (int i = 0; i < USERS; i++) {
      media[i] = false;
      art[i] = false;
      words[i] = false;
    }
    idle();
  }

  public MediaSnapshot snapshot() {
    return snapshot;
  }

  public long nanos() {
    return nanos;
  }

  public byte[] cover() {
    return cover;
  }

  public Lyrics lyrics() {
    return lyrics;
  }

  public void send(MediaCommand command, long argument) {
    if (sink != null) {
      sink.accept(command, argument);
    } else if (held) {
      bridge.send(command, argument);
    }
  }

  public void shutdown() {
    bridge.shutdown();
    held = false;
  }
}
