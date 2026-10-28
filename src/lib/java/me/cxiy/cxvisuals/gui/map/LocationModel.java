package me.cxiy.cxvisuals.gui.map;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.modules.map.share.LocationMode;

public final class LocationModel {
  public record Row(String uuid, String name, int entityId, LocationMode mode) {}

  public record Pending(String title, List<String> lines, String action) {}

  private record Choice(String uuid, LocationMode mode) {}

  private final String server;
  private final List<Row> rows;
  private final BiConsumer<String, LocationMode> apply;
  private Choice choice;

  public LocationModel(String server, List<Row> rows, BiConsumer<String, LocationMode> apply) {
    this.server = server;
    this.rows = new ArrayList<>(rows);
    this.apply = apply;
  }

  public static LocationMode next(LocationMode mode) {
    return switch (mode) {
      case OFF -> LocationMode.UNTIL_LEAVE;
      case UNTIL_LEAVE -> LocationMode.ONE_HOUR;
      case ONE_HOUR -> LocationMode.ALWAYS;
      case ALWAYS -> LocationMode.OFF;
    };
  }

  public static String label(LocationMode mode) {
    return switch (mode) {
      case OFF -> Lang.tr("Off");
      case UNTIL_LEAVE -> Lang.tr("Until I leave");
      case ONE_HOUR -> Lang.tr("For 1 hour");
      case ALWAYS -> Lang.tr("Always");
    };
  }

  public List<Row> rows() {
    return List.copyOf(rows);
  }

  public Row row(String uuid) {
    for (Row row : rows) {
      if (row.uuid().equals(uuid)) {
        return row;
      }
    }
    return null;
  }

  public void choose(String uuid, LocationMode mode) {
    Row row = row(uuid);
    if (row == null) {
      return;
    }
    if (mode == LocationMode.OFF || row.mode() != LocationMode.OFF) {
      choice = null;
      set(row, mode);
      return;
    }
    choice = new Choice(uuid, mode);
  }

  public Pending pending() {
    if (choice == null) {
      return null;
    }
    Row row = row(choice.uuid());
    String when =
        switch (choice.mode()) {
          case UNTIL_LEAVE -> Lang.tr("This stops when you leave the server.");
          case ONE_HOUR -> Lang.tr("This stops by itself after one hour.");
          default -> Lang.tr("This stays on until you switch it off.");
        };
    return new Pending(
        Lang.format("Share your location with {0}?", row.name()),
        List.of(
            Lang.format(
                "{0} will see where you are on the map while you are on {1}.", row.name(), server),
            when,
            Lang.tr("You can switch it off at any time.")),
        Lang.format("Share location with {0}", row.name()));
  }

  public void confirm() {
    if (choice == null) {
      return;
    }
    Row row = row(choice.uuid());
    LocationMode mode = choice.mode();
    choice = null;
    if (row != null) {
      set(row, mode);
    }
  }

  public void cancel() {
    choice = null;
  }

  private void set(Row row, LocationMode mode) {
    rows.set(rows.indexOf(row), new Row(row.uuid(), row.name(), row.entityId(), mode));
    apply.accept(row.uuid(), mode);
  }
}
