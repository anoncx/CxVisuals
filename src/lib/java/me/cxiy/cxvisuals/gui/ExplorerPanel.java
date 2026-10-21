package me.cxiy.cxvisuals.gui;

import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.module.ClientSettings.WindowMotion;
import me.cxiy.cxvisuals.gui.spatial.Spring;
import me.cxiy.cxvisuals.modules.inventory.Catalog;
import me.cxiy.cxvisuals.modules.inventory.Favorites;
import me.cxiy.cxvisuals.modules.inventory.Handles;
import me.cxiy.cxvisuals.modules.inventory.InventoryHost;
import me.cxiy.cxvisuals.modules.inventory.InventoryModule;
import me.cxiy.cxvisuals.modules.inventory.Query;

public final class ExplorerPanel {
  private static final float PAD = 10.0F;
  private static final float DISSOLVE_PAD = 24.0F;
  private static final float PAGER = 28.0F;
  private static final float GAP = 8.0F;
  private static final float ITEM_FRACTION = 0.8889F;
  private static final float FADE_SECONDS = 0.16F;
  private static final int PREVIOUS = 1;
  private static final int NEXT = 2;
  private static final int SEARCH = 3;
  private static final int CLEAR = 4;
  private static final int SCROLL_STEP = 1;

  private final InventoryModule module;
  private final Handles handles;
  private final Catalog catalog;
  private final Favorites favorites;
  private final InventoryHost host;
  private final Field field = new Field(Lang.key("Search  @mod  $tooltip"), 48);
  private final Hits hits = new Hits(8);
  private final Spring glideX = Spring.critical(420.0F);
  private final Spring glideY = Spring.critical(420.0F);
  private final Spring glow = Spring.critical(300.0F);
  private final Anim fade = new Anim(1.0F, FADE_SECONDS, Anim.Curve.SNAPPY);
  private final Presence presence = new Presence();
  private final Supplier<WindowMotion> motion;
  private boolean anchorRight = true;
  private boolean framed;

  private float x;
  private float y;
  private float w;
  private float h;
  private float cell = 18.0F;
  private int columns;
  private int rows;
  private int favoriteRows;
  private int page;
  private int hovered = Handles.NONE;
  private int hoveredFavorite = Handles.NONE;
  private boolean glowing;
  private String applied = "";
  private int revision = -1;
  private float gridX;
  private float gridY;
  private float favoriteY;
  private float searchY;
  private float pagerY;

  public ExplorerPanel(
      InventoryModule module,
      Handles handles,
      Catalog catalog,
      Favorites favorites,
      InventoryHost host,
      Supplier<WindowMotion> motion) {
    this.motion = motion;
    this.module = module;
    this.handles = handles;
    this.catalog = catalog;
    this.favorites = favorites;
    this.host = host;
  }

  public void open(boolean rightSide) {
    anchorRight = rightSide;
    presence.snap(0.0F);
  }

  public static float chrome() {
    return PAD * 2.0F + PAGER + GAP * 2.0F + Field.HEIGHT;
  }

  public static float padding() {
    return PAD;
  }

  public void layout(float left, float top, float width, float height, float cellSize) {
    x = left;
    y = top;
    w = width;
    h = height;
    cell = cellSize;
    columns = Math.max(1, (int) ((w - PAD * 2.0F) / cell));
    gridX = x + (w - columns * cell) * 0.5F;
    searchY = y + h - PAD - Field.HEIGHT;
    pagerY = searchY - GAP - PAGER;
    int favoriteCount = favorites.handles().length;
    favoriteRows = favoriteCount == 0 ? 0 : Math.min(2, (favoriteCount + columns - 1) / columns);
    favoriteY = y + PAD;
    float favoriteBlock = favoriteRows == 0 ? 0.0F : favoriteRows * cell + 14.0F + GAP;
    gridY = favoriteY + favoriteBlock;
    rows = Math.max(1, (int) ((pagerY - GAP - gridY) / cell));
  }

  public boolean contains(float mx, float my) {
    return mx >= x && mx < x + w && my >= y && my < y + h;
  }

  public boolean typing() {
    return field.focused();
  }

  public void focusSearch() {
    field.focus(true);
  }

  public int hovered() {
    return hoveredFavorite != Handles.NONE ? hoveredFavorite : hovered;
  }

  public void toggleFavorite(int handle) {
    favorites.toggle(handle);
  }

  public void draw(Canvas c, Theme t, float mx, float my, float dt) {
    syncQuery();
    int perPage = columns * rows;
    int pages = Math.max(1, (catalog.size() + perPage - 1) / perPage);
    page = Math.max(0, Math.min(page, pages - 1));
    fade.update(dt);

    WindowMotion style = motion.get();
    presence.step(style, true, dt);
    float opacity = (float) (module.opacity.get() / 100.0);
    float radius = t.radius();
    float cardW = presence.width(style, w);
    float cardH = presence.height(style, h);
    float cardX = anchorRight ? x + w - cardW : x;
    float cardY = y + h - cardH + presence.rise(style);
    framed = cardW < w - 0.5F || cardH < h - 0.5F;
    float pivotX = anchorRight ? x + w : x;
    c.pushAlpha(presence.alpha(style));
    c.pushTransform(presence.scale(style), pivotX, y + h);
    c.beginDissolve(
        x - DISSOLVE_PAD,
        y - DISSOLVE_PAD,
        w + DISSOLVE_PAD * 2.0F,
        h + DISSOLVE_PAD * 2.0F,
        presence.erosion(style));
    c.shadow(cardX, cardY, cardW, cardH, radius, 18.0F, 6.0F, t.cardShadow());
    if (t.glass()) {
      c.glass(cardX, cardY, cardW, cardH, radius, Theme.withAlpha(t.paper(), opacity));
    } else {
      c.roundRect(cardX, cardY, cardW, cardH, radius, Theme.withAlpha(t.paper(), opacity));
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
    if (framed) {
      c.pushClip(cardX, cardY, cardW, cardH);
    }
    c.pushAlpha(presence.content(style));

    hits.clear();
    hovered = Handles.NONE;
    hoveredFavorite = Handles.NONE;
    int hoverIndex = -1;
    float hoverX = 0.0F;
    float hoverY = 0.0F;
    float item = cell * ITEM_FRACTION;
    float inset = (cell - item) * 0.5F;

    int[] pinned = favorites.handles();
    if (favoriteRows > 0) {
      c.text(FontStyle.CAPTION, Lang.tr("Favourites"), gridX + 2.0F, favoriteY - 1.0F, t.grey());
      float rowTop = favoriteY + 14.0F;
      int shown = Math.min(pinned.length, favoriteRows * columns);
      for (int i = 0; i < shown; i++) {
        float cx = gridX + (i % columns) * cell;
        float cy = rowTop + (i / columns) * cell;
        if (mx >= cx && mx < cx + cell && my >= cy && my < cy + cell) {
          hoveredFavorite = pinned[i];
          hoverX = cx;
          hoverY = cy;
          hoverIndex = i;
        }
      }
      float indicatorX = glideX.value();
      float indicatorY = glideY.value();
      if (hoverIndex >= 0 && hoveredFavorite != Handles.NONE) {
        updateGlide(hoverX, hoverY, dt);
        drawIndicator(c, t, indicatorX, indicatorY);
      }
      for (int i = 0; i < shown; i++) {
        c.stack(
            pinned[i],
            gridX + (i % columns) * cell + inset,
            rowTop + (i / columns) * cell + inset,
            item);
      }
      float rule = rowTop + favoriteRows * cell + GAP * 0.5F;
      c.roundRect(gridX, rule, columns * cell, 1.0F, 0.5F, t.hair());
    }

    int start = page * perPage;
    int visible = Math.min(perPage, catalog.size() - start);
    float alpha = 0.35F + 0.65F * fade.get();
    float lift = (1.0F - fade.get()) * 6.0F;
    c.pushAlpha(alpha);
    for (int i = 0; i < visible; i++) {
      float cx = gridX + (i % columns) * cell;
      float cy = gridY + (i / columns) * cell + lift;
      int handle = catalog.at(start + i);
      if (hoveredFavorite == Handles.NONE
          && mx >= cx
          && mx < cx + cell
          && my >= cy - lift
          && my < cy - lift + cell
          && my >= gridY
          && my < gridY + rows * cell) {
        hovered = handle;
        hoverX = cx;
        hoverY = cy - lift;
        hoverIndex = i;
      }
    }
    if (hovered != Handles.NONE) {
      updateGlide(hoverX, hoverY, dt);
    } else if (hoveredFavorite == Handles.NONE) {
      glowing = false;
    }
    glow.step(dt, hovered != Handles.NONE || hoveredFavorite != Handles.NONE ? 1.0F : 0.0F);
    if (hovered != Handles.NONE) {
      drawIndicator(c, t, glideX.value(), glideY.value());
    }
    for (int i = 0; i < visible; i++) {
      int handle = catalog.at(start + i);
      float cx = gridX + (i % columns) * cell;
      float cy = gridY + (i / columns) * cell + lift;
      c.stack(handle, cx + inset, cy + inset, item);
      if (favorites.has(handle)) {
        c.roundRect(cx + cell - 6.0F, cy + 2.0F, 4.0F, 4.0F, 2.0F, t.red());
      }
    }
    c.popAlpha();
    if (visible == 0) {
      String empty = Lang.tr(catalog.total() == 0 ? "Loading items" : "Nothing matches");
      c.text(
          FontStyle.LABEL,
          empty,
          gridX + (columns * cell - c.textWidth(FontStyle.LABEL, empty)) * 0.5F,
          gridY + cell * 1.5F,
          t.grey());
    }

    drawPager(c, t, mx, my, dt, page, pages);
    drawSearch(c, t, mx, my, dt);
    c.popAlpha();
    if (framed) {
      c.popClip();
    }
    c.endDissolve();
    c.popTransform();
    c.popAlpha();
    host.hover(hoveredFavorite != Handles.NONE ? hoveredFavorite : hovered);
  }

  private void updateGlide(float tx, float ty, float dt) {
    if (!glowing) {
      glideX.snap(tx);
      glideY.snap(ty);
      glowing = true;
    }
    glideX.step(dt, tx);
    glideY.step(dt, ty);
  }

  private void drawIndicator(Canvas c, Theme t, float ix, float iy) {
    float strength = Math.max(0.0F, Math.min(1.0F, glow.value()));
    c.roundRect(
        ix + 1.0F,
        iy + 1.0F,
        cell - 2.0F,
        cell - 2.0F,
        6.0F,
        Theme.withAlpha(t.ink(), 0.12F * strength));
  }

  private void drawPager(Canvas c, Theme t, float mx, float my, float dt, int current, int pages) {
    float cy = pagerY + PAGER * 0.5F;
    float left = gridX;
    float right = gridX + columns * cell;
    float size = PAGER - 4.0F;
    pagerButton(c, t, PREVIOUS, "‹", left, cy, size, mx, my, current > 0);
    pagerButton(c, t, NEXT, "›", right - size, cy, size, mx, my, current < pages - 1);
    String label = (current + 1) + " / " + pages;
    float lw = c.textWidth(FontStyle.LABEL, label);
    c.text(
        FontStyle.LABEL,
        label,
        (left + right - lw) * 0.5F,
        cy - c.lineHeight(FontStyle.LABEL) * 0.5F - 4.0F,
        t.ink());
    String total = catalog.size() + (catalog.size() == 1 ? " item" : " items");
    float tw = c.textWidth(FontStyle.CAPTION, total);
    c.text(FontStyle.CAPTION, total, (left + right - tw) * 0.5F, cy + 3.0F, t.grey());
  }

  private void pagerButton(
      Canvas c,
      Theme t,
      int kind,
      String glyph,
      float bx,
      float cy,
      float size,
      float mx,
      float my,
      boolean enabled) {
    float by = cy - size * 0.5F;
    boolean over = enabled && mx >= bx && mx < bx + size && my >= by && my < by + size;
    float tint = hits.down(kind, null) && over ? 0.16F : over ? 0.08F : 0.0F;
    int fill = Theme.mix(t.surface2(), t.ink(), tint);
    Depth.raised(c, t, bx, by, size, size, size * 0.5F, fill);
    int ink = enabled ? t.ink() : t.dotOff();
    c.text(
        FontStyle.ROW,
        glyph,
        bx + (size - c.textWidth(FontStyle.ROW, glyph)) * 0.5F,
        by + (size - c.lineHeight(FontStyle.ROW)) * 0.5F - 1.0F,
        ink);
    if (enabled) {
      hits.add(kind, null, bx, by, size, size);
    }
  }

  private void drawSearch(Canvas c, Theme t, float mx, float my, float dt) {
    float fx = gridX;
    float fw = columns * cell;
    field.draw(c, t, fx, searchY, fw, dt);
    hits.add(SEARCH, null, fx, searchY, fw, Field.HEIGHT);
    if (!field.text().isEmpty()) {
      float size = 18.0F;
      float bx = fx + fw - size - 6.0F;
      float by = searchY + (Field.HEIGHT - size) * 0.5F;
      boolean over = mx >= bx && mx < bx + size && my >= by && my < by + size;
      c.roundRect(
          bx, by, size, size, size * 0.5F, Theme.mix(t.surface2(), t.ink(), over ? 0.2F : 0.1F));
      c.text(
          FontStyle.CAPTION,
          "×",
          bx + (size - c.textWidth(FontStyle.CAPTION, "×")) * 0.5F,
          by + (size - c.lineHeight(FontStyle.CAPTION)) * 0.5F - 1.0F,
          t.ink());
      hits.add(CLEAR, null, bx, by, size, size);
    }
  }

  private void syncQuery() {
    String text = field.text();
    if (!text.equals(applied) || revision != catalog.revision()) {
      if (!text.equals(applied)) {
        page = 0;
        fade.snap(0.0F);
        fade.set(1.0F);
      }
      applied = text;
      revision = catalog.revision();
      catalog.search(Query.parse(text));
    }
  }

  public boolean mouseDown(float mx, float my, int button, boolean shift, boolean control) {
    if (!contains(mx, my)) {
      field.focus(false);
      return false;
    }
    if (button == 0 && hits.press(mx, my)) {
      int index = hits.at(mx, my);
      int kind = hits.kind(index);
      field.focus(kind == SEARCH);
      if (kind == SEARCH) {
        field.press(mx);
      }
      return true;
    }
    int handle = hovered();
    if (handle != Handles.NONE) {
      field.focus(false);
      if (button == 0 && (shift || control) && host.canGive()) {
        host.give(handle, control);
      } else if (button == 0) {
        host.recipes(handle);
      } else if (button == 1) {
        host.uses(handle);
      } else if (button == 2) {
        favorites.toggle(handle);
      }
    }
    return true;
  }

  public void mouseDrag(float mx) {
    if (field.focused()) {
      field.drag(mx);
    }
  }

  public void mouseUp(float mx, float my) {
    field.release();
    int index = hits.release(mx, my);
    if (index == Hits.NONE) {
      return;
    }
    switch (hits.kind(index)) {
      case PREVIOUS -> turn(-1);
      case NEXT -> turn(1);
      case CLEAR -> field.set("");
      default -> {}
    }
  }

  public boolean scroll(float mx, float my, float amount) {
    if (!contains(mx, my)) {
      return false;
    }
    turn(amount > 0.0F ? -SCROLL_STEP : SCROLL_STEP);
    return true;
  }

  private void turn(int direction) {
    int perPage = columns * rows;
    int pages = Math.max(1, (catalog.size() + perPage - 1) / perPage);
    int next = Math.max(0, Math.min(pages - 1, page + direction));
    if (next != page) {
      page = next;
      fade.snap(0.0F);
      fade.set(1.0F);
    }
  }

  public boolean keyDown(int keyCode, boolean control, boolean shift) {
    if (!field.focused()) {
      return false;
    }
    if (keyCode == Field.ESCAPE || keyCode == Field.ENTER || keyCode == Field.KEYPAD_ENTER) {
      field.focus(false);
      return true;
    }
    field.key(keyCode, control, shift);
    return true;
  }

  public boolean charTyped(int codepoint) {
    return field.focused() && field.type(codepoint);
  }
}
