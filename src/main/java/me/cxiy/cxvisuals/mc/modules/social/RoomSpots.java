package me.cxiy.cxvisuals.mc.modules.social;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.modules.map.share.RoomBook;
import me.cxiy.cxvisuals.modules.map.share.SharedRoom;
import me.cxiy.cxvisuals.modules.map.share.SpotCodec;
import me.cxiy.cxvisuals.modules.map.share.SpotPolicy;
import net.minecraft.client.Minecraft;

final class RoomSpots {
  record Seen(String uuid, String name, SpotCodec.Spot spot, long receivedAt) {}

  private static final long FETCH_GAP_NANOS = 200_000_000L;
  private static final int CHECK_TICKS = 5;

  private final SpotPolicy policy = new SpotPolicy();
  private final Map<String, Seen> seen = new ConcurrentHashMap<>();
  private final AtomicReference<SpotCodec.Spot> outgoing = new AtomicReference<>();
  private final AtomicBoolean sending = new AtomicBoolean();
  private final AtomicBoolean fetching = new AtomicBoolean();
  private final RoomApi api;
  private final String roomId;
  private SpotCodec.Spot last;
  private long sentAt;
  private long fetchedAt;
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
    List<String> to = book.locationRecipients(roomId, now);
    to.retainAll(room.members());
    if (to.isEmpty()) {
      if (wasSharing) {
        wasSharing = false;
        last = null;
        Social.work(this::clear);
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
      Social.work(() -> send(List.copyOf(to)));
    }
  }

  void onEvent() {
    long now = System.nanoTime();
    if (now - fetchedAt < FETCH_GAP_NANOS || !fetching.compareAndSet(false, true)) {
      return;
    }
    fetchedAt = now;
    Social.work(this::fetch);
  }

  List<Seen> visible(String dimension, long nowMillis) {
    List<Seen> out = new ArrayList<>();
    seen.values().removeIf(row -> SpotPolicy.visibility(row.receivedAt(), nowMillis) <= 0.0F);
    for (Seen row : seen.values()) {
      if (row.spot().dimension().equals(dimension)) {
        out.add(row);
      }
    }
    return out;
  }

  void forget() {
    seen.clear();
  }

  private void send(List<String> to) {
    try {
      SpotCodec.Spot spot;
      while ((spot = outgoing.getAndSet(null)) != null) {
        byte[] plain = SpotCodec.encode(spot);
        for (String uuid : to) {
          byte[] sealed = FriendsLink.sealTo(uuid, plain);
          if (sealed != null) {
            api.sendSpot(uuid, sealed);
          }
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
      SharedRoom room = RoomLink.roomById(roomId);
      for (RoomApi.Spot row : api.spots()) {
        byte[] plain =
            room != null && room.members().contains(row.from())
                ? FriendsLink.openFrom(row.from(), row.body())
                : null;
        SpotCodec.Spot spot = SpotCodec.decode(plain);
        if (spot != null) {
          var friend = FriendsLink.friend(row.from());
          seen.put(
              row.from(),
              new Seen(
                  row.from(), friend == null ? "" : friend.name, spot, System.currentTimeMillis()));
        }
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
