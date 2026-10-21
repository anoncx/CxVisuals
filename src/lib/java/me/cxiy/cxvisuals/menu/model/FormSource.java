package me.cxiy.cxvisuals.menu.model;

public interface FormSource {
  boolean changed();

  Form read();

  void back();

  default boolean key(int keyCode, int scanCode, int modifiers) {
    return false;
  }
}
