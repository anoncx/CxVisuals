package me.cxiy.cxvisuals.gui.hud;

import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

public final class HudPaint {
  public static final float PILL = 28.0F;
  public static final float TILE = 78.0F;
  public static final float INSET = 11.0F;
  public static final float DOT = 5.0F;
  public static final float HEAD = 30.0F;
  public static final float ROW = 17.0F;
  public static final float CELL_WIDTH = 32.0F;
  public static final float CELL_HEIGHT = 38.0F;
  public static final float SIGNAL_WIDTH = 23.0F;
  public static final float SIGNAL_HEIGHT = 11.0F;

  private static final float ICON = 16.0F;
  private static final float WEAR_WIDTH = 18.0F;
  private static final float WORN = 0.2F;
  private static final int MAX_COUNT = 1000;
  private static final String[] COUNTS = new String[MAX_COUNT];
  private static final int SIGNAL_BARS = 5;
  private static final float[] SIGNAL_STEPS = {150.0F, 300.0F, 600.0F, 1000.0F};
  private static final float SEGMENT_GAP = 2.0F;
  private static final float BAR_GAP = 2.0F;
  private static final float BAR_MIN = 2.0F;
  private static final float FOLD_FROM = 0.6F;

  private HudPaint() {}

  public static void plate(Canvas c, Theme t, float x, float y, float w, float h, float radius) {
    if ((t.cardShadow() >>> 24) != 0) {
      c.shadow(x, y, w, h, radius, 12.0F, 5.0F, t.cardShadow());
    }
    if (t.glass()) {
      c.glass(x, y, w, h, radius, t.paper());
    } else {
      c.roundRect(x, y, w, h, radius, t.paper());
    }
    c.roundRectBorder(x, y, w, h, radius, 1.0F, t.hair());
    c.rim(x, y, w, h, radius, 1.0F, Math.min(h, radius * 1.4F), Depth.rimColor(t));
  }

  public static void plate(
      Canvas c, Theme t, HudElement element, float x, float y, float w, float h, float radius) {
    if (element.foldW == 1.0F && element.foldH == 1.0F) {
      plate(c, t, x, y, w, h, radius);
    } else {
      float width = folded(w, h, element.foldW, w);
      float height = folded(w, h, element.foldH, h);
      plate(
          c,
          t,
          x + (w - width) * 0.5F,
          y + (h - height) * 0.5F,
          width,
          height,
          Math.min(radius, Math.min(width, height) * 0.5F));
    }
    if (element.reveal < 1.0F && !element.veiled) {
      element.veiled = true;
      c.pushAlpha(element.reveal);
    }
  }

  public static float folded(float w, float h, float fold, float full) {
    float seed = Math.min(w, h) * FOLD_FROM;
    return seed + (full - seed) * fold;
  }

  public static void dot(Canvas c, float x, float y, int argb) {
    c.roundRect(x, y, DOT, DOT, DOT * 0.5F, argb);
  }

  public static void segments(
      Canvas c, float x, float y, float w, float h, int count, float fraction, int on, int off) {
    float width = (w - SEGMENT_GAP * (count - 1)) / count;
    int lit = Math.round(Math.max(0.0F, Math.min(1.0F, fraction)) * count);
    for (int i = 0; i < count; i++) {
      c.roundRect(x + i * (width + SEGMENT_GAP), y, width, h, h * 0.5F, i < lit ? on : off);
    }
  }

  public static void spark(
      Canvas c,
      float x,
      float y,
      float w,
      float h,
      float[] ring,
      int head,
      float peak,
      int latest,
      int trail) {
    int count = ring.length;
    float width = (w - BAR_GAP * (count - 1)) / count;
    float scale = peak <= 0.0F ? 0.0F : h / peak;
    for (int i = 0; i < count; i++) {
      float value = ring[(head + i) % count];
      float height = Math.max(BAR_MIN, Math.min(h, value * scale));
      c.roundRect(
          x + i * (width + BAR_GAP),
          y + h - height,
          width,
          height,
          width * 0.5F,
          i == count - 1 ? latest : trail);
    }
  }

  public static void header(
      Canvas c, Theme t, float x, float y, float w, String caption, String trailing) {
    c.text(FontStyle.CAPTION, caption, x + INSET, y + 8.0F, t.grey());
    if (trailing != null) {
      c.text(
          FontStyle.MONO,
          trailing,
          x + w - INSET - c.textWidth(FontStyle.MONO, trailing),
          y + 8.0F,
          t.ink2());
    }
    c.roundRect(x + INSET, y + HEAD - 3.0F, w - INSET * 2.0F, 1.0F, 0.0F, t.hair());
  }

  public static void pair(
      Canvas c,
      Theme t,
      float x,
      float top,
      float w,
      String label,
      String value,
      float valueWidth,
      int argb) {
    c.text(FontStyle.CAPTION, label, x + INSET, centred(c, FontStyle.CAPTION, top, ROW), t.grey());
    c.text(
        FontStyle.MONO,
        value,
        x + w - INSET - valueWidth,
        centred(c, FontStyle.MONO, top, ROW),
        argb);
  }

  public static int signal(float latency) {
    if (latency < 0.0F) {
      return 0;
    }
    for (int i = 0; i < SIGNAL_STEPS.length; i++) {
      if (latency < SIGNAL_STEPS[i]) {
        return SIGNAL_BARS - i;
      }
    }
    return 1;
  }

  public static void bars(Canvas c, float x, float y, int level, int on, int off) {
    for (int i = 0; i < SIGNAL_BARS; i++) {
      float height = 3.0F + i * 2.0F;
      c.roundRect(
          x + i * 5.0F, y + SIGNAL_HEIGHT - height, 3.0F, height, 1.0F, i < level ? on : off);
    }
  }

  public static void gear(
      Canvas c, Theme t, HudData data, int slot, float x, float y, boolean wear) {
    c.item(slot, x + (CELL_WIDTH - ICON) * 0.5F, y + 5.0F, ICON);
    float health = data.durability[slot];
    if (health >= 0.0F) {
      if (wear) {
        float barX = x + (CELL_WIDTH - WEAR_WIDTH) * 0.5F;
        c.roundRect(barX, y + 27.0F, WEAR_WIDTH, 3.0F, 1.5F, t.dotOff());
        c.roundRect(
            barX,
            y + 27.0F,
            Math.max(3.0F, WEAR_WIDTH * health),
            3.0F,
            1.5F,
            health < WORN ? t.red() : t.ink());
      }
    } else if (data.count[slot] > 1) {
      String amount = count(data.count[slot]);
      c.text(
          FontStyle.SMALL,
          amount,
          x + (CELL_WIDTH - c.textWidth(FontStyle.SMALL, amount)) * 0.5F,
          y + 21.0F,
          t.ink2());
    }
  }

  public static float centred(Canvas c, FontStyle font, float top, float height) {
    return top + (height - c.lineHeight(font)) * 0.5F;
  }

  public static String clock(int minutes, boolean military) {
    int hour = Math.floorMod(minutes / 60, 24);
    int minute = Math.floorMod(minutes, 60);
    StringBuilder text = new StringBuilder(8);
    if (military) {
      if (hour < 10) {
        text.append('0');
      }
      text.append(hour);
    } else {
      int twelve = hour % 12;
      text.append(twelve == 0 ? 12 : twelve);
    }
    text.append(':');
    if (minute < 10) {
      text.append('0');
    }
    text.append(minute);
    if (!military) {
      text.append(hour < 12 ? " AM" : " PM");
    }
    return text.toString();
  }

  private static String count(int amount) {
    int index = Math.min(amount, MAX_COUNT - 1);
    String cached = COUNTS[index];
    if (cached == null) {
      cached = Integer.toString(index);
      COUNTS[index] = cached;
    }
    return cached;
  }
}
