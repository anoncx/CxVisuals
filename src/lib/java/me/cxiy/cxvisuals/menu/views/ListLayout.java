package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.menu.model.Item;
import me.cxiy.cxvisuals.menu.model.ItemList;

final class ListLayout {
  static final float ITEM = 58.0F;
  static final float ITEM_HEADER = 34.0F;

  private static final float ICON = 40.0F;
  private static final float ICON_RADIUS = 10.0F;
  static final float BAR_WIDTH = 3.0F;
  static final float BAR_GAP = 2.0F;
  private static final float BAR_STEP = 2.5F;

  private ListLayout() {}

  static float height(FormPage p) {
    float content = 0.0F;
    ItemList items = p.form.items;
    for (int i = 0; i < items.size(); i++) {
      content += items.get(i).header ? ITEM_HEADER : ITEM;
    }
    return content;
  }

  static void items(FormPage p, Canvas c, Theme t, float cursor, float mouseX, float mouseY) {
    ItemList items = p.form.items;
    int count = items.size();
    boolean bare = true;
    for (int i = 0; i < count && bare; i++) {
      bare = items.get(i).header;
    }
    if (bare && !items.empty().isEmpty()) {
      String text = items.empty();
      c.text(
          FontStyle.LABEL,
          text,
          p.bodyLeft + (p.bodyWidth - c.textWidth(FontStyle.LABEL, text)) * 0.5F,
          p.bodyTop + p.bodyHeight * 0.42F,
          t.grey());
    }
    int selected = items.selected();
    float left = p.bodyLeft + 6.0F;
    float width = p.bodyWidth - 12.0F;
    float small = c.lineHeight(FontStyle.SMALL);
    int order = 0;
    for (int i = 0; i < count; i++) {
      Item item = items.get(i);
      float height = item.header ? ITEM_HEADER : ITEM;
      if (cursor + height <= p.bodyTop || cursor >= p.bodyTop + p.bodyHeight) {
        cursor += height;
        continue;
      }
      c.pushAlpha(Kit.reveal(p.age, order++));
      if (item.header) {
        c.text(
            FontStyle.CAPTION,
            item.title,
            left + 10.0F,
            cursor + height - c.lineHeight(FontStyle.CAPTION) - 5.0F,
            t.grey());
        c.popAlpha();
        cursor += height;
        continue;
      }
      boolean over = Kit.inside(mouseX, mouseY, left, cursor, width, height);
      if (i == selected) {
        Depth.raised(c, t, left, cursor + 2.0F, width, height - 4.0F, 14.0F, t.surface2());
      } else if (over) {
        c.roundRect(
            left, cursor + 2.0F, width, height - 4.0F, 14.0F, Theme.withAlpha(t.ink(), 0.06F));
      }
      float iconX = left + 9.0F;
      float iconY = cursor + (height - ICON) * 0.5F;
      if (!c.icon(item.icon, iconX, iconY, ICON, ICON_RADIUS)) {
        c.roundRect(iconX, iconY, ICON, ICON, ICON_RADIUS, Theme.withAlpha(t.ink(), 0.10F));
        String initial =
            item.title.isEmpty()
                ? "?"
                : item.title.substring(0, item.title.offsetByCodePoints(0, 1)).toUpperCase();
        c.text(
            FontStyle.DOTS,
            initial,
            iconX + (ICON - c.textWidth(FontStyle.DOTS, initial)) * 0.5F,
            iconY + (ICON - c.lineHeight(FontStyle.DOTS)) * 0.5F,
            t.grey());
      }
      float textX = iconX + ICON + 12.0F;
      float trailing = c.textWidth(FontStyle.MONO, item.trailing);
      float right = left + width - 12.0F;
      float signalWidth =
          item.signal == Item.NO_SIGNAL ? 0.0F : Item.BARS * (BAR_WIDTH + BAR_GAP) + 8.0F;
      boolean two = item.second.count > 0;
      float titleTop = cursor + (two ? 5.0F : 9.0F);
      c.pushClip(textX, cursor, right - signalWidth - trailing - 10.0F - textX, height);
      c.text(FontStyle.ROW, item.title, textX, titleTop, t.ink());
      float firstTop = titleTop + c.lineHeight(FontStyle.ROW) - 1.0F;
      c.styled(FontStyle.SMALL, item.first, textX, firstTop, t.grey());
      if (two) {
        c.styled(FontStyle.SMALL, item.second, textX, firstTop + small - 3.0F, t.grey());
      }
      c.popClip();
      if (!item.trailing.isEmpty()) {
        c.text(
            FontStyle.MONO,
            item.trailing,
            right - signalWidth - trailing,
            titleTop + 1.0F,
            item.flagged ? t.alert() : t.grey());
      }
      if (!item.note.isEmpty()) {
        c.text(
            FontStyle.SMALL,
            item.note,
            right - c.textWidth(FontStyle.SMALL, item.note),
            firstTop + small - 3.0F,
            item.flagged ? t.alert() : t.grey());
      }
      if (item.signal != Item.NO_SIGNAL) {
        bars(c, t, right - signalWidth + 8.0F, titleTop + 2.0F, item.signal, p.clock);
      }
      c.popAlpha();
      cursor += height;
    }
  }

  static void bars(Canvas c, Theme t, float x, float top, int signal, float clock) {
    float tallest = Item.BARS * BAR_STEP + 1.0F;
    for (int bar = 0; bar < Item.BARS; bar++) {
      float height = (bar + 1) * BAR_STEP + 1.0F;
      boolean lit = signal == Item.BUSY ? (int) (clock * 8.0F) % Item.BARS == bar : bar < signal;
      int ink = signal == 0 && bar == 0 ? t.alert() : lit ? t.ink() : t.dotOff();
      c.roundRect(
          x + bar * (BAR_WIDTH + BAR_GAP), top + tallest - height, BAR_WIDTH, height, 1.0F, ink);
    }
  }

  static int itemAt(FormPage p, float mx, float my) {
    if (p.layout != FormPage.LIST
        || !Kit.inside(mx, my, p.bodyLeft, p.bodyTop, p.bodyWidth, p.bodyHeight)) {
      return ItemList.NONE;
    }
    ItemList items = p.form.items;
    float cursor = p.itemsTop;
    for (int i = 0; i < items.size(); i++) {
      Item item = items.get(i);
      float height = item.header ? ITEM_HEADER : ITEM;
      if (my >= cursor && my < cursor + height) {
        return item.header ? ItemList.NONE : i;
      }
      cursor += height;
    }
    return ItemList.NONE;
  }
}
