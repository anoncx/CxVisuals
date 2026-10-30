package me.cxiy.cxvisuals.modules.map;

public interface Forecast {
  int GRID = TileKey.EDGE + 1;

  boolean grid(int originX, int originZ, int step, int[] rgb, short[] height, byte[] water);
}
