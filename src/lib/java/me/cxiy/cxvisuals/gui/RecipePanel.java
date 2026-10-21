package me.cxiy.cxvisuals.gui;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.module.ClientSettings.WindowMotion;
import me.cxiy.cxvisuals.gui.spatial.Spring;
import me.cxiy.cxvisuals.modules.inventory.Handles;
import me.cxiy.cxvisuals.modules.inventory.InventoryHost;
import me.cxiy.cxvisuals.modules.inventory.Kind;
import me.cxiy.cxvisuals.modules.inventory.Recipe;
import me.cxiy.cxvisuals.modules.inventory.RecipeBook;

public final class RecipePanel {
  private static final float PAD = 16.0F;
  private static final float DISSOLVE_PAD = 36.0F;
  private static final float GAP = 10.0F;
  private static final float TAB = 38.0F;
  private static final float HEAD = 46.0F;
  private static final float FOOT = 34.0F;
  private static final float ARROW = 34.0F;
  private static final float CYCLE_SECONDS = 1.1F;
  private static final float ITEM_FRACTION = 0.8889F;
  private static final int TAB_KIND = 1;
  private static final int CELL = 2;
  private static final int FILL = 3;
  private static final int PREVIOUS = 4;
  private static final int NEXT = 5;
  private static final int CLOSE = 6;
  private static final int BACK = 7;
  private static final int HISTORY = 12;

  private record View(int handle, boolean uses) {}

  private final Handles handles;
  private final InventoryHost host;
  private final Presence presence = new Presence();
  private final Supplier<WindowMotion> motion;
  private final Hits hits = new Hits(160);
  private final Spring tabGlide = Spring.critical(380.0F);
  private final Anim reveal = Anim.reveal(0.0F);
  private final List<View> history = new ArrayList<>();
  private RecipeBook book = RecipeBook.EMPTY;
  private final Map<Kind, List<Recipe>> groups = new EnumMap<>(Kind.class);
  private final List<Kind> kinds = new ArrayList<>();

  private View current;
  private Kind kind;
  private int page;
  private float clock;
  private float screenW;
  private float screenH;
  private float x;
  private float y;
  private float w;
  private float h;
  private float cell = 36.0F;
  private int perPage = 1;
  private int hovered = Handles.NONE;
  private boolean tabsPlaced;
  private boolean closing = true;
  private String tabTip;
  private float tabTipX;
  private float tabTipY;

  public RecipePanel(Handles handles, InventoryHost host, Supplier<WindowMotion> motion) {
    this.motion = motion;
    this.handles = handles;
    this.host = host;
  }

  public void book(RecipeBook next) {
    book = next;
  }

  public void show(int handle, boolean uses) {
    closing = false;
    if (current != null
        && history.size() < HISTORY
        && !(current.handle == handle && current.uses == uses)) {
      history.add(current);
    }
    open(new View(handle, uses));
  }

  private void open(View view) {
    current = view;
    groups.clear();
    kinds.clear();
    List<Recipe> list = view.uses ? book.using(view.handle) : book.making(view.handle);
    groups.putAll(RecipeBook.byKind(list));
    for (Kind candidate : Kind.values()) {
      if (groups.containsKey(candidate)) {
        kinds.add(candidate);
      }
    }
    kind = kinds.isEmpty() ? null : kinds.get(0);
    page = 0;
    tabsPlaced = false;
    reveal.snap(0.0F);
    reveal.set(1.0F);
  }

  public boolean back() {
    if (history.isEmpty()) {
      return false;
    }
    open(history.remove(history.size() - 1));
    return true;
  }

  public boolean closed() {
    return closing;
  }

  public void reset() {
    closing = true;
    presence.snap(0.0F);
    history.clear();
  }

  public boolean gone() {
    return presence.gone();
  }

  public float phase() {
    return presence.phase();
  }

  public void close() {
    closing = true;
  }

  public int hovered() {
    return hovered;
  }

  public void layout(float width, float height, float cellSize) {
    screenW = width;
    screenH = height;
    cell = cellSize;
    arrange();
  }

  private void arrange() {
    float rowWidth = Kind.CRAFT.columns * cell + ARROW + cell * 1.4F + PAD * 2.0F;
    w = Math.min(screenW - 40.0F, Math.max(rowWidth + 40.0F, 380.0F));
    float cardHeight = cardHeight();
    float usable = Math.min(screenH - 60.0F, 560.0F);
    perPage = Math.max(1, (int) ((usable - HEAD - TAB - FOOT - PAD) / (cardHeight + GAP)));
    int listed = kind == null ? 0 : groups.getOrDefault(kind, List.of()).size();
    int shown = Math.max(1, Math.min(perPage, listed));
    boolean paged = listed > perPage;
    h = HEAD + TAB + PAD + shown * (cardHeight + GAP) + (paged ? FOOT : 0.0F);
    x = (screenW - w) * 0.5F;
    y = (screenH - h) * 0.5F;
  }

  private float cardHeight() {
    int rows = 1;
    if (kind != null) {
      for (Recipe recipe : groups.getOrDefault(kind, List.of())) {
        rows = Math.max(rows, recipe.rows());
      }
    }
    return Math.max(cell * 1.6F, rows * cell) + 20.0F;
  }

  public void draw(Canvas c, Theme t, float mx, float my, float dt) {
    clock += dt;
    reveal.update(dt);
    hits.clear();
    hovered = Handles.NONE;
    tabTip = null;
    arrange();
    WindowMotion style = motion.get();
    presence.step(style, !closing, dt);
    float radius = t.radius();
    float cardW = presence.width(style, w);
    float cardH = presence.height(style, h);
    float cardX = x + (w - cardW) * 0.5F;
    float cardY = y + (h - cardH) * 0.5F + presence.rise(style);
    boolean folded = cardW < w - 0.5F || cardH < h - 0.5F;
    c.pushAlpha(presence.alpha(style));
    c.pushTransform(presence.scale(style), x + w * 0.5F, y + h * 0.5F);
    c.beginDissolve(
        x - DISSOLVE_PAD,
        y - DISSOLVE_PAD,
        w + DISSOLVE_PAD * 2.0F,
        h + DISSOLVE_PAD * 2.0F,
        presence.erosion(style));
    c.shadow(cardX, cardY, cardW, cardH, radius, 26.0F, 10.0F, t.cardShadow());
    if (t.glass()) {
      c.glass(cardX, cardY, cardW, cardH, radius, t.paper());
    } else {
      c.roundRect(cardX, cardY, cardW, cardH, radius, t.paper());
    }
    c.rim(
        cardX,
        cardY,
        cardW,
        cardH,
        radius,
        1.0F,
        Math.min(cardH, radius * 1.4F),
        Depth.rimColor(t));
    if (folded) {
      c.pushClip(cardX, cardY, cardW, cardH);
    }
    c.pushAlpha(presence.content(style));

    String title = current == null ? "" : handles.title(current.handle);
    String verb = current != null && current.uses ? Lang.tr("Uses") : Lang.tr("Recipes");
    c.text(FontStyle.ROW, title, x + PAD, y + 10.0F, t.ink());
    c.text(
        FontStyle.CAPTION, verb, x + PAD, y + 10.0F + c.lineHeight(FontStyle.ROW) + 1.0F, t.grey());
    float closeSize = 24.0F;
    float closeX = x + w - PAD - closeSize;
    float closeY = y + 10.0F;
    boolean overClose =
        mx >= closeX && mx < closeX + closeSize && my >= closeY && my < closeY + closeSize;
    c.roundRect(
        closeX,
        closeY,
        closeSize,
        closeSize,
        closeSize * 0.5F,
        Theme.mix(t.surface2(), t.ink(), overClose ? 0.18F : 0.08F));
    c.text(
        FontStyle.LABEL,
        "×",
        closeX + (closeSize - c.textWidth(FontStyle.LABEL, "×")) * 0.5F,
        closeY + (closeSize - c.lineHeight(FontStyle.LABEL)) * 0.5F - 1.0F,
        t.ink());
    hits.add(CLOSE, null, closeX, closeY, closeSize, closeSize);
    if (!history.isEmpty()) {
      float backX = closeX - closeSize - 8.0F;
      boolean overBack =
          mx >= backX && mx < backX + closeSize && my >= closeY && my < closeY + closeSize;
      c.roundRect(
          backX,
          closeY,
          closeSize,
          closeSize,
          closeSize * 0.5F,
          Theme.mix(t.surface2(), t.ink(), overBack ? 0.18F : 0.08F));
      c.text(
          FontStyle.LABEL,
          "‹",
          backX + (closeSize - c.textWidth(FontStyle.LABEL, "‹")) * 0.5F,
          closeY + (closeSize - c.lineHeight(FontStyle.LABEL)) * 0.5F - 1.0F,
          t.ink());
      hits.add(BACK, null, backX, closeY, closeSize, closeSize);
    }

    drawTabs(c, t, mx, my, dt);

    List<Recipe> list = kind == null ? List.of() : groups.getOrDefault(kind, List.of());
    int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
    page = Math.max(0, Math.min(page, pages - 1));
    float cardHeight = cardHeight();
    float top = y + HEAD + TAB + 6.0F;
    float shown = reveal.get();
    c.pushAlpha(shown);
    if (list.isEmpty()) {
      String empty = Lang.tr("No recipes");
      c.text(
          FontStyle.LABEL,
          empty,
          x + (w - c.textWidth(FontStyle.LABEL, empty)) * 0.5F,
          top + cell,
          t.grey());
    }
    int start = page * perPage;
    for (int i = 0; i < perPage && start + i < list.size(); i++) {
      float cy = top + i * (cardHeight + GAP) + (1.0F - shown) * 8.0F;
      drawCard(c, t, list.get(start + i), x + PAD, cy, w - PAD * 2.0F, cardHeight, mx, my);
    }
    c.popAlpha();

    float footY = y + h - FOOT + 4.0F;
    if (pages > 1) {
      String label = (page + 1) + " / " + pages;
      c.text(
          FontStyle.LABEL,
          label,
          x + (w - c.textWidth(FontStyle.LABEL, label)) * 0.5F,
          footY + 5.0F,
          t.ink());
      footButton(c, t, PREVIOUS, "‹", x + PAD, footY, mx, my, page > 0);
      footButton(c, t, NEXT, "›", x + w - PAD - 26.0F, footY, mx, my, page < pages - 1);
    }
    drawTabTip(c, t);
    c.popAlpha();
    if (folded) {
      c.popClip();
    }
    c.endDissolve();
    c.popTransform();
    c.popAlpha();
    host.hover(hovered);
  }

  private void footButton(
      Canvas c,
      Theme t,
      int kindId,
      String glyph,
      float bx,
      float by,
      float mx,
      float my,
      boolean enabled) {
    float size = 26.0F;
    boolean over = enabled && mx >= bx && mx < bx + size && my >= by && my < by + size;
    Depth.raised(
        c,
        t,
        bx,
        by,
        size,
        size,
        size * 0.5F,
        Theme.mix(t.surface2(), t.ink(), over ? 0.1F : 0.0F));
    c.text(
        FontStyle.ROW,
        glyph,
        bx + (size - c.textWidth(FontStyle.ROW, glyph)) * 0.5F,
        by + (size - c.lineHeight(FontStyle.ROW)) * 0.5F - 1.0F,
        enabled ? t.ink() : t.dotOff());
    if (enabled) {
      hits.add(kindId, null, bx, by, size, size);
    }
  }

  private void drawTabTip(Canvas c, Theme t) {
    if (tabTip == null) {
      return;
    }
    float nw = c.textWidth(FontStyle.CAPTION, tabTip) + 16.0F;
    float nx = Math.max(x + 4.0F, Math.min(x + w - nw - 4.0F, tabTipX - nw * 0.5F));
    c.pushLayer(8.0F);
    Depth.raised(c, t, nx, tabTipY, nw, 22.0F, 11.0F, t.surface2());
    c.text(
        FontStyle.CAPTION,
        tabTip,
        nx + 8.0F,
        tabTipY + (22.0F - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
        t.ink());
    c.popLayer();
  }

  private void drawTabs(Canvas c, Theme t, float mx, float my, float dt) {
    float ty = y + HEAD + 2.0F;
    float size = TAB - 8.0F;
    float total = kinds.size() * (size + 6.0F) - 6.0F;
    float tx = x + PAD;
    if (total > w - PAD * 2.0F) {
      size = (w - PAD * 2.0F + 6.0F) / kinds.size() - 6.0F;
    }
    int selected = kinds.indexOf(kind);
    if (selected >= 0) {
      float target = tx + selected * (size + 6.0F);
      if (!tabsPlaced) {
        tabGlide.snap(target);
        tabsPlaced = true;
      }
      tabGlide.step(dt, target);
      Depth.raised(c, t, tabGlide.value(), ty, size, size, 10.0F, t.surface2());
    }
    for (int i = 0; i < kinds.size(); i++) {
      float bx = tx + i * (size + 6.0F);
      Kind each = kinds.get(i);
      boolean over = mx >= bx && mx < bx + size && my >= ty && my < ty + size;
      if (over && each != kind) {
        c.roundRect(bx, ty, size, size, 10.0F, Theme.withAlpha(t.ink(), 0.06F));
      }
      int station = groups.get(each).get(0).station();
      float item = size * 0.62F;
      if (station >= 0) {
        c.stack(station, bx + (size - item) * 0.5F, ty + (size - item) * 0.5F, item);
      } else {
        String letter = each.title.substring(0, 1);
        c.text(
            FontStyle.LABEL,
            letter,
            bx + (size - c.textWidth(FontStyle.LABEL, letter)) * 0.5F,
            ty + (size - c.lineHeight(FontStyle.LABEL)) * 0.5F,
            t.ink());
      }
      if (over) {
        tabTip = Lang.tr(each.title) + "  " + groups.get(each).size();
        tabTipX = bx + size * 0.5F;
        tabTipY = ty + size + 4.0F;
      }
      hits.add(TAB_KIND, each, bx, ty, size, size);
    }
  }

  private int pick(int[] options) {
    if (options.length == 1) {
      return options[0];
    }
    return options[(int) (clock / CYCLE_SECONDS) % options.length];
  }

  private void drawCard(
      Canvas c,
      Theme t,
      Recipe recipe,
      float cx,
      float cy,
      float cw,
      float ch,
      float mx,
      float my) {
    Depth.inset(c, t, cx, cy, cw, ch, 12.0F, t.well());
    float item = cell * ITEM_FRACTION;
    float inset = (cell - item) * 0.5F;
    float gridW = recipe.columns() * cell;
    float gridH = recipe.rows() * cell;
    float gx = cx + 14.0F;
    float gy = cy + (ch - gridH) * 0.5F;
    for (int i = 0; i < recipe.cells(); i++) {
      float sx = gx + (i % recipe.columns()) * cell;
      float sy = gy + (i / recipe.columns()) * cell;
      c.roundRect(
          sx + 1.0F, sy + 1.0F, cell - 2.0F, cell - 2.0F, 6.0F, Theme.withAlpha(t.ink(), 0.05F));
      int[] options = recipe.inputs()[i];
      if (options.length == 0) {
        continue;
      }
      int handle = pick(options);
      c.stack(handle, sx + inset, sy + inset, item);
      int count = recipe.counts()[i];
      if (count > 1) {
        Counts.draw(c, t, count, sx + cell - 2.0F, sy + cell - 1.0F, item);
      }
      if (mx >= sx && mx < sx + cell && my >= sy && my < sy + cell) {
        hovered = handle;
        c.roundRect(
            sx + 1.0F, sy + 1.0F, cell - 2.0F, cell - 2.0F, 6.0F, Theme.withAlpha(t.ink(), 0.10F));
      }
      hits.add(CELL, handle, sx, sy, cell, cell);
    }
    float ax = gx + gridW + 8.0F;
    c.text(
        FontStyle.TITLE,
        "→",
        ax + (ARROW - 8.0F - c.textWidth(FontStyle.TITLE, "→")) * 0.5F,
        cy + (ch - c.lineHeight(FontStyle.TITLE)) * 0.5F,
        t.grey());
    float big = cell * 1.3F;
    float ox = ax + ARROW;
    float oy = cy + (ch - big) * 0.5F;
    Depth.raised(c, t, ox, oy, big, big, 10.0F, t.surface2());
    float oitem = big * ITEM_FRACTION;
    c.stack(recipe.output(), ox + (big - oitem) * 0.5F, oy + (big - oitem) * 0.5F, oitem);
    if (recipe.outputCount() > 1) {
      Counts.draw(c, t, recipe.outputCount(), ox + big - 3.0F, oy + big - 2.0F, oitem);
    }
    if (mx >= ox && mx < ox + big && my >= oy && my < oy + big) {
      hovered = recipe.output();
    }
    hits.add(CELL, recipe.output(), ox, oy, big, big);
    if (!recipe.note().isEmpty()) {
      float noteX = ox + big + 12.0F;
      float room = cx + cw - noteX - 12.0F;
      if (room > 40.0F) {
        String note = recipe.note();
        c.pushClip(noteX, cy, room, ch);
        c.text(
            FontStyle.CAPTION,
            note,
            noteX,
            cy + (ch - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
            t.grey());
        c.popClip();
      }
    }
    if (host.canFill(recipe)) {
      hits.pill(c, t, FILL, recipe, "+", cx + cw - 10.0F, cy + 22.0F, mx, my, t.ink(), false);
    }
  }

  public boolean contains(float mx, float my) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
  }

  public boolean mouseDown(float mx, float my, int button) {
    if (!contains(mx, my)) {
      close();
      return true;
    }
    if (button == 0) {
      hits.press(mx, my);
      return true;
    }
    int index = hits.at(mx, my);
    if (index != Hits.NONE && hits.kind(index) == CELL && button == 1) {
      show((Integer) hits.target(index), true);
    }
    return true;
  }

  public void mouseUp(float mx, float my) {
    int index = hits.release(mx, my);
    if (index == Hits.NONE) {
      return;
    }
    Object target = hits.target(index);
    switch (hits.kind(index)) {
      case TAB_KIND -> {
        kind = (Kind) target;
        page = 0;
        reveal.snap(0.0F);
        reveal.set(1.0F);
      }
      case CELL -> show((Integer) target, false);
      case FILL -> {
        host.fill((Recipe) target);
        close();
      }
      case PREVIOUS -> page = Math.max(0, page - 1);
      case NEXT -> page++;
      case CLOSE -> close();
      case BACK -> back();
      default -> {}
    }
  }

  public boolean scroll(float amount) {
    if (amount > 0.0F) {
      page = Math.max(0, page - 1);
    } else if (amount < 0.0F) {
      page++;
    }
    return true;
  }

  public boolean keyDown(int keyCode) {
    if (keyCode == Field.ESCAPE) {
      close();
      return true;
    }
    if (keyCode == Field.BACKSPACE) {
      back();
      return true;
    }
    if (keyCode == Field.LEFT) {
      page = Math.max(0, page - 1);
      return true;
    }
    if (keyCode == Field.RIGHT) {
      page++;
      return true;
    }
    return false;
  }
}
