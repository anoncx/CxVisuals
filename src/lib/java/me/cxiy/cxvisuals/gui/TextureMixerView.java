package me.cxiy.cxvisuals.gui;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.module.ClientSettings.WindowMotion;
import me.cxiy.cxvisuals.core.settings.Choice;
import me.cxiy.cxvisuals.gui.spatial.Spring;
import me.cxiy.cxvisuals.modules.texturemixer.Catalog;
import me.cxiy.cxvisuals.modules.texturemixer.Entry;
import me.cxiy.cxvisuals.modules.texturemixer.Live;
import me.cxiy.cxvisuals.modules.texturemixer.Mix;
import me.cxiy.cxvisuals.modules.texturemixer.Modrinth;
import me.cxiy.cxvisuals.modules.texturemixer.Pack;
import me.cxiy.cxvisuals.modules.texturemixer.Stage;
import me.cxiy.cxvisuals.modules.texturemixer.Studio;
import me.cxiy.cxvisuals.modules.texturemixer.Thumbs;

public final class TextureMixerView {
  public static final int KEY_ESCAPE = 256;
  public static final int KEY_F = 70;

  private static final float WIDTH = 1060.0F;
  private static final float HEIGHT = 640.0F;
  private static final float MARGIN = 16.0F;
  private static final float PAD = 16.0F;
  private static final float LEFT = 236.0F;
  private static final float SIDE = 300.0F;
  private static final float GAP = 14.0F;
  private static final float ROW = 48.0F;
  private static final float PACK_ROW = 58.0F;
  private static final float BROWSE_ROW = 70.0F;
  private static final float CARD_GAP = 8.0F;
  private static final float CARD_H = 178.0F;
  private static final float CARD_IMAGE = 124.0F;
  private static final float CHIP_HEIGHT = 24.0F;
  private static final float CHIP_PAD = 11.0F;
  private static final float FOOT = 34.0F;
  private static final float CLOSE = 26.0F;
  private static final float STAGE = 276.0F;
  private static final float SPIN = 0.55F;
  private static final float SPIN_HOLD = 2.2F;
  private static final float DRAG_TURN = 0.012F;
  private static final float MAX_PITCH = 1.35F;
  private static final long TYPE_DELAY = 420_000_000L;
  private static final int NONE = -1;
  private static final int AMBER = 0xFFE9D53A;
  private static final int GREEN = 0xFF3DDC5A;

  private final Studio studio;
  private final Live live;
  private final Supplier<WindowMotion> motion;
  private final Presence presence = new Presence();
  private final WindowFrame frame = new WindowFrame();
  private final Pick kinds =
      new Pick(Lang.tr("Blocks"), Lang.tr("Items"), Lang.tr("Entities"));
  private final Pick sorts = new Pick(Lang.tr("Popular"), Lang.tr("Newest"), Lang.tr("Updated"));
  private final Segmented kindBar = new Segmented(kinds);
  private final Segmented sortBar = new Segmented(sorts);
  private final Field search = new Field("Search textures", 48);
  private final Field remote = new Field("Search Modrinth", 48);
  private final Anim fade = Anim.reveal(1.0F);
  private final Anim cards = Anim.reveal(1.0F);
  private final Anim cardIn = Anim.hover(0.0F);
  private final Anim cardOut = Anim.hover(0.0F);
  private final Anim closeHover = Anim.hover(0.0F);
  private final Anim detail = Anim.move(0.0F);
  private final Spring listGlide = Spring.critical(420.0F);
  private final Spring listPick = Spring.critical(520.0F);
  private final Spring centerGlide = Spring.critical(420.0F);
  private final Spring browseGlide = Spring.critical(420.0F);
  private final Stage stage;
  private final List<Entry> rows = new ArrayList<>(1024);
  private final Map<Entry, Integer> counts = new HashMap<>(1024);
  private final Map<String, Integer> colours = new HashMap<>(32);

  private float screenW = WIDTH;
  private float screenH = HEIGHT;
  private float fit = 1.0F;
  private float originX;
  private float originY;
  private boolean closing;
  private Catalog builtFor;
  private int builtKind = NONE;
  private String builtQuery = "";
  private boolean builtChanged;
  private int builtMix = NONE;
  private int builtPacks = NONE;
  private boolean onlyChanged;
  private boolean browsing;
  private Entry selected;
  private float yaw = 0.8F;
  private float pitch = 0.45F;
  private boolean perspective = true;
  private float idle;
  private boolean turning;
  private float turnX;
  private float turnY;
  private float listScroll;
  private float listTop;
  private float listHeight;
  private int listHovered = NONE;
  private float centerScroll;
  private float centerTop;
  private float centerHeight;
  private int centerHovered = NONE;
  private int cardPrevious = NONE;
  private boolean removeHovered;
  private boolean applyHovered;
  private float browseScroll;
  private int browseHovered = NONE;
  private float leftX;
  private float centerLeft;
  private float centerWidth;
  private float sideLeft;
  private float footY;
  private float closeX;
  private float closeY;
  private float chipAX;
  private float chipAW;
  private float chipBX;
  private float chipBW;
  private float changedX;
  private float changedW;
  private float resetX = -1.0F;
  private float resetW;
  private float liveX;
  private float liveW;
  private float stageX;
  private float stageY;
  private float perspectiveX;
  private float perspectiveW;
  private final float[] stateX = new float[12];
  private final float[] stateY = new float[12];
  private final float[] stateW = new float[12];
  private int stateCount;
  private long typedAt;
  private long clock;

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

  public TextureMixerView(Studio studio, Live live, Supplier<WindowMotion> motion) {
    this.studio = studio;
    this.live = live;
    this.motion = motion;
    this.stage = new Stage(studio);
    presence.snap(0.0F);
  }

  public Studio studio() {
    return studio;
  }

  public Stage stage() {
    return stage;
  }

  public void open() {
    closing = false;
    presence.snap(0.0F);
    studio.open();
    studio.rescan();
  }

  public void close() {
    closing = true;
  }

  public boolean closed() {
    return closing && presence.gone();
  }

  public void select(Entry entry) {
    selected = entry;
    stage.inspect(entry);
    centerScroll = 0.0F;
    centerGlide.step(1.0F, 0.0F);
    cards.snap(0.0F);
    cards.set(1.0F);
    idle = 0.0F;
    browsing = false;
  }

  public void showKind(int index) {
    kinds.setIndex(index);
  }

  public void browse(boolean on) {
    browsing = on;
    if (on) {
      refreshBrowse();
    }
  }

  public void query(String text) {
    search.set(text);
  }

  public void dropped(List<Path> paths) {
    studio.importFiles(paths);
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

  private static boolean in(float mx, float my, float x, float y, float w, float h) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
  }

  private Entry.Kind kind() {
    return switch (kinds.index()) {
      case 1 -> Entry.Kind.ITEM;
      case 2 -> Entry.Kind.ENTITY;
      default -> Entry.Kind.BLOCK;
    };
  }

  private boolean inside(float mx, float my) {
    return in(mx, my, originX, originY, WIDTH, HEIGHT);
  }

  private int colour(String id) {
    return colours.computeIfAbsent(
        id, key -> Hsv.argb((Math.abs(key.hashCode()) % 360) / 360.0F, 0.5F, 0.92F));
  }

  private int versions(Entry entry) {
    return counts.getOrDefault(entry, 1);
  }

  private void rebuild() {
    Catalog catalog = studio.catalog();
    if (catalog == null) {
      return;
    }
    Mix mix = studio.mix();
    String query = search.text().trim().toLowerCase(Locale.ROOT);
    int revision = onlyChanged ? mix.revision() : 0;
    int packs = studio.packRevision();
    if (catalog == builtFor
        && kinds.index() == builtKind
        && query.equals(builtQuery)
        && onlyChanged == builtChanged
        && revision == builtMix
        && packs == builtPacks) {
      return;
    }
    boolean reset =
        catalog != builtFor
            || kinds.index() != builtKind
            || !query.equals(builtQuery)
            || onlyChanged != builtChanged;
    builtFor = catalog;
    builtKind = kinds.index();
    builtQuery = query;
    builtChanged = onlyChanged;
    builtMix = revision;
    builtPacks = packs;
    rows.clear();
    counts.clear();
    List<Pack> available = studio.packs();
    for (Entry entry : catalog.of(kind())) {
      if (!query.isEmpty() && !entry.lower().contains(query)) {
        continue;
      }
      if (onlyChanged && !mix.changed(entry)) {
        continue;
      }
      int total = 1;
      for (Pack pack : available) {
        if (pack.coverage(entry.textures()) > 0) {
          total++;
        }
      }
      if (total == 1 && !available.isEmpty()) {
        continue;
      }
      counts.put(entry, total);
      rows.add(entry);
    }
    rows.sort(
        Comparator.<Entry>comparingInt(entry -> -counts.get(entry))
            .thenComparing(Entry::title));
    if (reset) {
      listScroll = 0.0F;
      listGlide.step(1.0F, 0.0F);
      fade.snap(0.0F);
      fade.set(1.0F);
    }
  }

  public void draw(Canvas c, Theme t, float rawX, float rawY, float dt) {
    float mx = localX(rawX);
    float my = localY(rawY);
    clock++;
    WindowMotion style = motion.get();
    presence.step(style, !closing, dt);
    if (presence.phase() < 0.01F) {
      return;
    }
    rebuild();
    studio.thumbs().frame();
    stage.frame(yaw, pitch, perspective);
    fade.update(dt);
    cards.update(dt);
    detail.set(selected == null || browsing ? 0.0F : 1.0F);
    detail.update(dt);
    if (selected != null && !turning) {
      idle += dt;
      if (idle > SPIN_HOLD) {
        yaw += dt * SPIN;
      }
    }
    if (!frame.open(
        c, t, presence, style, originX, originY, WIDTH, HEIGHT, screenW, screenH, fit)) {
      return;
    }
    leftX = originX + PAD;
    centerLeft = leftX + LEFT + GAP;
    centerWidth = WIDTH - PAD * 2.0F - LEFT - SIDE - GAP * 2.0F;
    sideLeft = originX + WIDTH - PAD - SIDE;
    footY = originY + HEIGHT - PAD - FOOT + 6.0F;
    stage.focus(null);
    drawList(c, t, mx, my, dt);
    if (browsing) {
      drawBrowse(c, t, mx, my, dt);
    } else if (selected != null) {
      drawVersions(c, t, mx, my, dt);
    } else {
      drawPacks(c, t, mx, my, dt);
    }
    drawSide(c, t, mx, my, dt);
    drawFooter(c, t, mx, my);
    frame.close(c);
    if (typedAt != 0L && System.nanoTime() - typedAt > TYPE_DELAY) {
      typedAt = 0L;
      refreshBrowse();
    }
  }

  private void refreshBrowse() {
    browseScroll = 0.0F;
    studio.search(remote.text().trim(), sortValue(), false);
  }

  private Modrinth.Sort sortValue() {
    return switch (sorts.index()) {
      case 1 -> Modrinth.Sort.NEWEST;
      case 2 -> Modrinth.Sort.UPDATED;
      default -> Modrinth.Sort.DOWNLOADS;
    };
  }

  private float chipWidth(Canvas c, String label) {
    return c.textWidth(FontStyle.CAPTION, label) + CHIP_PAD * 2.0F;
  }

  private float chip(
      Canvas c, Theme t, String label, float cx, float cy, boolean on, float mx, float my) {
    float width = chipWidth(c, label);
    boolean over = in(mx, my, cx, cy, width, CHIP_HEIGHT);
    int fill = on ? t.ink() : Theme.mix(t.surface2(), t.ink(), over ? 0.12F : 0.0F);
    c.roundRect(cx, cy, width, CHIP_HEIGHT, CHIP_HEIGHT * 0.5F, fill);
    c.text(
        FontStyle.CAPTION,
        label,
        cx + CHIP_PAD,
        cy + (CHIP_HEIGHT - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        on ? t.paper() | 0xFF000000 : t.ink());
    return width;
  }

  private void badge(Canvas c, Theme t, Pack pack, float x, float y, float size) {
    float radius = size * 0.26F;
    String mark;
    if (pack.id().isEmpty()) {
      c.roundRect(x, y, size, size, radius, t.ink());
      mark = "V";
    } else if (pack.icon() != null && c.icon(pack.icon(), x, y, size, radius)) {
      return;
    } else {
      c.roundRect(x, y, size, size, radius, Theme.withAlpha(colour(pack.id()), 0.9F));
      mark = pack.name().isEmpty() ? "?" : pack.name().substring(0, 1).toUpperCase(Locale.ROOT);
    }
    c.text(
        FontStyle.LABEL,
        mark,
        x + (size - c.textWidth(FontStyle.LABEL, mark)) * 0.5F,
        y + (size - c.lineHeight(FontStyle.LABEL)) * 0.5F,
        pack.id().isEmpty() ? t.paper() | 0xFF000000 : 0xFF101010);
  }

  private void drawList(Canvas c, Theme t, float mx, float my, float dt) {
    float top = originY + PAD;
    String label = Lang.tr("Changed");
    changedW = chipWidth(c, label);
    changedX = leftX + LEFT - changedW;
    search.draw(c, t, leftX, top, LEFT - changedW - 8.0F, dt);
    chip(c, t, label, changedX, top + (Field.HEIGHT - CHIP_HEIGHT) * 0.5F, onlyChanged, mx, my);
    float tabsY = top + Field.HEIGHT + 10.0F;
    kindBar.limit(LEFT);
    kindBar.measure(c);
    kindBar.place(leftX, tabsY);
    kindBar.draw(c, t, mx, my, dt);
    listTop = tabsY + kindBar.h + 12.0F;
    listHeight = originY + HEIGHT - PAD - FOOT - listTop;
    float total = rows.size() * ROW;
    float maxScroll = Math.max(0.0F, total - listHeight);
    listScroll = Math.max(0.0F, Math.min(maxScroll, listScroll));
    listGlide.step(dt, listScroll);
    float offset = listGlide.value();
    Thumbs thumbs = studio.thumbs();
    Mix mix = studio.mix();
    listHovered = NONE;
    c.pushAlpha(fade.get());
    c.pushClip(leftX - 4.0F, listTop, LEFT + 8.0F, listHeight);
    int active = selected == null ? NONE : rows.indexOf(selected);
    if (active >= 0) {
      listPick.step(dt, active * ROW);
      Depth.raised(
          c,
          t,
          leftX,
          listTop + listPick.value() - offset + 2.0F,
          LEFT,
          ROW - 4.0F,
          12.0F,
          t.surface2());
    }
    int first = Math.max(0, (int) (offset / ROW));
    int last = Math.min(rows.size(), (int) ((offset + listHeight) / ROW) + 1);
    for (int i = first; i < last; i++) {
      float ry = listTop + i * ROW - offset;
      Entry entry = rows.get(i);
      boolean over =
          in(mx, my, leftX, listTop, LEFT, listHeight) && in(mx, my, leftX, ry, LEFT, ROW);
      if (over) {
        listHovered = i;
        if (i != active) {
          c.roundRect(leftX, ry + 2.0F, LEFT, ROW - 4.0F, 12.0F, Theme.withAlpha(t.ink(), 0.06F));
        }
      }
      drawRow(c, t, entry, ry, thumbs, mix);
    }
    c.popClip();
    c.popAlpha();
    if (rows.isEmpty()) {
      String empty =
          studio.catalog() == null
              ? Lang.tr("Reading vanilla textures")
              : Lang.tr("Nothing matches");
      c.text(
          FontStyle.CAPTION,
          empty,
          leftX + (LEFT - c.textWidth(FontStyle.CAPTION, empty)) * 0.5F,
          listTop + 24.0F,
          t.grey());
    }
    Depth.scrollFade(
        c, t, t.paper(), leftX - 4.0F, listTop, LEFT + 8.0F, listHeight, listScroll, maxScroll);
  }

  private void drawRow(Canvas c, Theme t, Entry entry, float ry, Thumbs thumbs, Mix mix) {
    float icon = 36.0F;
    int cell = thumbs.cell(entry, mix.signature(entry), studio::chosen);
    float ix = leftX + 8.0F;
    float iy = ry + (ROW - icon) * 0.5F;
    if (cell >= 0) {
      c.tile(thumbs.sheet(), cell, ix, iy, icon, icon, 0.0F);
    } else {
      float pulse = 0.05F + 0.04F * (float) Math.sin(clock * 0.12F + ry);
      c.roundRect(
          ix + 4.0F,
          iy + 4.0F,
          icon - 8.0F,
          icon - 8.0F,
          8.0F,
          Theme.withAlpha(t.ink(), pulse));
    }
    float textX = ix + icon + 10.0F;
    float room = LEFT - (textX - leftX) - 22.0F;
    int total = versions(entry);
    String meta =
        total <= 1
            ? Lang.tr("Vanilla only")
            : total + " " + Lang.count(total, "version", "versions");
    c.text(FontStyle.LABEL, c.fit(FontStyle.LABEL, entry.title(), room), textX, ry + 7.0F, t.ink());
    c.text(
        FontStyle.CAPTION,
        c.fit(FontStyle.CAPTION, meta, room),
        textX,
        ry + 7.0F + c.lineHeight(FontStyle.LABEL),
        t.grey());
    if (mix.changed(entry)) {
      String id = Pack.VANILLA;
      for (String texture : entry.textures()) {
        if (!mix.pack(texture).isEmpty()) {
          id = mix.pack(texture);
          break;
        }
      }
      float dot = 7.0F;
      c.roundRect(
          leftX + LEFT - dot - 10.0F,
          ry + (ROW - dot) * 0.5F,
          dot,
          dot,
          dot * 0.5F,
          colour(id));
    }
  }

  private void centerHeader(Canvas c, Theme t, String title, String caption) {
    float top = originY + PAD;
    c.text(FontStyle.ROW, title, centerLeft, top + 1.0F, t.ink());
    c.text(
        FontStyle.CAPTION,
        c.fit(FontStyle.CAPTION, caption, centerWidth - 180.0F),
        centerLeft,
        top + 1.0F + c.lineHeight(FontStyle.ROW) + 1.0F,
        t.grey());
  }

  private void drawPacks(Canvas c, Theme t, float mx, float my, float dt) {
    centerHeader(
        c,
        t,
        Lang.tr("Packs"),
        Lang.tr("Pick an item on the left to see every version your packs have"));
    float chipsY = originY + PAD + 2.0F;
    String browse = Lang.tr("Modrinth");
    String add = Lang.tr("Add pack");
    chipBW = chipWidth(c, browse);
    chipBX = centerLeft + centerWidth - chipBW;
    chipAW = chipWidth(c, add);
    chipAX = chipBX - 8.0F - chipAW;
    chip(c, t, add, chipAX, chipsY, false, mx, my);
    chip(c, t, browse, chipBX, chipsY, false, mx, my);
    centerTop = originY + PAD + 52.0F;
    centerHeight = originY + HEIGHT - PAD - FOOT - centerTop;
    List<Pack> packs = studio.packs();
    float total = packs.size() * PACK_ROW;
    float maxScroll = Math.max(0.0F, total - centerHeight);
    centerScroll = Math.max(0.0F, Math.min(maxScroll, centerScroll));
    centerGlide.step(dt, centerScroll);
    float offset = centerGlide.value();
    centerHovered = NONE;
    removeHovered = false;
    applyHovered = false;
    c.pushClip(centerLeft - 4.0F, centerTop, centerWidth + 8.0F, centerHeight);
    for (int i = 0; i < packs.size(); i++) {
      float ry = centerTop + i * PACK_ROW - offset;
      if (ry + PACK_ROW < centerTop || ry > centerTop + centerHeight) {
        continue;
      }
      drawPack(c, t, packs.get(i), i, ry, mx, my);
    }
    c.popClip();
    if (packs.isEmpty()) {
      String hint = Lang.tr("Drop .zip packs onto this window");
      c.text(
          FontStyle.LABEL,
          hint,
          centerLeft + (centerWidth - c.textWidth(FontStyle.LABEL, hint)) * 0.5F,
          centerTop + 50.0F,
          t.grey());
    }
    Depth.scrollFade(
        c,
        t,
        t.paper(),
        centerLeft - 4.0F,
        centerTop,
        centerWidth + 8.0F,
        centerHeight,
        centerScroll,
        maxScroll);
  }

  private void drawPack(Canvas c, Theme t, Pack pack, int index, float ry, float mx, float my) {
    boolean over =
        in(mx, my, centerLeft, centerTop, centerWidth, centerHeight)
            && in(mx, my, centerLeft, ry, centerWidth, PACK_ROW);
    if (over) {
      centerHovered = index;
    }
    c.roundRect(
        centerLeft,
        ry + 2.0F,
        centerWidth,
        PACK_ROW - 4.0F,
        14.0F,
        Theme.withAlpha(t.ink(), over ? 0.08F : 0.04F));
    float icon = 38.0F;
    badge(c, t, pack, centerLeft + 10.0F, ry + (PACK_ROW - icon) * 0.5F, icon);
    float textX = centerLeft + 10.0F + icon + 12.0F;
    float reserve = over ? 120.0F : 12.0F;
    float room = centerWidth - (textX - centerLeft) - reserve;
    String meta = pack.size() + " " + Lang.count(pack.size(), "texture", "textures");
    c.text(FontStyle.LABEL, c.fit(FontStyle.LABEL, pack.name(), room), textX, ry + 10.0F, t.ink());
    c.text(
        FontStyle.CAPTION,
        c.fit(FontStyle.CAPTION, meta, room),
        textX,
        ry + 10.0F + c.lineHeight(FontStyle.LABEL),
        t.grey());
    if (!over) {
      return;
    }
    float cy = ry + (PACK_ROW - CHIP_HEIGHT) * 0.5F;
    String all = Lang.tr("Use all");
    float allW = chipWidth(c, all);
    float removeW = pack.imported() ? 30.0F : 0.0F;
    float allX = centerLeft + centerWidth - 12.0F - removeW - allW;
    boolean hotAll = in(mx, my, allX, cy, allW, CHIP_HEIGHT);
    applyHovered = hotAll;
    chip(c, t, all, allX, cy, hotAll, mx, my);
    if (pack.imported()) {
      float rx = centerLeft + centerWidth - 12.0F - 22.0F;
      boolean hot = in(mx, my, rx, cy, 22.0F, CHIP_HEIGHT);
      removeHovered = hot;
      c.roundRect(
          rx, cy, 22.0F, CHIP_HEIGHT, 11.0F, Theme.withAlpha(t.alert(), hot ? 0.3F : 0.14F));
      c.text(
          FontStyle.CAPTION,
          "×",
          rx + (22.0F - c.textWidth(FontStyle.CAPTION, "×")) * 0.5F,
          cy + (CHIP_HEIGHT - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
          t.alert());
    }
  }

  private boolean active(Entry entry, Pack pack) {
    Mix mix = studio.mix();
    for (String texture : entry.textures()) {
      String want = pack.id().isEmpty() || !pack.has(texture) ? Pack.VANILLA : pack.id();
      if (!mix.pack(texture).equals(want)) {
        return false;
      }
    }
    return true;
  }

  private void drawVersions(Canvas c, Theme t, float mx, float my, float dt) {
    List<Pack> candidates = stage.candidates();
    int total = candidates.size();
    centerHeader(
        c,
        t,
        selected.title(),
        total
            + " "
            + Lang.count(total, "version", "versions")
            + "  ·  "
            + Lang.tr("click the one you like"));
    float chipsY = originY + PAD + 2.0F;
    String back = Lang.tr("Packs");
    chipBW = chipWidth(c, back);
    chipBX = centerLeft + centerWidth - chipBW;
    chip(c, t, back, chipBX, chipsY, false, mx, my);
    centerTop = originY + PAD + 52.0F;
    centerHeight = originY + HEIGHT - PAD - FOOT - centerTop;
    float cardW = (centerWidth - CARD_GAP * 2.0F) / 3.0F;
    int lines = (total + 2) / 3;
    float lineH = CARD_H + CARD_GAP;
    float maxScroll = Math.max(0.0F, lines * lineH - centerHeight);
    centerScroll = Math.max(0.0F, Math.min(maxScroll, centerScroll));
    centerGlide.step(dt, centerScroll);
    float offset = centerGlide.value();
    cardIn.update(dt);
    cardOut.update(dt);
    int over = NONE;
    if (in(mx, my, centerLeft, centerTop, centerWidth, centerHeight)) {
      int col = (int) ((mx - centerLeft) / (cardW + CARD_GAP));
      int line = (int) ((my - centerTop + offset) / lineH);
      int index = line * 3 + col;
      float cx = centerLeft + col * (cardW + CARD_GAP);
      float cy = centerTop - offset + line * lineH;
      if (col >= 0 && col < 3 && index >= 0 && index < total && in(mx, my, cx, cy, cardW, CARD_H)) {
        over = index;
      }
    }
    if (over != centerHovered) {
      cardPrevious = centerHovered;
      cardOut.snap(cardIn.get());
      cardOut.set(0.0F);
      cardIn.snap(0.0F);
      cardIn.set(over == NONE ? 0.0F : 1.0F);
      centerHovered = over;
    }
    if (over >= 0) {
      stage.focus(candidates.get(over));
    }
    c.pushAlpha(cards.get());
    c.pushClip(centerLeft - 6.0F, centerTop - 4.0F, centerWidth + 12.0F, centerHeight + 8.0F);
    for (int i = 0; i < total; i++) {
      float cx = centerLeft + (i % 3) * (cardW + CARD_GAP);
      float cy = centerTop - offset + (i / 3) * lineH;
      if (cy + CARD_H < centerTop - 8.0F || cy > centerTop + centerHeight + 8.0F) {
        continue;
      }
      float lift = i == centerHovered ? cardIn.get() : i == cardPrevious ? cardOut.get() : 0.0F;
      drawCard(c, t, candidates.get(i), i, cx, cy, cardW, lift);
    }
    c.popClip();
    c.popAlpha();
    Depth.scrollFade(
        c,
        t,
        t.paper(),
        centerLeft - 4.0F,
        centerTop,
        centerWidth + 8.0F,
        centerHeight,
        centerScroll,
        maxScroll);
  }

  private void drawCard(
      Canvas c, Theme t, Pack pack, int index, float x, float y, float w, float lift) {
    boolean current = active(selected, pack);
    c.pushTransform(1.0F + 0.04F * lift, x + w * 0.5F, y + CARD_H * 0.5F);
    if (current) {
      Depth.raised(c, t, x, y, w, CARD_H, 16.0F, t.surface2());
    } else {
      c.roundRect(x, y, w, CARD_H, 16.0F, Theme.withAlpha(t.ink(), 0.04F + 0.05F * lift));
    }
    int cell = stage.cell(index);
    float imageX = x + (w - CARD_IMAGE) * 0.5F;
    if (cell >= 0) {
      c.tile(stage.small(), cell, imageX, y + 8.0F, CARD_IMAGE, CARD_IMAGE, 0.0F);
    }
    float room = w - 24.0F;
    String name = pack.id().isEmpty() ? Lang.tr("Vanilla") : pack.name();
    int total = selected.textures().size();
    String meta =
        pack.id().isEmpty()
            ? Lang.tr("Original texture")
            : pack.coverage(selected.textures())
                + " / "
                + total
                + " "
                + Lang.count(total, "texture", "textures");
    float textY = y + 8.0F + CARD_IMAGE + 6.0F;
    c.text(
        FontStyle.LABEL,
        c.fit(FontStyle.LABEL, name, room),
        x + (w - Math.min(room, c.textWidth(FontStyle.LABEL, name))) * 0.5F,
        textY,
        t.ink());
    c.text(
        FontStyle.CAPTION,
        c.fit(FontStyle.CAPTION, meta, room),
        x + (w - Math.min(room, c.textWidth(FontStyle.CAPTION, meta))) * 0.5F,
        textY + c.lineHeight(FontStyle.LABEL),
        t.grey());
    if (current) {
      float dot = 8.0F;
      c.roundRect(x + w - dot - 12.0F, y + 12.0F, dot, dot, dot * 0.5F, GREEN);
    }
    c.popTransform();
  }

  private void drawBrowse(Canvas c, Theme t, float mx, float my, float dt) {
    float top = originY + PAD;
    String back = Lang.tr("Back");
    chipAW = chip(c, t, back, centerLeft, top + (Field.HEIGHT - CHIP_HEIGHT) * 0.5F, false, mx, my);
    chipAX = centerLeft;
    sortBar.measure(c);
    sortBar.place(centerLeft + centerWidth - sortBar.w, top);
    sortBar.draw(c, t, mx, my, dt);
    float fieldX = centerLeft + chipAW + 10.0F;
    remote.draw(c, t, fieldX, top, centerLeft + centerWidth - sortBar.w - 10.0F - fieldX, dt);
    centerTop = top + Field.HEIGHT + 14.0F;
    centerHeight = originY + HEIGHT - PAD - FOOT - centerTop;
    List<Modrinth.Hit> hits = studio.results();
    float maxScroll = Math.max(0.0F, hits.size() * BROWSE_ROW - centerHeight);
    browseScroll = Math.max(0.0F, Math.min(maxScroll, browseScroll));
    browseGlide.step(dt, browseScroll);
    float offset = browseGlide.value();
    if (browseScroll > maxScroll - BROWSE_ROW * 3.0F
        && !studio.exhausted()
        && !studio.searching()
        && !hits.isEmpty()) {
      studio.search("", sortValue(), true);
    }
    browseHovered = NONE;
    c.pushClip(centerLeft - 4.0F, centerTop, centerWidth + 8.0F, centerHeight);
    int first = Math.max(0, (int) (offset / BROWSE_ROW));
    int last = Math.min(hits.size(), (int) ((offset + centerHeight) / BROWSE_ROW) + 1);
    for (int i = first; i < last; i++) {
      drawHit(c, t, hits.get(i), i, centerTop + i * BROWSE_ROW - offset, mx, my);
    }
    c.popClip();
    if (hits.isEmpty()) {
      String line =
          studio.offline()
              ? Lang.tr("Modrinth is not reachable")
              : studio.searching() ? Lang.tr("Searching Modrinth") : Lang.tr("Nothing found");
      c.text(
          FontStyle.LABEL,
          line,
          centerLeft + (centerWidth - c.textWidth(FontStyle.LABEL, line)) * 0.5F,
          centerTop + 40.0F,
          t.grey());
    }
    Depth.scrollFade(
        c,
        t,
        t.paper(),
        centerLeft - 4.0F,
        centerTop,
        centerWidth + 8.0F,
        centerHeight,
        browseScroll,
        maxScroll);
  }

  private void drawHit(
      Canvas c, Theme t, Modrinth.Hit hit, int index, float ry, float mx, float my) {
    boolean over =
        in(mx, my, centerLeft, centerTop, centerWidth, centerHeight)
            && in(mx, my, centerLeft, ry, centerWidth, BROWSE_ROW);
    if (over) {
      browseHovered = index;
      c.roundRect(
          centerLeft,
          ry + 2.0F,
          centerWidth,
          BROWSE_ROW - 4.0F,
          14.0F,
          Theme.withAlpha(t.ink(), 0.06F));
    }
    float icon = 46.0F;
    float ix = centerLeft + 10.0F;
    float iy = ry + (BROWSE_ROW - icon) * 0.5F;
    byte[] cover = studio.cover(hit);
    if (cover == null || !c.icon(cover, ix, iy, icon, 11.0F)) {
      c.roundRect(ix, iy, icon, icon, 11.0F, Theme.withAlpha(colour(hit.id()), 0.85F));
      String mark =
          hit.title().isEmpty() ? "?" : hit.title().substring(0, 1).toUpperCase(Locale.ROOT);
      c.text(
          FontStyle.ROW,
          mark,
          ix + (icon - c.textWidth(FontStyle.ROW, mark)) * 0.5F,
          iy + (icon - c.lineHeight(FontStyle.ROW)) * 0.5F,
          0xFF101010);
    }
    Studio.Download job = studio.downloadOf(hit);
    boolean done = job != null && job.finished;
    boolean busy = job != null && !job.finished && !job.failed;
    String action =
        job == null
            ? Lang.tr("Get")
            : done ? Lang.tr("Added") : job.failed ? Lang.tr("Retry") : job.percent + "%";
    float actionW = Math.max(56.0F, chipWidth(c, action));
    float actionX = centerLeft + centerWidth - actionW - 12.0F;
    float textX = ix + icon + 12.0F;
    float room = actionX - 12.0F - textX;
    float label = c.lineHeight(FontStyle.LABEL);
    float caption = c.lineHeight(FontStyle.CAPTION);
    c.text(FontStyle.LABEL, c.fit(FontStyle.LABEL, hit.title(), room), textX, ry + 9.0F, t.ink());
    c.text(
        FontStyle.CAPTION,
        c.fit(FontStyle.CAPTION, hit.author() + "  ·  " + compact(hit.downloads()), room),
        textX,
        ry + 9.0F + label,
        t.grey());
    c.text(
        FontStyle.CAPTION,
        c.fit(FontStyle.CAPTION, hit.summary(), room),
        textX,
        ry + 9.0F + label + caption,
        t.grey());
    float chipY = ry + (BROWSE_ROW - CHIP_HEIGHT) * 0.5F;
    boolean hot = over && in(mx, my, actionX, chipY, actionW, CHIP_HEIGHT);
    int base =
        done
            ? Theme.withAlpha(t.ink(), 0.16F)
            : Theme.mix(t.surface2(), t.ink(), hot ? 0.14F : 0.0F);
    c.roundRect(actionX, chipY, actionW, CHIP_HEIGHT, CHIP_HEIGHT * 0.5F, base);
    if (busy) {
      c.pushClip(actionX, chipY, actionW * job.percent / 100.0F, CHIP_HEIGHT);
      c.roundRect(
          actionX,
          chipY,
          actionW,
          CHIP_HEIGHT,
          CHIP_HEIGHT * 0.5F,
          Theme.withAlpha(t.ink(), 0.28F));
      c.popClip();
    }
    c.text(
        FontStyle.CAPTION,
        action,
        actionX + (actionW - c.textWidth(FontStyle.CAPTION, action)) * 0.5F,
        chipY + (CHIP_HEIGHT - caption) * 0.5F,
        t.ink());
  }

  private static String compact(int value) {
    if (value >= 1_000_000) {
      return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
    }
    if (value >= 1_000) {
      return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
    }
    return Integer.toString(value);
  }

  private void drawSide(Canvas c, Theme t, float mx, float my, float dt) {
    float top = originY + PAD;
    closeX = sideLeft + SIDE - CLOSE;
    closeY = top;
    closeHover.set(in(mx, my, closeX, closeY, CLOSE, CLOSE) ? 1.0F : 0.0F);
    closeHover.update(dt);
    c.roundRect(
        closeX,
        closeY,
        CLOSE,
        CLOSE,
        CLOSE * 0.5F,
        Theme.mix(t.surface2(), t.ink(), 0.14F * closeHover.get()));
    c.text(
        FontStyle.ROW,
        "×",
        closeX + (CLOSE - c.textWidth(FontStyle.ROW, "×")) * 0.5F,
        closeY + (CLOSE - c.lineHeight(FontStyle.ROW)) * 0.5F,
        t.ink());
    float shown = detail.get();
    if (shown < 0.98F) {
      c.pushAlpha(1.0F - shown);
      drawSummary(c, t);
      c.popAlpha();
    }
    if (shown > 0.02F && selected != null) {
      c.pushAlpha(shown);
      drawStage(c, t, mx, my);
      c.popAlpha();
    }
  }

  private void drawSummary(Canvas c, Theme t) {
    float top = originY + PAD;
    c.text(FontStyle.ROW, Lang.tr("Texture Mixer"), sideLeft, top + 1.0F, t.ink());
    int mixed = studio.mix().size();
    float y = top + 70.0F;
    String number = Integer.toString(mixed);
    c.text(FontStyle.DISPLAY, number, sideLeft, y, t.ink());
    c.text(
        FontStyle.LABEL,
        Lang.count(mixed, "texture", "textures") + " " + Lang.tr("mixed"),
        sideLeft + c.textWidth(FontStyle.DISPLAY, number) + 10.0F,
        y + c.lineHeight(FontStyle.DISPLAY) - c.lineHeight(FontStyle.LABEL) - 4.0F,
        t.grey());
    int packs = studio.packs().size();
    c.text(
        FontStyle.CAPTION,
        packs + " " + Lang.count(packs, "pack", "packs") + " " + Lang.tr("loaded"),
        sideLeft,
        y + c.lineHeight(FontStyle.DISPLAY) + 6.0F,
        t.grey());
  }

  private void drawStage(Canvas c, Theme t, float mx, float my) {
    float top = originY + PAD;
    c.text(
        FontStyle.ROW,
        c.fit(FontStyle.ROW, selected.title(), SIDE - CLOSE - 12.0F),
        sideLeft,
        top + 1.0F,
        t.ink());
    stageX = sideLeft + (SIDE - STAGE) * 0.5F;
    stageY = top + 36.0F;
    Depth.inset(c, t, stageX, stageY, STAGE, STAGE, 20.0F, t.surface2());
    if (stage.has()) {
      c.tile(stage.big(), 0, stageX, stageY, STAGE, STAGE, 20.0F);
    }
    float line = c.lineHeight(FontStyle.CAPTION);
    c.text(
        FontStyle.CAPTION,
        turning ? Lang.tr("Release to spin") : Lang.tr("Drag to turn"),
        stageX + 14.0F,
        stageY + STAGE - line - 10.0F,
        Theme.withAlpha(t.ink(), 0.4F));
    String mode = perspective ? Lang.tr("Perspective") : Lang.tr("Isometric");
    perspectiveW = chipWidth(c, mode);
    perspectiveX = stageX + STAGE - perspectiveW - 10.0F;
    chip(c, t, mode, perspectiveX, stageY + 10.0F, false, mx, my);
    Mix mix = studio.mix();
    float y = stageY + STAGE + 14.0F;
    y = drawStates(c, t, y, mx, my);
    int shown = 0;
    for (String texture : selected.textures()) {
      if (shown++ >= 5) {
        break;
      }
      String id = mix.pack(texture);
      Pack pack = studio.pack(id);
      float dot = 6.0F;
      c.roundRect(
          sideLeft,
          y + (line - dot) * 0.5F,
          dot,
          dot,
          dot * 0.5F,
          id.isEmpty() ? t.grey() : colour(id));
      String name = pack == null || id.isEmpty() ? Lang.tr("Vanilla") : pack.name();
      c.text(
          FontStyle.CAPTION,
          c.fit(
              FontStyle.CAPTION,
              texture.substring(texture.indexOf('/') + 1) + "  ·  " + name,
              SIDE - 14.0F),
          sideLeft + 14.0F,
          y,
          t.ink2());
      y += line + 3.0F;
    }
  }

  private float drawStates(Canvas c, Theme t, float top, float mx, float my) {
    List<Entry.State> states = selected.states();
    stateCount = 0;
    if (states.size() < 2) {
      return top;
    }
    float x = sideLeft;
    float y = top;
    for (int i = 0; i < states.size() && i < stateX.length; i++) {
      String label = states.get(i).label().isEmpty() ? Lang.tr("Default") : states.get(i).label();
      float width = chipWidth(c, label);
      if (x + width > sideLeft + SIDE) {
        x = sideLeft;
        y += CHIP_HEIGHT + 6.0F;
      }
      chip(c, t, label, x, y, i == stage.state(), mx, my);
      stateX[i] = x;
      stateY[i] = y;
      stateW[i] = width;
      stateCount = i + 1;
      x += width + 6.0F;
    }
    return y + CHIP_HEIGHT + 12.0F;
  }

  private void drawFooter(Canvas c, Theme t, float mx, float my) {
    float line = c.lineHeight(FontStyle.CAPTION);
    Live.State state = live.state();
    String text =
        switch (state) {
          case OFF -> Lang.tr("Live off");
          case WAITING -> Lang.tr("Changes pending");
          case APPLYING -> Lang.tr("Reloading textures");
          case APPLIED ->
              Lang.tr("Live")
                  + "  ·  "
                  + live.applied()
                  + " "
                  + Lang.count(live.applied(), "texture", "textures");
          case FAILED -> Lang.tr("Could not apply");
        };
    int tone =
        switch (state) {
          case OFF -> t.grey();
          case WAITING, APPLYING -> AMBER;
          case APPLIED -> GREEN;
          case FAILED -> t.alert();
        };
    float dot = 6.0F;
    c.roundRect(leftX + 2.0F, footY + (CHIP_HEIGHT - dot) * 0.5F, dot, dot, dot * 0.5F, tone);
    c.text(
        FontStyle.CAPTION,
        c.fit(FontStyle.CAPTION, text, LEFT - 20.0F),
        leftX + 2.0F + dot + 8.0F,
        footY + (CHIP_HEIGHT - line) * 0.5F,
        t.grey());
    String liveLabel = live.enabled() ? Lang.tr("Live on") : Lang.tr("Live off");
    liveW = chipWidth(c, liveLabel);
    liveX = sideLeft + SIDE - liveW;
    chip(c, t, liveLabel, liveX, footY, live.enabled(), mx, my);
    resetX = -1.0F;
    if (studio.mix().size() > 0) {
      String reset = Lang.tr("Reset all");
      resetW = chipWidth(c, reset);
      resetX = liveX - 8.0F - resetW;
      chip(c, t, reset, resetX, footY, false, mx, my);
    }
  }

  public boolean mouseDown(float rawX, float rawY) {
    float mx = localX(rawX);
    float my = localY(rawY);
    float top = originY + PAD;
    boolean onSearch = in(mx, my, leftX, top, LEFT - changedW - 8.0F, Field.HEIGHT);
    search.focus(onSearch);
    float fieldX = centerLeft + chipAW + 10.0F;
    boolean onRemote =
        browsing
            && in(
                mx,
                my,
                fieldX,
                top,
                centerLeft + centerWidth - sortBar.w - 10.0F - fieldX,
                Field.HEIGHT);
    remote.focus(onRemote);
    if (onSearch) {
      search.press(mx);
      return true;
    }
    if (onRemote) {
      remote.press(mx);
      return true;
    }
    if (in(mx, my, closeX, closeY, CLOSE, CLOSE)) {
      close();
      return true;
    }
    if (kindBar.mouseDown(mx, my) || (browsing && sortBar.mouseDown(mx, my))) {
      return true;
    }
    if (in(mx, my, changedX, top + (Field.HEIGHT - CHIP_HEIGHT) * 0.5F, changedW, CHIP_HEIGHT)) {
      onlyChanged = !onlyChanged;
      Sound.play(Sound.Cue.TAB);
      return true;
    }
    if (footerDown(mx, my) || listDown()) {
      return true;
    }
    if (browsing) {
      return browseDown(mx, my) || inside(mx, my);
    }
    if (selected != null) {
      return versionsDown(mx, my) || stageDown(mx, my) || inside(mx, my);
    }
    return packsDown(mx, my) || inside(mx, my);
  }

  private boolean footerDown(float mx, float my) {
    if (in(mx, my, liveX, footY, liveW, CHIP_HEIGHT)) {
      live.enabled(!live.enabled());
      Sound.play(Sound.Cue.TAB);
      return true;
    }
    if (resetX >= 0.0F && in(mx, my, resetX, footY, resetW, CHIP_HEIGHT)) {
      studio.mix().clear();
      Sound.play(Sound.Cue.OFF);
      return true;
    }
    return false;
  }

  private boolean listDown() {
    if (listHovered == NONE || listHovered >= rows.size()) {
      return false;
    }
    select(rows.get(listHovered));
    Sound.play(Sound.Cue.OPEN);
    return true;
  }

  private boolean packsDown(float mx, float my) {
    float chipsY = originY + PAD + 2.0F;
    if (in(mx, my, chipAX, chipsY, chipAW, CHIP_HEIGHT)) {
      studio.pick();
      return true;
    }
    if (in(mx, my, chipBX, chipsY, chipBW, CHIP_HEIGHT)) {
      browse(true);
      Sound.play(Sound.Cue.TAB);
      return true;
    }
    List<Pack> packs = studio.packs();
    if (centerHovered == NONE || centerHovered >= packs.size()) {
      return false;
    }
    Pack pack = packs.get(centerHovered);
    if (removeHovered) {
      studio.remove(pack);
      Sound.play(Sound.Cue.OFF);
    } else if (applyHovered && studio.catalog() != null) {
      studio.mix().paint(studio.catalog().of(kind()), pack);
      Sound.play(Sound.Cue.ON);
    }
    return true;
  }

  private boolean versionsDown(float mx, float my) {
    float chipsY = originY + PAD + 2.0F;
    if (in(mx, my, chipBX, chipsY, chipBW, CHIP_HEIGHT)) {
      select(null);
      Sound.play(Sound.Cue.TAB);
      return true;
    }
    List<Pack> candidates = stage.candidates();
    if (centerHovered != NONE && centerHovered < candidates.size()) {
      studio.mix().paint(selected, candidates.get(centerHovered));
      Sound.play(Sound.Cue.ON);
      return true;
    }
    return false;
  }

  private boolean stageDown(float mx, float my) {
    for (int i = 0; i < stateCount; i++) {
      if (in(mx, my, stateX[i], stateY[i], stateW[i], CHIP_HEIGHT)) {
        stage.state(i);
        Sound.play(Sound.Cue.TAB);
        return true;
      }
    }
    if (in(mx, my, perspectiveX, stageY + 10.0F, perspectiveW, CHIP_HEIGHT)) {
      perspective = !perspective;
      Sound.play(Sound.Cue.TAB);
      return true;
    }
    if (in(mx, my, stageX, stageY, STAGE, STAGE)) {
      turning = true;
      turnX = mx;
      turnY = my;
      idle = 0.0F;
      return true;
    }
    return false;
  }

  private boolean browseDown(float mx, float my) {
    float top = originY + PAD;
    if (in(mx, my, chipAX, top + (Field.HEIGHT - CHIP_HEIGHT) * 0.5F, chipAW, CHIP_HEIGHT)) {
      browsing = false;
      Sound.play(Sound.Cue.TAB);
      return true;
    }
    if (browseHovered == NONE || browseHovered >= studio.results().size()) {
      return false;
    }
    Modrinth.Hit hit = studio.results().get(browseHovered);
    Studio.Download job = studio.downloadOf(hit);
    if (job == null || job.failed) {
      studio.download(hit);
      Sound.play(Sound.Cue.STEP);
    }
    return true;
  }

  public boolean secondaryDown(float rawX, float rawY) {
    float mx = localX(rawX);
    float my = localY(rawY);
    if (listHovered != NONE && listHovered < rows.size()) {
      select(rows.get(listHovered));
      Sound.play(Sound.Cue.OPEN);
      return true;
    }
    if (selected != null && inside(mx, my)) {
      select(null);
      return true;
    }
    return inside(mx, my);
  }

  public void mouseUp(float rawX, float rawY) {
    float mx = localX(rawX);
    float my = localY(rawY);
    search.release();
    remote.release();
    kindBar.mouseUp(mx, my);
    sortBar.mouseUp(mx, my);
    turning = false;
    idle = 0.0F;
  }

  public void mouseDrag(float rawX, float rawY) {
    float mx = localX(rawX);
    float my = localY(rawY);
    if (search.focused()) {
      search.drag(mx);
    } else if (remote.focused()) {
      remote.drag(mx);
    } else if (turning) {
      yaw += (mx - turnX) * DRAG_TURN;
      pitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch + (my - turnY) * DRAG_TURN));
      turnX = mx;
      turnY = my;
    }
  }

  public boolean outside(float rawX, float rawY) {
    return !inside(localX(rawX), localY(rawY));
  }

  public void scroll(float rawX, float rawY, float amount) {
    float mx = localX(rawX);
    if (mx < centerLeft - GAP * 0.5F) {
      float max = Math.max(0.0F, rows.size() * ROW - listHeight);
      listScroll = Math.max(0.0F, Math.min(max, listScroll - amount * ROW * 1.5F));
    } else if (mx >= sideLeft) {
      return;
    } else if (browsing) {
      float max = Math.max(0.0F, studio.results().size() * BROWSE_ROW - centerHeight);
      browseScroll = Math.max(0.0F, Math.min(max, browseScroll - amount * BROWSE_ROW));
    } else if (selected != null) {
      float lines = (stage.candidates().size() + 2) / 3;
      float max = Math.max(0.0F, lines * (CARD_H + CARD_GAP) - centerHeight);
      float step = (CARD_H + CARD_GAP) * 0.6F;
      centerScroll = Math.max(0.0F, Math.min(max, centerScroll - amount * step));
    } else {
      float max = Math.max(0.0F, studio.packs().size() * PACK_ROW - centerHeight);
      centerScroll = Math.max(0.0F, Math.min(max, centerScroll - amount * PACK_ROW));
    }
  }

  public boolean keyDown(int key, boolean control, boolean shift) {
    Field active = remote.focused() ? remote : search.focused() ? search : null;
    if (active != null) {
      if (key == Field.ESCAPE || key == Field.ENTER || key == Field.KEYPAD_ENTER) {
        active.focus(false);
        if (active == remote) {
          typedAt = 0L;
          refreshBrowse();
        }
        return true;
      }
      String before = active.text();
      active.key(key, control, shift);
      if (active == remote && !before.equals(active.text())) {
        typedAt = System.nanoTime();
      }
      return true;
    }
    if (key == KEY_ESCAPE) {
      if (browsing) {
        browsing = false;
      } else if (selected != null) {
        select(null);
      } else {
        close();
      }
      return true;
    }
    if (key == KEY_F && control) {
      search.focus(true);
      return true;
    }
    return false;
  }

  public boolean charTyped(int codepoint) {
    if (remote.focused()) {
      boolean taken = remote.type(codepoint);
      if (taken) {
        typedAt = System.nanoTime();
      }
      return taken;
    }
    return search.focused() && search.type(codepoint);
  }
}
