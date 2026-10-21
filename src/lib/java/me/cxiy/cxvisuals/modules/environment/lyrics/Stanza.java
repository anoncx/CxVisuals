package me.cxiy.cxvisuals.modules.environment.lyrics;

import java.util.Arrays;

public final class Stanza {
  private static final int INITIAL = 16;

  public boolean alive;
  public boolean follows;
  public String text = "";
  public double x;
  public double y;
  public double z;
  public float rx;
  public float ry;
  public float rz;
  public float ux;
  public float uy;
  public float uz;
  public float roll;
  public float scale;
  public float full;
  public int words;
  public String[] parts = new String[INITIAL];
  public float[] lefts = new float[INITIAL];
  public float[] tops = new float[INITIAL];
  public float[] widths = new float[INITIAL];
  public int[] letters = new int[INITIAL];
  public float width;
  public float height;
  public long born;
  public long leaving = Long.MAX_VALUE;
  public long lifeNanos = Long.MAX_VALUE;
  public int sung;
  public float fill;
  public int seed;
  public int letterCount;
  public boolean whole;

  public void reserve(int count) {
    if (count <= parts.length) {
      return;
    }
    int size = Math.max(count, parts.length * 2);
    parts = Arrays.copyOf(parts, size);
    lefts = Arrays.copyOf(lefts, size);
    tops = Arrays.copyOf(tops, size);
    widths = Arrays.copyOf(widths, size);
    letters = Arrays.copyOf(letters, size);
  }
}
