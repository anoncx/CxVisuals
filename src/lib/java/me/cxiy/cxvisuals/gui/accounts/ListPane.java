package me.cxiy.cxvisuals.gui.accounts;

import java.util.ArrayList;
import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.Field;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Segmented;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.spatial.Spring;
import me.cxiy.cxvisuals.modules.accounts.AccountBook;
import me.cxiy.cxvisuals.modules.accounts.Entry;
import me.cxiy.cxvisuals.modules.accounts.Hub;
import me.cxiy.cxvisuals.modules.accounts.Kind;

final class ListPane {
  interface Host {
    void select(String id);

    void use(Entry entry);

    void add();
  }

  static final float ROW = 56.0F;

  private static final float HEAD = 36.0F;
  private static final float HEAD_RADIUS = 11.0F;
  private static final float INSET = 8.0F;
  private static final float FOOT = 48.0F;
  private static final float SCROLL_STEP = 56.0F;
  private static final long DOUBLE_NANOS = 400_000_000L;
  private static final int NONE = -1;

  private final Hub hub;
  private final Host host;
  private final Field search = new Field(Lang.key("Search accounts"), 32);
  private final Pick filter =
      new Pick(Lang.key("All"), Lang.key("Microsoft"), Lang.key("Offline"), Lang.key("Token"));
  private final Segmented filterTabs = new Segmented(filter);
  private final Pill add = new Pill(Lang.key("Add account"), Pill.Tone.PRIMARY);
  private final Spring glide = Spring.critical(420.0F);
  private final List<Entry> rows = new ArrayList<>();
  private AccountBook.Order order = AccountBook.Order.RECENT;
  private String selected = "";
  private String applied = "";
  private int appliedRevision = -1;
  private int appliedFilter = -1;
  private AccountBook.Order appliedOrder;
  private float scroll;
  private float scrollMax;
  private float x;
  private float y;
  private float w;
  private float h;
  private float listTop;
  private float listHeight;
  private float sortX;
  private float sortY;
  private float sortW;
  private int hovered = NONE;
  private int pressed = NONE;
  private long clickedAt;
  private int clicked = NONE;

  ListPane(Hub hub, Host host) {
    this.hub = hub;
    this.host = host;
  }

  List<Entry> rows() {
    return rows;
  }

  String selected() {
    return selected;
  }

  void selected(String id) {
    selected = id == null ? "" : id;
  }

  boolean typing() {
    return search.focused();
  }

  void focusSearch() {
    search.focus(true);
  }

  void blur() {
    search.focus(false);
  }

  void refresh() {
    String query = search.text().trim();
    int revision = hub.book().revision();
    if (revision == appliedRevision
        && query.equals(applied)
        && filter.index() == appliedFilter
        && order == appliedOrder) {
      return;
    }
    boolean reset = !query.equals(applied) || filter.index() != appliedFilter;
    applied = query;
    appliedRevision = revision;
    appliedFilter = filter.index();
    appliedOrder = order;
    rows.clear();
    Kind kind = filter.index() == 0 ? null : Kind.values()[filter.index() - 1];
    rows.addAll(hub.book().list(query, kind, order));
    if (reset) {
      scroll = 0.0F;
      glide.snap(0.0F);
    }
  }

  int indexOf(String id) {
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i).id.equals(id)) {
        return i;
      }
    }
    return NONE;
  }

  void move(int direction) {
    if (rows.isEmpty()) {
      return;
    }
    int current = indexOf(selected);
    int next = Math.max(0, Math.min(rows.size() - 1, current == NONE ? 0 : current + direction));
    host.select(rows.get(next).id);
    float top = next * ROW;
    if (top < scroll) {
      scroll = top;
    } else if (top + ROW > scroll + listHeight) {
      scroll = top + ROW - listHeight;
    }
  }

  void draw(
      Canvas c, Theme t, float px, float py, float pw, float ph, float mx, float my, float dt) {
    x = px;
    y = py;
    w = pw;
    h = ph;
    refresh();
    search.draw(c, t, x, y, w, dt);
    filterTabs.limit(w);
    filterTabs.measure(c);
    filterTabs.place(x, y + Field.HEIGHT + 10.0F);
    filterTabs.draw(c, t, mx, my, dt);

    float labelY = y + Field.HEIGHT + 10.0F + filterTabs.h + 10.0F;
    drawSummary(c, t, labelY, mx, my);
    listTop = labelY + c.lineHeight(FontStyle.CAPTION) + 8.0F;
    listHeight = y + h - FOOT - listTop;
    scrollMax = Math.max(0.0F, rows.size() * ROW - listHeight);
    scroll = Math.max(0.0F, Math.min(scrollMax, scroll));
    glide.step(dt, scroll);
    float offset = glide.value();

    hovered = NONE;
    c.pushClip(x - 4.0F, listTop, w + 8.0F, listHeight);
    int first = Math.max(0, (int) (offset / ROW));
    int last = Math.min(rows.size(), (int) ((offset + listHeight) / ROW) + 1);
    boolean inside = mx >= x && mx < x + w && my >= listTop && my < listTop + listHeight;
    Entry active = hub.active();
    for (int i = first; i < last; i++) {
      float ry = listTop + i * ROW - offset;
      Entry entry = rows.get(i);
      boolean over = inside && my >= ry && my < ry + ROW;
      if (over) {
        hovered = i;
      }
      drawRow(
          c,
          t,
          entry,
          ry,
          entry.id.equals(selected),
          over,
          active != null && active.id.equals(entry.id));
    }
    c.popClip();
    if (rows.isEmpty()) {
      empty(c, t);
    }
    if (scrollMax > 0.0F) {
      float track = listHeight - 8.0F;
      float thumb = Math.max(24.0F, track * listHeight / (rows.size() * ROW));
      float thumbY = listTop + 4.0F + (track - thumb) * (offset / scrollMax);
      c.roundRect(x + w - 1.0F, thumbY, 3.0F, thumb, 1.5F, Theme.withAlpha(t.ink(), 0.22F));
    }
    add.draw(c, t, x, y + h - Pill.HEIGHT - 4.0F, w, mx, my, dt);
  }

  private void drawSummary(Canvas c, Theme t, float top, float mx, float my) {
    int total = hub.book().size();
    String count = total + " " + Lang.count(total, "account", "accounts");
    c.text(FontStyle.CAPTION, count, x + 2.0F, top, t.grey());
    String sort = Lang.tr("Sort") + ": " + Lang.tr(AccountBook.Order.LABELS[order.ordinal()]);
    sortW = c.textWidth(FontStyle.CAPTION, sort);
    sortX = x + w - sortW - 2.0F;
    sortY = top - 3.0F;
    boolean over =
        mx >= sortX - 4.0F
            && mx < sortX + sortW + 4.0F
            && my >= sortY
            && my < sortY + c.lineHeight(FontStyle.CAPTION) + 6.0F;
    c.text(FontStyle.CAPTION, sort, sortX, top, over ? t.ink() : t.grey());
  }

  private void empty(Canvas c, Theme t) {
    boolean none = hub.book().size() == 0;
    String title = Lang.tr(none ? "No accounts yet" : "Nothing matches");
    String hint =
        Lang.tr(none ? "Add one to switch with a single click." : "Try another name or filter.");
    float centre = x + w * 0.5F;
    float top = listTop + 28.0F;
    c.text(FontStyle.ROW, title, centre - c.textWidth(FontStyle.ROW, title) * 0.5F, top, t.ink());
    List<String> lines = Words.wrap(c, FontStyle.CAPTION, hint, w - 24.0F);
    float line = c.lineHeight(FontStyle.CAPTION);
    for (int i = 0; i < lines.size(); i++) {
      c.text(
          FontStyle.CAPTION,
          lines.get(i),
          centre - c.textWidth(FontStyle.CAPTION, lines.get(i)) * 0.5F,
          top + c.lineHeight(FontStyle.ROW) + 6.0F + i * line,
          t.grey());
    }
  }

  private void drawRow(
      Canvas c, Theme t, Entry entry, float ry, boolean current, boolean over, boolean active) {
    if (current) {
      Depth.raised(c, t, x, ry + 2.0F, w, ROW - 4.0F, 14.0F, t.surface2());
    } else if (over) {
      c.roundRect(x, ry + 2.0F, w, ROW - 4.0F, 14.0F, Theme.withAlpha(t.ink(), 0.06F));
    }
    float headX = x + INSET + 2.0F;
    Portrait.head(c, t, hub, entry, headX, ry + (ROW - HEAD) * 0.5F, HEAD, HEAD_RADIUS);
    float textX = headX + HEAD + 12.0F;
    float right = x + w - INSET - 4.0F;
    float marker = active ? 8.0F + c.textWidth(FontStyle.CAPTION, Lang.tr("Active")) + 6.0F : 0.0F;
    float room = right - marker - textX;
    float top =
        ry + (ROW - c.lineHeight(FontStyle.ROW) - c.lineHeight(FontStyle.CAPTION) - 1.0F) * 0.5F;
    c.text(FontStyle.ROW, c.fit(FontStyle.ROW, entry.username, room), textX, top, t.ink());
    String caption =
        Lang.tr(entry.kind().label()) + (entry.pinned ? "  ·  " + Lang.tr("Pinned") : "");
    c.text(
        FontStyle.CAPTION,
        c.fit(FontStyle.CAPTION, caption, room),
        textX,
        top + c.lineHeight(FontStyle.ROW) + 1.0F,
        t.grey());
    if (active) {
      String label = Lang.tr("Active");
      float labelX = right - c.textWidth(FontStyle.CAPTION, label);
      c.text(
          FontStyle.CAPTION,
          label,
          labelX,
          ry + (ROW - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
          t.ink2());
      c.roundRect(labelX - 12.0F, ry + (ROW - 6.0F) * 0.5F, 6.0F, 6.0F, 3.0F, t.red());
    }
  }

  private int rowAt(float mx, float my) {
    if (mx < x || mx >= x + w || my < listTop || my >= listTop + listHeight) {
      return NONE;
    }
    int index = (int) ((my - listTop + glide.value()) / ROW);
    return index >= 0 && index < rows.size() ? index : NONE;
  }

  boolean mouseDown(float mx, float my) {
    boolean onSearch = mx >= x && mx < x + w && my >= y && my < y + Field.HEIGHT;
    search.focus(onSearch);
    if (onSearch) {
      search.press(mx);
      return true;
    }
    if (add.down(mx, my)) {
      return true;
    }
    if (filterTabs.mouseDown(mx, my)) {
      return true;
    }
    if (mx >= sortX - 4.0F && mx < sortX + sortW + 4.0F && my >= sortY && my < sortY + 18.0F) {
      order = AccountBook.Order.values()[(order.ordinal() + 1) % AccountBook.Order.values().length];
      return true;
    }
    int index = rowAt(mx, my);
    if (index != NONE) {
      pressed = index;
      return true;
    }
    return false;
  }

  void mouseUp(float mx, float my) {
    search.release();
    filterTabs.mouseUp(mx, my);
    if (add.up(mx, my)) {
      host.add();
    }
    if (pressed != NONE) {
      int index = pressed;
      pressed = NONE;
      if (index == rowAt(mx, my) && index < rows.size()) {
        Entry entry = rows.get(index);
        long now = System.nanoTime();
        if (clicked == index && now - clickedAt < DOUBLE_NANOS) {
          clicked = NONE;
          host.use(entry);
        } else {
          clicked = index;
          clickedAt = now;
          host.select(entry.id);
        }
      }
    }
  }

  void mouseDrag(float mx) {
    if (search.focused()) {
      search.drag(mx);
    }
  }

  boolean scroll(float mx, float my, float amount) {
    if (mx < x - 8.0F || mx > x + w + 8.0F) {
      return false;
    }
    scroll = Math.max(0.0F, Math.min(scrollMax, scroll - amount * SCROLL_STEP));
    return true;
  }

  boolean keyDown(int key, boolean control, boolean shift) {
    if (!search.focused()) {
      return false;
    }
    if (key == Field.ESCAPE || key == Field.ENTER || key == Field.KEYPAD_ENTER) {
      search.focus(false);
      return true;
    }
    search.key(key, control, shift);
    return true;
  }

  boolean charTyped(int codepoint) {
    return search.focused() && search.type(codepoint);
  }
}
