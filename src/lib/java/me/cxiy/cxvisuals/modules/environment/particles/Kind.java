package me.cxiy.cxvisuals.modules.environment.particles;

public enum Kind {
  FIREFLIES(Tint.FIREFLY, 130, 16.0F, false),
  PETALS(Tint.CHERRY, 380, 15.0F, true),
  SNOW(Tint.FROST, 680, 14.0F, true),
  EMBERS(Tint.EMBER, 200, 14.0F, false),
  MOTES(Tint.SUNLIT, 260, 9.0F, false),
  BUTTERFLIES(Tint.WINGS, 30, 14.0F, false),
  BUBBLES(Tint.FILM, 70, 10.0F, false),
  WISPS(Tint.SPIRIT, 18, 16.0F, false);

  public final Tint tint;
  public final int count;
  public final float reach;
  public final boolean falls;

  Kind(Tint tint, int count, float reach, boolean falls) {
    this.tint = tint;
    this.count = count;
    this.reach = reach;
    this.falls = falls;
  }
}
