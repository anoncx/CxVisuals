package me.cxiy.cxvisuals.mc.modules.social;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.modules.map.Maps;
import me.cxiy.cxvisuals.mc.modules.waypoints.Markers;
import me.cxiy.cxvisuals.modules.friends.Note;
import me.cxiy.cxvisuals.modules.friends.Sealer;
import me.cxiy.cxvisuals.modules.map.share.LocationMode;
import me.cxiy.cxvisuals.modules.map.share.RemoteQueue;
import me.cxiy.cxvisuals.modules.map.share.RoomBook;
import me.cxiy.cxvisuals.modules.map.share.SharedRoom;
import me.cxiy.cxvisuals.modules.map.share.SpotPolicy;
import net.minecraft.client.Minecraft;

public final class RoomLink {
  public enum Result {
    DONE,
    WORKING,
    FAILED
  }

  private static final long APPLY_BUDGET_NANOS = 400_000L;
  private static final long CATCH_UP_BUDGET_NANOS = 1_200_000L;
  private static final int BACKLOG = 512;
  private static final long SAVE_NANOS = 10_000_000_000L;
  private static final String SERVER_SCOPE = "server:";
  private static final SecureRandom RANDOM = new SecureRandom();

  private static RoomBook book;
  private static volatile RoomRuntime active;
  private static volatile RoomSpots spots;
  private static volatile boolean dirty;
  private static long savedAt;

  private RoomLink() {}

  private static synchronized RoomBook book() {
    if (book == null) {
      book = RoomBook.load(Maps.root().resolve("rooms-" + owner() + ".json"));
    }
    return book;
  }

  private static String owner() {
    String home = FriendsLink.home();
    return home.isEmpty() ? Minecraft.getInstance().getUser().getProfileId().toString() : home;
  }

  public static void changed(String dimension, int chunkX, int chunkZ, long stamp) {
    RoomRuntime now = active;
    if (now != null) {
      now.mark(dimension, chunkX, chunkZ, stamp);
    }
  }

  public static void tick(Minecraft mc) {
    String scope = Markers.scope(mc);
    SharedRoom room =
        Social.connected() && scope.startsWith(SERVER_SCOPE) && Maps.root() != null
            ? book().forScope(scope)
            : null;
    if (room == null || (active != null && !active.id().equals(room.id()))) {
      halt();
    }
    if (room == null) {
      return;
    }
    if (active == null) {
      active =
          new RoomRuntime(book(), room.id(), RoomLink::touch, RoomLink::flush, RoomLink::vanished);
      active.start();
      spots = new RoomSpots(room.id());
    }
    if (mc.player != null) {
      active.near(Markers.dimension(mc), mc.player.getBlockX() >> 4, mc.player.getBlockZ() >> 4);
    }
    RemoteQueue queue = active.queue();
    queue.drain(
        queue.size() > BACKLOG ? CATCH_UP_BUDGET_NANOS : APPLY_BUDGET_NANOS,
        System::nanoTime,
        Maps::importRemote);
    spots.tick(mc, book(), room);
    long now = System.nanoTime();
    if (dirty && now - savedAt > SAVE_NANOS) {
      flush();
    }
  }

  static void onEvent(String event) {
    RoomRuntime now = active;
    if ("map".equals(event) && now != null) {
      now.pullNow();
    } else if ("rooms".equals(event)) {
      Social.work(RoomLink::refresh);
    }
  }

  static void onPayload(String event, String data) {
    RoomSpots now = spots;
    if ("spot".equals(event) && now != null) {
      now.receive(data);
    }
  }

  public record Spot(String uuid, String name, double x, double y, double z, float alpha) {}

  public static List<Spot> spots(String dimension) {
    RoomSpots now = spots;
    if (now == null) {
      return List.of();
    }
    long time = System.currentTimeMillis();
    List<Spot> out = new ArrayList<>();
    for (RoomSpots.Seen row : now.visible(dimension, time)) {
      out.add(
          new Spot(
              row.uuid(),
              row.name(),
              row.spot().x(),
              row.spot().y(),
              row.spot().z(),
              SpotPolicy.visibility(row.receivedAt(), time)));
    }
    return out;
  }

  public static LocationMode locationMode(String roomId, String uuid) {
    return book().locationMode(roomId, uuid, System.currentTimeMillis());
  }

  public static void locate(String roomId, String uuid, LocationMode mode) {
    book().shareLocation(roomId, uuid, mode, System.currentTimeMillis());
    dirty = true;
  }

  public static boolean owns(SharedRoom room) {
    return room.owner().equals(owner());
  }

  static SharedRoom roomById(String id) {
    return book().get(id);
  }

  public static boolean live() {
    return active != null;
  }

  public static boolean stale() {
    return active != null && active.stale();
  }

  public static List<SharedRoom> rooms() {
    return book().rooms();
  }

  public static SharedRoom current() {
    return book().forScope(Markers.scope(Minecraft.getInstance()));
  }

  public static boolean canShare(Minecraft mc) {
    return Social.connected() && Markers.scope(mc).startsWith(SERVER_SCOPE) && mc.level != null;
  }

  public static void create(String name, boolean backfill, Collection<String> invitees) {
    Minecraft mc = Minecraft.getInstance();
    SharedRoom existing = current();
    if (existing != null) {
      invite(existing, backfill, invitees);
      return;
    }
    String scope = Markers.scope(mc);
    if (!canShare(mc) || invitees.isEmpty()) {
      return;
    }
    byte[] raw = new byte[16];
    RANDOM.nextBytes(raw);
    String id = HexFormat.of().formatHex(raw);
    SharedRoom room =
        new SharedRoom(id, scope, name, owner(), 0, Sealer.newKey(), 0L, List.of(owner()));
    List<String> targets = List.copyOf(invitees);
    Maps.say(Lang.tr("Creating the shared map"));
    Social.work(
        () -> {
          try {
            RoomApi.create(id);
            Social.later(
                () -> {
                  book().put(room);
                  book().backfill(room.id(), backfill);
                  dirty = true;
                });
            invite(room, backfill, targets);
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.warn("Couldn't create a shared map: {}", e.getMessage());
            Social.later(() -> Maps.say(Lang.tr("Couldn't create the shared map.")));
          }
        });
  }

  public static void invite(SharedRoom room, boolean backfill, Collection<String> invitees) {
    if (backfill) {
      book().backfill(room.id(), true);
      dirty = true;
    }
    List<String> targets = List.copyOf(invitees);
    String key = Base64.getEncoder().encodeToString(room.key());
    Social.work(
        () -> {
          int sent = 0;
          for (String uuid : targets) {
            try {
              RoomApi.addMember(room.id(), uuid);
              FriendsLink.send(
                  uuid,
                  Note.room(room.name(), room.id(), key, room.scope(), room.epoch(), backfill, 0));
              sent++;
            } catch (IOException | RuntimeException e) {
              CxVisualsClient.LOG.warn("Couldn't invite to a shared map: {}", e.getMessage());
            }
          }
          int done = sent;
          Social.later(
              () ->
                  Maps.say(
                      done == targets.size()
                          ? Lang.tr("Invitation sent")
                          : Lang.tr("Couldn't invite everyone.")));
          refresh();
        });
  }

  public static void accept(String from, Note note, boolean shareMine, Consumer<Result> done) {
    byte[] key = Base64.getDecoder().decode(note.roomKey());
    if (book().get(note.roomId()) != null) {
      rekey(from, note);
      done.accept(Result.DONE);
      return;
    }
    done.accept(Result.WORKING);
    Social.work(
        () -> {
          try {
            RoomApi.Member member = null;
            for (RoomApi.Member row : RoomApi.list()) {
              if (row.room().equals(note.roomId())) {
                member = row;
              }
            }
            RoomApi.Member found = member;
            Social.later(
                () -> {
                  if (found == null) {
                    done.accept(Result.FAILED);
                    return;
                  }
                  book()
                      .put(
                          new SharedRoom(
                              note.roomId(),
                              note.roomScope(),
                              note.text(),
                              found.owner(),
                              note.epoch(),
                              key,
                              0L,
                              found.members()));
                  book().backfill(note.roomId(), shareMine);
                  dirty = true;
                  done.accept(Result.DONE);
                });
          } catch (IOException | RuntimeException e) {
            Social.later(() -> done.accept(Result.FAILED));
          }
        });
  }

  static boolean rekey(String from, Note note) {
    SharedRoom have = book().get(note.roomId());
    if (have == null || !have.owner().equals(from) || note.epoch() <= have.epoch()) {
      return false;
    }
    book().put(have.withKey(note.epoch(), Base64.getDecoder().decode(note.roomKey())));
    dirty = true;
    RoomRuntime now = active;
    if (now != null && now.id().equals(have.id())) {
      now.fresh();
    }
    return true;
  }

  public static void leaveWorld() {
    halt();
  }

  public static void leave(String id) {
    Social.work(
        () -> {
          try {
            RoomApi.removeMember(id, owner());
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.debug("Leaving a shared map failed: {}", e.getMessage());
          }
          Social.later(() -> forget(id));
        });
  }

  public static void remove(SharedRoom room, String uuid) {
    if (!room.owner().equals(owner())) {
      return;
    }
    Social.work(
        () -> {
          try {
            RoomApi.removeMember(room.id(), uuid);
            int epoch = RoomApi.rotate(room.id());
            byte[] key = Sealer.newKey();
            List<String> rest = new ArrayList<>(room.members());
            rest.remove(uuid);
            SharedRoom next = room.withKey(epoch, key).withMembers(rest);
            Social.later(
                () -> {
                  book().put(next);
                  book().clearRegions(next.id());
                  dirty = true;
                  if (active != null && active.id().equals(room.id())) {
                    active.fresh();
                  }
                });
            String sealed = Base64.getEncoder().encodeToString(key);
            for (String member : rest) {
              if (!member.equals(owner())) {
                FriendsLink.send(
                    member,
                    Note.room(room.name(), room.id(), sealed, room.scope(), epoch, false, 0));
              }
            }
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.warn("Couldn't remove a member: {}", e.getMessage());
            Social.later(() -> Maps.say(Lang.tr("Couldn't remove that person.")));
          }
        });
  }

  private static void refresh() {
    try {
      List<RoomApi.Member> rows = RoomApi.list();
      Social.later(
          () -> {
            for (SharedRoom room : book().rooms()) {
              RoomApi.Member row = null;
              for (RoomApi.Member candidate : rows) {
                if (candidate.room().equals(room.id())) {
                  row = candidate;
                }
              }
              if (row == null) {
                vanished(room.id());
              } else if (!row.members().equals(room.members())) {
                book().put(room.withMembers(row.members()));
                dirty = true;
              }
            }
          });
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.debug("Shared map refresh failed: {}", e.getMessage());
    }
  }

  private static void vanished(String id) {
    Social.later(
        () -> {
          SharedRoom room = book().get(id);
          forget(id);
          if (room != null) {
            Maps.say(Lang.format("{0} is no longer shared with you", room.name()));
          }
        });
  }

  private static void forget(String id) {
    if (active != null && active.id().equals(id)) {
      halt();
    }
    book().remove(id);
    dirty = true;
    flush();
  }

  private static void halt() {
    if (active != null) {
      book().endSession(active.id());
      active.stop();
      active = null;
    }
    if (spots != null) {
      spots.forget();
      spots = null;
    }
  }

  private static void touch() {
    dirty = true;
  }

  private static void flush() {
    dirty = false;
    savedAt = System.nanoTime();
    book().save();
  }

  public static void shutdown() {
    halt();
    if (book != null) {
      book.save();
    }
  }
}
