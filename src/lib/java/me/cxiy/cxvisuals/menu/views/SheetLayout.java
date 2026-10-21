package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;

final class SheetLayout {
  static final float SIDEBAR = 190.0F;

  private static final float SHEET_WIDTH = 580.0F;
  private static final float LIST_WIDTH = 720.0F;
  private static final float SPLIT_WIDTH = 800.0F;
  private static final float TALL = 540.0F;
  private static final float HEADER = 60.0F;
  private static final float FOOTER = 64.0F;
  private static final float SIDE_ITEM = 32.0F;
  private static final float TABS = 44.0F;
  private static final float TAB = 30.0F;
  private static final float RULE = 1.0F;

  private SheetLayout() {}

  static void measure(FormPage p, Canvas c, float roomW, float roomH) {
    float wide =
        p.layout == FormPage.LIST
            ? LIST_WIDTH
            : p.layout == FormPage.SPLIT ? SPLIT_WIDTH : SHEET_WIDTH;
    float width = Math.min(wide, roomW);
    p.top = HEADER + (p.tabs.isEmpty() ? 0.0F : TABS);
    p.footer = Kit.PAD;
    p.footerPad = Kit.PILL_PAD;
    p.footerGap = Kit.GAP;
    if (!p.actions.isEmpty()) {
      float avail = width - Kit.PAD * 2.0F;
      int rowCount = pack(p, c, avail, Kit.PILL_PAD, Kit.GAP);
      if (rowCount > 1) {
        int tightRows = pack(p, c, avail, Kit.TIGHT_PAD, Kit.TIGHT_GAP);
        if (tightRows <= rowCount) {
          rowCount = tightRows;
          p.footerPad = Kit.TIGHT_PAD;
          p.footerGap = Kit.TIGHT_GAP;
        }
      } else {
        float wanted = -Kit.GAP;
        for (ControlView view : p.actions) {
          wanted += Kit.pillWidth(c, view.control.label()) + Kit.GAP;
        }
        if (wanted > avail) {
          p.footerPad = Kit.TIGHT_PAD;
          p.footerGap = Kit.TIGHT_GAP;
        }
      }
      p.footer =
          rowCount <= 1
              ? FOOTER
              : Kit.PAD + rowCount * Kit.PILL + (rowCount - 1) * p.footerGap + 12.0F;
    }
    float content = p.layout == FormPage.LIST ? ListLayout.height(p) : 0.0F;
    for (FormPage.RowView row : p.rows) {
      content += FormPage.rowHeight(row);
    }
    p.contentHeight = content;
    float roomy = Math.min(TALL, roomH);
    p.wantW = width;
    p.wantH =
        p.layout == FormPage.SHEET ? Math.min(roomy, p.top + content + p.footer + 8.0F) : roomy;
  }

  private static int pack(FormPage p, Canvas c, float avail, float pad, float gap) {
    int rows = 1;
    float used = 0.0F;
    for (ControlView view : p.actions) {
      float w = Kit.pillWidth(c, view.control.label(), pad);
      float need = used <= 0.0F ? w : used + gap + w;
      if (used > 0.0F && need > avail) {
        rows++;
        used = w;
      } else {
        used = need;
      }
    }
    return rows;
  }

  static void draw(FormPage p, Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    p.bodyLeft = p.wx + (p.layout == FormPage.SPLIT ? SIDEBAR : 0.0F) + Kit.PAD - ControlView.INSET;
    p.bodyWidth = p.wx + p.ww - (Kit.PAD - ControlView.INSET) - p.bodyLeft;
    p.bodyTop = p.wy + p.top;
    p.bodyHeight = p.wh - p.top - p.footer;
    if (p.layout == FormPage.SPLIT) {
      side(p, c, t, mouseX, mouseY, dt);
    }
    float titleLeft = p.bodyLeft + ControlView.INSET;
    c.pushClip(titleLeft, p.wy, p.bodyWidth - ControlView.INSET * 2.0F, HEADER);
    c.text(
        FontStyle.TITLE,
        p.form.title,
        titleLeft,
        p.wy + (HEADER - c.lineHeight(FontStyle.TITLE)) * 0.5F + 2.0F,
        t.ink());
    c.popClip();
    if (p.form.busy) {
      Kit.dots(c, t, p.wx + p.ww - Kit.PAD - 16.0F, p.wy + HEADER * 0.5F, p.clock);
    }
    if (!p.tabs.isEmpty()) {
      tabs(p, c, t, mouseX, mouseY, dt);
    }

    float reach = Math.max(0.0F, p.contentHeight - p.bodyHeight);
    p.scrollGoal = Math.max(0.0F, Math.min(reach, p.scrollGoal));
    p.scroll += (p.scrollGoal - p.scroll) * Math.min(1.0F, dt * FormPage.SCROLL_RESPONSE);
    if (Math.abs(p.scrollGoal - p.scroll) < 0.05F) {
      p.scroll = p.scrollGoal;
    }
    boolean inBody = Kit.inside(mouseX, mouseY, p.bodyLeft, p.bodyTop, p.bodyWidth, p.bodyHeight);
    float hoverX = inBody ? mouseX : Float.NEGATIVE_INFINITY;
    c.pushClip(p.bodyLeft, p.bodyTop, p.bodyWidth, p.bodyHeight);
    float cursor = p.bodyTop - p.scroll;
    int order = 0;
    for (FormPage.RowView row : p.rows) {
      float height = FormPage.rowHeight(row);
      if (cursor + height > p.bodyTop && cursor < p.bodyTop + p.bodyHeight) {
        c.pushAlpha(Kit.reveal(p.age, order++));
        row(p, c, t, row, cursor, height, hoverX, mouseY, dt);
        c.popAlpha();
      }
      cursor += height;
    }
    p.itemsTop = cursor;
    if (p.layout == FormPage.LIST) {
      ListLayout.items(p, c, t, cursor, hoverX, mouseY);
    }
    c.popClip();
    if (t.translucent()) {
      if (p.scroll > 0.5F) {
        c.roundRect(
            p.bodyLeft + ControlView.INSET,
            p.bodyTop,
            p.bodyWidth - ControlView.INSET * 2.0F,
            RULE,
            0.0F,
            t.hair());
      }
      if (p.scroll < reach - 0.5F) {
        c.roundRect(
            p.bodyLeft + ControlView.INSET,
            p.bodyTop + p.bodyHeight - RULE,
            p.bodyWidth - ControlView.INSET * 2.0F,
            RULE,
            0.0F,
            t.hair());
      }
    } else {
      Depth.scrollFade(
          c, t, t.paper(), p.bodyLeft, p.bodyTop, p.bodyWidth, p.bodyHeight, p.scroll, reach);
    }
    footer(p, c, t, mouseX, mouseY, dt);
  }

  private static void footer(FormPage p, Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    int count = p.actions.size();
    if (count == 0) {
      return;
    }
    float avail = p.ww - Kit.PAD * 2.0F;
    int rows = pack(p, c, avail, p.footerPad, p.footerGap);
    float block = rows * Kit.PILL + (rows - 1) * p.footerGap;
    float cursorY = p.wy + p.wh - p.footer + (p.footer - block) * 0.5F;
    int start = 0;
    while (start < count) {
      int end = start;
      float used = 0.0F;
      while (end < count) {
        float w = Kit.pillWidth(c, p.actions.get(end).control.label(), p.footerPad);
        float need = used <= 0.0F ? w : used + p.footerGap + w;
        if (used > 0.0F && need > avail) {
          break;
        }
        used = need;
        end++;
      }
      float right = p.wx + p.ww - Kit.PAD;
      for (int i = end - 1; i >= start; i--) {
        ControlView view = p.actions.get(i);
        view.w = Kit.pillWidth(c, view.control.label(), p.footerPad);
        view.h = Kit.PILL;
        view.x = right - view.w;
        view.y = cursorY;
        view.pill(c, t, mouseX, mouseY, dt, view.control.leads());
        right = view.x - p.footerGap;
      }
      cursorY += Kit.PILL + p.footerGap;
      start = end;
    }
  }

  private static void side(FormPage p, Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    float left = p.wx + 10.0F;
    float width = SIDEBAR - 14.0F;
    float top = p.wy + 12.0F;
    float room = p.wh - 24.0F;
    float reach = Math.max(0.0F, p.sidebar.size() * SIDE_ITEM - room);
    p.sideScroll = Math.max(0.0F, Math.min(reach, p.sideScroll));
    Depth.inset(c, t, left, top, width, room, 14.0F, Theme.withAlpha(t.well(), 0.4F));
    c.pushClip(left, top + 3.0F, width, room - 6.0F);
    float cursor = top + 4.0F - p.sideScroll;
    for (ControlView view : p.sidebar) {
      view.x = left + 4.0F;
      view.y = cursor;
      view.w = width - 8.0F;
      view.h = SIDE_ITEM - 2.0F;
      view.item(c, t, mouseX, mouseY, dt);
      cursor += SIDE_ITEM;
    }
    c.popClip();
  }

  private static void tabs(FormPage p, Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    float left = p.bodyLeft + ControlView.INSET;
    float width = p.bodyWidth - ControlView.INSET * 2.0F;
    float top = p.wy + HEADER + (TABS - TAB) * 0.5F - 4.0F;
    Depth.inset(c, t, left, top, width, TAB, TAB * 0.5F, Theme.withAlpha(t.well(), 0.5F));
    float each = width / p.tabs.size();
    for (int i = 0; i < p.tabs.size(); i++) {
      ControlView view = p.tabs.get(i);
      view.x = left + each * i + 2.0F;
      view.y = top + 2.0F;
      view.w = each - 4.0F;
      view.h = TAB - 4.0F;
      view.shown = true;
      boolean on = view.control.on();
      if (on) {
        Depth.raised(c, t, view.x, view.y, view.w, view.h, view.h * 0.5F, t.surface2());
      }
      String label = view.control.label();
      c.text(
          FontStyle.CAPTION,
          label,
          view.x + (view.w - c.textWidth(FontStyle.CAPTION, label)) * 0.5F,
          view.y + (view.h - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
          on ? t.ink() : t.grey());
    }
  }

  private static void row(
      FormPage p,
      Canvas c,
      Theme t,
      FormPage.RowView row,
      float top,
      float height,
      float mouseX,
      float mouseY,
      float dt) {
    if (row.caption != null) {
      c.text(
          FontStyle.CAPTION,
          row.caption,
          p.bodyLeft + ControlView.INSET,
          top + height - c.lineHeight(FontStyle.CAPTION) - 5.0F,
          t.grey());
      return;
    }
    if (row.pills()) {
      float right = p.bodyLeft + p.bodyWidth - ControlView.INSET;
      float pillTop = top + (height - Kit.PILL) * 0.5F;
      if (row.label != null) {
        c.text(
            FontStyle.ROW,
            row.label,
            p.bodyLeft + ControlView.INSET,
            top + (height - c.lineHeight(FontStyle.ROW)) * 0.5F,
            t.ink());
      }
      for (int i = row.controls.size() - 1; i >= 0; i--) {
        ControlView view = row.controls.get(i);
        view.w = Kit.pillWidth(c, view.control.label());
        view.h = Kit.PILL;
        view.x = right - view.w;
        view.y = pillTop;
        view.pill(c, t, mouseX, mouseY, dt, false);
        right = view.x - Kit.GAP;
      }
      return;
    }
    if (row.controls.isEmpty()) {
      return;
    }
    ControlView view = row.controls.get(0);
    view.x = p.bodyLeft;
    view.y = top;
    view.w = p.bodyWidth;
    view.h = height;
    view.row(c, t, row.label != null ? row.label : view.control.label(), mouseX, mouseY, dt);
  }
}
