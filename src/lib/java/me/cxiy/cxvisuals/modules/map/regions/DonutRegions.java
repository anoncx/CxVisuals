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

  private static final int[][] GROUPS = groups();
  private static final double[][] BOUNDS = bounds();

  private DonutRegions() {}

  private static int[][] groups() {
    int[] seen = new int[CELLS + 1];
    java.util.List<int[]> found = new java.util.ArrayList<>();
    int[] stack = new int[CELLS];
    int[] members = new int[CELLS];
    for (int first = 1; first <= CELLS; first++) {
      if (seen[first] != 0) {
        continue;
      }
      Region kind = region(first);
      int depth = 0;
      int count = 0;
      stack[depth++] = first;
      seen[first] = 1;
      while (depth > 0) {
        int cell = stack[--depth];
        members[count++] = cell;
        int column = column(cell);
        int row = row(cell);
        int[] next = {
          column > 0 ? cell - 1 : 0,
          column < GRID - 1 ? cell + 1 : 0,
          row > 0 ? cell - GRID : 0,
          row < GRID - 1 ? cell + GRID : 0
        };
        for (int other : next) {
          if (other != 0 && seen[other] == 0 && region(other) == kind) {
            seen[other] = 1;
            stack[depth++] = other;
          }
        }
      }
      found.add(java.util.Arrays.copyOf(members, count));
    }
    return found.toArray(new int[0][]);
  }

  private static double[][] bounds() {
    double[][] out = new double[GROUPS.length][6];
    for (int g = 0; g < GROUPS.length; g++) {
      double minX = Double.MAX_VALUE;
      double minZ = Double.MAX_VALUE;
      double maxX = -Double.MAX_VALUE;
      double maxZ = -Double.MAX_VALUE;
      double sumX = 0.0;
      double sumZ = 0.0;
      for (int cell : GROUPS[g]) {
        minX = Math.min(minX, minX(cell));
        minZ = Math.min(minZ, minZ(cell));
        maxX = Math.max(maxX, minX(cell) + CELL);
        maxZ = Math.max(maxZ, minZ(cell) + CELL);
        sumX += minX(cell) + CELL / 2.0;
        sumZ += minZ(cell) + CELL / 2.0;
      }
      double meanX = sumX / GROUPS[g].length;
      double meanZ = sumZ / GROUPS[g].length;
      double best = Double.MAX_VALUE;
      for (int cell : GROUPS[g]) {
        double dx = minX(cell) + CELL / 2.0 - meanX;
        double dz = minZ(cell) + CELL / 2.0 - meanZ;
        if (dx * dx + dz * dz < best) {
          best = dx * dx + dz * dz;
          out[g][4] = minX(cell) + CELL / 2.0;
          out[g][5] = minZ(cell) + CELL / 2.0;
        }
      }
      out[g][0] = minX;
      out[g][1] = minZ;
      out[g][2] = maxX;
      out[g][3] = maxZ;
    }
    return out;
  }

  public static double[] groupBounds(int group) {
    return BOUNDS[group];
  }

  public static int groupCount() {
    return GROUPS.length;
  }

  public static int[] groupCells(int group) {
    return GROUPS[group];
  }

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
