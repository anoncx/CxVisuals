package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.menu.model.Glyph;

final class Channel {
  static final Channel EMPTY = new Channel("", "", "", null, Glyph.NONE, false, false, null, null);

  final String id;
  final String title;
  final String caption;
  final int glyph;
  final boolean enabled;
  final boolean flagged;
  final Runnable start;
  final Widget widget;
  final DotArt art;

  Channel(
      String id,
      String title,
      String caption,
      byte[] icon,
      int glyph,
      boolean enabled,
      boolean flagged,
      Runnable start,
      Widget widget) {
    this.id = id;
    this.title = title;
    this.caption = caption;
    this.glyph = glyph;
    this.enabled = enabled;
    this.flagged = flagged;
    this.start = start;
    this.widget = widget;
    this.art = DotArt.of(icon);
  }

  static Channel of(
      String id,
      String title,
      String caption,
      byte[] icon,
      int glyph,
      boolean enabled,
      boolean flagged,
      Runnable start) {
    return new Channel(id, title, caption, icon, glyph, enabled, flagged, start, null);
  }

  static Channel of(Widget widget) {
    return new Channel(
        "widget:" + widget.id(),
        widget.title(),
        "",
        null,
        widget.glyph(),
        false,
        false,
        null,
        widget);
  }

  boolean empty() {
    return this == EMPTY;
  }

  boolean launchable() {
    return !empty() && widget == null && enabled;
  }
}
