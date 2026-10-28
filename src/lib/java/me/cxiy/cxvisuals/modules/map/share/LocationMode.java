package me.cxiy.cxvisuals.modules.map.share;

public enum LocationMode {
  OFF,
  UNTIL_LEAVE,
  ONE_HOUR,
  ALWAYS;

  public static final long HOUR_MILLIS = 3_600_000L;

  public boolean persistent() {
    return this == ONE_HOUR || this == ALWAYS;
  }
}
