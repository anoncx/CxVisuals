package me.cxiy.cxvisuals.gui;

import static me.cxiy.cxvisuals.gui.ModuleMetrics.CARD_HEIGHT;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.HIDDEN;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.SCROLL_STEP;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.ease;

import java.util.List;
import java.util.function.Predicate;
import me.cxiy.cxvisuals.core.module.Module;

final class ModulesSidebar {
  static final float WIDTH = 168.0F;
  static final int NONE = -1;
  static final int CONSUMED = -2;

  private static final float ITEM = 30.0F;
  private static final float ITEM_INSET = 8.0F;
  private static final float ITEMS_TOP = 58.0F;
  private static final float GROUP_SPACE = 8.0F;
  private static final float WELL_PAD = 3.0F;
  private static final float WELL_RADIUS = 12.0F;
  private static final float WELL_GLASS = 0.4F;
  private static final float MARKER_RADIUS = 9.0F;
  private static final float PINNED_BOTTOM = 8.0F;
  private static final float PINNED_GAP = 8.0F;
  private static final float DOT = 5.0F;
  private static final float OPEN_INSET = 5.0F;
  private static final float OPEN_WIDTH = 2.0F;
  private static final float OPEN_HEIGHT = 12.0F;
  private static final float OPEN_ALPHA = 0.55F;
  private static final float FADE_BASIS = 40.0F;
  private static final float WORD_INSET = 14.0F;
  private static final float WORD_TOP = 15.0F;
  private static final float SEARCH_INSET = 8.0F;
  private static final float SEARCH_TOP = 16.0F;
  private static final float LABEL_INSET = 12.0F;
  private static final float DOT_INSET = 17.0F;
  private static final String WORDMARK = "CXVISUALS";

  private final List<ModuleView> entries;
  private final int moduleCount;
  private final int pinnedCount;
  private final float[] offsets;
  private final float[] wellTops;
  private final float[] wellHeights;
  private final int wells;
  private final float content;
  private final Predicate<Module> opened;
  private final IconButton search;
  private final Anim[] emphasis;
  private final Anim[] hover;
  private final Anim[] lit;
  private final Anim marker = Anim.pop(0.0F);
  private final Anim pinnedGlow = Anim.state(0.0F);
  private final Anim pinnedMarker = Anim.pop(0.0F);
  private float x;
  private float y;
  private float height;
  private float scroll;
  private float scrollTarget;

  ModulesSidebar(List<ModuleView> entries, Runnable onSearch, Predicate<Module> opened) {
    this.entries = entries;
    this.opened = opened;
    this.search = new IconButton(IconButton.Glyph.SEARCH, IconButton.LABEL_SEARCH, onSearch);
    this.pinnedCount = entries.isEmpty() ? 0 : countPinned();
    this.moduleCount = entries.size() - pinnedCount;
    this.offsets = new float[moduleCount];
    this.wellTops = new float[moduleCount];
    this.wellHeights = new float[moduleCount];
    this.emphasis = new Anim[entries.size()];
    this.hover = new Anim[entries.size()];
    this.lit = new Anim[entries.size()];
    for (int i = 0; i < entries.size(); i++) {
      emphasis[i] = Anim.state(0.0F);
      hover[i] = Anim.hover(0.0F);
      lit[i] = Anim.reveal(entries.get(i).module.live() ? 1.0F : 0.0F);
    }
    float cursor = 0.0F;
    int well = -1;
    String group = "";
    for (int i = 0; i < moduleCount; i++) {
      String next = entries.get(i).module.group();
      if (i == 0 || !next.equals(group)) {
        if (well >= 0) {
          cursor += WELL_PAD + GROUP_SPACE;
        }
        well++;
        wellTops[well] = cursor;
        cursor += WELL_PAD;
      }
      group = next;
      offsets[i] = cursor;
      cursor += ITEM;
      wellHeights[well] = cursor + WELL_PAD - wellTops[well];
    }
    if (well >= 0) {
      cursor += WELL_PAD;
    }
    this.wells = well + 1;
    this.content = cursor;
    if (moduleCount > 0) {
      marker.snap(offsets[0]);
    }
    if (moduleCount == 0) {
      pinnedGlow.snap(1.0F);
    }
  }

  private int countPinned() {
    int pinned = 0;
    for (int i = entries.size() - 1; i >= 0; i--) {
      if (entries.get(i).module.pinned()) {
        pinned++;
      } else {
        break;
      }
    }
    return Math.max(1, pinned);
  }

  void place(float x, float y, float height) {
    this.x = x;
    this.y = y;
    this.height = height;
  }

  IconButton search() {
    return search;
  }

  int moduleCount() {
    return moduleCount;
  }

  float scroll() {
    return scroll;
  }

  float maxScroll() {
    return Math.max(0.0F, content - listHeight());
  }

  private float listTop() {
    return y + ITEMS_TOP;
  }

  private float pinnedTop() {
    return y + height - PINNED_BOTTOM - WELL_PAD - ITEM * pinnedCount;
  }

  private float listHeight() {
    return pinnedTop() - WELL_PAD - PINNED_GAP - listTop();
  }

  float itemTop(int index) {
    if (index < moduleCount) {
      return listTop() + offsets[index] - scroll;
    }
    return pinnedTop() + (index - moduleCount) * ITEM;
  }

  float itemLeft() {
    return x + ITEM_INSET;
  }

  float itemWidth() {
    return WIDTH - ITEM_INSET * 2.0F;
  }

  float itemHeight() {
    return ITEM;
  }

  void adopt(ModulesSidebar previous) {
    scroll = previous.scroll;
    scrollTarget = previous.scrollTarget;
    marker.snap(previous.marker.get());
    pinnedMarker.snap(previous.pinnedMarker.get());
    pinnedGlow.snap(previous.pinnedGlow.get());
  }

  void scrollBy(float amount) {
    scrollTarget = Math.max(0.0F, Math.min(maxScroll(), scrollTarget - amount * SCROLL_STEP));
  }

  void advance(float dt) {
    scrollTarget = Math.max(0.0F, Math.min(maxScroll(), scrollTarget));
    scroll = ease(scroll, scrollTarget, dt);
  }

  int hit(float mx, float my) {
    float itemX = itemLeft();
    if (mx < itemX || mx >= itemX + itemWidth()) {
      return NONE;
    }
    float pinnedTop = pinnedTop();
    for (int p = 0; p < pinnedCount; p++) {
      if (my >= pinnedTop + p * ITEM && my < pinnedTop + (p + 1) * ITEM) {
        return moduleCount + p;
      }
    }
    float listTop = listTop();
    if (my < listTop || my >= listTop + listHeight()) {
      return NONE;
    }
    float at = my - listTop + scroll;
    for (int i = 0; i < moduleCount; i++) {
      if (at >= offsets[i] && at < offsets[i] + ITEM) {
        return i;
      }
    }
    return CONSUMED;
  }

  void draw(Canvas c, Theme t, int selected, float mx, float my, float dt) {
    c.pushLayer(CARD_HEIGHT);
    Depth.card(c, t, x, y, WIDTH, height);
    c.popLayer();
    c.text(FontStyle.DOTS, WORDMARK, x + WORD_INSET, y + WORD_TOP, t.ink());
    search.place(x + WIDTH - IconButton.SIZE - SEARCH_INSET, y + SEARCH_TOP);
    search.draw(c, t, mx, my, dt);

    float itemX = itemLeft();
    float itemW = itemWidth();
    float listTop = listTop();
    float listHeight = listHeight();
    float pinnedTop = pinnedTop();
    boolean pinnedSelected = selected >= moduleCount;
    float textTop = (ITEM - c.lineHeight(FontStyle.LABEL)) * 0.5F;

    if (!pinnedSelected) {
      marker.set(offsets[selected]);
    }
    marker.update(dt);
    pinnedGlow.set(pinnedSelected ? 1.0F : 0.0F);
    pinnedGlow.update(dt);
    float glow = Math.max(0.0F, Math.min(1.0F, pinnedGlow.get()));

    int well = t.translucent() ? Theme.withAlpha(t.well(), WELL_GLASS) : t.well();
    float listBottom = listTop + listHeight;
    c.pushClip(itemX, listTop, itemW, listHeight);
    for (int i = 0; i < wells; i++) {
      float wellTop = listTop + wellTops[i] - scroll;
      float shown = visibility(wellTop, wellHeights[i], listTop, listBottom);
      if (shown > HIDDEN) {
        c.pushAlpha(shown);
        Depth.inset(c, t, itemX, wellTop, itemW, wellHeights[i], WELL_RADIUS, well);
        c.popAlpha();
      }
    }
    if (glow < 1.0F && moduleCount > 0) {
      float markerTop = listTop + marker.get() - scroll;
      c.pushAlpha((1.0F - glow) * visibility(markerTop, ITEM, listTop, listBottom));
      Depth.raised(
          c,
          t,
          itemX + WELL_PAD,
          markerTop,
          itemW - WELL_PAD * 2.0F,
          ITEM,
          MARKER_RADIUS,
          t.surface2());
      c.popAlpha();
    }
    boolean overList = my >= listTop && my < listTop + listHeight;
    for (int i = 0; i < moduleCount; i++) {
      float itemY = listTop + offsets[i] - scroll;
      boolean hovered =
          overList && mx >= itemX && mx < itemX + itemW && my >= itemY && my < itemY + ITEM;
      animate(i, selected, hovered, dt);
      float shown = visibility(itemY, ITEM, listTop, listBottom);
      if (shown <= HIDDEN) {
        continue;
      }
      c.pushAlpha(shown);
      drawItem(c, t, i, itemX, itemW, itemY, textTop);
      c.popAlpha();
    }
    c.popClip();

    Depth.inset(
        c,
        t,
        itemX,
        pinnedTop - WELL_PAD,
        itemW,
        ITEM * pinnedCount + WELL_PAD * 2.0F,
        WELL_RADIUS,
        well);
    if (pinnedSelected) {
      pinnedMarker.set(selected - moduleCount);
    }
    pinnedMarker.update(dt);
    if (glow > 0.0F) {
      c.pushAlpha(glow);
      Depth.raised(
          c,
          t,
          itemX + WELL_PAD,
          pinnedTop + pinnedMarker.get() * ITEM,
          itemW - WELL_PAD * 2.0F,
          ITEM,
          MARKER_RADIUS,
          t.surface2());
      c.popAlpha();
    }
    for (int p = 0; p < pinnedCount; p++) {
      int index = moduleCount + p;
      float rowTop = pinnedTop + p * ITEM;
      boolean hovered = mx >= itemX && mx < itemX + itemW && my >= rowTop && my < rowTop + ITEM;
      animate(index, selected, hovered, dt);
      drawItem(c, t, index, itemX, itemW, rowTop, textTop);
    }
  }

  private static float visibility(float top, float height, float from, float to) {
    float visible = Math.min(to, top + height) - Math.max(from, top);
    return Math.max(0.0F, Math.min(1.0F, visible / Math.min(height, FADE_BASIS)));
  }

  private void animate(int index, int selected, boolean hovered, float dt) {
    emphasis[index].set(index == selected ? 1.0F : 0.0F);
    hover[index].set(hovered ? 1.0F : 0.0F);
    lit[index].set(entries.get(index).module.live() ? 1.0F : 0.0F);
    emphasis[index].update(dt);
    hover[index].update(dt);
    lit[index].update(dt);
  }

  private void drawItem(
      Canvas c, Theme t, int index, float itemX, float itemW, float itemY, float textTop) {
    int idle = Theme.mix(t.grey(), t.ink2(), hover[index].get());
    c.text(
        FontStyle.LABEL,
        entries.get(index).module.name(),
        itemX + LABEL_INSET,
        itemY + textTop,
        Theme.mix(idle, t.ink(), emphasis[index].get()));
    if (opened.test(entries.get(index).module)) {
      c.roundRect(
          itemX + OPEN_INSET,
          itemY + (ITEM - OPEN_HEIGHT) * 0.5F,
          OPEN_WIDTH,
          OPEN_HEIGHT,
          OPEN_WIDTH * 0.5F,
          Theme.withAlpha(t.ink(), OPEN_ALPHA));
    }
    if (lit[index].get() > HIDDEN) {
      c.roundRect(
          itemX + itemW - DOT_INSET,
          itemY + (ITEM - DOT) * 0.5F,
          DOT,
          DOT,
          DOT * 0.5F,
          Theme.withAlpha(t.red(), lit[index].get()));
    }
  }
}
