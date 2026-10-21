package me.cxiy.cxvisuals.modules.hud.media;

public enum MediaCommand {
  PLAY_PAUSE("playpause"),
  NEXT("next"),
  PREVIOUS("prev"),
  SEEK("seek");

  private final String token;

  MediaCommand(String token) {
    this.token = token;
  }

  public String line(long argument) {
    return this == SEEK ? token + " " + argument : token;
  }
}
