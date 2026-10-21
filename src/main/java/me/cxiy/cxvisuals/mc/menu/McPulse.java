package me.cxiy.cxvisuals.mc.menu;

import java.time.LocalDate;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.mc.CxVisuals;
import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import me.cxiy.cxvisuals.mc.modules.hud.Media;
import me.cxiy.cxvisuals.menu.model.Playtime;
import me.cxiy.cxvisuals.menu.model.Pulse;
import me.cxiy.cxvisuals.menu.model.QuickPlay;
import me.cxiy.cxvisuals.modules.friends.Friends;
import me.cxiy.cxvisuals.modules.hud.HudModule;
import me.cxiy.cxvisuals.modules.hud.media.Lyrics;
import me.cxiy.cxvisuals.modules.hud.media.MediaHub;
import me.cxiy.cxvisuals.modules.hud.media.MediaSnapshot;

final class McPulse implements Pulse {
  private Friends friends;
  private static final String[] SWITCHES = {
    Lang.key("Sounds"), Lang.key("Cursor"), Lang.key("Lyrics")
  };
  private static final double RESTORED = 50.0;
  private static final long MINUTE = 60_000L;

  private QuickPlay quick;
  private Playtime playtime;
  private double volume = RESTORED;
  private long day;
  private long dayChecked = Long.MIN_VALUE;
  private int user = -1;
  private boolean wanting;

  void friends(Friends source) {
    friends = source;
  }

  void sources(QuickPlay recent, Playtime time) {
    quick = recent;
    playtime = time;
  }

  @Override
  public int friendsOnline() {
    int online = 0;
    if (friends != null) {
      for (Friends.Friend friend : friends.friends()) {
        online += friend.online ? 1 : 0;
      }
    }
    return online;
  }

  @Override
  public String track() {
    MediaHub hub = Media.hub();
    return hub == null ? "" : hub.snapshot().title();
  }

  @Override
  public String artist() {
    MediaHub hub = Media.hub();
    return hub == null ? "" : hub.snapshot().artist();
  }

  @Override
  public String lyric() {
    MediaHub hub = Media.hub();
    HudModule module = Hud.module();
    if (hub == null || module == null || !CxVisuals.registry().client().menuLyrics.get()) {
      rest();
      return "";
    }
    if (user < 0) {
      user = hub.user();
    }
    wanting = true;
    hub.want(user, true, false, true);
    hub.poll(module.online.get());
    MediaSnapshot snapshot = hub.snapshot();
    Lyrics words = hub.lyrics();
    if (!snapshot.playing() || words.state() != Lyrics.State.FOUND) {
      return "";
    }
    int line =
        words.indexAt(
            snapshot.livePositionMs(hub.nanos()) + module.lyrics.offset.get().longValue());
    return line < 0 ? "" : words.line(line).strip();
  }

  @Override
  public void rest() {
    MediaHub hub = Media.hub();
    if (wanting && hub != null) {
      hub.want(user, false, false, false);
    }
    wanting = false;
  }

  private boolean hasWorld() {
    try {
      return quick != null && quick.count(QuickPlay.WORLDS) > 0;
    } catch (RuntimeException e) {
      return false;
    }
  }

  @Override
  public String resumeTitle() {
    return hasWorld() ? quick.item(QuickPlay.WORLDS, 0).title : "";
  }

  @Override
  public String resumeNote() {
    return hasWorld() ? quick.item(QuickPlay.WORLDS, 0).first.plain : "";
  }

  @Override
  public void resume() {
    if (hasWorld()) {
      quick.open(QuickPlay.WORLDS, 0);
    }
  }

  @Override
  public long playSeconds(int daysAgo) {
    long stamp = System.currentTimeMillis() / MINUTE;
    if (stamp != dayChecked) {
      dayChecked = stamp;
      day = LocalDate.now().toEpochDay();
    }
    return playtime == null ? 0L : playtime.seconds(day - daysAgo);
  }

  @Override
  public int switches() {
    return SWITCHES.length;
  }

  @Override
  public String switchName(int index) {
    return SWITCHES[index];
  }

  @Override
  public boolean switchOn(int index) {
    ClientSettings client = CxVisuals.registry().client();
    return switch (index) {
      case 0 -> client.menuVolume.get() > 0.0;
      case 1 -> client.wiiCursor.get();
      default -> client.menuLyrics.get();
    };
  }

  @Override
  public void flip(int index) {
    ClientSettings client = CxVisuals.registry().client();
    switch (index) {
      case 0 -> {
        double now = client.menuVolume.get();
        if (now > 0.0) {
          volume = now;
        }
        client.menuVolume.set(now > 0.0 ? 0.0 : volume);
      }
      case 1 -> client.wiiCursor.set(!client.wiiCursor.get());
      default -> client.menuLyrics.set(!client.menuLyrics.get());
    }
  }
}
