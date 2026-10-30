package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.menu.model.Glyph;

final class Pictograms {
  private static final int GRID = 9;
  private static final float DOT = 0.64F;
  private static final float TRACK = 0.42F;
  private static final float OFF = 0.55F;
  private static final float RIPPLE = 0.55F;
  private static final float SWING = 0.45F;
  static final float NO_WAVE = -1.0F;
  private static final String[][] ART = new String[Glyph.LAST + 1][];

  static {
    ART[Glyph.SINGLE] =
        new String[] {
          "....X....",
          "...XXX...",
          "...XXX...",
          "....X....",
          "..XXXXX..",
          ".XXXXXXX.",
          ".XXXXXXX.",
          ".XXXXXXX.",
          "........."
        };
    ART[Glyph.MULTI] =
        new String[] {
          ".........",
          "..X...X..",
          ".XXX.XXX.",
          "..X...X..",
          ".XXXXXXX.",
          "XXXXXXXXX",
          "XXXXXXXXX",
          ".........",
          "........."
        };
    ART[Glyph.REALMS] =
        new String[] {
          "..XXXXX..",
          ".X..X..X.",
          "X...X...X",
          "X...X...X",
          "XXXXXXXXX",
          "X...X...X",
          "X...X...X",
          ".X..X..X.",
          "..XXXXX.."
        };
    ART[Glyph.OPTIONS] =
        new String[] {
          ".........",
          "ooXXooooo",
          ".........",
          ".........",
          "oooooXXoo",
          ".........",
          ".........",
          "oXXoooooo",
          "........."
        };
    ART[Glyph.QUIT] =
        new String[] {
          "....X....",
          "..X.X.X..",
          ".X..X..X.",
          "X...X...X",
          "X.......X",
          "X.......X",
          ".X.....X.",
          "..XXXXX..",
          "........."
        };
    ART[Glyph.CX] =
        new String[] {
          ".........",
          ".XXX.....",
          "X........",
          "X...X..X.",
          "X....XX..",
          "X....XX..",
          "X...X..X.",
          ".XXX.....",
          "........."
        };
    ART[Glyph.WORLD] =
        new String[] {
          ".XXXXXXX.",
          "XXXXXXXXX",
          "X.......X",
          "X.......X",
          "X.......X",
          "X.......X",
          "X.......X",
          "XXXXXXXXX",
          "........."
        };
    ART[Glyph.SERVER] =
        new String[] {
          ".........",
          "..XXXXX..",
          ".X.....X.",
          "X..XXX..X",
          "..X...X..",
          ".....X...",
          ".........",
          "....X....",
          "........."
        };
    ART[Glyph.SHOT] =
        new String[] {
          ".........",
          "...XXX...",
          "XXXXXXXXX",
          "X.......X",
          "X..XXX..X",
          "X..X.X..X",
          "X..XXX..X",
          "XXXXXXXXX",
          "........."
        };
    ART[Glyph.HOME] =
        new String[] {
          "....X....",
          "...XXX...",
          "..XXXXX..",
          ".XXXXXXX.",
          "XXXXXXXXX",
          ".XX...XX.",
          ".XX...XX.",
          ".XX...XX.",
          "........."
        };
  }

  static {
    ART[Glyph.CLOCK] =
        new String[] {
          "..XXXXX..",
          ".X..X..X.",
          "X...X...X",
          "X...X...X",
          "X...XXX.X",
          "X.......X",
          "X.......X",
          ".X.....X.",
          "..XXXXX.."
        };
    ART[Glyph.SESSION] =
        new String[] {
          "..XXXXX..",
          "....X....",
          "..XXXXX..",
          ".X..X..X.",
          "X...X...X",
          "X...XX..X",
          "X.......X",
          ".X.....X.",
          "..XXXXX.."
        };
    ART[Glyph.MEMORY] =
        new String[] {
          ".........",
          "XXXXXXXXX",
          "X.......X",
          "X.XX.XX.X",
          "X.XX.XX.X",
          "X.......X",
          "XXXXXXXXX",
          ".X.X.X.X.",
          "........."
        };
    ART[Glyph.ACCOUNTS] =
        new String[] {
          ".........",
          "XXXXXXXXX",
          "X.......X",
          "X.XX.XXXX",
          "X.XX.X..X",
          "X....XXXX",
          "X.XXXX..X",
          "XXXXXXXXX",
          "........."
        };
    ART[Glyph.FRIENDS] =
        new String[] {
          "..X...X..",
          ".XXX.XXX.",
          "..X...X..",
          "XXXXXXXXX",
          "XXXXXXXXX",
          ".........",
          "....X....",
          "...XXX...",
          "....X...."
        };
    ART[Glyph.MUSIC] =
        new String[] {
          "....XXXXX",
          "....X...X",
          "....X...X",
          "....X...X",
          "....X...X",
          "..XXX.XXX",
          ".XXXX.XXX",
          "..XX...XX",
          "........."
        };
    ART[Glyph.RESUME] =
        new String[] {
          "..X......",
          "..XX.....",
          "..XXX....",
          "..XXXX...",
          "..XXXXX..",
          "..XXXX...",
          "..XXX....",
          "..XX.....",
          "..X......"
        };
    ART[Glyph.PLAYTIME] =
        new String[] {
          ".........",
          "........X",
          "........X",
          "......X.X",
          "......X.X",
          "....X.X.X",
          "..X.X.X.X",
          "X.X.X.X.X",
          "........."
        };
    ART[Glyph.SWITCHES] =
        new String[] {
          ".........",
          ".XXXXXXX.",
          "X.......X",
          "X....XX.X",
          "X....XX.X",
          "X.......X",
          ".XXXXXXX.",
          ".........",
          "........."
        };
    ART[Glyph.MODS] =
        new String[] {
          ".........",
          ".XXX.XXX.",
          ".XXX.XXX.",
          ".XXX.XXX.",
          ".........",
          ".XXX.XXX.",
          ".XXX.XXX.",
          ".XXX.XXX.",
          "........."
        };
    ART[Glyph.ADD] =
        new String[] {
          ".........",
          "....X....",
          "....X....",
          "....X....",
          ".XXXXXXX.",
          "....X....",
          "....X....",
          "....X....",
          "........."
        };
  }

  private static final int[][] LIT = new int[ART.length][];
  private static final int[][] TRACKS = new int[ART.length][];

  static {
    for (int glyph = 0; glyph < ART.length; glyph++) {
      if (ART[glyph] != null) {
        LIT[glyph] = cells(ART[glyph], 'X');
        TRACKS[glyph] = cells(ART[glyph], 'o');
      }
    }
  }

  private Pictograms() {}

  private static int[] cells(String[] rows, char mark) {
    int count = 0;
    for (String row : rows) {
      for (int i = 0; i < GRID; i++) {
        count += row.charAt(i) == mark ? 1 : 0;
      }
    }
    int[] found = new int[count];
    int next = 0;
    for (int row = 0; row < GRID; row++) {
      for (int column = 0; column < GRID; column++) {
        if (rows[row].charAt(column) == mark) {
          found[next++] = row * GRID + column;
        }
      }
    }
    return found;
  }

  static boolean has(int glyph) {
    return glyph > Glyph.NONE && glyph < ART.length && ART[glyph] != null;
  }

  static void draw(
      Canvas c, Theme t, int glyph, float x, float y, float size, int lit, boolean matrix) {
    draw(c, t, glyph, x, y, size, lit, matrix, NO_WAVE);
  }

  static void draw(
      Canvas c,
      Theme t,
      int glyph,
      float x,
      float y,
      float size,
      int lit,
      boolean matrix,
      float wave) {
    if (!has(glyph)) {
      return;
    }
    float cell = size / GRID;
    float dot = cell * DOT;
    float inset = (cell - dot) * 0.5F;
    if (matrix) {
      int off = Theme.withAlpha(t.dotOff(), OFF);
      for (int i = 0; i < GRID * GRID; i++) {
        c.roundRect(
            x + i % GRID * cell + inset, y + i / GRID * cell + inset, dot, dot, dot * 0.5F, off);
      }
    }
    int track = Theme.withAlpha(lit, TRACK);
    stamp(c, TRACKS[glyph], x, y, cell, inset, dot, track);
    if (wave == NO_WAVE) {
      stamp(c, LIT[glyph], x, y, cell, inset, dot, lit);
      return;
    }
    for (int index : LIT[glyph]) {
      float phase = wave - (index % GRID + index / GRID) * RIPPLE;
      int ink =
          Theme.withAlpha(lit, SWING + (1.0F - SWING) * (0.5F + 0.5F * (float) Math.sin(phase)));
      c.roundRect(
          x + index % GRID * cell + inset,
          y + index / GRID * cell + inset,
          dot,
          dot,
          dot * 0.5F,
          ink);
    }
  }

  private static void stamp(
      Canvas c, int[] cells, float x, float y, float cell, float inset, float dot, int ink) {
    for (int index : cells) {
      c.roundRect(
          x + index % GRID * cell + inset,
          y + index / GRID * cell + inset,
          dot,
          dot,
          dot * 0.5F,
          ink);
    }
  }
}
