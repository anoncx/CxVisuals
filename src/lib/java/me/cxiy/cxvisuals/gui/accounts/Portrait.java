package me.cxiy.cxvisuals.gui.accounts;

import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.modules.accounts.Entry;
import me.cxiy.cxvisuals.modules.accounts.Faces;
import me.cxiy.cxvisuals.modules.accounts.Hub;

final class Portrait {
  private Portrait() {}

  static void head(
      Canvas c, Theme t, Hub hub, Entry entry, float x, float y, float size, float radius) {
    Faces.Face face = hub.faces().of(entry);
    byte[] png = face.head();
    if (png == null || !c.icon(png, x, y, size, radius)) {
      letter(c, t, entry.username, x, y, size, radius);
    }
  }

  static void figure(
      Canvas c, Theme t, Hub hub, Entry entry, float x, float y, float size, float radius) {
    Faces.Face face = hub.faces().of(entry);
    byte[] png = face.figure();
    if (png == null || !c.icon(png, x, y, size, 0.0F)) {
      letter(c, t, entry.username, x + size * 0.18F, y + size * 0.18F, size * 0.64F, radius);
    }
  }

  private static void letter(
      Canvas c, Theme t, String name, float x, float y, float size, float radius) {
    Depth.raised(c, t, x, y, size, size, radius, t.surface2());
    String initial =
        name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase();
    FontStyle font =
        size >= 80.0F ? FontStyle.HERO : size >= 44.0F ? FontStyle.TITLE : FontStyle.DOTS;
    c.text(
        font,
        initial,
        x + (size - c.textWidth(font, initial)) * 0.5F,
        y + (size - c.lineHeight(font)) * 0.5F,
        t.ink());
  }
}
