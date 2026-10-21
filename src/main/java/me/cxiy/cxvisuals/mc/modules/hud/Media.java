package me.cxiy.cxvisuals.mc.modules.hud;

import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudModule;
import me.cxiy.cxvisuals.modules.hud.media.Cover;
import me.cxiy.cxvisuals.modules.hud.media.Lyrics;
import me.cxiy.cxvisuals.modules.hud.media.LyricsStore;
import me.cxiy.cxvisuals.modules.hud.media.MediaBridge;
import me.cxiy.cxvisuals.modules.hud.media.MediaCommand;
import me.cxiy.cxvisuals.modules.hud.media.MediaHub;
import me.cxiy.cxvisuals.modules.hud.media.MediaNet;
import me.cxiy.cxvisuals.modules.hud.media.MediaSnapshot;

public final class Media {
  private static final String LYRICS_FOLDER = "lyrics";
  private static final String HELPER_FOLDER = "media";

  private static MediaHub hub;
  private static int user;

  private Media() {}

  public static void init(Path folder) {
    ExecutorService worker =
        Executors.newSingleThreadExecutor(
            task -> {
              Thread thread = new Thread(task, "CxVisuals media worker");
              thread.setDaemon(true);
              return thread;
            });
    Path helpers = folder.resolve(HELPER_FOLDER);
    hub =
        new MediaHub(
            new MediaBridge(() -> MediaBridge.platformCommand(helpers), CxVisualsClient.LOG::warn),
            new Cover(MediaNet::bytes, MediaNet::coverUrl, worker),
            new LyricsStore(folder.resolve(LYRICS_FOLDER), MediaNet::lyrics, worker));
    user = hub.user();
  }

  public static MediaHub hub() {
    return hub;
  }

  public static void sink(BiConsumer<MediaCommand, Long> target) {
    hub.sink(target);
  }

  public static void inject(MediaSnapshot snapshot, byte[] png, Lyrics text) {
    hub.inject(snapshot, png, text);
  }

  static void frame(HudModule module, HudData data, boolean pointer) {
    data.pointer = pointer;
    if (hub == null) {
      return;
    }
    boolean card = module.media.shown.get();
    boolean words = module.lyrics.shown.get();
    hub.want(user, card || words, card, words);
    hub.poll(module.online.get());
    data.mediaNanos = hub.nanos();
    data.media = hub.snapshot();
    data.cover = hub.cover();
    data.lyrics = hub.lyrics();
  }

  static void rest(HudData data) {
    if (hub != null) {
      hub.want(user, false, false, false);
      hub.poll();
    }
    data.media = MediaSnapshot.EMPTY;
    data.cover = null;
    data.lyrics = Lyrics.MISSING;
  }

  static void send(MediaCommand command, long argument) {
    if (hub != null) {
      hub.send(command, argument);
    }
  }

  public static void shutdown() {
    if (hub != null) {
      hub.shutdown();
    }
  }
}
