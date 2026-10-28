package me.cxiy.cxvisuals.modules.map.share;

import java.util.List;

public record SharedRoom(
    String id,
    String scope,
    String name,
    String owner,
    int epoch,
    byte[] key,
    long cursor,
    List<String> members) {
  public static final int KEY_BYTES = 32;

  public SharedRoom {
    members = List.copyOf(members);
  }

  public SharedRoom withKey(int nextEpoch, byte[] nextKey) {
    return new SharedRoom(id, scope, name, owner, nextEpoch, nextKey, cursor, members);
  }

  public SharedRoom withCursor(long next) {
    return new SharedRoom(id, scope, name, owner, epoch, key, next, members);
  }

  public SharedRoom withMembers(List<String> next) {
    return new SharedRoom(id, scope, name, owner, epoch, key, cursor, next);
  }
}
