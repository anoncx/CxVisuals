package me.cxiy.cxvisuals.modules.friends;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class Friends {
  public static final class Friend {
    public final String uuid;
    public String name;
    public String key;
    public String live = "";
    public boolean online;
    public boolean busy;
    public String status = "";
    public String server = "";
    public String track = "";
    public boolean located;
    public double x;
    public double y;
    public double z;
    public String dimension = "";
    public int entityId = NO_ENTITY;
    public int unread;
    public boolean favorite;
    public long lastAt;
    public String lastText = "";

    Friend(String uuid, String name, String key) {
      this.uuid = uuid;
      this.name = name;
      this.key = key;
    }

    public String line() {
      if (!online) {
        return lastText.isEmpty() ? Lang.tr("Offline") : lastText;
      }
      if (!server.isEmpty()) {
        return Lang.format("On {0}", server);
      }
      if (!track.isEmpty()) {
        return "♪ " + track;
      }
      if (!status.isEmpty()) {
        return status;
      }
      return Lang.tr(busy ? "Do not disturb" : "Online");
    }
  }

  public record Person(String uuid, String name) {}

  public record Line(long id, boolean mine, Note note, long at) {}

  public static final int NO_ITEM = -1;

  public record Toast(String uuid, String title, String text, int entityId, String tag, int item) {
    public Toast(String uuid, String title, String text, int entityId, String tag) {
      this(uuid, title, text, entityId, tag, NO_ITEM);
    }

    public Toast(String uuid, String title, String text) {
      this(uuid, title, text, NO_ENTITY, "", NO_ITEM);
    }
  }

  public record Item(String id, String name, int handle) {}

  public enum Share {
    AVATAR,
    CONFIG,
    WAYPOINT
  }

  public static final class Pending {
    public final long token;
    public final Note note;
    public boolean failed;

    Pending(long token, Note note) {
      this.token = token;
      this.note = note;
    }
  }

  public enum Parcel {
    IDLE,
    WORKING,
    WORN,
    KEPT,
    FAILED
  }

  public interface Actions {
    void add(String name);

    void accept(String uuid);

    void remove(String uuid);

    void send(String uuid, Note note);

    List<Item> items(Share kind);

    void share(String uuid, Share kind, String id);

    void invite(String uuid);

    void join(String server);

    void shareSpot(String uuid);

    void config(Line line, boolean apply);

    void waypoint(Line line);

    default void map(Line line) {}

    default void room(String from, Line line, boolean shareMine) {}

    int preview(Line line);

    void tryOn(Line line);

    void keep(Line line);

    void forget(String uuid);
  }

  public static final int NO_ENTITY = -1;
  public static final int PREVIEW_NONE = -1;
  public static final int PREVIEW_LOADING = -2;
  public static final int MAX_LINES = 300;
  public static final int MAX_TOASTS = 4;
  private static final Actions IDLE =
      new Actions() {
        @Override
        public void add(String name) {}

        @Override
        public void accept(String uuid) {}

        @Override
        public void remove(String uuid) {}

        @Override
        public void send(String uuid, Note note) {}

        @Override
        public List<Item> items(Share kind) {
          return List.of();
        }

        @Override
        public void share(String uuid, Share kind, String id) {}

        @Override
        public void invite(String uuid) {}

        @Override
        public void join(String server) {}

        @Override
        public void shareSpot(String uuid) {}

        @Override
        public void config(Line line, boolean apply) {}

        @Override
        public void waypoint(Line line) {}

        @Override
        public int preview(Line line) {
          return PREVIEW_NONE;
        }

        @Override
        public void tryOn(Line line) {}

        @Override
        public void keep(Line line) {}

        @Override
        public void forget(String uuid) {}
      };

  private final List<Friend> friends = new ArrayList<>();
  private final List<Friend> friendsView = Collections.unmodifiableList(friends);
  private final List<Person> incoming = new ArrayList<>();
  private final List<Person> incomingView = Collections.unmodifiableList(incoming);
  private final List<Person> outgoing = new ArrayList<>();
  private final List<Person> outgoingView = Collections.unmodifiableList(outgoing);
  private final Map<String, List<Line>> chats = new HashMap<>();
  private final Map<String, Long> read = new HashMap<>();
  private final Map<String, List<Pending>> pending = new HashMap<>();
  private long tokens;
  private final Map<Long, Parcel> parcels = new HashMap<>();
  private final Set<String> favorites = new HashSet<>();
  private final ArrayDeque<Toast> toasts = new ArrayDeque<>();
  private Actions actions = IDLE;
  private String open = "";
  private String notice = "";
  private boolean watching;
  private boolean here;
  private double hereX;
  private double hereY;
  private double hereZ;
  private String hereDimension = "";
  private String hereServer = "";
  private boolean glanced;
  private boolean quiet;
  private boolean met;
  private boolean counted;
  private boolean locked;
  private int revision;

  public void actions(Actions next) {
    actions = next == null ? IDLE : next;
  }

  public Actions actions() {
    return actions;
  }

  public List<Friend> friends() {
    return friendsView;
  }

  public List<Person> incoming() {
    return incomingView;
  }

  public List<Person> outgoing() {
    return outgoingView;
  }

  public int revision() {
    return revision;
  }

  public boolean locked() {
    return locked;
  }

  public void locked(boolean on) {
    if (locked != on) {
      locked = on;
      revision++;
    }
  }

  public void live(String uuid, String account) {
    Friend friend = friend(uuid);
    if (friend != null) {
      friend.live = account == null ? "" : account;
    }
  }

  public Map<String, Long> marks() {
    return Collections.unmodifiableMap(read);
  }

  public void readFrom(Map<String, Long> remote) {
    for (Map.Entry<String, Long> entry : remote.entrySet()) {
      long mark = entry.getValue();
      if (mark <= read.getOrDefault(entry.getKey(), 0L)) {
        continue;
      }
      read.put(entry.getKey(), mark);
      Friend friend = friend(entry.getKey());
      List<Line> lines = chats.get(entry.getKey());
      if (friend != null && lines != null) {
        int unread = 0;
        for (int i = 0; i < lines.size(); i++) {
          if (!lines.get(i).mine() && lines.get(i).id() > mark) {
            unread++;
          }
        }
        friend.unread = unread;
      }
      revision++;
    }
  }

  public String notice() {
    return notice;
  }

  public void notice(String text) {
    notice = text == null ? "" : text;
    revision++;
  }

  public Friend friend(String uuid) {
    for (int i = 0; i < friends.size(); i++) {
      if (friends.get(i).uuid.equals(uuid)) {
        return friends.get(i);
      }
    }
    return null;
  }

  public void here(boolean present, double x, double y, double z, String dimension, String server) {
    here = present;
    hereX = x;
    hereY = y;
    hereZ = z;
    hereDimension = dimension == null ? "" : dimension;
    hereServer = server == null ? "" : server;
  }

  public static final int ELSEWHERE = -1;
  public static final int OTHER_DIMENSION = -2;
  public static final int OTHER_SERVER = -3;

  public int metresTo(Note spot) {
    if (!here) {
      return ELSEWHERE;
    }
    if (!spot.server().isEmpty()
        && !hereServer.isEmpty()
        && !spot.server().equalsIgnoreCase(hereServer)) {
      return OTHER_SERVER;
    }
    if (!spot.place().equals(hereDimension)) {
      return OTHER_DIMENSION;
    }
    double dx = spot.x() + 0.5 - hereX;
    double dy = spot.y() - hereY;
    double dz = spot.z() + 0.5 - hereZ;
    return (int) Math.round(Math.sqrt(dx * dx + dy * dy + dz * dz));
  }

  public Friend current() {
    return friend(open);
  }

  public void open(String uuid) {
    open = uuid == null ? "" : uuid;
    markRead();
    revision++;
  }

  public void watching(boolean viewing) {
    watching = viewing;
    glanced = viewing;
    if (viewing) {
      markRead();
    }
  }

  public void settle() {
    watching = glanced;
    glanced = false;
  }

  public void quiet(boolean muted) {
    quiet = muted;
  }

  private void markRead() {
    Friend friend = current();
    if (friend == null || !watching) {
      return;
    }
    friend.unread = 0;
    List<Line> lines = chats.get(friend.uuid);
    if (lines != null && !lines.isEmpty()) {
      read.put(friend.uuid, lines.get(lines.size() - 1).id());
    }
  }

  public int unread() {
    int total = incoming.size();
    for (int i = 0; i < friends.size(); i++) {
      total += friends.get(i).unread;
    }
    return total;
  }

  public List<Line> lines(String uuid) {
    List<Line> lines = chats.get(uuid);
    return lines == null ? List.of() : lines;
  }

  public void circle(List<Friend> next, List<Person> requests, List<Person> pending) {
    Set<String> known = new HashSet<>();
    for (int i = 0; i < incoming.size(); i++) {
      known.add(incoming.get(i).uuid());
    }
    List<Friend> merged = new ArrayList<>(next.size());
    for (Friend fresh : next) {
      Friend old = friend(fresh.uuid);
      if (old == null) {
        fresh.favorite = favorites.contains(fresh.uuid);
        merged.add(fresh);
      } else {
        old.name = fresh.name;
        old.key = fresh.key;
        merged.add(old);
      }
    }
    friends.clear();
    friends.addAll(merged);
    incoming.clear();
    incoming.addAll(requests);
    outgoing.clear();
    outgoing.addAll(pending);
    chats.keySet().removeIf(uuid -> friend(uuid) == null);
    this.pending.keySet().removeIf(uuid -> friend(uuid) == null);
    if (friend(open) == null) {
      open = "";
    }
    for (Person person : requests) {
      if (met && !known.contains(person.uuid())) {
        toast(
            new Toast(
                "", person.name(), Lang.tr("Wants to be friends"), NO_ENTITY, Lang.tr("REQUEST")));
      }
    }
    met = true;
    sort();
  }

  public static Friend person(String uuid, String name, String key) {
    return new Friend(uuid, name, key);
  }

  public void presence(String uuid, boolean busy, String status, String server, String track) {
    Friend friend = friend(uuid);
    if (friend == null) {
      return;
    }
    if (!friend.online && counted) {
      toast(
          new Toast(uuid, friend.name, Lang.tr("Came online"), friend.entityId, Lang.tr("ONLINE")));
    }
    friend.online = true;
    friend.busy = busy;
    friend.status = status;
    friend.server = server;
    friend.track = track;
  }

  public void spot(String uuid, boolean located, double x, double y, double z, String dimension) {
    Friend friend = friend(uuid);
    if (friend == null) {
      return;
    }
    friend.located = located;
    friend.x = x;
    friend.y = y;
    friend.z = z;
    friend.dimension = dimension == null ? "" : dimension;
  }

  public void presenceDone(Set<String> seen) {
    for (int i = 0; i < friends.size(); i++) {
      Friend friend = friends.get(i);
      if (!seen.contains(friend.uuid)) {
        friend.online = false;
        friend.busy = false;
        friend.server = "";
        friend.track = "";
        friend.status = "";
        friend.entityId = NO_ENTITY;
        friend.live = "";
        friend.located = false;
      }
    }
    counted = true;
    sort();
  }

  public void offline() {
    presenceDone(Set.of());
  }

  public long pend(String uuid, Note note) {
    if (friend(uuid) == null) {
      return 0L;
    }
    long token = ++tokens;
    pending.computeIfAbsent(uuid, ignored -> new ArrayList<>()).add(new Pending(token, note));
    revision++;
    return token;
  }

  public void fail(String uuid, long token) {
    for (Pending waiting : pending(uuid)) {
      if (waiting.token == token) {
        waiting.failed = true;
        revision++;
      }
    }
  }

  public void dismiss(String uuid, long token) {
    List<Pending> waiting = pending.get(uuid);
    if (waiting != null && waiting.removeIf(item -> item.token == token)) {
      revision++;
    }
  }

  public List<Pending> pending(String uuid) {
    List<Pending> waiting = pending.get(uuid);
    return waiting == null ? List.of() : waiting;
  }

  private void settle(String uuid, Note arrived) {
    List<Pending> waiting = pending.get(uuid);
    if (waiting == null) {
      return;
    }
    for (int i = 0; i < waiting.size(); i++) {
      Pending item = waiting.get(i);
      if (!item.failed
          && item.note.kind() == arrived.kind()
          && item.note.text().equals(arrived.text())) {
        waiting.remove(i);
        return;
      }
    }
  }

  public boolean receive(String uuid, Line line) {
    Friend friend = friend(uuid);
    if (friend == null) {
      return false;
    }
    List<Line> lines = chats.computeIfAbsent(uuid, ignored -> new ArrayList<>());
    if (!lines.isEmpty() && lines.get(lines.size() - 1).id() >= line.id()) {
      return false;
    }
    lines.add(line);
    if (line.mine()) {
      settle(uuid, line.note());
    }
    if (lines.size() > MAX_LINES) {
      parcels.remove(lines.remove(0).id());
    }
    friend.lastAt = line.at();
    friend.lastText =
        line.mine() ? Lang.format("You: {0}", line.note().preview()) : line.note().preview();
    boolean viewing = watching && uuid.equals(open);
    if (viewing) {
      read.put(uuid, line.id());
    } else if (!line.mine() && line.id() > read.getOrDefault(uuid, 0L)) {
      friend.unread++;
    }
    revision++;
    return !line.mine() && !viewing;
  }

  public void announce(String uuid, Line line) {
    Friend friend = friend(uuid);
    if (friend == null) {
      return;
    }
    Note note = line.note();
    String tag =
        switch (note.kind()) {
          case TEXT -> Lang.tr("MESSAGE");
          case WAVE -> Lang.tr("WAVE");
          case INVITE -> Lang.tr("INVITE");
          case MODEL -> Lang.tr("AVATAR");
          case CONFIG -> Lang.tr("CONFIG");
          case WAYPOINT -> Lang.tr(note.live() ? "LOCATION" : "WAYPOINT");
          case MAP -> Lang.tr("MAP");
          case ROOM_INVITE -> Lang.tr("SHARED MAP");
        };
    String text =
        switch (note.kind()) {
          case WAVE -> Lang.tr("Waved at you");
          case INVITE -> Lang.format("Invited you to {0}", note.text());
          case MODEL -> Lang.tr("Sent you an avatar") + " · " + note.text();
          case CONFIG -> Lang.tr("Sent you a config") + " · " + note.text();
          case MAP -> Lang.tr("Sent you a map") + " · " + note.text();
          case ROOM_INVITE -> Lang.format("Invited you to share a map · {0}", note.text());
          case WAYPOINT ->
              note.live()
                  ? Lang.tr("Shared their location")
                  : Lang.tr("Sent a waypoint") + " · " + note.text();
          default -> note.text();
        };
    toast(new Toast(uuid, friend.name, text, friend.entityId, tag));
  }

  public void forget(String uuid) {
    chats.remove(uuid);
    Friend friend = friend(uuid);
    if (friend != null) {
      friend.unread = 0;
      friend.lastText = "";
    }
    revision++;
  }

  public Parcel parcel(Line line) {
    return parcels.getOrDefault(line.id(), Parcel.IDLE);
  }

  public void parcel(Line line, Parcel state) {
    parcels.put(line.id(), state);
    revision++;
  }

  public void favorite(String uuid, boolean on) {
    if (on) {
      favorites.add(uuid);
    } else {
      favorites.remove(uuid);
    }
    Friend friend = friend(uuid);
    if (friend != null) {
      friend.favorite = on;
    }
    sort();
  }

  public String favorites() {
    return String.join(",", favorites.stream().sorted().toList());
  }

  public void favorites(String stored) {
    favorites.clear();
    for (String uuid : stored.split(",")) {
      if (!uuid.isBlank()) {
        favorites.add(uuid.strip());
      }
    }
  }

  public String read() {
    StringBuilder out = new StringBuilder();
    for (Map.Entry<String, Long> entry : read.entrySet()) {
      if (friend(entry.getKey()) != null) {
        out.append(out.isEmpty() ? "" : ",")
            .append(entry.getKey())
            .append(':')
            .append(entry.getValue());
      }
    }
    return out.toString();
  }

  public void read(String stored) {
    read.clear();
    for (String pair : stored.split(",")) {
      int split = pair.lastIndexOf(':');
      if (split <= 0) {
        continue;
      }
      try {
        read.put(pair.substring(0, split), Long.parseLong(pair.substring(split + 1)));
      } catch (NumberFormatException e) {
        read.remove(pair.substring(0, split));
      }
    }
  }

  public void toast(Toast toast) {
    if (quiet) {
      return;
    }
    while (toasts.size() >= MAX_TOASTS) {
      toasts.poll();
    }
    toasts.add(toast);
  }

  public Toast nextToast() {
    return toasts.poll();
  }

  public void clear() {
    friends.clear();
    incoming.clear();
    outgoing.clear();
    chats.clear();
    pending.clear();
    parcels.clear();
    toasts.clear();
    met = false;
    counted = false;
    locked = false;
    open = "";
    notice = "";
    revision++;
  }

  private void sort() {
    friends.sort(
        (a, b) -> {
          if (a.favorite != b.favorite) {
            return a.favorite ? -1 : 1;
          }
          if (a.online != b.online) {
            return a.online ? -1 : 1;
          }
          return a.name.toLowerCase(Locale.ROOT).compareTo(b.name.toLowerCase(Locale.ROOT));
        });
    revision++;
  }
}
