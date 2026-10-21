package me.cxiy.cxvisuals.core.settings;

public final class LegacyConfig {
  private static final String IDLE_CAMERA = "idle_camera.";

  private LegacyConfig() {}

  public static String mapKey(String old) {
    int dot = old.indexOf('.');
    if (dot <= 0 || dot == old.length() - 1) {
      return null;
    }
    String page = old.substring(0, dot);
    String setting = old.substring(dot + 1);
    return switch (page) {
      case "camera" ->
          setting.equals("enabled") ? IDLE_CAMERA + "enabled" : IDLE_CAMERA + "camera." + setting;
      case "shots" -> IDLE_CAMERA + "shots." + setting;
      case "look" ->
          setting.equals("appearance")
              ? "client.interface.appearance"
              : IDLE_CAMERA + "look." + setting;
      default -> null;
    };
  }
}
