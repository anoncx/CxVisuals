package me.cxiy.cxvisuals.modules.map;

public interface MapSource {
  MapAtlas atlas();

  boolean active();

  int layer();

  boolean clouds();

  int terrain();

  boolean waypoints();

  boolean friends();

  int players(double[] x, double[] z, int max);

  int mobs(double[] x, double[] z, boolean[] hostile, int[] ids, int max);

  boolean mobHeads();
}
