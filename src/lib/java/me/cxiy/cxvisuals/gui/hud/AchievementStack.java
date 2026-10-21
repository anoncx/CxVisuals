package me.cxiy.cxvisuals.gui.hud;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.modules.hud.Achievement;

public final class AchievementStack {
  public static final int CAPACITY = 3;
  public static final float WIDTH = 304.0F;
  public static final float HEIGHT = 64.0F;
  public static final float GAP = 8.0F;
  public static final float MERGE_WINDOW = 3.0F;

  private static final float RADIUS = 22.0F;
  private static final float PAD = 12.0F;
  private static final float TILE = 40.0F;
  private static final float TILE_RADIUS = 13.0F;
  private static final float ICON = 24.0F;
  private static final float TEXT_GAP = 11.0F;
  private static final float DOT = 6.0F;
  private static final float PULSE_REACH = 9.0F;
  private static final float BACKING = 0.9F;
  private static final float SAMPLE_AGE = 2.0F;
  private static final float GLYPH_SHIFT = 2.0F;
  private static final String[] SAMPLE_TITLES =
      Lang.keys("Achievement title", "Another achievement");
  private static final String[] SAMPLE_DETAILS = Lang.keys("What you did to earn it", "Keep going");
  private static final Achievement.Kind[] SAMPLE_KINDS = {
    Achievement.Kind.CHALLENGE, Achievement.Kind.TASK
  };
  private static final Achievement[] SAMPLES = new Achievement[SAMPLE_TITLES.length];

  private static int samplesIn = -1;

  private final Achievement[] cards = new Achievement[CAPACITY];
  private final float[] ages = new float[CAPACITY];
  private final float[] offsets = new float[CAPACITY];
  private int count;

  public int size() {
    return count;
  }

  public boolean full() {
    return count == CAPACITY;
  }

  public static float extent(int slots) {
    return slots * (HEIGHT + GAP) - GAP;
  }

  public static int samples() {
    return SAMPLES.length;
  }

  public Achievement newest() {
    return count == 0 ? null : cards[count - 1];
  }

  public float newestAge() {
    return count == 0 ? Float.MAX_VALUE : ages[count - 1];
  }

  public void refresh() {
    if (count > 0) {
      ages[count - 1] = Math.min(ages[count - 1], CardMotion.LIFE * 0.4F);
    }
  }

  public boolean push(Achievement card) {
    if (count == CAPACITY) {
      return false;
    }
    cards[count] = card;
    ages[count] = 0.0F;
    offsets[count] = count * (HEIGHT + GAP) - GAP;
    count++;
    return true;
  }

  public void clear() {
    for (int i = 0; i < count; i++) {
      cards[i] = null;
    }
    count = 0;
  }

  public void sample(
      Canvas c, Theme t, float x, float y, float height, float align, boolean rising) {
    if (samplesIn != Lang.generation()) {
      samplesIn = Lang.generation();
      for (int i = 0; i < SAMPLES.length; i++) {
        SAMPLES[i] =
            new Achievement(
                SAMPLE_KINDS[i],
                Lang.tr(SAMPLE_TITLES[i]),
                Lang.tr(SAMPLE_DETAILS[i]),
                Achievement.NO_ICON);
      }
    }
    for (int i = 0; i < SAMPLES.length; i++) {
      float offset = i * (HEIGHT + GAP);
      card(
          c,
          t,
          SAMPLES[i],
          x,
          rising ? y + height - HEIGHT - offset : y + offset,
          align,
          SAMPLE_AGE,
          0.0F);
    }
  }

  public void draw(
      Canvas c, Theme t, float x, float y, float height, float align, boolean rising, float dt) {
    float follow = 1.0F - (float) Math.exp(-dt * CardMotion.FOLLOW);
    for (int i = 0; i < count; i++) {
      ages[i] += dt;
      offsets[i] += (i * (HEIGHT + GAP) - offsets[i]) * follow;
      float out = CardMotion.leaving(ages[i]);
      float offset = offsets[i] - CardMotion.drift(out);
      card(
          c,
          t,
          cards[i],
          x,
          rising ? y + height - HEIGHT - offset : y + offset,
          align,
          ages[i],
          out);
    }
    int kept = 0;
    for (int i = 0; i < count; i++) {
      if (ages[i] < CardMotion.LIFE) {
        cards[kept] = cards[i];
        ages[kept] = ages[i];
        offsets[kept] = offsets[i];
        kept++;
      }
    }
    for (int i = kept; i < count; i++) {
      cards[i] = null;
    }
    count = kept;
  }

  private static void card(
      Canvas c, Theme t, Achievement card, float x, float top, float align, float age, float out) {
    float alpha = CardMotion.alpha(age, out);
    if (alpha <= 0.004F) {
      return;
    }
    float width = CardMotion.width(age, HEIGHT, WIDTH);
    float open = CardMotion.unit((width - HEIGHT) / (WIDTH - HEIGHT));
    float radius = HEIGHT * 0.5F + (RADIUS - HEIGHT * 0.5F) * open;
    float left = x + (WIDTH - width) * align;
    float text = CardMotion.text(age, out);
    boolean challenge = card.kind() == Achievement.Kind.CHALLENGE;
    c.pushAlpha(alpha);
    c.pushTransform(CardMotion.scale(out), left + width * 0.5F, top + HEIGHT * 0.5F);
    c.shadow(left, top, width, HEIGHT, radius, 18.0F, 6.0F, 0x73000000);
    c.roundRect(left, top, width, HEIGHT, radius, Theme.withAlpha(t.paper() | 0xFF000000, BACKING));
    HudPaint.plate(c, t, left, top, width, HEIGHT, radius);
    float alert = CardMotion.alert(age);
    if (challenge && alert > 0.0F) {
      c.roundRectBorder(left, top, width, HEIGHT, radius, 1.5F, Theme.withAlpha(t.red(), alert));
    }
    float tileLeft = left + PAD + (HEIGHT - PAD * 2.0F - TILE) * (1.0F - open) * 0.5F;
    float tileTop = top + (HEIGHT - TILE) * 0.5F;
    drawTile(c, t, card, tileLeft, tileTop, challenge, age, open, text);
    if (text > 0.004F) {
      drawText(c, t, card, left, top, width, tileLeft + TILE + TEXT_GAP, age, text);
    }
    c.popTransform();
    c.popAlpha();
  }

  private static void drawTile(
      Canvas c,
      Theme t,
      Achievement card,
      float left,
      float top,
      boolean challenge,
      float age,
      float open,
      float text) {
    c.roundRect(left, top, TILE, TILE, TILE_RADIUS, t.surface2());
    if (challenge) {
      c.roundRectBorder(left, top, TILE, TILE, TILE_RADIUS, 1.5F, Theme.withAlpha(t.red(), 0.85F));
    } else if (card.kind() == Achievement.Kind.GOAL) {
      c.roundRectBorder(left, top, TILE, TILE, TILE_RADIUS, 1.0F, Theme.withAlpha(t.ink(), 0.3F));
    }
    if (card.icon() >= 0) {
      c.stack(card.icon(), left + (TILE - ICON) * 0.5F, top + (TILE - ICON) * 0.5F, ICON);
    } else {
      String glyph = card.kind().name().substring(0, 1);
      c.text(
          FontStyle.DOTS,
          glyph,
          left + (TILE - c.textWidth(FontStyle.DOTS, glyph)) * 0.5F,
          top + (TILE - c.lineHeight(FontStyle.DOTS)) * 0.5F - GLYPH_SHIFT,
          t.ink());
    }
    if (!challenge) {
      return;
    }
    float dotX = left + TILE - DOT * 0.5F - 2.0F;
    float dotY = top - DOT * 0.5F + 3.0F;
    float pulse = CardMotion.unit((age - CardMotion.PULSE_AT) / CardMotion.PULSE);
    if (pulse > 0.0F && pulse < 1.0F) {
      float reach = PULSE_REACH * (float) Easing.snappy(pulse);
      c.roundRectBorder(
          dotX - reach,
          dotY - reach,
          DOT + reach * 2.0F,
          DOT + reach * 2.0F,
          (DOT + reach * 2.0F) * 0.5F,
          1.0F,
          Theme.withAlpha(t.red(), 0.7F * (1.0F - pulse)));
    }
    c.roundRect(
        dotX - 1.5F,
        dotY - 1.5F,
        DOT + 3.0F,
        DOT + 3.0F,
        (DOT + 3.0F) * 0.5F,
        Theme.withAlpha(t.paper() | 0xFF000000, open));
    c.roundRect(dotX, dotY, DOT, DOT, DOT * 0.5F, t.red());
  }

  private static void drawText(
      Canvas c,
      Theme t,
      Achievement card,
      float left,
      float top,
      float width,
      float textX,
      float age,
      float text) {
    float room = Math.max(0.0F, left + width - PAD - textX);
    float captionLine = c.lineHeight(FontStyle.CAPTION);
    float rowLine = c.lineHeight(FontStyle.ROW);
    float smallLine = c.lineHeight(FontStyle.SMALL);
    float textTop = top + (HEIGHT - captionLine - rowLine - smallLine) * 0.5F;
    float slide = CardMotion.slide(age) * 6.0F;
    c.pushClip(textX, top, room, HEIGHT);
    c.pushAlpha(text);
    c.pushContrast(t.paper());
    c.text(
        FontStyle.CAPTION,
        c.fit(FontStyle.CAPTION, card.caption(), room),
        textX + slide,
        textTop,
        t.grey());
    c.text(
        FontStyle.ROW,
        c.fit(FontStyle.ROW, card.title(), room),
        textX + slide,
        textTop + captionLine,
        t.ink());
    c.text(
        FontStyle.SMALL,
        c.fit(FontStyle.SMALL, card.detail(), room),
        textX + slide,
        textTop + captionLine + rowLine,
        t.ink2());
    c.popContrast();
    c.popAlpha();
    c.popClip();
  }
}
