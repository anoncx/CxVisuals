package me.cxiy.cxvisuals.gui.map;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.Field;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;

public final class ExplorePage {
  private enum Status {
    ADDING,
    ADDED,
    FAILED,
    REPORTED
  }

  private static final float MAX_WIDTH = 660.0F;
  private static final float MAX_HEIGHT = 470.0F;
  private static final float NOTICE_WIDTH = 400.0F;
  private static final float NOTICE_HEIGHT = 110.0F;
  private static final float MARGIN = 16.0F;
  private static final float PAD = 16.0F;
  private static final float HEADER = 60.0F;
  private static final float FILTERS = 46.0F;
  private static final float GAP = 12.0F;
  private static final float CARD_MIN = 184.0F;
  private static final float CARD_HEIGHT = 212.0F;
  private static final float PREVIEW = 104.0F;
  private static final float CHIP = 24.0F;
  private static final float RADIUS = 18.0F;
  private static final float SEARCH_DELAY = 0.35F;
  private static final float NOTICE_SECONDS = 3.5F;
  private static final float SHEET_WIDTH = 380.0F;
  private static final int MAX_SERVERS = 3;

  private final ExploreHost host;
  private final MapChrome.Frost frost;
  private final Runnable close;
  private final ExploreModel model = new ExploreModel();
  private final Zones zones = new Zones();
  private final Field search = new Field(Lang.key("Search maps and servers"), 40);
  private final Field title = new Field(Lang.key("Title"), PublishModel.TITLE_LIMIT);
  private final Anim thumb = Anim.pop(0.0F);
  private final Map<String, Status> status = new HashMap<>();
  private List<String> servers = List.of();
  private PublishModel publish;
  private String armed = "";
  private String notice = "";
  private float noticeLeft;
  private String typed = "";
  private float typedFor;
  private float scroll;
  private float contentHeight;
  private float left;
  private float top;
  private float width;
  private float height;
  private boolean serversAsked;
  private float[] searchBounds;
  private float[] titleBounds;

  public ExplorePage(ExploreHost host, MapChrome.Frost frost, Runnable close) {
    this.host = host;
    this.frost = frost;
    this.close = close;
  }

  public void draw(Canvas c, Theme t, float screenW, float screenH, float mx, float my, float dt) {
    boolean signedIn = host.available();
    width = Math.min(signedIn ? MAX_WIDTH : NOTICE_WIDTH, screenW - MARGIN * 2.0F);
    height = Math.min(signedIn ? MAX_HEIGHT : NOTICE_HEIGHT, screenH - MARGIN * 2.0F);
    left = (screenW - width) * 0.5F;
    top = (screenH - height) * 0.5F;
    zones.clear();
    c.shadow(
        left - 4.0F, top, width + 8.0F, height + 6.0F, RADIUS + 6.0F, 40.0F, 14.0F, 0x66000000);
    MapChrome.panel(c, t, frost, left, top, width, height, RADIUS);
    if (!host.available()) {
      unavailable(c, t);
      return;
    }
    watchSearch(dt);
    loadIfNeeded();
    header(c, t, mx, my, dt);
    filters(c, t, mx, my, dt);
    grid(c, t, mx, my);
    toast(c, t, dt);
    if (publish != null) {
      sheet(c, t, mx, my, dt);
    }
  }

  public boolean mouseDown(float mx, float my) {
    if (!ShareWindowMath.inside(mx, my, left, top, width, height)) {
      return false;
    }
    search.focus(false);
    title.focus(false);
    if (publish != null && onField(titleBounds, mx, my)) {
      title.focus(true);
      title.press(mx);
    } else if (publish == null && onField(searchBounds, mx, my)) {
      search.focus(true);
      search.press(mx);
    } else {
      zones.press(mx, my);
    }
    return true;
  }

  public void drag(float mx) {
    if (title.focused()) {
      title.drag(mx);
    } else if (search.focused()) {
      search.drag(mx);
    }
  }

  public void release() {
    title.release();
    search.release();
  }

  public void scroll(float amount) {
    scroll =
        Math.max(
            0.0F, Math.min(Math.max(0.0F, contentHeight - gridHeight()), scroll - amount * 48.0F));
  }

  public boolean key(int key, boolean control, boolean shift) {
    if (publish != null && title.focused()) {
      return title.key(key, control, shift);
    }
    if (search.focused()) {
      if (search.submits(key)) {
        model.text(search.text());
        typed = search.text();
        typedFor = 0.0F;
        return true;
      }
      return search.key(key, control, shift);
    }
    return false;
  }

  public boolean type(int codepoint) {
    if (publish != null && title.focused()) {
      boolean taken = title.type(codepoint);
      publish.title(title.text());
      return taken;
    }
    return search.focused() && search.type(codepoint);
  }

  public boolean typing() {
    return search.focused() || title.focused();
  }

  public boolean back() {
    if (publish != null && !publish.busy()) {
      publish = null;
      title.focus(false);
      return true;
    }
    if (search.focused()) {
      search.focus(false);
      return true;
    }
    return false;
  }

  private void unavailable(Canvas c, Theme t) {
    c.text(FontStyle.ROW, Lang.tr("Explore"), left + PAD, top + PAD, t.ink());
    SheetDraw.paragraph(
        c,
        FontStyle.LABEL,
        Lang.tr("Sign in to CxVisuals to browse and publish maps."),
        left + PAD,
        top + PAD + 34.0F,
        width - PAD * 2.0F,
        t.grey());
    zones.add(left + width - PAD - 30.0F, top + PAD - 4.0F, 30.0F, 30.0F, close);
    MapChrome.button(
        c,
        t,
        MapChrome.Glyph.CLOSE,
        left + width - PAD - 30.0F,
        top + PAD - 4.0F,
        30.0F,
        0.0F,
        false);
  }

  private void watchSearch(float dt) {
    String now = search.text().strip();
    if (!now.equals(typed)) {
      typed = now;
      typedFor = 0.0F;
    } else if (!now.equals(model.text())) {
      typedFor += dt;
      if (typedFor >= SEARCH_DELAY) {
        model.text(now);
      }
    }
    if (noticeLeft > 0.0F) {
      noticeLeft = Math.max(0.0F, noticeLeft - dt);
    }
  }

  private void loadIfNeeded() {
    if (!serversAsked) {
      serversAsked = true;
      host.servers(list -> servers = list);
    }
    if (model.wantsLoad()) {
      ExploreModel.Request request = model.begin();
      scroll = 0.0F;
      host.list(request, page -> model.receive(request, page), () -> model.fail(request));
    }
  }

  private void header(Canvas c, Theme t, float mx, float my, float dt) {
    c.text(FontStyle.ROW, Lang.tr("Explore"), left + PAD, top + 21.0F, t.ink());
    float right = left + width - PAD;
    float close = 30.0F;
    zones.add(right - close, top + 15.0F, close, close, this.close);
    MapChrome.button(
        c,
        t,
        MapChrome.Glyph.CLOSE,
        right - close,
        top + 15.0F,
        close,
        ShareWindowMath.inside(mx, my, right - close, top + 15.0F, close, close) ? 1.0F : 0.0F,
        false);
    right -= close + 10.0F;
    String label = Lang.tr("Publish");
    boolean can = host.canPublish() && publish == null;
    float pill =
        SheetDraw.pill(
            c, t, zones, label, right, top + 13.0F, true, !can, mx, my, this::openPublish);
    right -= pill + 10.0F;
    float fieldWidth = Math.min(230.0F, right - (left + PAD + 90.0F));
    searchBounds = null;
    if (publish == null && fieldWidth > 80.0F) {
      search.draw(c, t, right - fieldWidth, top + 15.0F, fieldWidth, dt);
      searchBounds = new float[] {right - fieldWidth, top + 15.0F, fieldWidth};
    }
  }

  private void openPublish() {
    publish = new PublishModel(host.server(), host.suggestedTitle(), host.hasSelection());
    title.set(publish.title());
    title.focus(true);
    search.focus(false);
  }

  private void filters(Canvas c, Theme t, float mx, float my, float dt) {
    float y = top + HEADER;
    String[] labels = {Lang.tr("Newest"), Lang.tr("Popular"), Lang.tr("Largest")};
    float segment = 76.0F;
    Depth.inset(c, t, left + PAD, y, segment * labels.length, 30.0F, 15.0F, t.surface2());
    thumb.set(model.sort().ordinal());
    thumb.update(dt);
    c.roundRect(
        left + PAD + 2.0F + thumb.get() * segment,
        y + 2.0F,
        segment - 4.0F,
        26.0F,
        13.0F,
        t.raised());
    for (int i = 0; i < labels.length; i++) {
      ExploreModel.Sort sort = ExploreModel.Sort.values()[i];
      float labelWidth = c.textWidth(FontStyle.CAPTION, labels[i]);
      c.text(
          FontStyle.CAPTION,
          labels[i],
          left + PAD + i * segment + (segment - labelWidth) * 0.5F,
          y + (30.0F - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
          model.sort() == sort ? t.ink() : t.grey());
      zones.add(left + PAD + i * segment, y, segment, 30.0F, () -> model.sort(sort));
    }
    float x = left + PAD + segment * labels.length + 16.0F;
    float end = left + width - PAD;
    for (int i = 0; i < servers.size() && i < MAX_SERVERS; i++) {
      String name = servers.get(i);
      String shown = c.fit(FontStyle.CAPTION, name, 120.0F);
      float chipW = c.textWidth(FontStyle.CAPTION, shown) + 22.0F;
      if (x + chipW > end) {
        break;
      }
      boolean on = model.server().equals(name);
      boolean over = ShareWindowMath.inside(mx, my, x, y + 2.0F, chipW, 26.0F);
      c.roundRect(
          x,
          y + 2.0F,
          chipW,
          26.0F,
          13.0F,
          on ? t.ink() : Theme.withAlpha(t.ink(), over ? 0.14F : 0.07F));
      c.text(
          FontStyle.CAPTION,
          shown,
          x + 11.0F,
          y + (30.0F - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
          on ? t.paper() | SheetDraw.OPAQUE : t.ink2());
      zones.add(x, y + 2.0F, chipW, 26.0F, () -> model.server(name));
      x += chipW + 8.0F;
    }
  }

  private float gridTop() {
    return top + HEADER + FILTERS;
  }

  private float gridHeight() {
    return height - HEADER - FILTERS - PAD * 0.5F;
  }

  private void grid(Canvas c, Theme t, float mx, float my) {
    float areaX = left + PAD;
    float areaW = width - PAD * 2.0F;
    float areaY = gridTop();
    float areaH = gridHeight();
    List<ExploreModel.Entry> entries = model.entries();
    if (entries.isEmpty()) {
      contentHeight = 0.0F;
      centred(c, t, areaX, areaY, areaW, areaH);
      return;
    }
    int columns = Math.max(1, (int) ((areaW + GAP) / (CARD_MIN + GAP)));
    float cardW = (areaW - GAP * (columns - 1)) / columns;
    int rows = (entries.size() + columns - 1) / columns;
    contentHeight = rows * (CARD_HEIGHT + GAP);
    scroll = Math.max(0.0F, Math.min(Math.max(0.0F, contentHeight - areaH), scroll));
    c.pushClip(areaX - 4.0F, areaY, areaW + 8.0F, areaH);
    for (int i = 0; i < entries.size(); i++) {
      float x = areaX + (i % columns) * (cardW + GAP);
      float y = areaY + (i / columns) * (CARD_HEIGHT + GAP) - scroll;
      if (y + CARD_HEIGHT < areaY || y > areaY + areaH) {
        continue;
      }
      card(c, t, entries.get(i), x, y, cardW, mx, my, areaY, areaY + areaH);
    }
    c.popClip();
    if (model.wantsMore() && scroll >= contentHeight - areaH - CARD_HEIGHT) {
      ExploreModel.Request request = model.beginMore();
      host.list(request, page -> model.receive(request, page), () -> model.fail(request));
    }
  }

  private void centred(Canvas c, Theme t, float x, float y, float w, float h) {
    String text;
    int color = t.grey();
    if (model.failed()) {
      text = Lang.tr("Couldn't reach Explore. Tap to retry.");
      color = t.ink2();
      zones.add(x, y, w, h, model::retry);
    } else if (model.loading() || model.wantsLoad()) {
      text = Lang.tr("Loading maps");
    } else {
      text = Lang.tr("No maps here yet. Be the first to publish one.");
    }
    float textWidth = c.textWidth(FontStyle.LABEL, text);
    c.text(FontStyle.LABEL, text, x + (w - textWidth) * 0.5F, y + h * 0.4F, color);
  }

  private void card(
      Canvas c,
      Theme t,
      ExploreModel.Entry entry,
      float x,
      float y,
      float w,
      float mx,
      float my,
      float visibleTop,
      float visibleBottom) {
    boolean over =
        ShareWindowMath.inside(mx, my, x, y, w, CARD_HEIGHT)
            && my >= visibleTop
            && my < visibleBottom;
    Depth.raised(c, t, x, y, w, CARD_HEIGHT, 14.0F, over ? t.raised() : t.surface2());
    float boxX = x + 8.0F;
    float boxW = w - 16.0F;
    Depth.inset(c, t, boxX, y + 8.0F, boxW, PREVIEW, 10.0F, t.well());
    preview(c, t, entry.id(), boxX + (boxW - PREVIEW) * 0.5F, y + 8.0F);
    float textX = x + 10.0F;
    float room = w - 20.0F;
    float cursor = y + 8.0F + PREVIEW + 8.0F;
    c.text(FontStyle.LABEL, c.fit(FontStyle.LABEL, entry.title(), room), textX, cursor, t.ink());
    cursor += c.lineHeight(FontStyle.LABEL) + 1.0F;
    String by = entry.author().isEmpty() ? Lang.tr("Anonymous") : entry.author();
    c.text(
        FontStyle.CAPTION,
        c.fit(FontStyle.CAPTION, entry.server() + " · " + by, room),
        textX,
        cursor,
        t.grey());
    cursor += c.lineHeight(FontStyle.CAPTION) + 1.0F;
    String meta = Lang.format("{0} chunks", entry.chunks()) + " · " + size(entry.size());
    c.text(FontStyle.MONO, c.fit(FontStyle.MONO, meta, room), textX, cursor, t.grey());
    actions(c, t, entry, x, y + CARD_HEIGHT - CHIP - 8.0F, w, mx, my);
  }

  private void preview(Canvas c, Theme t, String id, float x, float y) {
    byte[] png = host.preview(id);
    if (png != null && png.length > 0 && c.icon(png, x, y, PREVIEW, 10.0F)) {
      return;
    }
    String text = png == null ? Lang.tr("Loading") : Lang.tr("No preview");
    c.text(
        FontStyle.CAPTION,
        text,
        x + (PREVIEW - c.textWidth(FontStyle.CAPTION, text)) * 0.5F,
        y + (PREVIEW - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        t.grey());
  }

  private void actions(
      Canvas c, Theme t, ExploreModel.Entry entry, float x, float y, float w, float mx, float my) {
    Status state = status.get(entry.id());
    String add =
        state == null
            ? Lang.tr("Add to my map")
            : switch (state) {
              case ADDING -> Lang.tr("Adding");
              case ADDED -> Lang.tr("Added");
              case FAILED -> Lang.tr("Couldn't add");
              case REPORTED -> Lang.tr("Add to my map");
            };
    boolean busy = state == Status.ADDING || state == Status.ADDED;
    float addW = c.textWidth(FontStyle.CAPTION, add) + 22.0F;
    chip(c, t, add, x + 8.0F, y, addW, true, busy, mx, my);
    if (!busy) {
      zones.add(x + 8.0F, y, addW, CHIP, () -> add(entry));
    }
    boolean mine = entry.mine();
    boolean sure = entry.id().equals(armed);
    String side =
        mine
            ? (sure ? Lang.tr("Tap to confirm") : Lang.tr("Delete"))
            : state == Status.REPORTED
                ? Lang.tr("Reported")
                : sure ? Lang.tr("Tap to confirm") : Lang.tr("Report");
    float sideW = c.textWidth(FontStyle.CAPTION, side) + 18.0F;
    float sideX = x + w - 8.0F - sideW;
    boolean reported = state == Status.REPORTED;
    chip(c, t, side, sideX, y, sideW, sure, reported, mx, my);
    if (!reported) {
      zones.add(sideX, y, sideW, CHIP, () -> sideAction(entry, mine));
    }
  }

  private void chip(
      Canvas c,
      Theme t,
      String label,
      float x,
      float y,
      float w,
      boolean strong,
      boolean dim,
      float mx,
      float my) {
    boolean over = !dim && ShareWindowMath.inside(mx, my, x, y, w, CHIP);
    int fill =
        strong
            ? Theme.withAlpha(t.ink(), dim ? 0.35F : over ? 0.88F : 1.0F)
            : Theme.withAlpha(t.ink(), over ? 0.14F : 0.07F);
    c.roundRect(x, y, w, CHIP, CHIP * 0.5F, fill);
    c.text(
        FontStyle.CAPTION,
        label,
        x + (w - c.textWidth(FontStyle.CAPTION, label)) * 0.5F,
        y + (CHIP - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        strong ? t.paper() | SheetDraw.OPAQUE : t.ink2());
  }

  private void add(ExploreModel.Entry entry) {
    status.put(entry.id(), Status.ADDING);
    host.add(
        entry.id(),
        done -> {
          status.put(entry.id(), done ? Status.ADDED : Status.FAILED);
          say(done ? Lang.tr("Added to your map") : Lang.tr("Couldn't add that map."));
        });
  }

  private void sideAction(ExploreModel.Entry entry, boolean mine) {
    if (!entry.id().equals(armed)) {
      armed = entry.id();
      return;
    }
    armed = "";
    if (mine) {
      host.delete(
          entry.id(),
          () -> {
            model.remove(entry.id());
            say(Lang.tr("Deleted"));
          });
    } else {
      host.report(entry.id());
      status.put(entry.id(), Status.REPORTED);
      say(Lang.tr("Thanks, we'll take a look."));
    }
  }

  private void say(String text) {
    notice = text;
    noticeLeft = NOTICE_SECONDS;
  }

  private void toast(Canvas c, Theme t, float dt) {
    if (noticeLeft <= 0.0F || notice.isEmpty()) {
      return;
    }
    float textWidth = c.textWidth(FontStyle.LABEL, notice);
    float w = textWidth + 32.0F;
    float x = left + (width - w) * 0.5F;
    float y = top + height - 54.0F;
    c.pushAlpha(Math.min(1.0F, noticeLeft * 2.0F));
    c.roundRect(x, y, w, 34.0F, 17.0F, t.ink());
    c.text(
        FontStyle.LABEL,
        notice,
        x + 16.0F,
        y + (34.0F - c.lineHeight(FontStyle.LABEL)) * 0.5F,
        t.paper() | SheetDraw.OPAQUE);
    c.popAlpha();
  }

  private static boolean onField(float[] bounds, float mx, float my) {
    return bounds != null
        && ShareWindowMath.inside(mx, my, bounds[0], bounds[1], bounds[2], Field.HEIGHT);
  }

  private void sheet(Canvas c, Theme t, float mx, float my, float dt) {
    zones.clear();
    c.roundRect(left, top, width, height, RADIUS, Theme.withAlpha(0xFF000000, 0.55F));
    float room = SHEET_WIDTH - PAD * 2.0F;
    float lines = 0.0F;
    for (String line : publish.warning()) {
      lines += SheetDraw.paragraphHeight(c, FontStyle.LABEL, line, room - 14.0F) + 6.0F;
    }
    float h =
        PAD
            + 24.0F
            + Field.HEIGHT
            + 14.0F
            + 30.0F
            + 12.0F
            + 40.0F
            + 8.0F
            + lines
            + 10.0F
            + SheetDraw.BUTTON
            + PAD;
    float x = left + (width - SHEET_WIDTH) * 0.5F;
    float y = top + (height - h) * 0.5F;
    MapChrome.panel(c, t, frost, x, y, SHEET_WIDTH, h, 16.0F);
    float cursor = y + PAD;
    c.text(FontStyle.ROW, Lang.tr("Publish this map"), x + PAD, cursor, t.ink());
    cursor += 28.0F;
    title.draw(c, t, x + PAD, cursor, room, dt);
    titleBounds = new float[] {x + PAD, cursor, room};
    cursor += Field.HEIGHT + 14.0F;
    scope(c, t, x + PAD, cursor, room);
    cursor += 42.0F;
    nameRow(c, t, x, cursor);
    cursor += 40.0F;
    for (String line : publish.warning()) {
      c.roundRect(x + PAD, cursor + 6.0F, 4.0F, 4.0F, 2.0F, t.grey());
      SheetDraw.paragraph(
          c, FontStyle.LABEL, line, x + PAD + 14.0F, cursor, room - 14.0F, t.ink2());
      cursor += SheetDraw.paragraphHeight(c, FontStyle.LABEL, line, room - 14.0F) + 6.0F;
    }
    cursor += 10.0F;
    float edge = x + SHEET_WIDTH - PAD;
    String action = publish.busy() ? Lang.tr("Publishing") : Lang.tr("Publish map");
    float actionW =
        SheetDraw.pill(
            c, t, zones, action, edge, cursor, true, !publish.canPublish(), mx, my, this::send);
    SheetDraw.pill(
        c,
        t,
        zones,
        Lang.tr("Cancel"),
        edge - actionW - 8.0F,
        cursor,
        false,
        publish.busy(),
        mx,
        my,
        () -> {
          publish = null;
          title.focus(false);
        });
  }

  private void scope(Canvas c, Theme t, float x, float y, float w) {
    String[] labels = {Lang.tr("Everything I've explored"), Lang.tr("Selected area")};
    float segment = w / 2.0F;
    Depth.inset(c, t, x, y, w, 30.0F, 15.0F, t.surface2());
    int on = publish.whole() ? 0 : 1;
    c.roundRect(x + 2.0F + on * segment, y + 2.0F, segment - 4.0F, 26.0F, 13.0F, t.raised());
    for (int i = 0; i < 2; i++) {
      boolean allowed = i == 0 || publish.hasSelection();
      float labelWidth = c.textWidth(FontStyle.CAPTION, labels[i]);
      c.text(
          FontStyle.CAPTION,
          c.fit(FontStyle.CAPTION, labels[i], segment - 12.0F),
          x + i * segment + Math.max(6.0F, (segment - labelWidth) * 0.5F),
          y + (30.0F - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
          i == on ? t.ink() : allowed ? t.grey() : t.dotOff());
      if (allowed) {
        boolean whole = i == 0;
        zones.add(x + i * segment, y, segment, 30.0F, () -> publish.whole(whole));
      }
    }
  }

  private void nameRow(Canvas c, Theme t, float x, float y) {
    String label = Lang.tr("Show my name");
    c.text(
        FontStyle.LABEL,
        label,
        x + PAD,
        y + (30.0F - c.lineHeight(FontStyle.LABEL)) * 0.5F,
        t.ink());
    SheetDraw.toggle(c, t, x + SHEET_WIDTH - PAD - 38.0F, y + 4.0F, publish.showName());
    zones.add(x + 6.0F, y, SHEET_WIDTH - 12.0F, 30.0F, () -> publish.showName(!publish.showName()));
  }

  private void send() {
    PublishModel sending = publish;
    if (sending == null || !sending.begin()) {
      return;
    }
    host.publish(
        sending,
        done -> {
          if (done) {
            publish = null;
            title.focus(false);
            model.sort(ExploreModel.Sort.NEW);
            model.text("");
            search.set("");
            say(Lang.tr("Published. Thanks for sharing!"));
          } else {
            sending.finish(false);
            say(Lang.tr("Couldn't publish that map."));
          }
        });
  }

  private static String size(long bytes) {
    if (bytes < 1024L) {
      return bytes + " B";
    }
    if (bytes < 1024L * 1024L) {
      return String.format(Locale.ROOT, "%.0f KB", bytes / 1024.0);
    }
    return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
  }
}
