package me.cxiy.cxvisuals.menu.views;

import java.util.List;

final class Widgets {
  private static final List<Widget> ALL =
      List.of(
          new Widget.Clock(),
          new Widget.Session(),
          new Widget.Memory(),
          new Widget.Friends(),
          new Widget.Music(),
          new Widget.Resume(),
          new Widget.Played(),
          new Widget.Switches());

  private Widgets() {}

  static List<Widget> all() {
    return ALL;
  }

  static Widget byId(String id) {
    for (Widget widget : ALL) {
      if (widget.id().equals(id)) {
        return widget;
      }
    }
    return null;
  }
}
