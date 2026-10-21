package me.cxiy.cxvisuals.gui;

public final class TileSheet {
  private static int next;

  private final int id = next++;
  private final int edge;
  private final int columns;
  private final int rows;
  private final int[] pixels;
  private int revision;

  public TileSheet(int edge, int columns, int rows) {
    this.edge = edge;
    this.columns = columns;
    this.rows = rows;
    this.pixels = new int[edge * columns * edge * rows];
  }

  public int id() {
    return id;
  }

  public int edge() {
    return edge;
  }

  public int cells() {
    return columns * rows;
  }

  public int width() {
    return edge * columns;
  }

  public int height() {
    return edge * rows;
  }

  public int[] pixels() {
    return pixels;
  }

  public int revision() {
    return revision;
  }

  public float u(int cell) {
    return (cell % columns) / (float) columns;
  }

  public float v(int cell) {
    return (cell / columns) / (float) rows;
  }

  public float du() {
    return 1.0F / columns;
  }

  public float dv() {
    return 1.0F / rows;
  }

  public void write(int cell, int[] argb) {
    int width = width();
    int x = (cell % columns) * edge;
    int y = (cell / columns) * edge;
    for (int row = 0; row < edge; row++) {
      int from = row * edge;
      int to = (y + row) * width + x;
      for (int i = 0; i < edge; i++) {
        int p = argb[from + i];
        pixels[to + i] = (p & 0xFF00FF00) | ((p >> 16) & 0xFF) | ((p & 0xFF) << 16);
      }
    }
    revision++;
  }
}
