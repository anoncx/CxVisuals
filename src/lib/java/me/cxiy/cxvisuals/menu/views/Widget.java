package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.menu.model.Glyph;
import me.cxiy.cxvisuals.menu.model.Playtime;
import me.cxiy.cxvisuals.menu.model.Pulse;

abstract class Widget {
  static final float PAD = 14.0F;
  static final float BIG_FROM = 170.0F;
  static final int DOTS = 12;

  private final String id;
  private final String title;
  private final int glyph;

  Widget(String id, String title, int glyph) {
    this.id = id;
    this.title = title;
    this.glyph = glyph;
  }

  final String id() {
    return id;
  }

  final String title() {
    return Lang.tr(title);
  }

  final int glyph() {
    return glyph;
  }

  abstract void draw(
      Canvas c, Theme t, Telemetry data, Pulse pulse, float x, float y, float w, float h);

  boolean press(Pulse pulse, float x, float y, float w, float h) {
    return false;
  }

  final void heading(Canvas c, Theme t, float x, float y) {
    c.text(FontStyle.CAPTION, title(), x + PAD, y + PAD - 2.0F, t.grey());
  }

  final void stat(
      Canvas c, Theme t, String value, String caption, float x, float y, float w, float h) {
    FontStyle face = display(c, value, w);
    c.text(
        face,
        value,
        x + (w - c.textWidth(face, value)) * 0.5F,
        y + (h - c.lineHeight(face)) * 0.5F + 2.0F,
        t.ink());
    c.text(
        FontStyle.CAPTION,
        caption,
        x + (w - c.textWidth(FontStyle.CAPTION, caption)) * 0.5F,
        y + h - PAD - c.lineHeight(FontStyle.CAPTION) + 2.0F,
        t.grey());
  }

  static FontStyle display(Canvas c, String text, float w) {
    return w >= BIG_FROM && c.textWidth(FontStyle.HERO, text) <= w - PAD * 2.0F
        ? FontStyle.HERO
        : FontStyle.DOTS;
  }

  static final class Clock extends Widget {
    Clock() {
      super("clock", Lang.key("Clock"), Glyph.CLOCK);
    }

    @Override
    void draw(Canvas c, Theme t, Telemetry data, Pulse pulse, float x, float y, float w, float h) {
      heading(c, t, x, y);
      stat(c, t, data.time(), data.day(), x, y, w, h);
    }
  }

  static final class Session extends Widget {
    Session() {
      super("session", Lang.key("Session"), Glyph.SESSION);
    }

    @Override
    void draw(Canvas c, Theme t, Telemetry data, Pulse pulse, float x, float y, float w, float h) {
      heading(c, t, x, y);
      FontStyle face = FontStyle.DOTS;
      float width = c.textWidth(face, data.session());
      c.text(
          face,
          data.session(),
          x + (w - width) * 0.5F,
          y + (h - c.lineHeight(face)) * 0.5F + 6.0F,
          t.ink());
    }
  }

  static final class Memory extends Widget {
    Memory() {
      super("memory", Lang.key("Memory"), Glyph.MEMORY);
    }

    @Override
    void draw(Canvas c, Theme t, Telemetry data, Pulse pulse, float x, float y, float w, float h) {
      heading(c, t, x, y);
      float dot = Math.min(6.0F, (w - PAD * 2.0F) / (DOTS * 1.6F));
      float step = (w - PAD * 2.0F - dot) / (DOTS - 1);
      int lit = Math.round(data.memoryUse() * DOTS);
      float top = y + h * 0.5F - dot;
      for (int i = 0; i < DOTS; i++) {
        int ink = i >= lit ? t.dotOff() : data.memoryFull() && i == lit - 1 ? t.red() : t.ink();
        c.roundRect(x + PAD + i * step, top, dot, dot, dot * 0.5F, ink);
      }
      float width = c.textWidth(FontStyle.MONO, data.memory());
      c.text(FontStyle.MONO, data.memory(), x + (w - width) * 0.5F, top + dot + 8.0F, t.ink2());
    }
  }

  static final class Friends extends Widget {
    private static final String ONLINE = Lang.key("friends online");

    Friends() {
      super("friends", Lang.key("Friends"), Glyph.FRIENDS);
    }

    @Override
    void draw(Canvas c, Theme t, Telemetry data, Pulse pulse, float x, float y, float w, float h) {
      heading(c, t, x, y);
      stat(c, t, Integer.toString(pulse.friendsOnline()), Lang.tr(ONLINE), x, y, w, h);
    }
  }

  static final class Resume extends Widget {
    private static final String IDLE = Lang.key("No worlds yet");
    private static final float ICON = 14.0F;

    Resume() {
      super("resume", Lang.key("Continue"), Glyph.RESUME);
    }

    @Override
    void draw(Canvas c, Theme t, Telemetry data, Pulse pulse, float x, float y, float w, float h) {
      heading(c, t, x, y);
      String name = pulse.resumeTitle();
      float room = w - PAD * 2.0F;
      c.pushClip(x + PAD, y, room, h);
      if (name.isEmpty()) {
        c.text(
            FontStyle.ROW,
            Lang.tr(IDLE),
            x + PAD,
            y + h * 0.5F - c.lineHeight(FontStyle.ROW) * 0.5F + 4.0F,
            t.grey());
      } else {
        float middle = y + h * 0.5F + 4.0F;
        c.text(FontStyle.ROW, name, x + PAD, middle - c.lineHeight(FontStyle.ROW), t.ink());
        c.text(FontStyle.CAPTION, pulse.resumeNote(), x + PAD, middle + 2.0F, t.grey());
      }
      c.popClip();
      if (!name.isEmpty()) {
        Pictograms.draw(
            c, t, Glyph.RESUME, x + w - PAD - ICON, y + PAD - 2.0F, ICON, t.ink2(), false);
      }
    }

    @Override
    boolean press(Pulse pulse, float x, float y, float w, float h) {
      if (pulse.resumeTitle().isEmpty()) {
        return false;
      }
      pulse.resume();
      return true;
    }
  }

  static final class Played extends Widget {
    private static final String WEEK = Lang.key("this week");
    private static final float BAR = 3.0F;
    private static final float BAR_STEP = 6.0F;
    private static final float BAR_MAX = 14.0F;
    private static final long MINUTE = 60L;
    private static final long HOUR = 3600L;

    private final StringBuilder scratch = new StringBuilder(16);
    private long todayShown = -1L;
    private long weekShown = -1L;
    private String today = "";
    private String week = "";

    Played() {
      super("playtime", Lang.key("Playtime"), Glyph.PLAYTIME);
    }

    @Override
    void draw(Canvas c, Theme t, Telemetry data, Pulse pulse, float x, float y, float w, float h) {
      heading(c, t, x, y);
      long total = 0L;
      long most = 1L;
      for (int ago = 0; ago < Playtime.WEEK; ago++) {
        long seconds = pulse.playSeconds(ago);
        total += seconds;
        most = Math.max(most, seconds);
      }
      long now = pulse.playSeconds(0);
      if (now / MINUTE != todayShown || total / MINUTE != weekShown) {
        todayShown = now / MINUTE;
        weekShown = total / MINUTE;
        today = span(now);
        week = Lang.tr(WEEK) + "  " + span(total);
      }
      float left = x + w - PAD - BAR_STEP * Playtime.WEEK + (BAR_STEP - BAR);
      for (int ago = Playtime.WEEK - 1; ago >= 0; ago--) {
        long seconds = pulse.playSeconds(ago);
        float height = Math.max(BAR, BAR_MAX * seconds / most);
        int ink = ago == 0 ? t.ink() : seconds > 0L ? t.ink2() : t.dotOff();
        float bx = left + (Playtime.WEEK - 1 - ago) * BAR_STEP;
        c.roundRect(bx, y + PAD + BAR_MAX - height, BAR, height, BAR * 0.5F, ink);
      }
      stat(c, t, today, week, x, y, w, h);
    }

    private String span(long seconds) {
      scratch.setLength(0);
      long hours = seconds / HOUR;
      long minutes = seconds / MINUTE % MINUTE;
      if (hours > 0L) {
        scratch.append(hours).append("h ");
        if (minutes < 10L) {
          scratch.append('0');
        }
      }
      scratch.append(minutes).append('m');
      return scratch.toString();
    }
  }

  static final class Switches extends Widget {
    private static final float TOP = 30.0F;
    private static final float TRACK_W = 26.0F;
    private static final float TRACK_H = 12.0F;

    Switches() {
      super("switches", Lang.key("Quick toggles"), Glyph.SWITCHES);
    }

    private static float rowHeight(Pulse pulse, float h) {
      return (h - TOP - PAD * 0.5F) / Math.max(1, pulse.switches());
    }

    @Override
    void draw(Canvas c, Theme t, Telemetry data, Pulse pulse, float x, float y, float w, float h) {
      heading(c, t, x, y);
      float row = rowHeight(pulse, h);
      for (int i = 0; i < pulse.switches(); i++) {
        float middle = y + TOP + row * (i + 0.5F);
        boolean on = pulse.switchOn(i);
        String name = Lang.tr(pulse.switchName(i));
        c.text(
            FontStyle.LABEL,
            name,
            x + PAD,
            middle - c.lineHeight(FontStyle.LABEL) * 0.5F + 1.0F,
            on ? t.ink() : t.grey());
        float left = x + w - PAD - TRACK_W;
        c.roundRectBorder(
            left,
            middle - TRACK_H * 0.5F,
            TRACK_W,
            TRACK_H,
            TRACK_H * 0.5F,
            1.0F,
            on ? t.ink() : t.hair());
        float knob = TRACK_H - 4.0F;
        c.roundRect(
            on ? left + TRACK_W - knob - 2.0F : left + 2.0F,
            middle - knob * 0.5F,
            knob,
            knob,
            knob * 0.5F,
            on ? t.ink() : t.dotOff());
      }
    }

    @Override
    boolean press(Pulse pulse, float x, float y, float w, float h) {
      int row = (int) Math.floor((y - TOP) / rowHeight(pulse, h));
      if (row < 0 || row >= pulse.switches()) {
        return false;
      }
      pulse.flip(row);
      return true;
    }
  }

  static final class Music extends Widget {
    private static final String IDLE = Lang.key("Nothing playing");

    Music() {
      super("music", Lang.key("Now playing"), Glyph.MUSIC);
    }

    @Override
    void draw(Canvas c, Theme t, Telemetry data, Pulse pulse, float x, float y, float w, float h) {
      heading(c, t, x, y);
      String track = pulse.track();
      float room = w - PAD * 2.0F;
      c.pushClip(x + PAD, y, room, h);
      if (track.isEmpty()) {
        c.text(
            FontStyle.ROW,
            Lang.tr(IDLE),
            x + PAD,
            y + h * 0.5F - c.lineHeight(FontStyle.ROW) * 0.5F + 4.0F,
            t.grey());
      } else {
        float middle = y + h * 0.5F + 4.0F;
        c.text(FontStyle.ROW, track, x + PAD, middle - c.lineHeight(FontStyle.ROW), t.ink());
        c.text(FontStyle.CAPTION, pulse.artist(), x + PAD, middle + 2.0F, t.grey());
      }
      c.popClip();
    }
  }
}
