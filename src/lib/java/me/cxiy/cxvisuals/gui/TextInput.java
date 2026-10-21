package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.settings.TextSetting;

public final class TextInput extends Widget {
  private static final int ENTER = 257;
  private static final int ESCAPE = 256;
  private static final int BACKSPACE = 259;
  private static final int DELETE = 261;
  private static final int LEFT = 263;
  private static final int RIGHT = 262;
  private static final int HOME = 268;
  private static final int END = 269;
  private static final int KEY_A = 65;
  private static final int KEY_C = 67;
  private static final int KEY_V = 86;
  private static final int KEY_X = 88;
  private static final float RADIUS = 9.0F;
  private static final float PAD = 12.0F;
  private static final float MIN_WIDTH = 150.0F;
  private static final float MAX_WIDTH = 230.0F;
  private static final int MAX_LENGTH = 256;
  private static final long DOUBLE_NANOS = 400_000_000L;
  private static final float DOUBLE_SLOP = 6.0F;
  private static final char MASK = '•';

  private final TextSetting setting;
  private final Anim focus = Anim.state(0.0F);
  private final Anim blink = new Anim(0.0F, 0.45F, Anim.Curve.LINEAR);
  private Canvas screen;
  private boolean editing;
  private boolean dragging;
  private String draft = "";
  private int cursor;
  private int anchor = -1;
  private long lastClick;
  private float lastX;

  public TextInput(TextSetting setting) {
    this.setting = setting;
    this.h = 30.0F;
    this.w = MIN_WIDTH;
  }

  public boolean editing() {
    return editing;
  }

  @Override
  public TextInput focusedText() {
    return editing ? this : null;
  }

  @Override
  public void finishEditing() {
    finish();
  }

  public void finish() {
    if (!editing) {
      return;
    }
    editing = false;
    dragging = false;
    anchor = -1;
    setting.set(draft);
  }

  public void cancel() {
    editing = false;
    dragging = false;
    anchor = -1;
  }

  private String shown() {
    String value = editing ? draft : setting.get();
    if (setting.isConcealed() && !value.isEmpty()) {
      return String.valueOf(MASK).repeat(Math.min(value.length(), MAX_LENGTH));
    }
    return value;
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

  private void collapse(int index) {
    cursor = Math.max(0, Math.min(draft.length(), index));
    anchor = -1;
  }

  private void extend(int index) {
    if (anchor < 0) {
      anchor = cursor;
    }
    cursor = Math.max(0, Math.min(draft.length(), index));
    if (anchor == cursor) {
      anchor = -1;
    }
  }

  private void removeSelection() {
    int from = lo();
    int to = hi();
    if (from != to) {
      draft = draft.substring(0, from) + draft.substring(to);
    }
    collapse(from);
  }

  private static boolean wordChar(char c) {
    return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
  }

  private int wordStart(int index) {
    int i = Math.max(0, Math.min(draft.length(), index));
    while (i > 0 && wordChar(draft.charAt(i - 1))) {
      i--;
    }
    return i;
  }

  private int wordEnd(int index) {
    int i = Math.max(0, Math.min(draft.length(), index));
    while (i < draft.length() && wordChar(draft.charAt(i))) {
      i++;
    }
    return i;
  }

  private int indexAt(float localX) {
    if (screen == null || draft.isEmpty()) {
      return draft.length();
    }
    float target = localX - PAD;
    if (target <= 0.0F) {
      return 0;
    }
    int best = draft.length();
    float bestGap = Float.MAX_VALUE;
    for (int i = 0; i <= draft.length(); i++) {
      float edge = screen.textWidth(FontStyle.MONO, draft.substring(0, i)) - shift();
      float gap = Math.abs(edge - target);
      if (gap < bestGap) {
        bestGap = gap;
        best = i;
      }
    }
    return best;
  }

  private float shift() {
    if (screen == null) {
      return 0.0F;
    }
    String text = shown();
    float inner = w - PAD * 2.0F;
    float full = screen.textWidth(FontStyle.MONO, text);
    if (full <= inner || !editing) {
      return 0.0F;
    }
    float caret =
        screen.textWidth(FontStyle.MONO, text.substring(0, Math.min(cursor, text.length())));
    return Math.min(full - inner, Math.max(0.0F, caret - inner + 8.0F));
  }

  private String clean(String value) {
    StringBuilder out = new StringBuilder(Math.min(value.length(), MAX_LENGTH));
    for (int i = 0; i < value.length() && out.length() < MAX_LENGTH; i++) {
      char c = value.charAt(i);
      if (c >= 32 && c != 127) {
        out.append(c);
      }
    }
    return out.toString();
  }

  private void insert(String value) {
    String clean = clean(value);
    int room = MAX_LENGTH - draft.length() + (hi() - lo());
    if (room <= 0 || clean.isEmpty()) {
      return;
    }
    if (clean.length() > room) {
      clean = clean.substring(0, room);
    }
    removeSelection();
    draft = draft.substring(0, cursor) + clean + draft.substring(cursor);
    collapse(cursor + clean.length());
  }

  private String clipboard() {
    if (screen != null) {
      String text = screen.clipboardText();
      return text == null ? "" : text;
    }
    return "";
  }

  @Override
  public float measure(Canvas c) {
    screen = c;
    float text = c.textWidth(FontStyle.MONO, shown());
    w = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, text + PAD * 2.0F));
    return w;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    screen = c;
    focus.set(editing ? 1.0F : 0.0F);
    focus.update(dt);
    if (editing) {
      blink.set(blink.get() >= 1.0F ? 0.0F : 1.0F);
      blink.update(dt);
    } else {
      blink.snap(0.0F);
    }
    Depth.inset(c, t, x, y, w, h, RADIUS, Theme.mix(t.surface2(), t.surface(), focus.get()));
    if (focus.get() > 0.01F) {
      c.roundRectBorder(x, y, w, h, RADIUS, 1.0F, Theme.withAlpha(t.focus(), focus.get()));
    }
    String text = shown();
    float slide = shift();
    c.pushClip(x + 2.0F, y, w - 4.0F, h);
    float top = centered(c, FontStyle.MONO, y, h);
    if (text.isEmpty() && !editing) {
      c.text(FontStyle.MONO, "—", x + PAD, top, t.grey());
    } else {
      if (editing && selected()) {
        float from = c.textWidth(FontStyle.MONO, text.substring(0, lo()));
        float to = c.textWidth(FontStyle.MONO, text.substring(0, hi()));
        c.roundRect(
            x + PAD - slide + from,
            y + 6.0F,
            Math.max(2.0F, to - from),
            h - 12.0F,
            2.0F,
            Theme.withAlpha(t.focus(), 0.35F));
      }
      c.text(
          FontStyle.MONO,
          text,
          x + PAD - slide,
          top,
          Theme.mix(t.grey(), t.ink(), editing ? 1.0F : 0.75F));
    }
    if (editing && !selected() && blink.get() < 0.55F) {
      float caret = c.textWidth(FontStyle.MONO, text.substring(0, Math.min(cursor, text.length())));
      c.roundRect(x + PAD - slide + caret, y + 7.0F, 2.0F, h - 14.0F, 1.0F, t.ink());
    }
    c.popClip();
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (!contains(mx, my)) {
      return false;
    }
    long now = System.nanoTime();
    if (!editing) {
      editing = true;
      draft = setting.get();
      collapse(draft.length());
    }
    int index = screen == null ? draft.length() : indexAt(mx - x + shift());
    if (now - lastClick < DOUBLE_NANOS && Math.abs(mx - lastX) < DOUBLE_SLOP) {
      anchor = wordStart(index);
      cursor = wordEnd(index);
      dragging = false;
      lastClick = 0L;
      return true;
    }
    lastClick = now;
    lastX = mx;
    collapse(index);
    anchor = cursor;
    dragging = true;
    return true;
  }

  @Override
  public void mouseUp(float mx, float my) {
    dragging = false;
    if (anchor == cursor) {
      anchor = -1;
    }
  }

  @Override
  public void mouseDrag(float mx, float my) {
    if (editing && dragging && screen != null) {
      cursor = Math.max(0, Math.min(draft.length(), indexAt(mx - x + shift())));
    }
  }

  @Override
  public boolean keyDown(int keyCode) {
    return keyDown(keyCode, false, false);
  }

  @Override
  public boolean keyDown(int keyCode, boolean control, boolean shift) {
    if (!editing) {
      return false;
    }
    if (keyCode == ENTER) {
      finish();
      return true;
    }
    if (keyCode == ESCAPE) {
      cancel();
      return true;
    }
    if (control) {
      return controlKey(keyCode, shift);
    }
    if (keyCode == LEFT) {
      if (shift) {
        extend(cursor - 1);
      } else {
        collapse(cursor - 1);
      }
      return true;
    }
    if (keyCode == RIGHT) {
      if (shift) {
        extend(cursor + 1);
      } else {
        collapse(cursor + 1);
      }
      return true;
    }
    if (keyCode == HOME) {
      if (shift) {
        extend(0);
      } else {
        collapse(0);
      }
      return true;
    }
    if (keyCode == END) {
      if (shift) {
        extend(draft.length());
      } else {
        collapse(draft.length());
      }
      return true;
    }
    if (keyCode == BACKSPACE) {
      if (selected()) {
        removeSelection();
      } else if (cursor > 0) {
        draft = draft.substring(0, cursor - 1) + draft.substring(cursor);
        collapse(cursor - 1);
      }
      return true;
    }
    if (keyCode == DELETE) {
      if (selected()) {
        removeSelection();
      } else if (cursor < draft.length()) {
        draft = draft.substring(0, cursor) + draft.substring(cursor + 1);
      }
      return true;
    }
    return false;
  }

  private boolean controlKey(int keyCode, boolean shift) {
    if (keyCode == KEY_A) {
      anchor = 0;
      cursor = draft.length();
      return true;
    }
    if (keyCode == KEY_C) {
      if (selected() && screen != null) {
        screen.clipboardText(draft.substring(lo(), hi()));
      }
      return true;
    }
    if (keyCode == KEY_X) {
      if (selected()) {
        if (screen != null) {
          screen.clipboardText(draft.substring(lo(), hi()));
        }
        removeSelection();
      }
      return true;
    }
    if (keyCode == KEY_V) {
      insert(clipboard());
      return true;
    }
    if (keyCode == LEFT) {
      int next = wordStart(cursor);
      if (next == cursor) {
        next = wordStart(cursor - 1);
      }
      if (shift) {
        extend(next);
      } else {
        collapse(next);
      }
      return true;
    }
    if (keyCode == RIGHT) {
      int next = wordEnd(cursor);
      if (next == cursor) {
        next = wordEnd(cursor + 1);
      }
      if (shift) {
        extend(next);
      } else {
        collapse(next);
      }
      return true;
    }
    if (keyCode == BACKSPACE) {
      if (selected()) {
        removeSelection();
      } else {
        int from = wordStart(cursor);
        if (from == cursor) {
          from = wordStart(cursor - 1);
        }
        draft = draft.substring(0, from) + draft.substring(cursor);
        collapse(from);
      }
      return true;
    }
    if (keyCode == DELETE) {
      if (selected()) {
        removeSelection();
      } else {
        int to = wordEnd(cursor);
        if (to == cursor) {
          to = wordEnd(cursor + 1);
        }
        draft = draft.substring(0, cursor) + draft.substring(to);
      }
      return true;
    }
    return false;
  }

  @Override
  public boolean charTyped(int codepoint) {
    if (!editing
        || codepoint < 32
        || codepoint == 127
        || draft.length() - (hi() - lo()) >= MAX_LENGTH) {
      return false;
    }
    insert(new String(Character.toChars(codepoint)));
    return true;
  }
}
