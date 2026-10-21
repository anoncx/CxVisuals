package me.cxiy.cxvisuals.modules.avatar.anim;

public final class ClipMap {
  private static final String[] OFF = new String[0];

  public static final ClipMap AUTO =
      new ClipMap(new String[ClipSet.EVENTS][], new boolean[ClipSet.EVENTS]);

  private final String[][] names;
  private final boolean[] random;

  private ClipMap(String[][] names, boolean[] random) {
    this.names = names;
    this.random = random;
  }

  public String[] names(int event) {
    return names[event];
  }

  public boolean random(int event) {
    return random[event];
  }

  public boolean isAuto(int event) {
    return names[event] == null;
  }

  public boolean isOff(int event) {
    return names[event] != null && names[event].length == 0;
  }

  public boolean allAuto() {
    for (String[] event : names) {
      if (event != null) {
        return false;
      }
    }
    return true;
  }

  public ClipMap with(int event, String[] clips, boolean shuffle) {
    String[][] nextNames = names.clone();
    boolean[] nextRandom = random.clone();
    nextNames[event] = clips == null ? null : clips.length == 0 ? OFF : clips.clone();
    nextRandom[event] = clips != null && clips.length > 0 && shuffle;
    return new ClipMap(nextNames, nextRandom);
  }

  public ClipMap auto(int event) {
    return with(event, null, false);
  }

  public ClipMap off(int event) {
    return with(event, OFF, false);
  }
}
