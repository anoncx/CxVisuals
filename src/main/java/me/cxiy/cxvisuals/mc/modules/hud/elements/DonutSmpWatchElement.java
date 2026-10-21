package me.cxiy.cxvisuals.mc.modules.hud.elements;

import java.util.List;
import java.util.Locale;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.TrendChart;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.mc.modules.donutsmp.DonutSMP;
import me.cxiy.cxvisuals.mc.modules.donutsmp.DonutSMP.Watched;
import me.cxiy.cxvisuals.modules.donutsmp.PriceHistory;
import me.cxiy.cxvisuals.modules.donutsmp.Watchlist;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;

public final class DonutSmpWatchElement extends HudElement {
  public enum Variant {
    LIST,
    STRIP,
    TICKER
  }

  private static final float ROW = 24.0F;
  private static final float ICON = 16.0F;
  private static final float FOOT = 7.0F;
  private static final float GAP = 10.0F;
  private static final float MIN_WIDTH = 176.0F;
  private static final float NAME_LIMIT = 118.0F;
  private static final float CHANGE_WIDTH = 46.0F;
  private static final float SPARK_WIDTH = 40.0F;
  private static final float CELL = 62.0F;
  private static final float CELL_ICON = 24.0F;
  private static final float STRIP_PAD = 8.0F;
  private static final float STRIP_HEIGHT = 72.0F;
  private static final float TICKER_HEIGHT = 38.0F;
  private static final float TICKER_ICON = 22.0F;
  private static final float TICKER_PAD = 12.0F;
  private static final float PERIOD = 4.0F;
  private static final float FADE_IN = 0.28F;
  private static final float FADE_OUT = 0.2F;
  private static final float SLIDE = 7.0F;
  private static final float PAGE_DOT = 4.0F;
  private static final float PAGE_GAP = 4.0F;
  private static final float FLAT = 0.0005F;
  private static final int UP = 0xFF3DDC5A;
  private static final int AMBER = 0xFFE9D53A;
  private static final String CAPTION = Lang.key("WATCHING");
  private static final String EMPTY = Lang.key("Nothing watched yet");

  public final EnumSetting<Variant> variant;
  public final NumberSetting limit;
  public final BoolSetting graphs;

  private final TrendChart chart = new TrendChart();
  private List<Watched> rows = List.of();
  private String[] names = new String[0];
  private String[] changes = new String[0];
  private float[] priceWidths = new float[0];
  private float captionWidth = -1.0F;
  private float nameColumn;
  private float priceColumn;
  private int stamp = -1;
  private boolean measured;
  private int shownCount = -1;
  private int page;
  private float clock;

  public DonutSmpWatchElement(Settings settings) {
    super(
        settings,
        "donutsmp_watch",
        "DonutSMP watchlist",
        Category.INFO,
        false,
        Anchor.RIGHT,
        -10.0,
        220.0);
    variant =
        group()
            .enumOf("variant", "Style", Variant.LIST, "List", "Strip", "Ticker")
            .describe(
                "List shows rows, Strip a row of cells, Ticker cycles through one item at a time.");
    limit =
        group()
            .number("limit", "Items", 5.0, 2.0, Watchlist.LIMIT, 1.0, "")
            .describe("How many watched items to show.");
    graphs =
        group()
            .bool("graphs", "Graphs", true)
            .describe("Draws a small price curve on every row of the list.");
  }

  @Override
  public float radius() {
    return variant.get() == Variant.TICKER ? TICKER_HEIGHT * 0.5F : 16.0F;
  }

  @Override
  public boolean elastic() {
    return true;
  }

  @Override
  public boolean present(HudData data) {
    return data.editing || DonutSMP.onServer() && !DonutSMP.watched().isEmpty();
  }

  @Override
  public void sample(HudData data) {
    int revision = DonutSMP.watchedRevision();
    if (revision == stamp) {
      return;
    }
    stamp = revision;
    rows = DonutSMP.watched();
    measured = false;
  }

  private int shown() {
    return Math.min(limit.get().intValue(), rows.size());
  }

  @Override
  public void measure(Canvas c, HudData data) {
    if (captionWidth < 0.0F || reworded()) {
      captionWidth = c.textWidth(FontStyle.CAPTION, Lang.tr(CAPTION));
    }
    int count = shown();
    if (!measured || shownCount != count) {
      measured = true;
      shownCount = count;
      names = new String[count];
      changes = new String[count];
      priceWidths = new float[count];
      nameColumn = 0.0F;
      priceColumn = 0.0F;
      for (int i = 0; i < count; i++) {
        Watched row = rows.get(i);
        names[i] = fit(c, row.title());
        changes[i] =
            Float.isNaN(row.change())
                ? ""
                : Math.abs(row.change()) < FLAT
                    ? "0.0%"
                    : String.format(Locale.ROOT, "%+.1f%%", row.change() * 100.0F);
        priceWidths[i] = c.textWidth(FontStyle.LABEL, row.price());
        nameColumn = Math.max(nameColumn, c.textWidth(FontStyle.LABEL, names[i]));
        priceColumn = Math.max(priceColumn, priceWidths[i]);
      }
    }
    switch (variant.get()) {
      case LIST -> {
        float graph = graphs.get() ? SPARK_WIDTH + GAP : 0.0F;
        float body =
            HudPaint.INSET * 2.0F
                + ICON
                + 8.0F
                + nameColumn
                + GAP
                + graph
                + priceColumn
                + GAP
                + CHANGE_WIDTH;
        w =
            (float)
                Math.ceil(
                    Math.max(
                        MIN_WIDTH, Math.max(body, captionWidth + HudPaint.INSET * 2.0F + 48.0F)));
        h = HudPaint.HEAD + Math.max(1, count) * ROW + FOOT;
      }
      case STRIP -> {
        w = STRIP_PAD * 2.0F + Math.max(2, count) * CELL;
        h = STRIP_HEIGHT;
      }
      case TICKER -> {
        float dots = count > 1 ? GAP + count * PAGE_DOT + (count - 1) * PAGE_GAP : 0.0F;
        w =
            (float)
                Math.ceil(
                    TICKER_PAD * 2.0F
                        + TICKER_ICON
                        + 9.0F
                        + Math.max(nameColumn, 70.0F)
                        + GAP
                        + Math.max(priceColumn, 48.0F)
                        + GAP
                        + CHANGE_WIDTH
                        - 8.0F
                        + dots);
        h = TICKER_HEIGHT;
      }
    }
  }

  private static String fit(Canvas c, String text) {
    if (c.textWidth(FontStyle.LABEL, text) <= NAME_LIMIT) {
      return text;
    }
    int end = text.length();
    while (end > 1 && c.textWidth(FontStyle.LABEL, text.substring(0, end) + "…") > NAME_LIMIT) {
      end--;
    }
    return text.substring(0, end) + "…";
  }

  private static int tone(Theme t, float change) {
    return Float.isNaN(change) || Math.abs(change) < FLAT
        ? t.grey()
        : change > 0.0F ? UP : t.alert();
  }

  @Override
  public void draw(Canvas c, Theme t, HudData data, float x, float y, float dt) {
    int count = shown();
    switch (variant.get()) {
      case LIST -> drawList(c, t, x, y, count);
      case STRIP -> drawStrip(c, t, x, y, count);
      case TICKER -> drawTicker(c, t, x, y, count, dt);
    }
  }

  private void empty(Canvas c, Theme t, float x, float y, float top) {
    String text = Lang.tr(EMPTY);
    c.text(
        FontStyle.CAPTION,
        text,
        x + (w - c.textWidth(FontStyle.CAPTION, text)) * 0.5F,
        top + (ROW - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        t.grey());
  }

  private void drawList(Canvas c, Theme t, float x, float y, int count) {
    HudPaint.plate(c, t, this, x, y, w, h, 16.0F);
    HudPaint.header(c, t, x, y, w, Lang.tr(CAPTION), DonutSMP.stale() ? Lang.tr("OLD") : null);
    if (count == 0) {
      empty(c, t, x, y, y + HudPaint.HEAD);
      return;
    }
    boolean curves = graphs.get() && DonutSMP.history() != null;
    long now = PriceHistory.now();
    long span = DonutSMP.windowSeconds();
    for (int i = 0; i < count; i++) {
      Watched row = rows.get(i);
      float ry = y + HudPaint.HEAD + i * ROW;
      c.stack(row.handle(), x + HudPaint.INSET, ry + (ROW - ICON) * 0.5F, ICON);
      float lineY = ry + (ROW - c.lineHeight(FontStyle.LABEL)) * 0.5F;
      c.text(FontStyle.LABEL, names[i], x + HudPaint.INSET + ICON + 8.0F, lineY, t.ink());
      float priceRight = x + w - HudPaint.INSET - CHANGE_WIDTH - GAP;
      c.text(FontStyle.LABEL, row.price(), priceRight - priceWidths[i], lineY, t.ink2());
      if (curves) {
        float graphRight = priceRight - priceColumn - GAP;
        chart.sparkline(
            c,
            DonutSMP.history(),
            row.id(),
            DonutSMP.kind(),
            now - span,
            now,
            graphRight - SPARK_WIDTH,
            ry + 5.0F,
            SPARK_WIDTH,
            ROW - 10.0F,
            tone(t, row.change()));
      }
      String change = changes[i];
      if (!change.isEmpty()) {
        c.text(
            FontStyle.CAPTION,
            change,
            x + w - HudPaint.INSET - c.textWidth(FontStyle.CAPTION, change),
            ry + (ROW - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
            tone(t, row.change()));
      }
    }
  }

  private void drawStrip(Canvas c, Theme t, float x, float y, int count) {
    HudPaint.plate(c, t, this, x, y, w, h, 16.0F);
    if (count == 0) {
      empty(c, t, x, y, y + (h - ROW) * 0.5F);
      return;
    }
    for (int i = 0; i < count; i++) {
      Watched row = rows.get(i);
      float cx = x + STRIP_PAD + i * CELL;
      if (i > 0) {
        c.roundRect(cx - 0.5F, y + 12.0F, 1.0F, h - 24.0F, 0.5F, t.hair());
      }
      c.stack(row.handle(), cx + (CELL - CELL_ICON) * 0.5F, y + 10.0F, CELL_ICON);
      float priceY = y + 10.0F + CELL_ICON + 5.0F;
      c.text(FontStyle.LABEL, row.price(), cx + (CELL - priceWidths[i]) * 0.5F, priceY, t.ink());
      String change = changes[i];
      if (!change.isEmpty()) {
        c.text(
            FontStyle.CAPTION,
            change,
            cx + (CELL - c.textWidth(FontStyle.CAPTION, change)) * 0.5F,
            priceY + c.lineHeight(FontStyle.LABEL),
            tone(t, row.change()));
      }
    }
    if (DonutSMP.stale()) {
      HudPaint.dot(c, x + w - 12.0F, y + 8.0F, AMBER);
    }
  }

  private void drawTicker(Canvas c, Theme t, float x, float y, int count, float dt) {
    HudPaint.plate(c, t, this, x, y, w, h, h * 0.5F);
    if (count == 0) {
      empty(c, t, x, y, y + (h - ROW) * 0.5F);
      return;
    }
    clock += dt;
    if (clock >= PERIOD) {
      clock -= PERIOD;
      page = (page + 1) % count;
    }
    page = Math.min(page, count - 1);
    float fadeIn = Math.min(1.0F, clock / FADE_IN);
    float fadeOut = Math.min(1.0F, (PERIOD - clock) / FADE_OUT);
    float alpha = smooth(Math.min(fadeIn, fadeOut));
    Watched row = rows.get(page);
    float slide = (1.0F - smooth(fadeIn)) * SLIDE;
    c.pushAlpha(alpha);
    float iconX = x + TICKER_PAD;
    c.stack(row.handle(), iconX, y + (h - TICKER_ICON) * 0.5F + slide, TICKER_ICON);
    float textX = iconX + TICKER_ICON + 9.0F;
    c.text(
        FontStyle.LABEL,
        names[page],
        textX,
        y + (h - c.lineHeight(FontStyle.LABEL)) * 0.5F + slide,
        t.ink());
    float dots = count > 1 ? GAP + count * PAGE_DOT + (count - 1) * PAGE_GAP : 0.0F;
    float right = x + w - TICKER_PAD - dots;
    String change = changes[page];
    float changeWidth = change.isEmpty() ? 0.0F : c.textWidth(FontStyle.CAPTION, change);
    if (!change.isEmpty()) {
      float pillW = changeWidth + 14.0F;
      float pillH = 18.0F;
      float pillX = right - pillW;
      float pillY = y + (h - pillH) * 0.5F + slide;
      int tone = tone(t, row.change());
      c.roundRect(pillX, pillY, pillW, pillH, pillH * 0.5F, Theme.withAlpha(tone, 0.16F));
      c.text(
          FontStyle.CAPTION,
          change,
          pillX + 7.0F,
          pillY + (pillH - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
          tone);
      right = pillX - 8.0F;
    }
    c.text(
        FontStyle.ROW,
        row.price(),
        right - c.textWidth(FontStyle.ROW, row.price()),
        y + (h - c.lineHeight(FontStyle.ROW)) * 0.5F + slide,
        t.ink2());
    c.popAlpha();
    if (count > 1) {
      float dotX = x + w - TICKER_PAD - dots + GAP;
      for (int i = 0; i < count; i++) {
        int tint = i == page ? t.ink() : t.dotOff();
        c.roundRect(
            dotX + i * (PAGE_DOT + PAGE_GAP),
            y + (h - PAGE_DOT) * 0.5F,
            PAGE_DOT,
            PAGE_DOT,
            PAGE_DOT * 0.5F,
            tint);
      }
    }
  }

  private static float smooth(float value) {
    return value * value * (3.0F - 2.0F * value);
  }
}
