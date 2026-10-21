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
import me.cxiy.cxvisuals.modules.hud.Detail;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

public final class NetworkElement extends HudElement {
  private static final String CAPTION = Lang.key("NETWORK");
  private static final String UNIT = "ms";
  private static final String MISSING = "--";
  private static final String[] STATUS =
      Lang.keys("No signal", "Lagging", "Lagging", "Unstable", "Stable", "Stable");
  private static final String[] LABELS = Lang.keys("Jitter", "TPS", "Packets /s");
  private static final int HISTORY = 24;
  private static final int LEVELS = 5;
  private static final String WIDEST = "000";
  private static final float WIDTH = 150.0F;
  private static final float GAP = 14.0F;
  private static final float GRAPH_TOP = 32.0F;
  private static final float HEADROOM = 1.15F;
  private static final float FLOOR = 50.0F;
  private static final float FULL_HEIGHT =
      HudPaint.TILE - HudPaint.INSET + 8.0F + HudPaint.ROW * LABELS.length + 7.0F;

  public final EnumSetting<Detail> detail = detail();

  private final float[] latencies = new float[HISTORY];
  private final float[] levels = new float[HISTORY];
  private final String[] values = {"", "", ""};
  private final float[] valueWidths = new float[LABELS.length];
  private final Rolling rolling = new Rolling();
  private int head;
  private int signal;
  private int jitter = -1;
  private int tenths = Integer.MIN_VALUE;
  private int packets = -1;
  private float peak = FLOOR;
  private boolean stale = true;
  private boolean labelled;
  private float unitWidth;
  private float captionWidth;
  private float slotWidth;
  private float statusWidth;
  private float valueWidth = -1.0F;
  private String measuredStatus;

  public NetworkElement(Settings settings) {
    super(settings, "network", "Network", Category.INFO, false, Anchor.TOP_LEFT, 178.0, 10.0);
    rolling.snap(MISSING);
  }

  public String status() {
    return Lang.tr(STATUS[signal]);
  }

  @Override
  public float radius() {
    return 18.0F;
  }

  @Override
  public void sample(HudData data) {
    signal = HudPaint.signal(data.ping);
    latencies[head] = data.ping;
    levels[head] = signal;
    head = (head + 1) % HISTORY;
    float most = FLOOR;
    float swing = 0.0F;
    for (int i = 0; i < HISTORY; i++) {
      most = Math.max(most, latencies[i]);
      if (i > 0) {
        swing += Math.abs(latencies[(head + i) % HISTORY] - latencies[(head + i - 1) % HISTORY]);
      }
    }
    peak = most * HEADROOM;
    int spread = Math.round(swing / (HISTORY - 1));
    if (spread != jitter) {
      jitter = spread;
      set(0, spread + " ms");
    }
    int rate = data.tps < 0.0F ? -1 : Math.round(data.tps * 10.0F);
    if (rate != tenths) {
      tenths = rate;
      set(1, rate < 0 ? MISSING : rate / 10 + "." + rate % 10);
    }
    int flow = Math.round(data.packetsIn) * 1000 + Math.round(data.packetsOut);
    if (flow != packets) {
      packets = flow;
      set(2, Math.round(data.packetsIn) + " / " + Math.round(data.packetsOut));
    }
    String text = data.ping < 0.0F ? MISSING : Integer.toString(Math.round(data.ping));
    if (!text.equals(rolling.text())) {
      if (MISSING.equals(rolling.text()) || MISSING.equals(text)) {
        rolling.snap(text);
      } else {
        rolling.set(text);
      }
      valueWidth = -1.0F;
    }
  }

  private void set(int row, String value) {
    values[row] = value;
    stale = true;
  }

  @Override
  public void measure(Canvas c, HudData data) {
    if (!labelled || reworded()) {
      labelled = true;
      unitWidth = c.textWidth(FontStyle.CAPTION, UNIT);
      captionWidth = c.textWidth(FontStyle.CAPTION, Lang.tr(CAPTION));
      slotWidth = c.textWidth(FontStyle.ROW, WIDEST);
    }
    if (stale) {
      stale = false;
      for (int i = 0; i < LABELS.length; i++) {
        valueWidths[i] = c.textWidth(FontStyle.MONO, values[i]);
      }
    }
    String status = Lang.tr(STATUS[signal]);
    if (status != measuredStatus) {
      measuredStatus = status;
      statusWidth = c.textWidth(FontStyle.ROW, status);
    }
    if (valueWidth < 0.0F) {
      valueWidth = rolling.width(c, FontStyle.ROW);
    }
    float right = detail.get() == Detail.FULL ? slotWidth + 3.0F + unitWidth : statusWidth;
    w =
        (float)
            Math.ceil(
                Math.max(
                    WIDTH,
                    HudPaint.INSET * 2.0F
                        + HudPaint.SIGNAL_WIDTH
                        + 7.0F
                        + captionWidth
                        + GAP
                        + right));
    h = detail.get() == Detail.FULL ? FULL_HEIGHT : HudPaint.TILE;
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    boolean full = detail.get() == Detail.FULL;
    boolean weak = signal <= 2;
    int accent = weak ? t.red() : t.ink();
    HudPaint.plate(c, t, this, x, y, w, h, radius());
    HudPaint.bars(c, x + HudPaint.INSET, y + 9.0F, signal, accent, t.dotOff());
    c.text(
        FontStyle.CAPTION,
        Lang.tr(CAPTION),
        x + HudPaint.INSET + HudPaint.SIGNAL_WIDTH + 7.0F,
        y + 8.0F,
        t.grey());
    float right = x + w - HudPaint.INSET;
    if (full) {
      c.text(FontStyle.CAPTION, UNIT, right - unitWidth, y + 8.0F, t.grey());
      rolling.draw(
          c,
          FontStyle.ROW,
          right - unitWidth - 3.0F - valueWidth,
          y + 7.0F,
          true,
          t.ink(),
          dt,
          data.still);
    } else {
      c.text(FontStyle.ROW, Lang.tr(STATUS[signal]), right - statusWidth, y + 7.0F, accent);
    }
    float graphHeight = HudPaint.TILE - GRAPH_TOP - HudPaint.INSET;
    HudPaint.spark(
        c,
        x + HudPaint.INSET,
        y + GRAPH_TOP,
        w - HudPaint.INSET * 2.0F,
        graphHeight,
        full ? latencies : levels,
        head,
        full ? peak : LEVELS,
        accent,
        t.dotOff());
    if (!full) {
      return;
    }
    float rowsTop = y + HudPaint.TILE - HudPaint.INSET;
    c.roundRect(
        x + HudPaint.INSET, rowsTop + 3.0F, w - HudPaint.INSET * 2.0F, 1.0F, 0.0F, t.hair());
    for (int i = 0; i < LABELS.length; i++) {
      HudPaint.pair(
          c,
          t,
          x,
          rowsTop + 8.0F + i * HudPaint.ROW,
          w,
          Lang.tr(LABELS[i]),
          values[i],
          valueWidths[i],
          t.ink());
    }
  }
}
