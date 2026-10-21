package me.cxiy.cxvisuals.menu.model;

import java.util.List;

public interface TitleSource {
  boolean changed();

  List<Control> nav();

  String version();

  String player();

  default Control options() {
    return null;
  }

  default Control quit() {
    return null;
  }
}
