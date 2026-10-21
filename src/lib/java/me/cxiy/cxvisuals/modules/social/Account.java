package me.cxiy.cxvisuals.modules.social;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class Account {
  public enum State {
    SIGNED_OUT,
    CONNECTING,
    ONLINE,
    RETRYING,
    STOPPED
  }

  public enum Failure {
    UNREACHABLE(Lang.key("Can't reach the cloud."), true),
    MOJANG_DOWN(Lang.key("Minecraft services aren't answering."), true),
    BUSY(Lang.key("The cloud is busy."), true),
    NOT_VERIFIED(
        Lang.key("Minecraft couldn't verify this session. Restart the game and try again."), false),
    NO_ACCOUNT(Lang.key("Connecting needs a signed-in Minecraft account."), false);

    public final String text;
    public final boolean transientFault;

    Failure(String text, boolean transientFault) {
      this.text = text;
      this.transientFault = transientFault;
    }
  }

  public record Player(String uuid, String name, int entityId, String detail) {}

  public interface Actions {
    void connect();

    void disconnect();

    void erase();

    void block(String uuid, String name);

    void unblock(String uuid);
  }

  public static final int NO_ENTITY = -1;
  static final long FIRST_RETRY = 5_000_000_000L;
  static final long LAST_RETRY = 60_000_000_000L;
  private static final long SECOND = 1_000_000_000L;
  private static final Actions IDLE =
      new Actions() {
        @Override
        public void connect() {}

        @Override
        public void disconnect() {}

        @Override
        public void erase() {}

        @Override
        public void block(String uuid, String name) {}

        @Override
        public void unblock(String uuid) {}
      };

  private final List<Player> nearby = new ArrayList<>();
  private final List<Player> nearbyView = Collections.unmodifiableList(nearby);
  private final List<Player> blocked = new ArrayList<>();
  private final List<Player> blockedView = Collections.unmodifiableList(blocked);
  private Actions actions = IDLE;
  private State state = State.SIGNED_OUT;
  private Failure failure;
  private String name = "";
  private int entityId = NO_ENTITY;
  private boolean attempting;
  private boolean paused;
  private int failures;
  private long retryAt;
  private long now;

  public void actions(Actions next) {
    actions = next == null ? IDLE : next;
  }

  public Actions actions() {
    return actions;
  }

  public State state() {
    return state;
  }

  public boolean signedIn() {
    return state == State.ONLINE || state == State.RETRYING;
  }

  public boolean paused() {
    return paused;
  }

  public boolean online() {
    return state == State.ONLINE;
  }

  public String name() {
    return name;
  }

  public int entityId() {
    return entityId;
  }

  public List<Player> nearby() {
    return nearbyView;
  }

  public List<Player> blocked() {
    return blockedView;
  }

  public boolean blocks(String uuid) {
    for (int i = 0; i < blocked.size(); i++) {
      if (blocked.get(i).uuid().equals(uuid)) {
        return true;
      }
    }
    return false;
  }

  public void identity(String playerName, int playerEntity) {
    name = playerName == null ? "" : playerName;
    entityId = playerEntity;
  }

  public void clock(long nanos, boolean modulePaused) {
    now = nanos;
    paused = modulePaused;
  }

  public void begin() {
    state = State.CONNECTING;
    failure = null;
    failures = 0;
    attempting = false;
    retryAt = now;
  }

  public boolean due() {
    if (attempting || paused) {
      return false;
    }
    if (state == State.CONNECTING || (state == State.RETRYING && now - retryAt >= 0L)) {
      attempting = true;
      return true;
    }
    return false;
  }

  public void connected() {
    attempting = false;
    state = State.ONLINE;
    failure = null;
    failures = 0;
  }

  public void failed(Failure reason) {
    attempting = false;
    failure = reason;
    if (state == State.SIGNED_OUT) {
      return;
    }
    if (!reason.transientFault) {
      state = State.STOPPED;
      nearby.clear();
      return;
    }
    long wait = Math.min(LAST_RETRY, FIRST_RETRY << Math.min(failures, 4));
    failures++;
    retryAt = now + wait;
    state = State.RETRYING;
  }

  public void expired() {
    if (state == State.ONLINE) {
      state = State.CONNECTING;
      attempting = false;
    }
  }

  public void takeover() {
    nearby.clear();
    blocked.clear();
    if (state != State.SIGNED_OUT) {
      begin();
    }
  }

  public void renewed() {
    if (state == State.STOPPED) {
      begin();
    }
  }

  public void signOut() {
    state = State.SIGNED_OUT;
    failure = null;
    failures = 0;
    attempting = false;
    nearby.clear();
    blocked.clear();
  }

  public void nearby(List<Player> players) {
    nearby.clear();
    nearby.addAll(players);
  }

  public void blocked(List<Player> players) {
    blocked.clear();
    blocked.addAll(players);
  }

  public void block(Player player) {
    if (!blocks(player.uuid())) {
      blocked.add(new Player(player.uuid(), player.name(), NO_ENTITY, ""));
    }
    nearby.removeIf(row -> row.uuid().equals(player.uuid()));
  }

  public void unblock(String uuid) {
    blocked.removeIf(row -> row.uuid().equals(uuid));
  }

  public boolean trouble() {
    return state == State.RETRYING || state == State.STOPPED;
  }

  public String status() {
    return switch (state) {
      case SIGNED_OUT -> Lang.tr("Not connected");
      case CONNECTING -> Lang.tr("Connecting");
      case ONLINE ->
          paused
              ? Lang.tr("Paused")
              : nearby.isEmpty()
                  ? Lang.tr("Connected")
                  : Lang.format("Sharing with {0} nearby", nearby.size());
      case RETRYING ->
          Lang.format("Retrying in {0} s", Math.max(1L, (retryAt - now + SECOND - 1L) / SECOND));
      case STOPPED -> Lang.tr("Couldn't connect");
    };
  }

  public String fault() {
    return trouble() && failure != null ? Lang.tr(failure.text) : "";
  }
}
