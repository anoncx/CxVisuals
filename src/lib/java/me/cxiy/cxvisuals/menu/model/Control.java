package me.cxiy.cxvisuals.menu.model;

public interface Control {
  enum Kind {
    BUTTON,
    TOGGLE,
    CYCLE,
    SLIDER,
    TEXT
  }

  Kind kind();

  String label();

  default String value() {
    return "";
  }

  default boolean on() {
    return false;
  }

  default boolean enabled() {
    return true;
  }

  default int icon() {
    return Glyph.NONE;
  }

  default boolean leads() {
    return false;
  }

  default float fraction() {
    return 0.0F;
  }

  default void fraction(float value) {}

  default void release() {}

  default void press() {}

  default String text() {
    return "";
  }

  default void text(String value) {}

  default int limit() {
    return 64;
  }
}
