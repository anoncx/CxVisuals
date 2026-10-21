package me.cxiy.cxvisuals.menu.views;

public interface Sfx {
  Sfx NONE = new Sfx() {};

  default void hover(int column) {}

  default void press() {}

  default void launch() {}

  default void back() {}

  default void turn() {}
}
