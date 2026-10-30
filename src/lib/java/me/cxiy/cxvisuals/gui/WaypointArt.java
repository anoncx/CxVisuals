package me.cxiy.cxvisuals.gui;

import java.util.HashMap;
import java.util.Map;
import me.cxiy.cxvisuals.modules.waypoints.Icons;

public final class WaypointArt {
  private static final int GRID = 9;
  private static final Map<String, int[]> LIT = new HashMap<>();

  static {
    put(
        Icons.HOME,
        "....X....",
        "...XXX...",
        "..XXXXX..",
        ".XXXXXXX.",
        "..XXXXX..",
        "..XX.XX..",
        "..XX.XX..",
        "..XX.XX..",
        ".........");
    put(
        Icons.BASE,
        "X.X.X.X.X",
        "XXXXXXXXX",
        ".XXXXXXX.",
        ".XXX.XXX.",
        ".XXX.XXX.",
        ".XXXXXXX.",
        ".XXXXXXX.",
        "XXXXXXXXX",
        ".........");
    put(
        Icons.MINE,
        "..XXXXX..",
        ".X..X..X.",
        "X...X...X",
        "....X....",
        "....X....",
        "....X....",
        "....X....",
        "....X....",
        "....X....");
    put(
        Icons.FARM,
        ".........",
        ".XX...XX.",
        "XXXX.XXXX",
        ".XXX.XXX.",
        "..XXXXX..",
        "....X....",
        "....X....",
        "....X....",
        "...XXX...");
    put(
        Icons.VILLAGE,
        ".........",
        ".....X...",
        "..X..X.X.",
        "..X.XX.X.",
        "XXX.XXXXX",
        "XXXXXXXXX",
        "XXXXXXXXX",
        "XXXXXXXXX",
        ".........");
    put(
        Icons.PORTAL,
        "..XXXXX..",
        ".XXXXXXX.",
        ".XX...XX.",
        ".XX...XX.",
        ".XX...XX.",
        ".XX...XX.",
        ".XX...XX.",
        ".XXXXXXX.",
        "..XXXXX..");
    put(
        Icons.CHEST,
        ".........",
        ".XXXXXXX.",
        "XXXXXXXXX",
        "XXXXXXXXX",
        "XXXX.XXXX",
        "XXXXXXXXX",
        "XXXXXXXXX",
        "XXXXXXXXX",
        ".........");
    put(
        Icons.SWORD,
        ".......XX",
        "......XXX",
        ".....XXX.",
        "....XXX..",
        ".X.XXX...",
        "..XXX....",
        "..XX.....",
        ".X.XX....",
        "X...X....");
    put(
        Icons.TREE,
        "...XXX...",
        "..XXXXX..",
        ".XXXXXXX.",
        "XXXXXXXXX",
        ".XXXXXXX.",
        "..XXXXX..",
        "....X....",
        "....X....",
        "...XXX...");
    put(
        Icons.BOAT,
        "....X....",
        "....XX...",
        "....XXX..",
        "....XXXX.",
        "....X....",
        "XXXXXXXXX",
        ".XXXXXXX.",
        "..XXXXX..",
        ".........");
    put(
        Icons.STAR,
        "....X....",
        "....X....",
        "...XXX...",
        "XXXXXXXXX",
        ".XXXXXXX.",
        "..XXXXX..",
        "..XX.XX..",
        ".XX...XX.",
        ".X.....X.");
    put(
        Icons.SKULL,
        "..XXXXX..",
        ".XXXXXXX.",
        "XXXXXXXXX",
        "XX.XXX.XX",
        "XX.XXX.XX",
        "XXXXXXXXX",
        ".XXXXXXX.",
        "..X.X.X..",
        ".........");
  }

  private WaypointArt() {}

  private static void put(String id, String... rows) {
    int count = 0;
    for (String row : rows) {
      for (int i = 0; i < GRID; i++) {
        count += row.charAt(i) == 'X' ? 1 : 0;
      }
    }
    int[] cells = new int[count];
    int next = 0;
    for (int row = 0; row < GRID; row++) {
      for (int column = 0; column < GRID; column++) {
        if (rows[row].charAt(column) == 'X') {
          cells[next++] = row * GRID + column;
        }
      }
    }
    LIT.put(id, cells);
  }

  public static boolean has(String icon) {
    return icon != null && LIT.containsKey(icon);
  }

  public static void draw(Canvas c, float cx, float cy, float size, String icon, int color) {
    int[] cells = LIT.get(icon);
    if (cells == null) {
      return;
    }
    float cell = size / GRID;
    float left = cx - size * 0.5F;
    float top = cy - size * 0.5F;
    for (int i = 0; i < cells.length; i++) {
      int at = cells[i];
      c.roundRect(left + (at % GRID) * cell, top + (at / GRID) * cell, cell, cell, 0.0F, color);
    }
  }
}
