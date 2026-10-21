package me.cxiy.cxvisuals.modules.waypoints;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.cxiy.cxvisuals.core.i18n.Lang;

public final class Waypoints {
  public record Waypoint(
      String id, String name, int x, int y, int z, String dimension, int color, long died) {
    public String place() {
      return x + " " + y + " " + z;
    }

    public boolean death() {
      return died > 0L;
    }
  }

  public record Target(String uuid, String name, int entityId, boolean online) {}

  public interface Actions {
    void addHere(String name);

    List<Target> targets();

    boolean share(Waypoint waypoint, String uuid);
  }

  public static final int LIMIT = 64;
  public static final int NAME_LIMIT = 24;
  public static final int DEATHS_KEPT = 3;
  public static final int DEATH_COLOR = 0xFFFF5F57;
  private static final long MINUTE = 60_000L;
  public static final int[] PALETTE = {
    0xFFD71921, 0xFFFFFFFF, 0xFFFFB020, 0xFF4FC3F7, 0xFF30D158, 0xFFB388FF
  };
  private static final Actions IDLE =
      new Actions() {
        @Override
        public void addHere(String name) {}

        @Override
        public List<Target> targets() {
          return List.of();
        }

        @Override
        public boolean share(Waypoint waypoint, String uuid) {
          return false;
        }
      };

  private final Map<String, List<Waypoint>> scopes = new LinkedHashMap<>();
  private List<Waypoint> current = new ArrayList<>();
  private List<Waypoint> view = Collections.unmodifiableList(current);
  private Actions actions = IDLE;
  private String scope = "";
  private String dimension = "";
  private String notice = "";
  private final Map<String, String> ages = new java.util.HashMap<>();
  private long agesMinute = Long.MIN_VALUE;
  private int revision;
  private long counter;

  public void actions(Actions next) {
    actions = next == null ? IDLE : next;
  }

  public Actions actions() {
    return actions;
  }

  public List<Waypoint> list() {
    return view;
  }

  public String scope() {
    return scope;
  }

  public String dimension() {
    return dimension;
  }

  public int revision() {
    return revision;
  }

  public String notice() {
    return notice;
  }

  public void notice(String text) {
    notice = text == null ? "" : text;
  }

  public void place(String nextScope, String nextDimension) {
    dimension = nextDimension == null ? "" : nextDimension;
    String key = nextScope == null ? "" : nextScope;
    if (key.equals(scope)) {
      return;
    }
    scope = key;
    current =
        key.isEmpty()
            ? new ArrayList<>()
            : scopes.computeIfAbsent(key, ignored -> new ArrayList<>());
    view = Collections.unmodifiableList(current);
    notice = "";
  }

  public Waypoint add(String name, int x, int y, int z, String where) {
    if (scope.isEmpty()) {
      notice = Lang.tr("Join a world first.");
      return null;
    }
    if (current.size() >= LIMIT) {
      notice = Lang.format("This world already has {0} waypoints.", LIMIT);
      return null;
    }
    String label = clean(name);
    if (label.isEmpty()) {
      label = Lang.tr("Waypoint") + " " + (current.size() + 1);
    }
    Waypoint waypoint =
        new Waypoint(
            Long.toHexString(System.nanoTime()) + Long.toHexString(counter++),
            label,
            x,
            y,
            z,
            where == null ? "" : where,
            PALETTE[current.size() % PALETTE.length],
            0L);
    current.add(waypoint);
    notice = "";
    revision++;
    return waypoint;
  }

  public Waypoint death(int x, int y, int z, String where, long now) {
    if (scope.isEmpty()) {
      return null;
    }
    int deaths = 0;
    for (int i = current.size() - 1; i >= 0; i--) {
      if (current.get(i).death() && ++deaths >= DEATHS_KEPT) {
        current.remove(i);
      }
    }
    if (current.size() >= LIMIT) {
      return null;
    }
    Waypoint waypoint =
        new Waypoint(
            Long.toHexString(System.nanoTime()) + Long.toHexString(counter++),
            Lang.tr("Death"),
            x,
            y,
            z,
            where == null ? "" : where,
            DEATH_COLOR,
            Math.max(1L, now));
    current.add(waypoint);
    revision++;
    return waypoint;
  }

  public static String age(long died, long now) {
    long minutes = Math.max(0L, now - died) / MINUTE;
    if (minutes < 1L) {
      return Lang.tr("just now");
    }
    if (minutes < 60L) {
      return Lang.format("{0} min ago", minutes);
    }
    if (minutes < 48L * 60L) {
      return Lang.format("{0} h ago", minutes / 60L);
    }
    return Lang.format("{0} d ago", minutes / (24L * 60L));
  }

  public String ago(Waypoint waypoint, long now) {
    if (!waypoint.death()) {
      return "";
    }
    long minute = now / MINUTE;
    if (minute != agesMinute) {
      agesMinute = minute;
      ages.clear();
    }
    return ages.computeIfAbsent(waypoint.id(), ignored -> age(waypoint.died(), now));
  }

  public void remove(String id) {
    if (current.removeIf(waypoint -> waypoint.id().equals(id))) {
      revision++;
    }
  }

  public static String clean(String value) {
    if (value == null) {
      return "";
    }
    StringBuilder out = new StringBuilder(Math.min(value.length(), NAME_LIMIT));
    for (int i = 0; i < value.length() && out.length() < NAME_LIMIT; i++) {
      char c = value.charAt(i);
      out.append(c < 0x20 || c == 0x7F ? ' ' : c);
    }
    return out.toString().strip();
  }

  public String toJson() {
    JsonObject root = new JsonObject();
    for (Map.Entry<String, List<Waypoint>> entry : scopes.entrySet()) {
      if (entry.getValue().isEmpty()) {
        continue;
      }
      JsonArray rows = new JsonArray();
      for (Waypoint waypoint : entry.getValue()) {
        JsonObject row = new JsonObject();
        row.addProperty("id", waypoint.id());
        row.addProperty("name", waypoint.name());
        row.addProperty("x", waypoint.x());
        row.addProperty("y", waypoint.y());
        row.addProperty("z", waypoint.z());
        row.addProperty("dimension", waypoint.dimension());
        row.addProperty("color", waypoint.color());
        if (waypoint.death()) {
          row.addProperty("died", waypoint.died());
        }
        rows.add(row);
      }
      root.add(entry.getKey(), rows);
    }
    return root.toString();
  }

  public void fromJson(String json) {
    scopes.clear();
    try {
      JsonObject root = JsonParser.parseString(json).getAsJsonObject();
      for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
        List<Waypoint> rows = new ArrayList<>();
        for (JsonElement element : entry.getValue().getAsJsonArray()) {
          JsonObject row = element.getAsJsonObject();
          String name = clean(row.get("name").getAsString());
          if (rows.size() < LIMIT && !name.isEmpty()) {
            rows.add(
                new Waypoint(
                    row.get("id").getAsString(),
                    name,
                    row.get("x").getAsInt(),
                    row.get("y").getAsInt(),
                    row.get("z").getAsInt(),
                    row.get("dimension").getAsString(),
                    row.get("color").getAsInt() | 0xFF000000,
                    row.has("died") ? Math.max(0L, row.get("died").getAsLong()) : 0L));
          }
        }
        scopes.put(entry.getKey(), rows);
      }
    } catch (RuntimeException e) {
      scopes.clear();
    }
    String restored = scope;
    scope = "";
    current = new ArrayList<>();
    view = Collections.unmodifiableList(current);
    place(restored, dimension);
    revision++;
  }
}
