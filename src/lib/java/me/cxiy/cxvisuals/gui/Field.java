package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.i18n.Lang;

public final class Field {
  public static final int ENTER = 257;
  public static final int KEYPAD_ENTER = 335;
  public static final int ESCAPE = 256;
  public static final int BACKSPACE = 259;
  public static final int DELETE = 261;
  public static final int RIGHT = 262;
  public static final int LEFT = 263;
  public static final int HOME = 268;
  public static final int END = 269;
  public static final int KEY_A = 65;
  public static final int KEY_C = 67;
  public static final int KEY_V = 86;
  public static final int KEY_X = 88;
  public static final float HEIGHT = 30.0F;
  private static final float PAD = 13.0F;
  private static final float CARET_PERIOD = 1.0F;
  private static final float CARET_EDGE = 8.0F;
  private static final float GLIDE = 28.0F;
  private static final long DOUBLE_NANOS = 400_000_000L;
  private static final float DOUBLE_SLOP = 6.0F;

  private final StringBuilder draft = new StringBuilder();
  private final String placeholder;
  private final int limit;
  private String text = "";
  private String shown = "";
  private boolean masked;
  private Canvas screen;
  private boolean focused;
  private boolean dragging;
  private boolean stale = true;
  private int cursor;
  private int anchor = -1;
  private float blink;
  private float left;
  private float room;
  private float slide;
  private float full;
  private float caretX;
  private float caretShown;
  private float fromX;
  private float toX;
  private long lastClick;
  private float lastX;

  public Field(String placeholder, int limit) {
    this.placeholder = placeholder;
    this.limit = limit;
  }

  public String text() {
    return text;
  }

  public Field masked(boolean on) {
    masked = on;
    changed();
    return this;
  }

  public boolean focused() {
    return focused;
  }

  public void focus(boolean on) {
    if (!on) {
      anchor = -1;
      dragging = false;
    }
    focused = on;
    blink = 0.0F;
  }

  public void set(String value) {
    if (text.equals(value)) {
      return;
    }
    draft.setLength(0);
    draft.append(value, 0, Math.min(limit, value.length()));
    cursor = draft.length();
    anchor = -1;
    changed();
  }

  public String take() {
    String taken = text.strip();
    draft.setLength(0);
    cursor = 0;
    anchor = -1;
    changed();
    return taken;
  }

  private void changed() {
    text = draft.toString();
    shown = masked ? "•".repeat(text.length()) : text;
    stale = true;
    blink = 0.0F;
  }

  private void moved() {
    stale = true;
    blink = 0.0F;
  }

  private int lo() {
    return anchor < 0 ? cursor : Math.min(anchor, cursor);
  }

  private int hi() {
    return anchor < 0 ? cursor : Math.max(anchor, cursor);
  }

  private boolean selected() {
    return anchor >= 0 && anchor != cursor;
  }

  private int clamp(int index) {
    return Math.max(0, Math.min(draft.length(), index));
  }

  private int step(int index, int direction) {
    int next = clamp(index + direction);
    if (next > 0
        && next < draft.length()
        && Character.isLowSurrogate(draft.charAt(next))
        && Character.isHighSurrogate(draft.charAt(next - 1))) {
      next += direction;
    }
    return next;
  }

  private void go(int index, boolean extend) {
    int next = clamp(index);
    if (extend) {
      if (anchor < 0) {
        anchor = cursor;
      }
      cursor = next;
      if (anchor == cursor) {
        anchor = -1;
      }
    } else {
      cursor = next;
      anchor = -1;
    }
    moved();
  }

  private boolean erase() {
    int from = lo();
    int to = hi();
    if (from == to) {
      return false;
    }
    draft.delete(from, to);
    cursor = from;
    anchor = -1;
    changed();
    return true;
  }

  private void erase(int from, int to) {
    if (from < to) {
      draft.delete(from, to);
      cursor = from;
      anchor = -1;
      changed();
    }
  }

  private boolean word(int index) {
    return index >= 0 && index < draft.length() && !Character.isWhitespace(draft.charAt(index));
  }

  private int wordStart(int index) {
    int i = clamp(index);
    while (i > 0 && !word(i - 1)) {
      i--;
    }
    while (i > 0 && word(i - 1)) {
      i--;
    }
    return i;
  }

  private int wordEnd(int index) {
    int i = clamp(index);
    while (i < draft.length() && !word(i)) {
      i++;
    }
    while (i < draft.length() && word(i)) {
      i++;
    }
    return i;
  }

  private void insert(CharSequence value) {
    int room = limit - draft.length() + hi() - lo();
    StringBuilder clean = new StringBuilder(Math.min(value.length(), Math.max(0, room)));
    for (int i = 0; i < value.length() && clean.length() < room; i++) {
      char c = value.charAt(i);
      if (c == '\n' || c == '\t') {
        c = ' ';
      }
      if (c >= 0x20 && c != 0x7F) {
        clean.append(c);
      }
    }
    int last = clean.length() - 1;
    if (last >= 0 && Character.isHighSurrogate(clean.charAt(last))) {
      clean.setLength(last);
    }
    if (clean.isEmpty()) {
      return;
    }
    erase();
    draft.insert(cursor, clean);
    cursor += clean.length();
    changed();
  }

  private void copy() {
    if (!masked && selected() && screen != null) {
      screen.clipboardText(draft.substring(lo(), hi()));
    }
  }

  private float edge(Canvas c, int index) {
    return index <= 0
        ? 0.0F
        : index >= shown.length() ? full : c.textWidth(FontStyle.LABEL, shown.substring(0, index));
  }

  private void measure(Canvas c) {
    stale = false;
    full = shown.isEmpty() ? 0.0F : c.textWidth(FontStyle.LABEL, shown);
    caretX = edge(c, cursor);
    fromX = selected() ? edge(c, lo()) : 0.0F;
    toX = selected() ? edge(c, hi()) : 0.0F;
  }

  private int indexAt(float mx) {
    if (screen == null || text.isEmpty()) {
      return 0;
    }
    float target = mx - left + slide;
    int low = 0;
    int high = text.length();
    while (low < high) {
      int middle = (low + high + 1) >>> 1;
      if (screen.textWidth(FontStyle.LABEL, shown.substring(0, middle)) <= target) {
        low = middle;
      } else {
        high = middle - 1;
      }
    }
    if (low < text.length()) {
      int next = step(low, 1);
      float before = screen.textWidth(FontStyle.LABEL, shown.substring(0, low));
      float after = screen.textWidth(FontStyle.LABEL, shown.substring(0, next));
      if (after - target < target - before) {
        low = next;
      }
    }
    return low;
  }

  public void press(float mx) {
    if (!focused) {
      return;
    }
    long now = System.nanoTime();
    int index = indexAt(mx);
    if (now - lastClick < DOUBLE_NANOS && Math.abs(mx - lastX) < DOUBLE_SLOP) {
      int from = index;
      while (from > 0 && word(from - 1)) {
        from--;
      }
      int to = index;
      while (to < draft.length() && word(to)) {
        to++;
      }
      anchor = from;
      cursor = to;
      dragging = false;
      lastClick = 0L;
      moved();
      return;
    }
    lastClick = now;
    lastX = mx;
    cursor = index;
    anchor = index;
    dragging = true;
    moved();
  }

  public void drag(float mx) {
    if (focused && dragging) {
      int index = indexAt(mx);
      if (index != cursor) {
        cursor = index;
        moved();
      }
    }
  }

  public void release() {
    dragging = false;
    if (anchor == cursor) {
      anchor = -1;
    }
  }

  public void draw(Canvas c, Theme t, float x, float y, float w, float dt) {
    screen = c;
    left = x + PAD;
    room = w - PAD * 2.0F;
    if (stale) {
      measure(c);
    }
    Depth.inset(c, t, x, y, w, HEIGHT, HEIGHT * 0.5F, t.surface2());
    if (focused) {
      c.roundRectBorder(x, y, w, HEIGHT, HEIGHT * 0.5F, 1.0F, t.focus());
    }
    float reach = Math.max(0.0F, full - room);
    if (!focused) {
      slide = 0.0F;
    } else if (caretX - slide > room - CARET_EDGE) {
      slide = caretX - room + CARET_EDGE;
    } else if (caretX - slide < CARET_EDGE) {
      slide = caretX - CARET_EDGE;
    }
    slide = Math.max(0.0F, Math.min(reach, slide));
    float top = y + (HEIGHT - c.lineHeight(FontStyle.LABEL)) * 0.5F;
    boolean blank = text.isEmpty();
    c.pushClip(left - 1.0F, y, room + 4.0F, HEIGHT);
    if (focused && selected()) {
      c.roundRect(
          left - slide + fromX,
          y + 6.0F,
          Math.max(2.0F, toX - fromX),
          HEIGHT - 12.0F,
          3.0F,
          Theme.withAlpha(t.focus(), 0.35F));
    }
    c.text(
        FontStyle.LABEL,
        blank ? Lang.tr(placeholder) : shown,
        left - slide,
        top,
        blank ? t.grey() : t.ink());
    if (focused) {
      blink = (blink + dt) % CARET_PERIOD;
      float goal = left - slide + caretX;
      caretShown =
          Math.abs(goal - caretShown) > room
              ? goal
              : caretShown + (goal - caretShown) * Math.min(1.0F, dt * GLIDE);
      if (!selected() && blink < CARET_PERIOD * 0.5F) {
        c.roundRect(caretShown + 1.0F, y + 8.0F, 1.5F, HEIGHT - 16.0F, 0.75F, t.ink());
      }
    }
    c.popClip();
  }

  public boolean key(int keyCode, boolean control) {
    return key(keyCode, control, false);
  }

  public boolean key(int keyCode, boolean control, boolean shift) {
    if (!focused) {
      return false;
    }
    blink = 0.0F;
    switch (keyCode) {
      case ESCAPE -> {
        focus(false);
        return false;
      }
      case LEFT ->
          go(control ? wordStart(cursor) : selected() && !shift ? lo() : step(cursor, -1), shift);
      case RIGHT ->
          go(control ? wordEnd(cursor) : selected() && !shift ? hi() : step(cursor, 1), shift);
      case HOME -> go(0, shift);
      case END -> go(draft.length(), shift);
      case BACKSPACE -> {
        if (!erase()) {
          erase(control ? wordStart(cursor) : step(cursor, -1), cursor);
        }
      }
      case DELETE -> {
        if (!erase()) {
          erase(cursor, control ? wordEnd(cursor) : step(cursor, 1));
        }
      }
      case KEY_A -> {
        if (control && draft.length() > 0) {
          anchor = 0;
          cursor = draft.length();
          moved();
        }
      }
      case KEY_C -> {
        if (control) {
          copy();
        }
      }
      case KEY_X -> {
        if (control) {
          copy();
          erase();
        }
      }
      case KEY_V -> {
        if (control && screen != null) {
          String pasted = screen.clipboardText();
          if (pasted != null) {
            insert(pasted);
          }
        }
      }
      default -> {}
    }
    return true;
  }

  public boolean submits(int keyCode) {
    return focused && (keyCode == ENTER || keyCode == KEYPAD_ENTER);
  }

  public boolean type(int codepoint) {
    if (!focused) {
      return false;
    }
    if (codepoint >= 0x20 && codepoint != 0x7F && Character.isValidCodePoint(codepoint)) {
      insert(new String(Character.toChars(codepoint)));
    }
    blink = 0.0F;
    return true;
  }
}
