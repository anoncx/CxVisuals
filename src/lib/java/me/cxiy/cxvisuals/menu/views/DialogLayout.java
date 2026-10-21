package me.cxiy.cxvisuals.menu.views;

import java.util.List;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;

final class DialogLayout {
  private static final float DIALOG_WIDTH = 400.0F;
  private static final float MENU_WIDTH = 360.0F;
  private static final float MENU_BTN = 38.0F;
  private static final float MENU_GAP = 8.0F;
  private static final float LINE_GAP = 4.0F;
  private static final float STACK_GAP = 8.0F;
  private static final float STACK_PILL = 38.0F;
  private static final float PROGRESS = 4.0F;
  private static final float BUSY = 26.0F;
  private static final int ROW_BUTTONS = 3;

  private DialogLayout() {}

  private static boolean stacked(FormPage p) {
    return p.actions.size() > ROW_BUTTONS;
  }

  private static float titleHeight(FormPage p, Canvas c) {
    return p.form.title.isEmpty() ? 0.0F : c.lineHeight(FontStyle.TITLE) + 12.0F;
  }

  private static float listHeight(FormPage p) {
    int count = p.buttons.size();
    return count * MENU_BTN + Math.max(0, count - 1) * MENU_GAP;
  }

  static void measure(FormPage p, Canvas c, float roomW, float roomH) {
    if (p.layout == FormPage.MENU) {
      p.wantW = Math.min(MENU_WIDTH, roomW);
      p.wantH = Math.min(Kit.PAD + titleHeight(p, c) + listHeight(p) + Kit.PAD, roomH);
      return;
    }
    float width = Math.min(DIALOG_WIDTH, roomW);
    p.wrap(c, width - Kit.PAD * 2.0F);
    float height = Kit.PAD;
    if (!p.form.title.isEmpty()) {
      height += c.lineHeight(FontStyle.TITLE) + 10.0F;
    }
    if (p.form.busy) {
      height += BUSY;
    }
    height += p.wrapped.size() * (c.lineHeight(FontStyle.LABEL) + LINE_GAP);
    if (p.form.progress >= 0.0F) {
      height += PROGRESS + 18.0F;
    }
    for (FormPage.RowView row : p.rows) {
      height += FormPage.rowHeight(row);
    }
    if (!p.actions.isEmpty()) {
      height +=
          16.0F + (stacked(p) ? p.actions.size() * (STACK_PILL + STACK_GAP) - STACK_GAP : Kit.PILL);
    }
    p.wantW = width;
    p.wantH = Math.min(height + Kit.PAD, roomH);
  }

  static void draw(FormPage p, Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    if (p.layout == FormPage.MENU) {
      menu(p, c, t, mouseX, mouseY, dt);
    } else {
      dialog(p, c, t, mouseX, mouseY, dt);
    }
  }

  private static void dialog(FormPage p, Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    float inner = p.ww - Kit.PAD * 2.0F;
    float lineHeight = c.lineHeight(FontStyle.LABEL) + LINE_GAP;
    boolean titled = !p.form.title.isEmpty();
    boolean stack = stacked(p);
    float cursor = p.wy + Kit.PAD;
    float left = p.wx + Kit.PAD;
    if (titled) {
      c.pushClip(left, cursor, inner, c.lineHeight(FontStyle.TITLE) + 4.0F);
      c.text(FontStyle.TITLE, p.form.title, left, cursor, t.ink());
      c.popClip();
      cursor += c.lineHeight(FontStyle.TITLE) + 10.0F;
    }
    if (p.form.busy) {
      Kit.dots(c, t, left + 14.0F, cursor + BUSY * 0.5F - 4.0F, p.clock);
      cursor += BUSY;
    }
    for (String line : p.wrapped) {
      c.text(FontStyle.LABEL, line, left, cursor, t.ink2());
      cursor += lineHeight;
    }
    if (p.form.progress >= 0.0F) {
      cursor += 8.0F;
      c.roundRect(left, cursor, inner, PROGRESS, PROGRESS * 0.5F, t.surface2());
      c.roundRect(
          left,
          cursor,
          Math.max(PROGRESS, inner * Math.min(1.0F, p.form.progress)),
          PROGRESS,
          PROGRESS * 0.5F,
          t.ink());
      cursor += PROGRESS + 10.0F;
    }
    for (FormPage.RowView row : p.rows) {
      float height0 = FormPage.rowHeight(row);
      if (row.caption != null) {
        c.text(
            FontStyle.CAPTION,
            row.caption,
            left,
            cursor + height0 - c.lineHeight(FontStyle.CAPTION) - 4.0F,
            t.grey());
      } else if (!row.controls.isEmpty()) {
        ControlView view = row.controls.get(0);
        view.x = p.wx + Kit.PAD - ControlView.INSET;
        view.y = cursor;
        view.w = p.ww - (Kit.PAD - ControlView.INSET) * 2.0F;
        view.h = height0;
        view.row(c, t, "", mouseX, mouseY, dt);
      }
      cursor += height0;
    }
    if (p.actions.isEmpty()) {
      return;
    }
    cursor += 16.0F;
    if (stack) {
      for (ControlView view : p.actions) {
        view.x = left;
        view.y = cursor;
        view.w = inner;
        view.h = STACK_PILL;
        view.pill(c, t, mouseX, mouseY, dt, view.control.leads());
        cursor += STACK_PILL + STACK_GAP;
      }
      return;
    }
    float each = (inner - Kit.GAP * (p.actions.size() - 1)) / p.actions.size();
    float at = left;
    for (ControlView view : p.actions) {
      view.x = at;
      view.y = cursor;
      view.w = each;
      view.h = Kit.PILL;
      view.pill(c, t, mouseX, mouseY, dt, view.control.leads());
      at += each + Kit.GAP;
    }
  }

  private static void menu(FormPage p, Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    float inner = p.ww - Kit.PAD * 2.0F;
    List<ControlView> buttons = p.buttons;
    boolean titled = !p.form.title.isEmpty();
    float titleH = titleHeight(p, c);
    float listH = listHeight(p);
    float left = p.wx + Kit.PAD;
    float cursor = p.wy + Kit.PAD;
    if (titled) {
      c.pushClip(left, cursor, inner, c.lineHeight(FontStyle.TITLE) + 4.0F);
      c.text(FontStyle.TITLE, p.form.title, left, cursor, t.ink());
      c.popClip();
      cursor += titleH;
    }
    float listTop = cursor;
    float listBottom = p.wy + p.wh - Kit.PAD;
    float reach = Math.max(0.0F, listH - (listBottom - listTop));
    p.scrollGoal = Math.max(0.0F, Math.min(reach, p.scrollGoal));
    p.scroll += (p.scrollGoal - p.scroll) * Math.min(1.0F, dt * FormPage.SCROLL_RESPONSE);
    if (Math.abs(p.scrollGoal - p.scroll) < 0.05F) {
      p.scroll = p.scrollGoal;
    }
    c.pushClip(left - 2.0F, listTop, inner + 4.0F, listBottom - listTop);
    float y = listTop - p.scroll;
    for (ControlView view : buttons) {
      if (y + MENU_BTN >= listTop && y <= listBottom) {
        view.x = left;
        view.y = y;
        view.w = inner;
        view.h = MENU_BTN;
        view.pill(c, t, mouseX, mouseY, dt, view.control.leads());
      } else {
        view.shown = false;
      }
      y += MENU_BTN + MENU_GAP;
    }
    c.popClip();
    if (reach > 1.0F) {
      float barH = Math.max(20.0F, (listBottom - listTop) * (listBottom - listTop) / listH);
      float barY = listTop + ((listBottom - listTop) - barH) * (p.scroll / reach);
      c.roundRect(p.wx + p.ww - 8.0F, barY, 3.0F, barH, 1.5F, Theme.withAlpha(t.ink(), 0.30F));
    }
    p.bodyLeft = left;
    p.bodyTop = listTop;
    p.bodyWidth = inner;
    p.bodyHeight = listBottom - listTop;
  }
}
