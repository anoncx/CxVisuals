package me.cxiy.cxvisuals.gui;

import java.util.function.Consumer;
import me.cxiy.cxvisuals.core.i18n.Language;
import me.cxiy.cxvisuals.core.math.Easing;

public final class LanguagePicker {
  public static final int KEY_ENTER = 257;
  public static final int KEY_KEYPAD_ENTER = 335;
  public static final int KEY_DOWN = 264;
  public static final int KEY_UP = 265;
  public static final int KEY_TAB = 258;

  private static final Language[] LANGUAGES = Language.values();
  private static final String[] HEADINGS = {"LANGUAGE", "SPRACHE", "ЯЗЫК"};
  private static final String[] NOTES = {
    "You can change this later in Settings.",
    "Du kannst das später in den Einstellungen ändern.",
    "Позже это можно изменить в настройках."
  };
  private static final String[] CONFIRMS = {"Continue", "Weiter", "Продолжить"};
  private static final String[] CODES = {"EN", "DE", "RU"};
  private static final String WORDMARK = "CXVISUALS";
  private static final float WIDTH = 420.0F;
  private static final float PAD = 28.0F;
  private static final float RADIUS = 22.0F;
  private static final float HEAD = 104.0F;
  private static final float ROW = 54.0F;
  private static final float ROW_GAP = 6.0F;
  private static final float ROW_RADIUS = 16.0F;
  private static final float LIST_INSET = 5.0F;
  private static final float CODE = 34.0F;
  private static final float DOT = 8.0F;
  private static final float MARK = 6.0F;
  private static final float FOOT = 84.0F;
  private static final float BUTTON = 40.0F;
  private static final float MARGIN = 16.0F;
  private static final float OPEN_TIME = 0.42F;
  private static final float CLOSE_TIME = 0.2F;
  private static final float POP_FROM = 0.94F;
  private static final float STAGGER = 0.07F;
  private static final float REVEAL = 0.5F;
  private static final float RISE = 10.0F;
  private static final float SWAP_SHIFT = 6.0F;
  private static final int SHADOW = 0x66000000;
  private static final int NONE = -1;
  private static final int CONFIRM = LANGUAGES.length;

  private final Consumer<Language> picked;
  private final Anim thumb;
  private final Anim swap = Anim.reveal(1.0F);
  private final Anim press = Anim.pop(0.0F);
  private final Anim confirmHover = Anim.hover(0.0F);
  private final Anim confirmPress = Anim.press(0.0F);
  private final Anim[] emphasis = new Anim[LANGUAGES.length];
  private final Anim[] hover = new Anim[LANGUAGES.length];
  private float screenW = WIDTH;
  private float screenH = 480.0F;
  private float fit = 1.0F;
  private float phase;
  private boolean closing;
  private boolean reported;
  private int chosen;
  private int shown;
  private int previous;
  private int held = NONE;

  public LanguagePicker(Language initial, Consumer<Language> picked) {
    this.picked = picked;
    this.chosen = initial.ordinal();
    this.shown = chosen;
    this.previous = chosen;
    this.thumb = Anim.pop(chosen);
    for (int i = 0; i < LANGUAGES.length; i++) {
      emphasis[i] = Anim.state(i == chosen ? 1.0F : 0.0F);
      hover[i] = Anim.hover(0.0F);
    }
  }

  public Language chosen() {
    return LANGUAGES[chosen];
  }

  public boolean closing() {
    return closing;
  }

  public boolean closed() {
    return closing && phase <= 0.0F;
  }

  public void resize(float width, float height) {
    screenW = width;
    screenH = height;
    fit =
        Math.max(
            0.1F,
            Math.min(
                1.0F,
                Math.min((width - MARGIN * 2.0F) / WIDTH, (height - MARGIN * 2.0F) / height())));
  }

  public static float height() {
    return HEAD
        + LIST_INSET * 2.0F
        + LANGUAGES.length * ROW
        + (LANGUAGES.length - 1) * ROW_GAP
        + FOOT;
  }

  public float cardX() {
    return (screenW - WIDTH * fit) * 0.5F;
  }

  public float cardY() {
    return (screenH - height() * fit) * 0.5F;
  }

  public float rowX() {
    return cardX() + (PAD + LIST_INSET) * fit;
  }

  public float rowY(int index) {
    return cardY() + (HEAD + LIST_INSET + index * (ROW + ROW_GAP)) * fit;
  }

  public float rowWidth() {
    return (WIDTH - (PAD + LIST_INSET) * 2.0F) * fit;
  }

  public float rowHeight() {
    return ROW * fit;
  }

  public float confirmX() {
    return cardX() + PAD * fit;
  }

  public float confirmY() {
    return cardY() + (height() - PAD - BUTTON) * fit;
  }

  public float confirmWidth() {
    return (WIDTH - PAD * 2.0F) * fit;
  }

  public float confirmHeight() {
    return BUTTON * fit;
  }

  public void choose(int index) {
    int next = Math.max(0, Math.min(LANGUAGES.length - 1, index));
    if (next == chosen) {
      return;
    }
    chosen = next;
  }

  public void confirm() {
    if (closing) {
      return;
    }
    closing = true;
    if (!reported) {
      reported = true;
      picked.accept(LANGUAGES[chosen]);
    }
  }

  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    phase =
        closing ? Math.max(0.0F, phase - dt / CLOSE_TIME) : Math.min(1.0F, phase + dt / OPEN_TIME);
    if (chosen != shown && swap.settled()) {
      previous = shown;
      shown = chosen;
      swap.snap(0.0F);
      swap.set(1.0F);
    }
    int over = closing ? NONE : hit(mouseX, mouseY);
    thumb.set(chosen);
    press.set(held >= 0 && held < CONFIRM ? 1.0F : 0.0F);
    confirmHover.set(over == CONFIRM ? 1.0F : 0.0F);
    confirmPress.set(held == CONFIRM ? 1.0F : 0.0F);
    thumb.update(dt);
    swap.update(dt);
    press.update(dt);
    confirmHover.update(dt);
    confirmPress.update(dt);

    float grown = (float) (closing ? Easing.snappy(phase) : Easing.spring(phase));
    float alpha = Math.min(1.0F, phase * 3.0F);
    float left = (screenW - WIDTH) * 0.5F;
    float top = (screenH - height()) * 0.5F;
    float tall = height();

    c.pushAlpha(alpha);
    c.pushTransform(fit * (POP_FROM + (1.0F - POP_FROM) * grown), screenW * 0.5F, screenH * 0.5F);
    c.shadow(left, top, WIDTH, tall, RADIUS, 44.0F, 18.0F, SHADOW);
    if (t.glass()) {
      c.glass(left, top, WIDTH, tall, RADIUS, t.paper());
    } else {
      c.roundRect(left, top, WIDTH, tall, RADIUS, t.paper());
    }
    c.roundRectBorder(left, top, WIDTH, tall, RADIUS, 1.0F, t.hair());
    c.rim(left, top, WIDTH, tall, RADIUS, 1.0F, RADIUS * 1.4F, Depth.rimColor(t));

    float markTop = top + PAD + (c.lineHeight(FontStyle.MONO) - MARK) * 0.5F;
    c.roundRect(left + PAD, markTop, MARK, MARK, MARK * 0.5F, t.red());
    c.text(FontStyle.MONO, WORDMARK, left + PAD + MARK + 8.0F, top + PAD, t.grey());

    float headingTop = top + PAD + c.lineHeight(FontStyle.MONO) + 8.0F;
    float noteTop = headingTop + c.lineHeight(FontStyle.DOTS) + 4.0F;
    float arrived = Math.max(0.0F, Math.min(1.0F, swap.get()));
    if (arrived < 1.0F) {
      c.pushAlpha(1.0F - arrived);
      c.text(
          FontStyle.DOTS,
          HEADINGS[previous],
          left + PAD,
          headingTop - SWAP_SHIFT * arrived,
          t.ink());
      c.text(FontStyle.SMALL, NOTES[previous], left + PAD, noteTop, t.grey());
      c.popAlpha();
    }
    c.pushAlpha(arrived);
    c.text(
        FontStyle.DOTS,
        HEADINGS[shown],
        left + PAD,
        headingTop + SWAP_SHIFT * (1.0F - arrived),
        t.ink());
    c.text(FontStyle.SMALL, NOTES[shown], left + PAD, noteTop, t.grey());
    c.popAlpha();

    float listLeft = left + PAD;
    float listTop = top + HEAD;
    float listWidth = WIDTH - PAD * 2.0F;
    float listHeight =
        LIST_INSET * 2.0F + LANGUAGES.length * ROW + (LANGUAGES.length - 1) * ROW_GAP;
    Depth.inset(
        c, t, listLeft, listTop, listWidth, listHeight, ROW_RADIUS + LIST_INSET, t.surface2());

    float rowLeft = listLeft + LIST_INSET;
    float rowWidth = listWidth - LIST_INSET * 2.0F;
    float thumbTop = listTop + LIST_INSET + thumb.get() * (ROW + ROW_GAP);
    c.pushTransform(1.0F - 0.03F * press.get(), rowLeft + rowWidth * 0.5F, thumbTop + ROW * 0.5F);
    c.shadow(rowLeft, thumbTop, rowWidth, ROW, ROW_RADIUS, 6.0F, 2.0F, t.shadow());
    Depth.raised(c, t, rowLeft, thumbTop, rowWidth, ROW, ROW_RADIUS, t.raised());
    c.popTransform();

    float nameLine = c.lineHeight(FontStyle.ROW);
    float codeLine = c.lineHeight(FontStyle.MONO);
    for (int i = 0; i < LANGUAGES.length; i++) {
      Anim tone = emphasis[i];
      Anim glow = hover[i];
      tone.set(i == chosen ? 1.0F : 0.0F);
      glow.set(i == over && i != chosen ? 1.0F : 0.0F);
      tone.update(dt);
      glow.update(dt);
      float lit = Math.max(0.0F, Math.min(1.0F, tone.get()));
      float shownIn = closing ? 1.0F : reveal(i);
      float rowTop = listTop + LIST_INSET + i * (ROW + ROW_GAP) + (1.0F - shownIn) * RISE;
      c.pushAlpha(shownIn);
      if (glow.get() > 0.01F) {
        c.roundRect(
            rowLeft,
            rowTop,
            rowWidth,
            ROW,
            ROW_RADIUS,
            Theme.withAlpha(t.ink(), 0.05F * glow.get()));
      }
      float codeLeft = rowLeft + 10.0F;
      float codeTop = rowTop + (ROW - CODE) * 0.5F;
      c.roundRect(codeLeft, codeTop, CODE, CODE, 10.0F, Theme.mix(t.well(), t.surface2(), lit));
      c.text(
          FontStyle.MONO,
          CODES[i],
          codeLeft + (CODE - c.textWidth(FontStyle.MONO, CODES[i])) * 0.5F,
          codeTop + (CODE - codeLine) * 0.5F,
          Theme.mix(t.grey(), t.ink(), lit));
      c.text(
          FontStyle.ROW,
          LANGUAGES[i].nativeName,
          codeLeft + CODE + 14.0F,
          rowTop + (ROW - nameLine) * 0.5F,
          Theme.mix(t.ink2(), t.ink(), Math.max(lit, glow.get())));
      float dotLeft = rowLeft + rowWidth - 18.0F - DOT;
      float dotTop = rowTop + (ROW - DOT) * 0.5F;
      c.roundRect(dotLeft, dotTop, DOT, DOT, DOT * 0.5F, t.dotOff());
      if (lit > 0.01F) {
        float size = DOT * lit;
        c.roundRect(
            dotLeft + (DOT - size) * 0.5F,
            dotTop + (DOT - size) * 0.5F,
            size,
            size,
            size * 0.5F,
            t.red());
      }
      c.popAlpha();
    }

    float buttonLeft = left + PAD;
    float buttonTop = top + tall - PAD - BUTTON;
    float buttonWidth = WIDTH - PAD * 2.0F;
    float buttonIn = closing ? 1.0F : reveal(LANGUAGES.length);
    c.pushAlpha(buttonIn);
    c.pushTransform(
        1.0F - 0.04F * confirmPress.get(),
        buttonLeft + buttonWidth * 0.5F,
        buttonTop + BUTTON * 0.5F);
    Depth.raised(
        c,
        t,
        buttonLeft,
        buttonTop + (1.0F - buttonIn) * RISE,
        buttonWidth,
        BUTTON,
        BUTTON * 0.5F,
        Theme.mix(t.ink(), t.paper(), 0.08F * confirmHover.get()));
    String label = CONFIRMS[shown];
    c.text(
        FontStyle.LABEL,
        label,
        buttonLeft + (buttonWidth - c.textWidth(FontStyle.LABEL, label)) * 0.5F,
        buttonTop + (1.0F - buttonIn) * RISE + (BUTTON - c.lineHeight(FontStyle.LABEL)) * 0.5F,
        t.paper() | 0xFF000000);
    c.popTransform();
    c.popAlpha();

    c.popTransform();
    c.popAlpha();
  }

  private float reveal(int order) {
    float t = (phase - 0.3F - order * STAGGER) / REVEAL;
    return t <= 0.0F ? 0.0F : t >= 1.0F ? 1.0F : (float) Easing.snappy(t);
  }

  private int hit(float mx, float my) {
    if (mx >= confirmX()
        && mx < confirmX() + confirmWidth()
        && my >= confirmY()
        && my < confirmY() + confirmHeight()) {
      return CONFIRM;
    }
    if (mx < rowX() || mx >= rowX() + rowWidth()) {
      return NONE;
    }
    for (int i = 0; i < LANGUAGES.length; i++) {
      if (my >= rowY(i) && my < rowY(i) + rowHeight()) {
        return i;
      }
    }
    return NONE;
  }

  public boolean mouseDown(float mx, float my) {
    if (closing) {
      return false;
    }
    held = hit(mx, my);
    if (held >= 0 && held < CONFIRM) {
      choose(held);
    }
    return held != NONE;
  }

  public void mouseUp(float mx, float my) {
    int was = held;
    held = NONE;
    if (was == CONFIRM && hit(mx, my) == CONFIRM) {
      confirm();
    }
  }

  public boolean keyDown(int keyCode, boolean shift) {
    if (closing) {
      return false;
    }
    switch (keyCode) {
      case KEY_UP -> choose(chosen - 1);
      case KEY_DOWN -> choose(chosen + 1);
      case KEY_TAB -> choose(Math.floorMod(chosen + (shift ? -1 : 1), LANGUAGES.length));
      case KEY_ENTER, KEY_KEYPAD_ENTER -> confirm();
      default -> {
        return false;
      }
    }
    return true;
  }
}
