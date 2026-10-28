package me.cxiy.cxvisuals.mc.modules.social;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import me.cxiy.cxvisuals.gui.map.LocationModel;
import me.cxiy.cxvisuals.gui.map.ShareHost;
import me.cxiy.cxvisuals.gui.map.ShareModel;
import me.cxiy.cxvisuals.mc.modules.waypoints.Markers;
import me.cxiy.cxvisuals.modules.friends.Friends;
import me.cxiy.cxvisuals.modules.map.share.LocationMode;
import me.cxiy.cxvisuals.modules.map.share.SharedRoom;
import net.minecraft.client.Minecraft;

public final class RoomShare implements ShareHost {
  public static final RoomShare INSTANCE = new RoomShare();

  private RoomShare() {}

  @Override
  public boolean available() {
    return RoomLink.canShare(Minecraft.getInstance());
  }

  @Override
  public boolean live() {
    return RoomLink.current() != null;
  }

  @Override
  public String server() {
    return Markers.scope(Minecraft.getInstance());
  }

  @Override
  public String roomName() {
    SharedRoom room = RoomLink.current();
    return room == null ? "" : room.name();
  }

  @Override
  public List<ShareModel.Friend> friends() {
    SharedRoom room = RoomLink.current();
    List<ShareModel.Friend> out = new ArrayList<>();
    for (Friends.Friend friend : FriendsLink.friendList()) {
      if (!friend.key.isEmpty()) {
        boolean member = room != null && room.members().contains(friend.uuid);
        out.add(
            new ShareModel.Friend(
                friend.uuid, friend.name, friend.entityId, friend.online, member));
      }
    }
    return out;
  }

  @Override
  public List<LocationModel.Row> locationRows() {
    SharedRoom room = RoomLink.current();
    List<LocationModel.Row> out = new ArrayList<>();
    if (room == null) {
      return out;
    }
    for (Friends.Friend friend : FriendsLink.friendList()) {
      if (room.members().contains(friend.uuid)) {
        out.add(
            new LocationModel.Row(
                friend.uuid,
                friend.name,
                friend.entityId,
                RoomLink.locationMode(room.id(), friend.uuid)));
      }
    }
    return out;
  }

  @Override
  public void share(Collection<String> uuids, boolean backfill) {
    String server = server().replaceFirst("^server:", "");
    RoomLink.create(server, backfill, uuids);
  }

  @Override
  public void locate(String uuid, LocationMode mode) {
    SharedRoom room = RoomLink.current();
    if (room != null) {
      RoomLink.locate(room.id(), uuid, mode);
    }
  }

  @Override
  public void leave() {
    SharedRoom room = RoomLink.current();
    if (room != null) {
      RoomLink.leave(room.id());
    }
  }

  @Override
  public boolean owner() {
    SharedRoom room = RoomLink.current();
    return room != null && RoomLink.owns(room);
  }

  @Override
  public void remove(String uuid) {
    SharedRoom room = RoomLink.current();
    if (room != null) {
      RoomLink.remove(room, uuid);
    }
  }
}
