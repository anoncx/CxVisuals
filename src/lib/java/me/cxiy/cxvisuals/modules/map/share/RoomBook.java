package me.cxiy.cxvisuals.modules.map.share;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RoomBook {
  private record Grant(LocationMode mode, long expires) {}

  private static final Gson GSON = new Gson();
  private static final LocationMode[] MODES = LocationMode.values();

  private final Path file;
  private final Map<String, SharedRoom> rooms = new LinkedHashMap<>();
  private final Map<String, Map<String, Grant>> location = new LinkedHashMap<>();
  private final Set<String> backfill = new HashSet<>();
  private final Map<String, Map<String, Long>> done = new LinkedHashMap<>();

  public RoomBook(Path file) {
    this.file = file;
  }

  public static RoomBook load(Path file) {
    RoomBook book = new RoomBook(file);
    try {
      JsonObject root =
          JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
      for (JsonElement row : root.getAsJsonArray("rooms")) {
        SharedRoom room = readRoom(row.getAsJsonObject());
        if (room != null) {
          book.rooms.put(room.id(), room);
        }
      }
      book.readBackfill(root);
      book.readLocation(root);
    } catch (IOException | RuntimeException e) {
      book.rooms.clear();
      book.location.clear();
      book.backfill.clear();
      book.done.clear();
    }
    return book;
  }

  public synchronized List<SharedRoom> rooms() {
    return List.copyOf(rooms.values());
  }

  public synchronized SharedRoom get(String id) {
    return rooms.get(id);
  }

  public synchronized SharedRoom forScope(String scope) {
    if (scope == null || scope.isEmpty()) {
      return null;
    }
    for (SharedRoom room : rooms.values()) {
      if (room.scope().equals(scope)) {
        return room;
      }
    }
    return null;
  }

  public synchronized void put(SharedRoom room) {
    rooms.put(room.id(), room);
  }

  public synchronized void remove(String id) {
    rooms.remove(id);
    location.remove(id);
    backfill.remove(id);
    done.remove(id);
  }

  public synchronized boolean backfills(String roomId) {
    return backfill.contains(roomId);
  }

  public synchronized void backfill(String roomId, boolean on) {
    if (!rooms.containsKey(roomId)) {
      return;
    }
    if (on) {
      backfill.add(roomId);
    } else {
      backfill.remove(roomId);
    }
  }

  public synchronized long regionHash(String roomId, String dimension, long regionKey) {
    Map<String, Long> hashes = done.get(roomId);
    Long hash = hashes == null ? null : hashes.get(dimension + "|" + regionKey);
    return hash == null ? 0L : hash;
  }

  public synchronized void regionDone(String roomId, String dimension, long regionKey, long hash) {
    if (rooms.containsKey(roomId)) {
      done.computeIfAbsent(roomId, id -> new LinkedHashMap<>())
          .put(dimension + "|" + regionKey, hash);
    }
  }

  public synchronized void clearRegions(String roomId) {
    done.remove(roomId);
  }

  public synchronized LocationMode locationMode(String roomId, String friend, long nowMillis) {
    Map<String, Grant> grants = location.get(roomId);
    Grant grant = grants == null ? null : grants.get(friend);
    if (grant == null || (grant.expires() != 0L && nowMillis > grant.expires())) {
      return LocationMode.OFF;
    }
    return grant.mode();
  }

  public synchronized void shareLocation(
      String roomId, String friend, LocationMode mode, long nowMillis) {
    if (!rooms.containsKey(roomId)) {
      return;
    }
    if (mode == LocationMode.OFF) {
      Map<String, Grant> grants = location.get(roomId);
      if (grants != null) {
        grants.remove(friend);
      }
      return;
    }
    long expires = mode == LocationMode.ONE_HOUR ? nowMillis + LocationMode.HOUR_MILLIS : 0L;
    location
        .computeIfAbsent(roomId, id -> new LinkedHashMap<>())
        .put(friend, new Grant(mode, expires));
  }

  public synchronized void endSession(String roomId) {
    Map<String, Grant> grants = location.get(roomId);
    if (grants != null) {
      grants.values().removeIf(grant -> grant.mode() == LocationMode.UNTIL_LEAVE);
    }
  }

  public synchronized List<String> locationRecipients(String roomId, long nowMillis) {
    Map<String, Grant> grants = location.get(roomId);
    List<String> out = new ArrayList<>();
    if (grants != null) {
      for (String friend : grants.keySet()) {
        if (locationMode(roomId, friend, nowMillis) != LocationMode.OFF) {
          out.add(friend);
        }
      }
    }
    return out;
  }

  public synchronized void save() {
    JsonObject root = new JsonObject();
    JsonArray list = new JsonArray();
    for (SharedRoom room : rooms.values()) {
      list.add(writeRoom(room));
    }
    root.add("rooms", list);
    root.add("location", writeLocation());
    root.add("backfill", GSON.toJsonTree(new ArrayList<>(backfill)));
    JsonObject regions = new JsonObject();
    for (Map.Entry<String, Map<String, Long>> entry : done.entrySet()) {
      regions.add(entry.getKey(), GSON.toJsonTree(entry.getValue()));
    }
    root.add("done", regions);
    Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
    try {
      Files.createDirectories(file.toAbsolutePath().getParent());
      Files.writeString(tmp, root.toString(), StandardCharsets.UTF_8);
      move(tmp);
    } catch (IOException e) {
      try {
        Files.deleteIfExists(tmp);
      } catch (IOException ignored) {
        return;
      }
    }
  }

  private void move(Path tmp) throws IOException {
    try {
      Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private JsonObject writeLocation() {
    JsonObject out = new JsonObject();
    for (Map.Entry<String, Map<String, Grant>> room : location.entrySet()) {
      JsonObject friends = new JsonObject();
      for (Map.Entry<String, Grant> friend : room.getValue().entrySet()) {
        Grant grant = friend.getValue();
        if (grant.mode().persistent()) {
          JsonArray row = new JsonArray();
          row.add(grant.mode().ordinal());
          row.add(grant.expires());
          friends.add(friend.getKey(), row);
        }
      }
      if (!friends.isEmpty()) {
        out.add(room.getKey(), friends);
      }
    }
    return out;
  }

  private void readLocation(JsonObject root) {
    JsonElement shared = root.get("location");
    if (shared == null || !shared.isJsonObject()) {
      return;
    }
    for (Map.Entry<String, JsonElement> room : shared.getAsJsonObject().entrySet()) {
      if (!rooms.containsKey(room.getKey())) {
        continue;
      }
      Map<String, Grant> grants = new LinkedHashMap<>();
      for (Map.Entry<String, JsonElement> friend : room.getValue().getAsJsonObject().entrySet()) {
        JsonArray row = friend.getValue().getAsJsonArray();
        int ordinal = row.get(0).getAsInt();
        if (ordinal > 0 && ordinal < MODES.length && MODES[ordinal].persistent()) {
          grants.put(friend.getKey(), new Grant(MODES[ordinal], row.get(1).getAsLong()));
        }
      }
      location.put(room.getKey(), grants);
    }
  }

  private void readBackfill(JsonObject root) {
    JsonElement ids = root.get("backfill");
    if (ids != null && ids.isJsonArray()) {
      for (JsonElement id : ids.getAsJsonArray()) {
        if (rooms.containsKey(id.getAsString())) {
          backfill.add(id.getAsString());
        }
      }
    }
    JsonElement regions = root.get("done");
    if (regions != null && regions.isJsonObject()) {
      for (Map.Entry<String, JsonElement> room : regions.getAsJsonObject().entrySet()) {
        if (!rooms.containsKey(room.getKey())) {
          continue;
        }
        Map<String, Long> hashes = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> row : room.getValue().getAsJsonObject().entrySet()) {
          hashes.put(row.getKey(), row.getValue().getAsLong());
        }
        done.put(room.getKey(), hashes);
      }
    }
  }

  private static JsonObject writeRoom(SharedRoom room) {
    JsonObject out = new JsonObject();
    out.addProperty("id", room.id());
    out.addProperty("scope", room.scope());
    out.addProperty("name", room.name());
    out.addProperty("owner", room.owner());
    out.addProperty("epoch", room.epoch());
    out.addProperty("key", Base64.getEncoder().encodeToString(room.key()));
    out.addProperty("cursor", room.cursor());
    out.add("members", GSON.toJsonTree(room.members()));
    return out;
  }

  private static SharedRoom readRoom(JsonObject json) {
    try {
      byte[] key = Base64.getDecoder().decode(json.get("key").getAsString());
      String id = json.get("id").getAsString();
      if (key.length != SharedRoom.KEY_BYTES || !id.matches("[0-9a-f]{32}")) {
        return null;
      }
      List<String> members = new ArrayList<>();
      for (JsonElement member : json.getAsJsonArray("members")) {
        members.add(member.getAsString());
      }
      return new SharedRoom(
          id,
          json.get("scope").getAsString(),
          json.get("name").getAsString(),
          json.get("owner").getAsString(),
          json.get("epoch").getAsInt(),
          key,
          json.get("cursor").getAsLong(),
          members);
    } catch (RuntimeException e) {
      return null;
    }
  }
}
