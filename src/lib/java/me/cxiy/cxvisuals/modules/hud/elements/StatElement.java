package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.gui.hud.Rolling;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

public final class StatElement extends HudElement {
  public interface Reading {
    float read(HudData data);
  }

  public enum Variant {
    PILL,
    TILE,
    GRAPH
  }

  public static final float NO_LIMIT = 0.0F;

  private static final int HISTORY = 24;
  private static final int SEGMENTS = 10;
  private static final float GRAPH_WIDTH = 150.0F;
  private static final float PILL_PAD = 12.0F;
  private static final float HEADROOM = 1.15F;
  private static final String MISSING = "--";
  private static final String WHOLE = "000";
  private static final String TENTHS = "00.0";

  public final EnumSetting<Variant> variant;

  private final String caption;
  private final String unit;
  private final boolean tenths;
  private final float ceiling;
  private final float low;
  private final float high;
  private final Reading reading;
  private final float[] history = new float[HISTORY];
  private final Rolling rolling = new Rolling();

  private Variant measured;
  private int head;
  private float peak = 1.0F;
  private float value = HudData.UNKNOWN;
  private boolean alert;
  private String text = MISSING;
  private int textFor = Integer.MIN_VALUE;
  private float captionWidth = -1.0F;
  private float unitWidth;
  private float slot;
  private float valueWidth;

  public StatElement(
      Settings settings,
      String id,
      String name,
      String caption,
      String unit,
      boolean tenths,
      float ceiling,
      float low,
      float high,
      Reading reading,
      boolean shown,
      double homeY) {
    super(settings, id, name, Category.INFO, shown, Anchor.TOP_LEFT, 10.0, homeY);
    this.caption = caption;
    this.unit = unit;
    this.tenths = tenths;
    this.ceiling = ceiling;
    this.low = low;
    this.high = high;
    this.reading = reading;
    this.variant = group().enumOf("variant", "Style", Variant.PILL, "Pill", "Tile", "Graph");
    this.rolling.snap(MISSING);
  }

  public String text() {
    return text;
  }

  public boolean alert() {
    return alert;
  }

  @Override
  public float radius() {
    return variant.get() == Variant.PILL ? HudPaint.PILL * 0.5F : 18.0F;
  }

  @Override
  public void sample(HudData data) {
    value = reading.read(data);
    boolean known = value >= 0.0F;
    history[head] = known ? value : 0.0F;
    head = (head + 1) % HISTORY;
    if (ceiling > 0.0F) {
      peak = ceiling;
    } else {
      float most = 1.0F;
      for (int i = 0; i < HISTORY; i++) {
        most = Math.max(most, history[i]);
      }
      peak = most * HEADROOM;
    }
    alert = known && ((low > 0.0F && value < low) || (high > 0.0F && value > high));
    int scaled = known ? Math.round(tenths ? value * 10.0F : value) : -1;
    if (scaled != textFor) {
      textFor = scaled;
      boolean fresh = text == MISSING || !known;
      text = !known ? MISSING : tenths ? scaled / 10 + "." + scaled % 10 : Integer.toString(scaled);
      if (fresh) {
        rolling.snap(text);
      } else {
        rolling.set(text);
      }
      valueWidth = -1.0F;
    }
  }

  @Override
  public void measure(Canvas c, HudData data) {
    if (captionWidth < 0.0F || reworded()) {
      captionWidth = c.textWidth(FontStyle.CAPTION, Lang.tr(caption));
      unitWidth = unit.isEmpty() ? 0.0F : c.textWidth(FontStyle.CAPTION, unit);
    }
    Variant style = variant.get();
    FontStyle font = style == Variant.TILE ? FontStyle.DOTS : FontStyle.ROW;
    if (style != measured) {
      measured = style;
      valueWidth = -1.0F;
    }
    if (valueWidth < 0.0F) {
      valueWidth = rolling.width(c, font);
      slot = Math.max(valueWidth, c.textWidth(font, tenths ? TENTHS : WHOLE));
    }
    switch (style) {
      case PILL -> {
        w =
            (float)
                Math.ceil(
                    PILL_PAD * 2.0F
                        + HudPaint.DOT
                        + 7.0F
                        + captionWidth
                        + 8.0F
                        + slot
                        + (unitWidth > 0.0F ? unitWidth + 3.0F : 0.0F));
        h = HudPaint.PILL;
      }
      case TILE -> {
        w =
            Math.max(
                HudPaint.TILE,
                (float)
                    Math.ceil(
                        HudPaint.INSET * 2.0F
                            + slot
                            + (unitWidth > 0.0F ? unitWidth + 3.0F : 0.0F)));
        h = HudPaint.TILE;
      }
      case GRAPH -> {
        w = GRAPH_WIDTH;
        h = HudPaint.TILE;
      }
    }
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    int accent = alert ? t.red() : t.ink();
    int signal = alert ? t.red() : t.dotOff();
    switch (variant.get()) {
      case PILL -> {
        HudPaint.plate(c, t, this, x, y, w, h, h * 0.5F);
        HudPaint.dot(c, x + PILL_PAD, y + (h - HudPaint.DOT) * 0.5F, signal);
        float cursor = x + PILL_PAD + HudPaint.DOT + 7.0F;
        c.text(
            FontStyle.CAPTION,
            Lang.tr(caption),
            cursor,
            HudPaint.centred(c, FontStyle.CAPTION, y, h),
            t.grey());
        cursor += captionWidth + 8.0F + slot;
        rolling.draw(
            c,
            FontStyle.ROW,
            cursor - valueWidth,
            HudPaint.centred(c, FontStyle.ROW, y, h),
            true,
            t.ink(),
            dt,
            data.still);
        if (unitWidth > 0.0F) {
          c.text(
              FontStyle.CAPTION,
              unit,
              cursor + 3.0F,
              HudPaint.centred(c, FontStyle.CAPTION, y, h),
              t.grey());
        }
      }
      case TILE -> {
        HudPaint.plate(c, t, this, x, y, w, h, 18.0F);
        c.text(FontStyle.CAPTION, Lang.tr(caption), x + HudPaint.INSET, y + 8.0F, t.grey());
        HudPaint.dot(c, x + w - HudPaint.INSET - HudPaint.DOT, y + 13.0F, signal);
        float valueTop = y + 27.0F;
        rolling.draw(
            c, FontStyle.DOTS, x + HudPaint.INSET - 1.0F, valueTop, false, t.ink(), dt, data.still);
        if (unitWidth > 0.0F) {
          float unitTop =
              valueTop + c.lineHeight(FontStyle.DOTS) - c.lineHeight(FontStyle.CAPTION) - 3.0F;
          c.text(
              FontStyle.CAPTION, unit, x + HudPaint.INSET + valueWidth + 3.0F, unitTop, t.grey());
        }
        HudPaint.segments(
            c,
            x + HudPaint.INSET,
            y + h - 15.0F,
            w - HudPaint.INSET * 2.0F,
            4.0F,
            SEGMENTS,
            value / peak,
            accent,
            t.dotOff());
      }
      case GRAPH -> {
        HudPaint.plate(c, t, this, x, y, w, h, 18.0F);
        c.text(FontStyle.CAPTION, Lang.tr(caption), x + HudPaint.INSET, y + 8.0F, t.grey());
        float right = x + w - HudPaint.INSET;
        if (unitWidth > 0.0F) {
          c.text(FontStyle.CAPTION, unit, right - unitWidth, y + 8.0F, t.grey());
          right -= unitWidth + 3.0F;
        }
        rolling.draw(c, FontStyle.ROW, right - valueWidth, y + 7.0F, true, t.ink(), dt, data.still);
        HudPaint.spark(
            c,
            x + HudPaint.INSET,
            y + 32.0F,
            w - HudPaint.INSET * 2.0F,
            h - 32.0F - HudPaint.INSET,
            history,
            head,
            peak,
            accent,
            t.dotOff());
      }
    }
  }
}
