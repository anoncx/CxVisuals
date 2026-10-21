package me.cxiy.cxvisuals.modules.donutsmp.auction;

public enum Size {
  SINGLE("one", "single"),
  PARTIAL("part", "partial stack"),
  FULL("full", "full stack");

  public static final int MIN_SAMPLE = 3;

  private final String tag;
  private final String label;

  Size(String tag, String label) {
    this.tag = tag;
    this.label = label;
  }

  public static Size of(int count, int maxStack) {
    if (count <= 1) {
      return SINGLE;
    }
    return count >= maxStack ? FULL : PARTIAL;
  }

  public static boolean splits(int maxStack) {
    return maxStack > 1;
  }

  public String key(String id) {
    return id + "|" + tag;
  }

  public String label() {
    return label;
  }
}
