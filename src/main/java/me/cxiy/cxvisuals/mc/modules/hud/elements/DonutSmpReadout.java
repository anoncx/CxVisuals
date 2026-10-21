package me.cxiy.cxvisuals.mc.modules.hud.elements;

import java.util.Locale;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.TrendChart;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.gui.hud.Rolling;
import me.cxiy.cxvisuals.mc.modules.donutsmp.DonutSMP;
import me.cxiy.cxvisuals.modules.donutsmp.Trail;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

abstract class DonutSmpReadout extends HudElement {
  public enum Variant {
    PILL,
    TILE,
    GRAPH,
    BADGE
  }

  private static final float PILL_PAD = 13.0F;
  private static final float GAP = 8.0F;
  private static final float GRAPH_WIDTH = 196.0F;
  private static final float GRAPH_HEIGHT = 96.0F;
  private static final float GRAPH_TOP = 56.0F;
  private static final float GRAPH_LINE = 1.4F;
  private static final float BADGE_HEIGHT = 24.0F;
  private static final float BADGE_PAD = 10.0F;
  private static final float FLAT = 0.0005F;
  private static final int UP = 0xFF3DDC5A;
  private static final int AMBER = 0xFFE9D53A;

  public final EnumSetting<Variant> variant;

  private final String caption;
  private final Rolling rolling = new Rolling();
  private final TrendChart chart = new TrendChart();
  private float[] samples = new float[0];
  private int sampleCount;
  private String note = "";
  private String delta = "";
  private float change = Float.NaN;
  private int stamp = -1;
  private int trailStamp = -1;
  private Variant measured;
  private float captionWidth = -1.0F;
  private float valueWidth = -1.0F;
  private float noteWidth;
  private boolean seeded;

  protected DonutSmpReadout(
      Settings settings,
      String id,
      String name,
      String caption,
      Anchor home,
      double homeX,
      double homeY) {
    super(settings, id, name, Category.INFO, false, home, homeX, homeY);
    this.caption = caption;
    this.variant =
        group()
            .enumOf("variant", "Style", Variant.PILL, "Pill", "Tile", "Graph", "Badge")
            .describe(
                "Pill and Badge stay small, Tile adds a note, Graph draws the last ten minutes.");
  }

  protected abstract int revision();

  protected abstract String value();

  protected abstract String subtitle();

  protected abstract Trail trail();

  @Override
  public final boolean present(HudData data) {
    return data.editing || DonutSMP.onServer();
  }

  @Override
  public final float radius() {
    Variant style = variant.get();
    return style == Variant.PILL
        ? HudPaint.PILL * 0.5F
        : style == Variant.BADGE ? BADGE_HEIGHT * 0.5F : 18.0F;
  }

  @Override
  public final void sample(HudData data) {
    int revision = revision();
    if (revision != stamp) {
      stamp = revision;
      String next = value();
      if (seeded) {
        rolling.set(next);
      } else {
        rolling.snap(next);
        seeded = true;
      }
      note = subtitle();
      valueWidth = -1.0F;
    }
    Trail trail = trail();
    if (trail.revision() != trailStamp) {
      trailStamp = trail.revision();
      if (samples.length != trail.capacity()) {
        samples = new float[trail.capacity()];
      }
      trail.copyTo(samples);
      sampleCount = trail.size();
      change =
          sampleCount >= 2 && samples[0] > 0.0F
              ? (samples[sampleCount - 1] - samples[0]) / samples[0]
              : Float.NaN;
      delta =
          Float.isNaN(change)
              ? ""
              : Math.abs(change) < FLAT
                  ? "0.0%"
                  : String.format(Locale.ROOT, "%+.1f%%", change * 100.0F);
    }
  }

  private static FontStyle font(Variant style) {
    return style == Variant.TILE || style == Variant.GRAPH ? FontStyle.DOTS : FontStyle.ROW;
  }

  @Override
  public final void measure(Canvas c, HudData data) {
    if (captionWidth < 0.0F || reworded()) {
      captionWidth = c.textWidth(FontStyle.CAPTION, Lang.tr(caption));
    }
    Variant style = variant.get();
    if (style != measured) {
      measured = style;
      valueWidth = -1.0F;
    }
    if (valueWidth < 0.0F) {
      valueWidth = rolling.width(c, font(style));
      noteWidth = note.isEmpty() ? 0.0F : c.textWidth(FontStyle.SMALL, note);
    }
    switch (style) {
      case PILL -> {
        w = (float) Math.ceil(PILL_PAD * 2.0F + captionWidth + GAP + valueWidth);
        h = HudPaint.PILL;
      }
      case TILE -> {
        w =
            Math.max(
                HudPaint.TILE,
                (float)
                    Math.ceil(
                        HudPaint.INSET * 2.0F
                            + Math.max(valueWidth, Math.max(captionWidth, noteWidth))));
        h = HudPaint.TILE;
      }
      case GRAPH -> {
        w = Math.max(GRAPH_WIDTH, (float) Math.ceil(HudPaint.INSET * 2.0F + valueWidth + 56.0F));
        h = GRAPH_HEIGHT;
      }
      case BADGE -> {
        w = (float) Math.ceil(BADGE_PAD * 2.0F + HudPaint.DOT + 7.0F + valueWidth);
        h = BADGE_HEIGHT;
      }
    }
  }

  private static int status() {
    return DonutSMP.stale() ? AMBER : UP;
  }

  private int tone(Theme t) {
    return Float.isNaN(change) || Math.abs(change) < FLAT
        ? t.ink()
        : change > 0.0F ? UP : t.alert();
  }

  @Override
  public final void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    switch (variant.get()) {
      case PILL -> {
        HudPaint.plate(c, t, this, x, y, w, h, h * 0.5F);
        c.text(
            FontStyle.CAPTION,
            Lang.tr(caption),
            x + PILL_PAD,
            HudPaint.centred(c, FontStyle.CAPTION, y, h),
            t.grey());
        rolling.draw(
            c,
            FontStyle.ROW,
            x + PILL_PAD + captionWidth + GAP,
            HudPaint.centred(c, FontStyle.ROW, y, h),
            false,
            t.ink(),
            dt,
            data.still);
      }
      case TILE -> {
        HudPaint.plate(c, t, this, x, y, w, h, 18.0F);
        c.text(FontStyle.CAPTION, Lang.tr(caption), x + HudPaint.INSET, y + 8.0F, t.grey());
        rolling.draw(
            c,
            FontStyle.DOTS,
            x + HudPaint.INSET - 1.0F,
            y + 25.0F,
            false,
            t.ink(),
            dt,
            data.still);
        if (!note.isEmpty()) {
          c.text(
              FontStyle.SMALL,
              note,
              x + HudPaint.INSET,
              y + h - 9.0F - c.lineHeight(FontStyle.SMALL),
              t.grey());
        }
      }
      case GRAPH -> drawGraph(c, t, data, x, y, dt);
      case BADGE -> {
        HudPaint.plate(c, t, this, x, y, w, h, h * 0.5F);
        HudPaint.dot(c, x + BADGE_PAD, y + (h - HudPaint.DOT) * 0.5F, status());
        rolling.draw(
            c,
            FontStyle.ROW,
            x + BADGE_PAD + HudPaint.DOT + 7.0F,
            HudPaint.centred(c, FontStyle.ROW, y, h),
            false,
            t.ink(),
            dt,
            data.still);
      }
    }
  }

  private void drawGraph(Canvas c, Theme t, HudData state, float x, float y, float dt) {
    HudPaint.plate(c, t, this, x, y, w, h, 18.0F);
    float inset = HudPaint.INSET;
    HudPaint.dot(c, x + inset, y + 10.0F, status());
    c.text(
        FontStyle.CAPTION, Lang.tr(caption), x + inset + HudPaint.DOT + 6.0F, y + 8.0F, t.grey());
    String right = delta.isEmpty() ? note : delta;
    if (!right.isEmpty()) {
      FontStyle face = delta.isEmpty() ? FontStyle.SMALL : FontStyle.MONO;
      c.text(
          face,
          right,
          x + w - inset - c.textWidth(face, right),
          y + 8.0F,
          delta.isEmpty() ? t.grey() : tone(t));
    }
    rolling.draw(c, FontStyle.DOTS, x + inset - 1.0F, y + 24.0F, false, t.ink(), dt, state.still);
    float areaY = y + GRAPH_TOP;
    float areaH = h - GRAPH_TOP - inset;
    if (sampleCount >= 2) {
      chart.curve(
          c, samples, sampleCount, x + inset, areaY, w - inset * 2.0F, areaH, tone(t), GRAPH_LINE);
    } else {
      c.roundRect(x + inset, areaY + areaH * 0.5F, w - inset * 2.0F, 1.0F, 0.5F, t.hair());
    }
  }
}
