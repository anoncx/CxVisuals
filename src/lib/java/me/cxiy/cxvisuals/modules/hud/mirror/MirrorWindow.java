package me.cxiy.cxvisuals.modules.hud.mirror;

public record MirrorWindow(String app, String title, long handle, boolean minimized) {
  private static final char SPLIT = '|';
  private static final String JOIN = " · ";

  public String key() {
    return app + SPLIT + title;
  }

  public String label() {
    return app.isEmpty() ? title : title + JOIN + app;
  }

  public static String appOf(String key) {
    int cut = key.indexOf(SPLIT);
    return cut < 0 ? key : key.substring(0, cut);
  }
}
