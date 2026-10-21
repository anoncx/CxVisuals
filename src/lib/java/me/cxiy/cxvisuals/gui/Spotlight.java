package me.cxiy.cxvisuals.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.math.Easing;

final class Spotlight {
  static final int ESCAPE = 256;
  static final int ENTER = 257;
  static final int KEYPAD_ENTER = 335;
  static final int DOWN = 264;
  static final int UP = 265;

  private static final float WIDTH = 460.0F;
  private static final float MARGIN = 16.0F;
  private static final float RADIUS = 20.0F;
  private static final float PAD = 10.0F;
  private static final float ROW = 46.0F;
  private static final float ROW_RADIUS = 12.0F;
  private static final float RISE = 10.0F;
  private static final float TEXT_INSET = 14.0F;
  private static final float DIM = 0.4F;
  private static final float EMPTY_HEIGHT = 56.0F;
  private static final int LIMIT = 6;
  private static final int SHADOW = 0x99000000;
  private static final String NONE = Lang.key("Nothing matches that");
  private static final String PLACEHOLDER = Lang.key("Search settings");

  private final Consumer<SettingsIndex.Hit> jump;
  private final Field field = new Field(PLACEHOLDER, 40);
  private final Anim shown = Anim.reveal(0.0F);
  private SettingsIndex index;
  private List<SettingsIndex.Hit> hits = new ArrayList<>();
  private String shownQuery = "";
  private int selected;
  private boolean open;
  private float x;
  private float y;
  private float w;
  private float h;
  private float lastX = Float.NaN;
  private float lastY = Float.NaN;

  Spotlight(Consumer<SettingsIndex.Hit> jump) {
    this.jump = jump;
  }

  boolean open() {
    return open;
  }

  boolean visible() {
    return open || shown.get() > 0.004F;
  }

  void show(SettingsIndex source) {
    index = source;
    open = true;
    field.set("");
    field.focus(true);
    hits = new ArrayList<>();
    shownQuery = "";
    selected = 0;
    shown.set(1.0F);
    Sound.play(Sound.Cue.OPEN);
  }

  void hide() {
    if (!open) {
      return;
    }
    open = false;
    field.focus(false);
    shown.set(0.0F);
    Sound.play(Sound.Cue.CLOSE);
  }

  private void refresh() {
    String query = field.text();
    if (query.equals(shownQuery)) {
      return;
    }
    shownQuery = query;
    hits = index == null ? new ArrayList<>() : index.search(query, LIMIT);
    selected = 0;
  }

  private float panelHeight() {
    float fieldBlock = Field.HEIGHT + PAD * 2.0F;
    if (hits.isEmpty()) {
      return fieldBlock + (field.text().isBlank() ? 0.0F : EMPTY_HEIGHT);
    }
    return fieldBlock + hits.size() * ROW + PAD;
  }

  void draw(
      Canvas c,
      Theme t,
      float centreX,
      float top,
      float screenW,
      float screenH,
      float mouseX,
      float mouseY,
      float dt) {
    shown.update(dt);
    float presence = (float) Easing.smoothstep(shown.get());
    if (presence <= 0.004F) {
      return;
    }
    refresh();
    w = Math.min(WIDTH, screenW - MARGIN * 2.0F);
    h = panelHeight();
    x = Math.max(MARGIN, Math.min(screenW - MARGIN - w, centreX - w * 0.5F));
    y = top + (1.0F - presence) * -RISE;

    c.roundRect(0.0F, 0.0F, screenW, screenH, 0.0F, Theme.withAlpha(0xFF000000, DIM * presence));
    c.pushAlpha(presence);
    c.shadow(x, y, w, h, RADIUS, 36.0F, 14.0F, SHADOW);
    Depth.raised(c, t, x, y, w, h, RADIUS, t.surface());
    field.draw(c, t, x + PAD, y + PAD, w - PAD * 2.0F, dt);

    float listTop = y + PAD * 2.0F + Field.HEIGHT;
    if (hits.isEmpty()) {
      if (!field.text().isBlank()) {
        c.text(
            FontStyle.SMALL,
            Lang.tr(NONE),
            x + TEXT_INSET + PAD,
            listTop + (EMPTY_HEIGHT - c.lineHeight(FontStyle.SMALL)) * 0.5F - 4.0F,
            t.grey());
      }
    } else {
      hover(mouseX, mouseY, listTop);
      for (int i = 0; i < hits.size(); i++) {
        drawRow(c, t, hits.get(i), i, listTop + i * ROW);
      }
    }
    c.popAlpha();
  }

  private void drawRow(Canvas c, Theme t, SettingsIndex.Hit hit, int index, float top) {
    float left = x + PAD;
    float width = w - PAD * 2.0F;
    if (index == selected) {
      Depth.raised(c, t, left, top, width, ROW - 4.0F, ROW_RADIUS, t.surface2());
    }
    float textLeft = left + TEXT_INSET;
    float room = width - TEXT_INSET * 2.0F;
    float titleLine = c.lineHeight(FontStyle.ROW);
    float subLine = c.lineHeight(FontStyle.SMALL);
    float textTop = top + (ROW - 4.0F - titleLine - subLine) * 0.5F;
    c.text(FontStyle.ROW, c.fit(FontStyle.ROW, hit.title(), room), textLeft, textTop, t.ink());
    c.text(
        FontStyle.SMALL,
        c.fit(FontStyle.SMALL, hit.path() + " · " + hit.group(), room),
        textLeft,
        textTop + titleLine,
        t.grey());
  }

  private void hover(float mx, float my, float listTop) {
    if (mx < x || mx >= x + w || my < listTop) {
      return;
    }
    int row = (int) ((my - listTop) / ROW);
    if (row >= 0 && row < hits.size() && (mx != lastX || my != lastY)) {
      selected = row;
    }
    lastX = mx;
    lastY = my;
  }

  boolean mouseDown(float mx, float my) {
    if (!visible()) {
      return false;
    }
    boolean inside = mx >= x && mx < x + w && my >= y && my < y + h;
    if (!inside) {
      hide();
      return true;
    }
    float listTop = y + PAD * 2.0F + Field.HEIGHT;
    if (my < listTop) {
      field.press(mx);
      return true;
    }
    int row = (int) ((my - listTop) / ROW);
    if (row >= 0 && row < hits.size()) {
      activate(row);
    }
    return true;
  }

  void mouseDrag(float mx) {
    if (open) {
      field.drag(mx);
    }
  }

  void mouseUp() {
    field.release();
  }

  boolean keyDown(int keyCode, boolean control, boolean shift) {
    if (!open) {
      return false;
    }
    switch (keyCode) {
      case ESCAPE -> hide();
      case ENTER, KEYPAD_ENTER -> {
        if (!hits.isEmpty()) {
          activate(selected);
        }
      }
      case DOWN -> move(1);
      case UP -> move(-1);
      default -> field.key(keyCode, control, shift);
    }
    return true;
  }

  boolean charTyped(int codepoint) {
    return open && field.type(codepoint);
  }

  private void move(int step) {
    if (hits.isEmpty()) {
      return;
    }
    int next = Math.floorMod(selected + step, hits.size());
    if (next != selected) {
      selected = next;
      Sound.play(Sound.Cue.STEP);
    }
  }

  private void activate(int row) {
    SettingsIndex.Hit hit = hits.get(row);
    hide();
    jump.accept(hit);
  }
}
