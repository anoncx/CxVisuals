package me.cxiy.cxvisuals.menu.model;

public interface Pulse {
  Pulse NONE = new Pulse() {};

  default int friendsOnline() {
    return 0;
  }

  default String track() {
    return "";
  }

  default String artist() {
    return "";
  }

  default String lyric() {
    return "";
  }

  default void rest() {}

  default String resumeTitle() {
    return "";
  }

  default String resumeNote() {
    return "";
  }

  default void resume() {}

  default long playSeconds(int daysAgo) {
    return 0L;
  }

  default int switches() {
    return 0;
  }

  default String switchName(int index) {
    return "";
  }

  default boolean switchOn(int index) {
    return false;
  }

  default void flip(int index) {}
}
