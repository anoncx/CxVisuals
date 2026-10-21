package me.cxiy.cxvisuals.gui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.module.ClientSettings.WindowMotion;
import me.cxiy.cxvisuals.core.settings.Choice;
import me.cxiy.cxvisuals.gui.spatial.Spring;
import me.cxiy.cxvisuals.modules.donutsmp.DonutSMPModule;
import me.cxiy.cxvisuals.modules.donutsmp.DonutSMPModule.Window;
import me.cxiy.cxvisuals.modules.donutsmp.Money;
import me.cxiy.cxvisuals.modules.donutsmp.PriceHistory;
import me.cxiy.cxvisuals.modules.donutsmp.TrendsHost;

public final class TrendsView {
  public static final int KEY_ESCAPE = 256;
  public static final int KEY_TAB = 258;
  public static final int KEY_RIGHT = 262;
  public static final int KEY_LEFT = 263;
  public static final int KEY_DOWN = 264;
  public static final int KEY_UP = 265;
  public static final int KEY_F = 70;
  public static final int KEY_W = 87;
  public static final int KEY_ENTER = 257;
  private static final int FLIPS = 4;

  private static final float WIDTH = 800.0F;
  private static final float HEIGHT = 500.0F;
  private static final float MARGIN = 16.0F;
  private static final float PAD = 16.0F;
  private static final float LIST = 296.0F;
  private static final float GAP = 14.0F;
  private static final float ROW = 46.0F;
  private static final float ROW_INSET = 8.0F;
  private static final float ICON = 26.0F;
  private static final float SPARK_WIDTH = 44.0F;
  private static final float SPARK_HEIGHT = 16.0F;
  private static final float CHANGE_WIDTH = 46.0F;
  private static final float CLOSE = 26.0F;
  private static final float TILE_HEIGHT = 54.0F;
  private static final float TILE_GAP = 8.0F;
  private static final float SCROLL_STEP = 46.0F;
  private static final float FLAT = 0.0005F;
  private static final float FOOT = 30.0F;
  private static final float CHIP_HEIGHT = 22.0F;
  private static final float CHIP_PAD = 10.0F;
  private static final int UP = 0xFF3DDC5A;
  private static final int AMBER = 0xFFE9D53A;
  private static final int NO_ROW = -1;

  private final PriceHistory history;
  private final TrendsHost host;
  private final DonutSMPModule module;
  private final Supplier<WindowMotion> motion;
  private final Presence presence = new Presence();
  private final WindowFrame frame = new WindowFrame();
  private final Field search = new Field("Search items", 48);
  private final Pick range = new Pick("1H", "6H", "24H", "7D");
  private final Pick kind = new Pick("Crowd", "AH", "Seen");
  private final Pick sort = new Pick("Top", "Gain", "Loss", "A–Z", "Flip");
  private final Segmented rangeTabs = new Segmented(range);
  private final Segmented kindTabs = new Segmented(kind);
  private final Segmented sortTabs = new Segmented(sort);
  private final TrendChart chart = new TrendChart();
  private final Spring glide = Spring.critical(420.0F);
  private final Anim closeHover = Anim.hover(0.0F);
  private final List<Entry> rows = new ArrayList<>(2048);

  private float screenW = WIDTH;
  private float screenH = HEIGHT;
  private float fit = 1.0F;
  private boolean closing;
  private String selected;
  private String applied = "";
  private int appliedRevision = -1;
  private int appliedSort = -1;
  private int appliedKind = -1;
  private int appliedRange = -1;
  private int appliedWatch = -1;
  private boolean appliedOnly;
  private boolean watchOnly;
  private float footerY;
  private float footerChipX;
  private float footerChipWidth;
  private float watchWidthNow;
  private float watchX;
  private float watchY;
  private float findX;
  private float findY;
  private float findWidth;
  private int detailKey = Integer.MIN_VALUE;
  private float scroll;
  private float scrollMax;
  private float listTop;
  private float listHeight;
  private float listLeft;
  private int hoveredRow = NO_ROW;
  private float chartTop;
  private float chartLeft;
  private float chartWidth;
  private float chartHeight;
  private float detailLeft;
  private float detailWidth;
  private float originX;
  private float originY;
  private String heading = "";
  private String subheading = "";
  private String priceText = "--";
  private String changeText = "";
  private float changeValue = Float.NaN;
  private final String[] tileText = new String[4];
  private final String[] tileLabel = {"High", "Low", "Average", "AH vs crowd"};

  private static final class Pick implements Choice {
    private final List<String> labels;
    private int index;

    Pick(String... labels) {
      this.labels = List.of(labels);
    }

    @Override
    public List<String> labels() {
      return labels;
    }

    @Override
    public int index() {
      return index;
    }

    @Override
    public void setIndex(int next) {
      index = Math.max(0, Math.min(labels.size() - 1, next));
    }
  }

  private static final class Entry {
    final String id;
    final String title;
    final String lower;
    final float price;
    final float change;
    final String priceText;
    final String changeText;
    String shown;
    int handle = -1;

    Entry(String id, String title, float price, float change, String priceText) {
      this.id = id;
      this.title = title;
      this.lower = title.toLowerCase(Locale.ROOT);
      this.price = price;
      this.change = change;
      this.priceText = priceText;
      this.changeText = percent(change);
    }
  }

  public TrendsView(
      PriceHistory history, TrendsHost host, DonutSMPModule module, Supplier<WindowMotion> motion) {
    this.history = history;
    this.host = host;
    this.module = module;
    this.motion = motion;
    kind.setIndex(PriceHistory.AUCTION);
    range.setIndex(module.window.get().ordinal());
    presence.snap(0.0F);
    java.util.Arrays.fill(tileText, "--");
  }

  public void open() {
    closing = false;
    presence.snap(0.0F);
    chart.restart();
  }

  public void close() {
    closing = true;
  }

  public boolean closed() {
    return closing && presence.gone();
  }

  public void resize(float width, float height) {
    screenW = width;
    screenH = height;
    fit =
        Math.max(
            0.1F,
            Math.min(
                1.0F,
                Math.min((width - MARGIN * 2.0F) / WIDTH, (height - MARGIN * 2.0F) / HEIGHT)));
    originX = (width - WIDTH) * 0.5F;
    originY = (height - HEIGHT) * 0.5F;
  }

  private float localX(float mx) {
    return screenW * 0.5F + (mx - screenW * 0.5F) / fit;
  }

  private float localY(float my) {
    return screenH * 0.5F + (my - screenH * 0.5F) / fit;
  }

  private Window window() {
    return Window.values()[range.index()];
  }

  private static String percent(float change) {
    if (Float.isNaN(change)) {
      return "";
    }
    if (Math.abs(change) < FLAT) {
      return "0.0%";
    }
    return String.format(Locale.ROOT, "%+.1f%%", change * 100.0F);
  }

  private int tone(Theme t, float change) {
    if (Float.isNaN(change) || Math.abs(change) < FLAT) {
      return t.grey();
    }
    return change > 0.0F ? UP : t.alert();
  }

  private void rebuild() {
    String query = search.text().trim().toLowerCase(Locale.ROOT);
    int revision = history.revision();
    int watching = host.watchRevision();
    if (revision == appliedRevision
        && query.equals(applied)
        && sort.index() == appliedSort
        && kind.index() == appliedKind
        && range.index() == appliedRange
        && watching == appliedWatch
        && watchOnly == appliedOnly) {
      return;
    }
    boolean reset =
        !query.equals(applied)
            || sort.index() != appliedSort
            || kind.index() != appliedKind
            || range.index() != appliedRange
            || watchOnly != appliedOnly;
    appliedWatch = watching;
    appliedOnly = watchOnly;
    applied = query;
    appliedRevision = revision;
    appliedSort = sort.index();
    appliedKind = kind.index();
    appliedRange = range.index();

    long now = PriceHistory.now();
    long span = window().seconds;
    int series = kind.index();
    rows.clear();
    for (String id : history.ids()) {
      if (!history.has(id, series)) {
        continue;
      }
      if (watchOnly && !host.watched(id)) {
        continue;
      }
      String title = host.title(id);
      if (!query.isEmpty()
          && !title.toLowerCase(Locale.ROOT).contains(query)
          && !id.contains(query)) {
        continue;
      }
      if (sort.index() == FLIPS) {
        float margin = host.flip(id);
        if (!Float.isNaN(margin)) {
          rows.add(new Entry(id, title, history.latest(id, series), margin, host.flipText(id)));
        }
        continue;
      }
      float latest = history.latest(id, series);
      rows.add(
          new Entry(
              id,
              title,
              latest,
              history.change(id, series, span, now),
              "$" + Money.format(latest)));
    }
    Comparator<Entry> order =
        switch (sort.index()) {
          case 1, FLIPS ->
              Comparator.comparingDouble(
                      (Entry e) -> Float.isNaN(e.change) ? Double.MAX_VALUE : -e.change)
                  .thenComparing(e -> e.title);
          case 2 ->
              Comparator.comparingDouble(
                      (Entry e) -> Float.isNaN(e.change) ? Double.MAX_VALUE : e.change)
                  .thenComparing(e -> e.title);
          case 3 -> Comparator.comparing((Entry e) -> e.lower);
          default -> Comparator.comparingDouble((Entry e) -> -e.price).thenComparing(e -> e.title);
        };
    rows.sort(order);
    if (reset) {
      scroll = 0.0F;
      glide.snap(0.0F);
    }
    if (selected == null && !rows.isEmpty()) {
      select(rows.get(0).id);
    }
    detailKey = Integer.MIN_VALUE;
  }

  private void select(String id) {
    if (id.equals(selected)) {
      return;
    }
    selected = id;
    detailKey = Integer.MIN_VALUE;
    chart.restart();
  }

  private int indexOf(String id) {
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i).id.equals(id)) {
        return i;
      }
    }
    return NO_ROW;
  }

  private void refreshDetail() {
    if (selected == null) {
      return;
    }
    int key = (selected.hashCode() * 31 + kind.index()) * 31 + range.index();
    key = key * 31 + history.revision();
    if (key == detailKey) {
      return;
    }
    detailKey = key;
    int series = kind.index();
    long now = PriceHistory.now();
    long span = window().seconds;
    heading = host.title(selected);
    long since = history.since(selected, series);
    subheading =
        selected
            + (since == 0L ? "" : "  ·  " + Lang.tr("tracked") + " " + TrendChart.ago(now - since));
    float latest = history.latest(selected, series);
    priceText = Float.isNaN(latest) ? "--" : "$" + Money.format(latest);
    changeValue = history.change(selected, series, span, now);
    changeText = percent(changeValue);
    float[] extent = history.extent(selected, series, now - span, now);
    tileText[0] = extent == null ? "--" : "$" + Money.format(extent[1]);
    tileText[1] = extent == null ? "--" : "$" + Money.format(extent[0]);
    tileText[2] = extent == null ? "--" : "$" + Money.format(extent[2]);
    float crowd = history.latest(selected, PriceHistory.COMMUNITY);
    float auction = history.latest(selected, PriceHistory.AUCTION);
    tileText[3] =
        Float.isNaN(crowd) || Float.isNaN(auction) || crowd <= 0.0F
            ? "--"
            : percent((auction - crowd) / crowd);
  }

  public void draw(Canvas c, Theme t, float rawX, float rawY, float dt) {
    float mx = localX(rawX);
    float my = localY(rawY);
    rebuild();
    refreshDetail();

    WindowMotion style = motion.get();
    presence.step(style, !closing, dt);
    if (presence.phase() < 0.01F) {
      return;
    }
    if (!frame.open(
        c, t, presence, style, originX, originY, WIDTH, HEIGHT, screenW, screenH, fit)) {
      return;
    }
    layout(c);
    drawList(c, t, mx, my, dt);
    drawDetail(c, t, mx, my, dt);
    drawClose(c, t, mx, my, dt);

    frame.close(c);
  }

  private void layout(Canvas c) {
    listLeft = originX + PAD;
    float top = originY + PAD;
    sortTabs.measure(c);
    sortTabs.place(listLeft, top + Field.HEIGHT + 10.0F);
    listTop = top + Field.HEIGHT + 10.0F + sortTabs.h + 12.0F;
    listHeight = originY + HEIGHT - PAD - FOOT - listTop;
    footerY = originY + HEIGHT - PAD - FOOT + 8.0F;
    detailLeft = listLeft + LIST + GAP;
    detailWidth = originX + WIDTH - PAD - detailLeft;
  }

  private void drawList(Canvas c, Theme t, float mx, float my, float dt) {
    float top = originY + PAD;
    search.draw(c, t, listLeft, top, LIST, dt);
    sortTabs.draw(c, t, mx, my, dt);

    scrollMax = Math.max(0.0F, rows.size() * ROW - listHeight);
    scroll = Math.max(0.0F, Math.min(scrollMax, scroll));
    glide.step(dt, scroll);
    float offset = glide.value();

    hoveredRow = NO_ROW;
    c.pushClip(listLeft - 4.0F, listTop, LIST + 8.0F, listHeight);
    int first = Math.max(0, (int) (offset / ROW));
    int last = Math.min(rows.size(), (int) ((offset + listHeight) / ROW) + 1);
    boolean inside =
        mx >= listLeft && mx < listLeft + LIST && my >= listTop && my < listTop + listHeight;
    for (int i = first; i < last; i++) {
      float ry = listTop + i * ROW - offset;
      Entry entry = rows.get(i);
      boolean over = inside && my >= ry && my < ry + ROW;
      if (over) {
        hoveredRow = i;
      }
      boolean current = entry.id.equals(selected);
      if (current) {
        Depth.raised(c, t, listLeft, ry + 2.0F, LIST, ROW - 4.0F, 12.0F, t.surface2());
      } else if (over) {
        c.roundRect(listLeft, ry + 2.0F, LIST, ROW - 4.0F, 12.0F, Theme.withAlpha(t.ink(), 0.06F));
      }
      drawRow(c, t, entry, ry);
    }
    c.popClip();

    drawFooter(c, t, mx, my);
    if (rows.isEmpty()) {
      String empty = Lang.tr(history.size() == 0 ? "Collecting prices" : "Nothing matches");
      c.text(
          FontStyle.LABEL,
          empty,
          listLeft + (LIST - c.textWidth(FontStyle.LABEL, empty)) * 0.5F,
          listTop + 24.0F,
          t.grey());
    }
    if (scrollMax > 0.0F) {
      float track = listHeight - 8.0F;
      float thumb = Math.max(24.0F, track * listHeight / (rows.size() * ROW));
      float thumbY = listTop + 4.0F + (track - thumb) * (offset / scrollMax);
      c.roundRect(
          listLeft + LIST + 3.0F, thumbY, 3.0F, thumb, 1.5F, Theme.withAlpha(t.ink(), 0.22F));
    }
  }

  private void drawFooter(Canvas c, Theme t, float mx, float my) {
    long updated = host.updated();
    long age = PriceHistory.now() - updated;
    boolean waiting = updated == 0L;
    boolean stale = !waiting && age > host.staleAfter();
    int tone = waiting ? t.alert() : stale ? AMBER : UP;
    String state = Lang.tr(waiting ? "Waiting for prices" : stale ? "Stale" : "Live");
    String detail = updated == 0L ? "" : "  ·  " + TrendChart.ago(age);
    float dot = 6.0F;
    float line = c.lineHeight(FontStyle.CAPTION);
    c.roundRect(listLeft + 4.0F, footerY + (CHIP_HEIGHT - dot) * 0.5F, dot, dot, dot * 0.5F, tone);
    c.text(
        FontStyle.CAPTION,
        state + detail,
        listLeft + 4.0F + dot + 8.0F,
        footerY + (CHIP_HEIGHT - line) * 0.5F,
        t.grey());
    String chipLabel = Lang.tr("Watched");
    footerChipWidth = chipWidth(c, chipLabel);
    footerChipX = listLeft + LIST - footerChipWidth;
    chip(c, t, chipLabel, footerChipX, footerY, watchOnly, mx, my);
  }

  private float chipWidth(Canvas c, String label) {
    return c.textWidth(FontStyle.CAPTION, label) + CHIP_PAD * 2.0F;
  }

  private void chip(
      Canvas c, Theme t, String label, float cx, float cy, boolean on, float mx, float my) {
    float width = chipWidth(c, label);
    boolean over = mx >= cx && mx < cx + width && my >= cy && my < cy + CHIP_HEIGHT;
    int fill = on ? t.ink() : Theme.mix(t.surface2(), t.ink(), over ? 0.1F : 0.0F);
    c.roundRect(cx, cy, width, CHIP_HEIGHT, CHIP_HEIGHT * 0.5F, fill);
    c.text(
        FontStyle.CAPTION,
        label,
        cx + CHIP_PAD,
        cy + (CHIP_HEIGHT - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        on ? t.paper() | 0xFF000000 : t.ink());
  }

  private void drawRow(Canvas c, Theme t, Entry entry, float ry) {
    if (entry.handle < 0) {
      entry.handle = host.handle(entry.id);
    }
    float iconX = listLeft + ROW_INSET + 2.0F;
    c.stack(entry.handle, iconX, ry + (ROW - ICON) * 0.5F, ICON);
    float textX = iconX + ICON + 10.0F;
    float right = listLeft + LIST - ROW_INSET;
    float changeX = right - CHANGE_WIDTH;
    float sparkX = changeX - SPARK_WIDTH - 8.0F;
    float room = sparkX - 8.0F - textX;
    if (entry.shown == null) {
      entry.shown = c.fit(FontStyle.LABEL, entry.title, room);
    }
    c.text(FontStyle.LABEL, entry.shown, textX, ry + 7.0F, t.ink());
    c.text(
        FontStyle.CAPTION,
        entry.priceText,
        textX,
        ry + 7.0F + c.lineHeight(FontStyle.LABEL),
        t.grey());
    if (host.watched(entry.id)) {
      float mark = 5.0F;
      c.roundRect(
          textX + c.textWidth(FontStyle.CAPTION, entry.priceText) + 7.0F,
          ry
              + 7.0F
              + c.lineHeight(FontStyle.LABEL)
              + (c.lineHeight(FontStyle.CAPTION) - mark) * 0.5F,
          mark,
          mark,
          mark * 0.5F,
          AMBER);
    }
    long now = PriceHistory.now();
    chart.sparkline(
        c,
        history,
        entry.id,
        kind.index(),
        now - window().seconds,
        now,
        sparkX,
        ry + (ROW - SPARK_HEIGHT) * 0.5F,
        SPARK_WIDTH,
        SPARK_HEIGHT,
        tone(t, entry.change));
    String change = entry.changeText.isEmpty() ? "–" : entry.changeText;
    c.text(
        FontStyle.CAPTION,
        change,
        right - c.textWidth(FontStyle.CAPTION, change),
        ry + (ROW - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        tone(t, entry.change));
  }

  private void drawDetail(Canvas c, Theme t, float mx, float my, float dt) {
    float top = originY + PAD;
    float right = detailLeft + detailWidth;
    if (selected == null) {
      String empty = Lang.tr("Select an item");
      c.text(
          FontStyle.LABEL,
          empty,
          detailLeft + (detailWidth - c.textWidth(FontStyle.LABEL, empty)) * 0.5F,
          originY + HEIGHT * 0.5F,
          t.grey());
      return;
    }
    if (host.handle(selected) >= 0) {
      c.stack(host.handle(selected), detailLeft, top, 36.0F);
    }
    float titleX = detailLeft + 36.0F + 12.0F;
    kindTabs.measure(c);
    String watchLabel = Lang.tr(host.watched(selected) ? "Watching" : "Watch");
    float watchWidth = chipWidth(c, watchLabel);
    watchWidthNow = watchWidth;
    float roomForTitle = right - CLOSE - 12.0F - kindTabs.w - 12.0F - watchWidth - 10.0F - titleX;
    c.text(FontStyle.ROW, c.fit(FontStyle.ROW, heading, roomForTitle), titleX, top + 1.0F, t.ink());
    c.text(
        FontStyle.CAPTION,
        c.fit(FontStyle.CAPTION, subheading, roomForTitle),
        titleX,
        top + 1.0F + c.lineHeight(FontStyle.ROW) + 1.0F,
        t.grey());

    kindTabs.place(right - CLOSE - 12.0F - kindTabs.w, top + 3.0F);
    kindTabs.draw(c, t, mx, my, dt);
    watchX = kindTabs.x - 10.0F - watchWidth;
    watchY = top + 3.0F + (kindTabs.h - CHIP_HEIGHT) * 0.5F;
    chip(c, t, watchLabel, watchX, watchY, host.watched(selected), mx, my);

    float priceY = top + 50.0F;
    c.text(FontStyle.DISPLAY, priceText, detailLeft, priceY, t.ink());
    float priceWidth = c.textWidth(FontStyle.DISPLAY, priceText);
    int accent = Float.isNaN(changeValue) ? t.ink() : tone(t, changeValue);
    float used = detailLeft + priceWidth;
    if (!changeText.isEmpty()) {
      String label = (changeValue > FLAT ? "▲ " : changeValue < -FLAT ? "▼ " : "") + changeText;
      float pillW = c.textWidth(FontStyle.LABEL, label) + 20.0F;
      float pillH = 24.0F;
      float pillX = detailLeft + priceWidth + 14.0F;
      float pillY = priceY + (c.lineHeight(FontStyle.DISPLAY) - pillH) * 0.5F + 2.0F;
      c.roundRect(pillX, pillY, pillW, pillH, pillH * 0.5F, Theme.withAlpha(accent, 0.16F));
      c.text(
          FontStyle.LABEL,
          label,
          pillX + 10.0F,
          pillY + (pillH - c.lineHeight(FontStyle.LABEL)) * 0.5F,
          accent);
      used = pillX + pillW;
    }
    rangeTabs.measure(c);
    rangeTabs.place(
        right - rangeTabs.w,
        priceY + (c.lineHeight(FontStyle.DISPLAY) - rangeTabs.h) * 0.5F + 2.0F);
    rangeTabs.draw(c, t, mx, my, dt);
    String findLabel = Lang.tr("Find in AH");
    findWidth = chipWidth(c, findLabel);
    findX = used + 10.0F;
    findY = rangeTabs.y + (rangeTabs.h - CHIP_HEIGHT) * 0.5F;
    if (findX + findWidth + 10.0F <= rangeTabs.x) {
      chip(c, t, findLabel, findX, findY, false, mx, my);
    } else {
      findWidth = 0.0F;
    }

    float tilesTop = originY + HEIGHT - PAD - TILE_HEIGHT;
    chartTop = priceY + c.lineHeight(FontStyle.DISPLAY) + 12.0F;
    chartLeft = detailLeft;
    chartWidth = detailWidth;
    chartHeight = tilesTop - 12.0F - chartTop;
    long now = PriceHistory.now();
    chart.draw(
        c,
        t,
        history,
        selected,
        kind.index(),
        now - window().seconds,
        now,
        chartLeft,
        chartTop,
        chartWidth,
        chartHeight,
        mx,
        my,
        accent,
        dt);

    float tileW = (detailWidth - TILE_GAP * 3.0F) / 4.0F;
    for (int i = 0; i < 4; i++) {
      float tx = detailLeft + i * (tileW + TILE_GAP);
      Depth.inset(c, t, tx, tilesTop, tileW, TILE_HEIGHT, 14.0F, t.surface2());
      c.text(FontStyle.CAPTION, Lang.tr(tileLabel[i]), tx + 12.0F, tilesTop + 9.0F, t.grey());
      int ink = i == 3 && !tileText[3].equals("--") ? tone(t, parsePercent(tileText[3])) : t.ink();
      c.text(
          FontStyle.ROW,
          tileText[i],
          tx + 12.0F,
          tilesTop + 9.0F + c.lineHeight(FontStyle.CAPTION) + 3.0F,
          ink);
    }
  }

  private static float parsePercent(String text) {
    return text.startsWith("-") ? -1.0F : text.startsWith("+") ? 1.0F : 0.0F;
  }

  private void drawClose(Canvas c, Theme t, float mx, float my, float dt) {
    float cx = originX + WIDTH - PAD - CLOSE;
    float cy = originY + PAD + 2.0F;
    boolean over = mx >= cx && mx < cx + CLOSE && my >= cy && my < cy + CLOSE;
    closeHover.set(over ? 1.0F : 0.0F);
    closeHover.update(dt);
    c.roundRect(
        cx,
        cy,
        CLOSE,
        CLOSE,
        CLOSE * 0.5F,
        Theme.mix(t.surface2(), t.ink(), 0.12F * closeHover.get()));
    String glyph = "×";
    c.text(
        FontStyle.ROW,
        glyph,
        cx + (CLOSE - c.textWidth(FontStyle.ROW, glyph)) * 0.5F,
        cy + (CLOSE - c.lineHeight(FontStyle.ROW)) * 0.5F - 1.0F,
        t.ink());
  }

  public boolean mouseDown(float rawX, float rawY) {
    float mx = localX(rawX);
    float my = localY(rawY);
    float cx = originX + WIDTH - PAD - CLOSE;
    float cy = originY + PAD + 2.0F;
    if (mx >= cx && mx < cx + CLOSE && my >= cy && my < cy + CLOSE) {
      close();
      return true;
    }
    boolean onSearch =
        mx >= listLeft
            && mx < listLeft + LIST
            && my >= originY + PAD
            && my < originY + PAD + Field.HEIGHT;
    search.focus(onSearch);
    if (onSearch) {
      search.press(mx);
      return true;
    }
    if (mx >= footerChipX
        && mx < footerChipX + footerChipWidth
        && my >= footerY
        && my < footerY + CHIP_HEIGHT) {
      watchOnly = !watchOnly;
      return true;
    }
    if (selected != null
        && findWidth > 0.0F
        && mx >= findX
        && mx < findX + findWidth
        && my >= findY
        && my < findY + CHIP_HEIGHT) {
      host.search(selected);
      close();
      return true;
    }
    if (selected != null
        && mx >= watchX
        && mx < watchX + watchWidthNow
        && my >= watchY
        && my < watchY + CHIP_HEIGHT) {
      host.watch(selected);
      return true;
    }
    if (sortTabs.mouseDown(mx, my) || kindTabs.mouseDown(mx, my) || rangeTabs.mouseDown(mx, my)) {
      return true;
    }
    if (hoveredRow != NO_ROW && hoveredRow < rows.size()) {
      select(rows.get(hoveredRow).id);
      return true;
    }
    boolean inside =
        mx >= originX && mx < originX + WIDTH && my >= originY && my < originY + HEIGHT;
    return inside;
  }

  public void mouseUp(float rawX, float rawY) {
    float mx = localX(rawX);
    float my = localY(rawY);
    search.release();
    sortTabs.mouseUp(mx, my);
    kindTabs.mouseUp(mx, my);
    rangeTabs.mouseUp(mx, my);
  }

  public void mouseDrag(float rawX) {
    if (search.focused()) {
      search.drag(localX(rawX));
    }
  }

  public boolean outside(float rawX, float rawY) {
    float mx = localX(rawX);
    float my = localY(rawY);
    return mx < originX || mx >= originX + WIDTH || my < originY || my >= originY + HEIGHT;
  }

  public void scroll(float rawX, float rawY, float amount) {
    float mx = localX(rawX);
    float my = localY(rawY);
    if (mx < detailLeft) {
      scroll = Math.max(0.0F, Math.min(scrollMax, scroll - amount * SCROLL_STEP));
    } else if (my >= chartTop && my < chartTop + chartHeight) {
      step(amount > 0.0F ? -1 : 1);
    }
  }

  private void step(int direction) {
    int before = range.index();
    range.setIndex(before + direction);
  }

  private void move(int direction) {
    if (rows.isEmpty()) {
      return;
    }
    int current = selected == null ? NO_ROW : indexOf(selected);
    int next = Math.max(0, Math.min(rows.size() - 1, current == NO_ROW ? 0 : current + direction));
    select(rows.get(next).id);
    float top = next * ROW;
    if (top < scroll) {
      scroll = top;
    } else if (top + ROW > scroll + listHeight) {
      scroll = top + ROW - listHeight;
    }
  }

  public boolean keyDown(int key, boolean control, boolean shift) {
    if (search.focused()) {
      if (key == Field.ESCAPE || key == Field.ENTER || key == Field.KEYPAD_ENTER) {
        search.focus(false);
        return true;
      }
      search.key(key, control, shift);
      return true;
    }
    switch (key) {
      case KEY_ESCAPE -> close();
      case KEY_DOWN -> move(1);
      case KEY_UP -> move(-1);
      case KEY_RIGHT -> step(1);
      case KEY_LEFT -> step(-1);
      case KEY_TAB -> kind.setIndex((kind.index() + 1) % PriceHistory.KINDS);
      case KEY_ENTER -> {
        if (selected != null) {
          host.search(selected);
          close();
        }
      }
      case KEY_W -> {
        if (selected != null) {
          host.watch(selected);
        }
      }
      case KEY_F -> {
        if (!control) {
          return false;
        }
        search.focus(true);
      }
      default -> {
        return false;
      }
    }
    return true;
  }

  public boolean charTyped(int codepoint) {
    return search.focused() && search.type(codepoint);
  }
}
