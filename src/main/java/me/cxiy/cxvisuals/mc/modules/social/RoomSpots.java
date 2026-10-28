package me.cxiy.cxvisuals.mc.modules.social;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.modules.friends.Friends;
import me.cxiy.cxvisuals.modules.map.share.RoomBook;
import me.cxiy.cxvisuals.modules.map.share.SharedRoom;
import me.cxiy.cxvisuals.modules.map.share.Smoother;
import me.cxiy.cxvisuals.modules.map.share.SpotCodec;
import me.cxiy.cxvisuals.modules.map.share.SpotPolicy;
import net.minecraft.client.Minecraft;

final class RoomSpots {
  record Seen(String uuid, String name, SpotCodec.Spot spot, long receivedAt) {}

  private static final long POLL_MILLIS = 3_000L;
  private static final int CHECK_TICKS = 5;
  private static final double MAX_STEP_SECONDS = 0.25;
  private static final ExecutorService IO =
      Executors.newFixedThreadPool(
          2,
          task -> {
            Thread thread = new Thread(task, "CxVisuals Shared Map IO");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 2);
            return thread;
          });

  private final SpotPolicy policy = new SpotPolicy();
  private final Map<String, Seen> seen = new ConcurrentHashMap<>();
  private final Map<String, Smoother> smoothers = new HashMap<>();
  private final AtomicReference<SpotCodec.Spot> outgoing = new AtomicReference<>();
  private final AtomicBoolean sending = new AtomicBoolean();
  private final AtomicBoolean fetching = new AtomicBoolean();
  private final RoomApi api;
  private final String roomId;
  private SpotCodec.Spot last;
  private long sentAt;
  private long polledAt;
  private long shownAt;
  private boolean wasSharing;
  private int ticks;

  RoomSpots(String roomId) {
    this.roomId = roomId;
    this.api = new RoomApi(roomId);
  }

  void tick(Minecraft mc, RoomBook book, SharedRoom room) {
    if (++ticks % CHECK_TICKS != 0) {
      return;
    }
    long now = System.currentTimeMillis();
    poll(now);
    List<String> to = book.locationRecipients(roomId, now);
    to.retainAll(room.members());
    if (to.isEmpty()) {
      if (wasSharing) {
        wasSharing = false;
        last = null;
        IO.execute(this::clear);
      }
      return;
    }
    wasSharing = true;
    if (mc.player == null || mc.level == null || mc.isPaused()) {
      return;
    }
    SpotCodec.Spot here =
        new SpotCodec.Spot(
            mc.level.dimension().identifier().toString(),
            mc.player.getX(),
            mc.player.getY(),
            mc.player.getZ(),
            mc.player.getYRot(),
            now);
    if (!policy.shouldSend(last, here, now - sentAt)) {
      return;
    }
    last = here;
    sentAt = now;
    outgoing.set(here);
    if (sending.compareAndSet(false, true)) {
      IO.execute(() -> send(List.copyOf(to)));
    }
  }

  void receive(String payload) {
    int split = payload.indexOf(' ');
    if (split <= 0) {
      return;
    }
    try {
      accept(
          payload.substring(0, split).toLowerCase(),
          Base64.getDecoder().decode(payload.substring(split + 1)));
    } catch (IllegalArgumentException e) {
      CxVisualsClient.LOG.debug("Ignored a malformed location: {}", e.getMessage());
    }
  }

  List<Seen> visible(String dimension, long nowMillis) {
    double seconds =
        shownAt == 0L ? 0.0 : Math.min(MAX_STEP_SECONDS, (nowMillis - shownAt) / 1000.0);
    shownAt = nowMillis;
    seen.values().removeIf(row -> SpotPolicy.visibility(row.receivedAt(), nowMillis) <= 0.0F);
    smoothers.keySet().retainAll(seen.keySet());
    List<Seen> out = new ArrayList<>();
    for (Seen row : seen.values()) {
      SpotCodec.Spot spot = row.spot();
      if (!spot.dimension().equals(dimension)) {
        smoothers.remove(row.uuid());
        continue;
      }
      double[] at =
          smoothers
              .computeIfAbsent(row.uuid(), id -> new Smoother())
              .step(new double[] {spot.x(), spot.y(), spot.z()}, seconds);
      SpotCodec.Spot shown =
          new SpotCodec.Spot(spot.dimension(), at[0], at[1], at[2], spot.yaw(), spot.at());
      out.add(new Seen(row.uuid(), row.name(), shown, row.receivedAt()));
    }
    return out;
  }

  void forget() {
    seen.clear();
    smoothers.clear();
  }

  private void poll(long now) {
    if (now - polledAt < POLL_MILLIS || !fetching.compareAndSet(false, true)) {
      return;
    }
    polledAt = now;
    IO.execute(this::fetch);
  }

  private void accept(String from, byte[] sealed) {
    SharedRoom room = RoomLink.roomById(roomId);
    if (room == null || !room.members().contains(from)) {
      return;
    }
    SpotCodec.Spot spot = SpotCodec.decode(FriendsLink.openFrom(from, sealed));
    if (spot == null) {
      return;
    }
    Friends.Friend friend = FriendsLink.friend(from);
    seen.put(
        from, new Seen(from, friend == null ? "" : friend.name, spot, System.currentTimeMillis()));
  }

  private void send(List<String> to) {
    try {
      SpotCodec.Spot spot;
      while ((spot = outgoing.getAndSet(null)) != null) {
        byte[] plain = SpotCodec.encode(spot);
        Map<String, byte[]> sealed = new HashMap<>();
        for (String uuid : to) {
          byte[] blob = FriendsLink.sealTo(uuid, plain);
          if (blob != null) {
            sealed.put(uuid, blob);
          }
        }
        if (!sealed.isEmpty()) {
          api.sendSpots(sealed);
        }
      }
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.debug("Location update failed: {}", e.getMessage());
    } finally {
      sending.set(false);
    }
  }

  private void fetch() {
    try {
      for (RoomApi.Spot row : api.spots()) {
        accept(row.from(), row.body());
      }
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.debug("Location fetch failed: {}", e.getMessage());
    } finally {
      fetching.set(false);
    }
  }

  private void clear() {
    try {
      api.clearSpots();
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.debug("Clearing location failed: {}", e.getMessage());
    }
  }
}
