package me.cxiy.cxvisuals.modules.map.regions;

import java.util.Locale;

public final class DonutRegions {
  public enum Region {
    EU_CENTRAL("EU Central", 0xFF3478D8),
    EU_WEST("EU West", 0xFF3FA84F),
    NA_EAST("NA East", 0xFFD95B22),
    NA_WEST("NA West", 0xFFDBA932),
    ASIA("Asia", 0xFF7646D6),
    OCEANIA("Oceania", 0xFFCC6666);

    private final String label;
    private final int color;

    Region(String label, int color) {
      this.label = label;
      this.color = color;
    }

    public String label() {
      return label;
    }

    public int color() {
      return color;
    }
  }

  public static final int GRID = 9;
  public static final int CELL = 50_000;
  public static final int HALF = GRID * CELL / 2;
  public static final int CELLS = GRID * GRID;

  private static final String HOST = "donutsmp.net";
  private static final String SCOPE = "server:";
  private static final String[] ROWS = {
    "ONNNEEEEE",
    "ONNNEEEEE",
    "ONNNEEEEE",
    "OOONEEEEE",
    "AAAAEEEEE",
    "AWWCCCCCE",
    "AWWCCCCCE",
    "CWCCCCCCC",
    "CWWWWWWCC"
  };

  private DonutRegions() {}

  public static boolean matches(String scope) {
    if (scope == null || !scope.startsWith(SCOPE)) {
      return false;
    }
    String address = scope.substring(SCOPE.length()).toLowerCase(Locale.ROOT);
    int port = address.indexOf(':');
    String host = port < 0 ? address : address.substring(0, port);
    return host.equals(HOST) || host.endsWith("." + HOST);
  }

  public static Region region(int cell) {
    return switch (ROWS[(cell - 1) / GRID].charAt((cell - 1) % GRID)) {
      case 'C' -> Region.EU_CENTRAL;
      case 'W' -> Region.EU_WEST;
      case 'E' -> Region.NA_EAST;
      case 'N' -> Region.NA_WEST;
      case 'A' -> Region.ASIA;
      default -> Region.OCEANIA;
    };
  }

  public static int cellAt(double x, double z) {
    if (!(x >= -HALF && x < HALF && z >= -HALF && z < HALF)) {
      return 0;
    }
    int column = (int) Math.floor((x + HALF) / CELL);
    int row = (int) Math.floor((z + HALF) / CELL);
    return row * GRID + column + 1;
  }

  public static int column(int cell) {
    return (cell - 1) % GRID;
  }

  public static int row(int cell) {
    return (cell - 1) / GRID;
  }

  public static double minX(int cell) {
    return -HALF + (double) column(cell) * CELL;
  }

  public static double minZ(int cell) {
    return -HALF + (double) row(cell) * CELL;
  }

  public static boolean borderEast(int cell) {
    return column(cell) == GRID - 1 || region(cell) != region(cell + 1);
  }

  public static boolean borderSouth(int cell) {
    return row(cell) == GRID - 1 || region(cell) != region(cell + GRID);
  }
}
