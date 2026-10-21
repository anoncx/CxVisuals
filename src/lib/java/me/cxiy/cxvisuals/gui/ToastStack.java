package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.gui.hud.CardMotion;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.modules.friends.Friends;

public final class ToastStack {
  public static final int CAPACITY = 3;
  public static final float WIDTH = 272.0F;
  public static final float HEIGHT = 48.0F;
  public static final float GAP = 6.0F;
  static final float LIFE = CardMotion.LIFE;
  private static final float PULSE_REACH = 9.0F;
  private static final float DOT = 6.0F;
  private static final float PAD = 10.0F;
  private static final float FACE = 30.0F;
  private static final float FACE_RADIUS = 9.0F;
  private static final float ITEM = 22.0F;
  private static final float BACKING = 0.9F;
  private static final float SAMPLE_AGE = 2.0F;
  private static final String[] SAMPLE_NAMES = {"Nova", "Echo"};
  private static final String[] SAMPLE_TEXTS = Lang.keys("Sent you a message", "Came online");
  private static final Friends.Toast[] SAMPLES = new Friends.Toast[SAMPLE_NAMES.length];

  private static int samplesIn = -1;

  private final Friends.Toast[] toasts = new Friends.Toast[CAPACITY];
  private final float[] ages = new float[CAPACITY];
  private final float[] offsets = new float[CAPACITY];
  private int count;

  public int size() {
    return count;
  }

  public static float extent(int slots) {
    return slots * (HEIGHT + GAP) - GAP;
  }

  public static int samples() {
    return SAMPLES.length;
  }

  public boolean push(Friends.Toast toast) {
    if (count == CAPACITY) {
      return false;
    }
    toasts[count] = toast;
    ages[count] = 0.0F;
    offsets[count] = count * (HEIGHT + GAP) - GAP;
    count++;
    return true;
  }

  public void clear() {
    for (int i = 0; i < count; i++) {
      toasts[i] = null;
    }
    count = 0;
  }

  float top(int index) {
    return offsets[index];
  }

  float width(int index) {
    return width(ages[index]);
  }

  private static float width(float age) {
    return CardMotion.width(age, HEIGHT, WIDTH);
  }

  public void sample(
      Canvas c, Theme t, float x, float y, float height, float align, boolean rising) {
    if (samplesIn != Lang.generation()) {
      samplesIn = Lang.generation();
      for (int i = 0; i < SAMPLES.length; i++) {
        SAMPLES[i] = new Friends.Toast("", SAMPLE_NAMES[i], Lang.tr(SAMPLE_TEXTS[i]));
      }
    }
    for (int i = 0; i < SAMPLES.length; i++) {
      float offset = i * (HEIGHT + GAP);
      pill(
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
      pill(
          c,
          t,
          toasts[i],
          x,
          rising ? y + height - HEIGHT - offset : y + offset,
          align,
          ages[i],
          out);
    }
    int kept = 0;
    for (int i = 0; i < count; i++) {
      if (ages[i] < LIFE) {
        toasts[kept] = toasts[i];
        ages[kept] = ages[i];
        offsets[kept] = offsets[i];
        kept++;
      }
    }
    for (int i = kept; i < count; i++) {
      toasts[i] = null;
    }
    count = kept;
  }

  private static void pill(
      Canvas c,
      Theme t,
      Friends.Toast toast,
      float x,
      float top,
      float align,
      float age,
      float out) {
    float alpha = CardMotion.alpha(age, out);
    if (alpha <= 0.004F) {
      return;
    }
    float width = width(age);
    float left = x + (WIDTH - width) * align;
    float text = CardMotion.text(age, out);
    c.pushAlpha(alpha);
    c.pushTransform(CardMotion.scale(out), left + width * 0.5F, top + HEIGHT * 0.5F);
    c.shadow(left, top, width, HEIGHT, HEIGHT * 0.5F, 18.0F, 6.0F, 0x73000000);
    c.roundRect(
        left, top, width, HEIGHT, HEIGHT * 0.5F, Theme.withAlpha(t.paper() | 0xFF000000, BACKING));
    HudPaint.plate(c, t, left, top, width, HEIGHT, HEIGHT * 0.5F);
    float alert = CardMotion.alert(age);
    if (alert > 0.0F) {
      c.roundRectBorder(
          left, top, width, HEIGHT, HEIGHT * 0.5F, 1.5F, Theme.withAlpha(t.red(), alert));
    }
    float open = CardMotion.unit((width - HEIGHT) / (WIDTH - HEIGHT));
    float faceLeft = left + PAD - 1.0F;
    float faceTop = top + (HEIGHT - FACE) * 0.5F;
    if (text > 0.004F) {
      c.pushAlpha(text);
      if (toast.item() >= 0) {
        c.roundRect(faceLeft, faceTop, FACE, FACE, FACE_RADIUS, t.surface2());
        c.stack(
            toast.item(), faceLeft + (FACE - ITEM) * 0.5F, faceTop + (FACE - ITEM) * 0.5F, ITEM);
      } else {
        Hits.face(c, t, toast.entityId(), toast.title(), faceLeft, faceTop, FACE, FACE_RADIUS);
      }
      c.popAlpha();
    }
    float restX = faceLeft + FACE - DOT * 0.5F - 1.0F;
    float restY = faceTop - DOT * 0.5F + 2.0F;
    float dotX = left + (HEIGHT - DOT) * 0.5F + (restX - left - (HEIGHT - DOT) * 0.5F) * open;
    float dotY = top + (HEIGHT - DOT) * 0.5F + (restY - top - (HEIGHT - DOT) * 0.5F) * open;
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
    if (text > 0.004F) {
      float textX = left + PAD + FACE + 9.0F;
      float rowLine = c.lineHeight(FontStyle.ROW);
      float smallLine = c.lineHeight(FontStyle.SMALL);
      float textTop = top + (HEIGHT - rowLine - smallLine) * 0.5F;
      float slide = CardMotion.slide(age) * 6.0F;
      float tagWidth = toast.tag().isEmpty() ? 0.0F : c.textWidth(FontStyle.MONO, toast.tag());
      float room = Math.max(0.0F, width - PAD - FACE - 9.0F - PAD - 4.0F);
      c.pushClip(textX, top, room, HEIGHT);
      c.pushAlpha(text);
      c.pushContrast(t.paper());
      c.text(FontStyle.ROW, toast.title(), textX + slide, textTop, t.ink());
      if (tagWidth > 0.0F) {
        c.text(
            FontStyle.MONO,
            toast.tag(),
            textX + room - tagWidth,
            textTop + (rowLine - c.lineHeight(FontStyle.MONO)) * 0.5F,
            t.grey());
      }
      c.text(FontStyle.SMALL, toast.text(), textX + slide, textTop + rowLine, t.ink2());
      c.popContrast();
      c.popAlpha();
      c.popClip();
    }
    c.popTransform();
    c.popAlpha();
  }
}
