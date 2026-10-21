package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.menu.model.Glyph;
import me.cxiy.cxvisuals.menu.model.Pulse;

final class ChannelTile {
  static final float RADIUS = Kit.WINDOW_RADIUS;
  static final float REMOVE = 20.0F;
  static final float REMOVE_INSET = 10.0F;

  private static final float LIFT = 1.07F;
  private static final float PRESS = 0.97F;
  private static final float ART_MAX = 46.0F;
  private static final float ART_SHARE = 0.42F;
  private static final float ART_TOP = 0.15F;
  private static final float LABEL_GAP = 0.08F;
  private static final float TEXT_PAD = 10.0F;
  private static final float MARK = 6.0F;
  private static final float MARK_INSET = 17.0F;
  private static final float MARK_TOP = 11.0F;
  private static final float EMPTY_DOT = 4.0F;
  private static final float EMPTY_STEP = 12.0F;
  private static final float EMPTY_ALPHA = 0.5F;
  private static final float BREATH_ALPHA = 0.16F;
  private static final float FOCUS_GAP = 4.0F;
  private static final float ADD_SIZE = 28.0F;

  static final class Look {
    float glow;
    float alpha = 1.0F;
    float wave = Pictograms.NO_WAVE;
    float breathe;
    float add;
    boolean down;
    boolean focus;
    boolean removable;
    boolean removeHover;
    boolean ghost;

    void carried() {
      glow = 1.0F;
      alpha = 0.94F;
      wave = Pictograms.NO_WAVE;
      add = 0.0F;
      breathe = 0.0F;
      down = false;
      focus = false;
      removable = false;
      removeHover = false;
      ghost = false;
    }
  }

  private ChannelTile() {}

  static boolean overRemove(float mx, float my, float x, float y, float w) {
    return Kit.inside(mx, my, x + w - REMOVE_INSET - REMOVE, y + REMOVE_INSET, REMOVE, REMOVE);
  }

  static void draw(
      Canvas c,
      Theme t,
      Channel channel,
      Telemetry data,
      Pulse pulse,
      float x,
      float y,
      float w,
      float h,
      Look look) {
    float scale = (1.0F + (LIFT - 1.0F) * look.glow) * (look.down ? PRESS : 1.0F);
    c.pushAlpha(look.alpha);
    c.pushTransform(scale, x + w * 0.5F, y + h * 0.5F);
    if (channel.empty() || look.ghost) {
      empty(c, t, x, y, w, h, look);
    } else {
      filled(c, t, channel, data, pulse, x, y, w, h, look);
    }
    c.popTransform();
    c.popAlpha();
  }

  private static void empty(Canvas c, Theme t, float x, float y, float w, float h, Look look) {
    c.roundRectBorder(x, y, w, h, RADIUS, 1.0F, t.hair());
    if (look.add > 0.01F) {
      c.roundRectBorder(x, y, w, h, RADIUS, 1.0F, Theme.withAlpha(t.ink(), 0.35F * look.add));
      Pictograms.draw(
          c,
          t,
          Glyph.ADD,
          x + (w - ADD_SIZE) * 0.5F,
          y + (h - ADD_SIZE) * 0.5F,
          ADD_SIZE,
          Theme.withAlpha(t.ink(), look.add),
          false);
      return;
    }
    float cx = x + w * 0.5F - EMPTY_DOT * 0.5F;
    float cy = y + h * 0.5F - EMPTY_DOT * 0.5F;
    int ink = Theme.withAlpha(t.dotOff(), EMPTY_ALPHA);
    for (int row = -1; row <= 1; row++) {
      for (int column = -1; column <= 1; column++) {
        c.roundRect(
            cx + column * EMPTY_STEP,
            cy + row * EMPTY_STEP,
            EMPTY_DOT,
            EMPTY_DOT,
            EMPTY_DOT * 0.5F,
            ink);
      }
    }
  }

  private static void filled(
      Canvas c,
      Theme t,
      Channel channel,
      Telemetry data,
      Pulse pulse,
      float x,
      float y,
      float w,
      float h,
      Look look) {
    Kit.window(c, t, x, y, w, h);
    if (look.breathe > 0.01F) {
      c.roundRectBorder(
          x, y, w, h, RADIUS, 1.0F, Theme.withAlpha(t.ink(), BREATH_ALPHA * look.breathe));
    }
    if (look.glow > 0.01F) {
      c.roundRectBorder(x, y, w, h, RADIUS, 1.2F, Theme.withAlpha(t.ink(), 0.7F * look.glow));
    }
    if (look.focus) {
      c.roundRectBorder(
          x - FOCUS_GAP,
          y - FOCUS_GAP,
          w + FOCUS_GAP * 2.0F,
          h + FOCUS_GAP * 2.0F,
          RADIUS + FOCUS_GAP,
          1.0F,
          Theme.withAlpha(t.ink(), 0.55F));
    }
    if (channel.widget != null) {
      channel.widget.draw(c, t, data, pulse, x, y, w, h);
      if (look.removable && look.glow > 0.01F) {
        remove(c, t, x + w - REMOVE - REMOVE_INSET * 2.0F, y, look);
      }
      return;
    }
    if (look.glow > 0.01F) {
      c.roundRect(
          x + w - MARK_INSET,
          y + MARK_TOP,
          MARK,
          MARK,
          MARK * 0.5F,
          Theme.withAlpha(t.red(), look.glow));
    } else if (channel.flagged) {
      c.roundRect(x + w - MARK_INSET, y + MARK_TOP, MARK, MARK, MARK * 0.5F, t.alert());
    }
    int ink = channel.enabled ? Theme.mix(t.ink2(), t.ink(), look.glow) : t.dotOff();
    float size = Math.min(h * ART_SHARE, ART_MAX);
    float artX = x + (w - size) * 0.5F;
    float artY = y + h * ART_TOP;
    if (channel.art != null) {
      channel.art.draw(c, t, artX, artY, size, ink, look.glow > 0.5F, look.wave);
    } else {
      Pictograms.draw(c, t, channel.glyph, artX, artY, size, ink, look.glow > 0.5F, look.wave);
    }
    float labelY = artY + size + h * LABEL_GAP;
    float room = w - TEXT_PAD * 2.0F;
    c.pushClip(x + TEXT_PAD, y, room, h);
    c.text(
        FontStyle.LABEL,
        channel.title,
        x + (w - Math.min(room, c.textWidth(FontStyle.LABEL, channel.title))) * 0.5F,
        labelY,
        ink);
    if (!channel.caption.isEmpty()) {
      float width = Math.min(room, c.textWidth(FontStyle.CAPTION, channel.caption));
      c.text(
          FontStyle.CAPTION,
          channel.caption,
          x + (w - width) * 0.5F,
          labelY + c.lineHeight(FontStyle.LABEL) + 1.0F,
          t.grey());
    }
    c.popClip();
  }

  private static void remove(Canvas c, Theme t, float x, float y, Look look) {
    float cx = x + REMOVE_INSET;
    float cy = y + REMOVE_INSET;
    c.roundRect(
        cx,
        cy,
        REMOVE,
        REMOVE,
        REMOVE * 0.5F,
        Theme.mix(t.surface2(), t.ink(), look.removeHover ? 0.2F : 0.0F));
    float bar = 8.0F;
    float mid = REMOVE * 0.5F;
    c.roundRect(
        cx + mid - bar * 0.5F,
        cy + mid - 0.6F,
        bar,
        1.2F,
        0.6F,
        look.removeHover ? t.ink() : t.ink2());
  }
}
