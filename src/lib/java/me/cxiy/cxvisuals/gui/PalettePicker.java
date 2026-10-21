package me.cxiy.cxvisuals.gui;

import me.cxiy.cxvisuals.core.settings.Palette;
import me.cxiy.cxvisuals.core.settings.PaletteSetting;

public final class PalettePicker extends Widget {
  private static final float HEADER = 40.0F;
  private static final float LEFT = 8.0F;
  private static final float RIGHT = 6.0F;
  private static final float DOT = 20.0F;
  private static final float DOT_GAP = 9.0F;
  private static final float RING = 3.0F;
  private static final float BODY = 126.0F;
  private static final float FIELD_HEIGHT = 96.0F;
  private static final float FIELD_SHARE = 0.56F;
  private static final float FIELD_RADIUS = 10.0F;
  private static final float GAP = 14.0F;
  private static final float HUE_HEIGHT = 14.0F;
  private static final float KNOB = 16.0F;
  private static final float CHIP_WIDTH = 58.0F;
  private static final float CHIP_HEIGHT = 28.0F;
  private static final float CHIP_GAP = 8.0F;
  private static final float SWATCH = 14.0F;
  private static final int KNOB_COLOR = 0xFFFFFFFF;
  private static final int KNOB_SHADOW = 0x40000000;
  private static final int KNOB_RING = 0x1F000000;
  private static final int NONE = 0;
  private static final int FIELD = 1;
  private static final int HUE = 2;
  private static final String[] ENDS = {"A", "B"};

  private final PaletteSetting setting;
  private final Anim open;
  private final Anim marker;
  private final Anim[] hover = new Anim[Palette.count() + 1];
  private final float[][] hsv = new float[2][3];
  private final int[] shown = new int[2];
  private final String[] hex = {"", ""};
  private int end;
  private int dragging = NONE;

  public PalettePicker(PaletteSetting setting) {
    this.setting = setting;
    boolean custom = setting.get().preset() == Palette.CUSTOM;
    this.open = Anim.move(custom ? 1.0F : 0.0F);
    this.marker = Anim.pop(index());
    for (int i = 0; i < hover.length; i++) {
      hover[i] = Anim.hover(0.0F);
    }
    this.h = HEADER + BODY * open.get();
    sync();
  }

  private int index() {
    int preset = setting.get().preset();
    return preset == Palette.CUSTOM ? Palette.count() : preset;
  }

  private void sync() {
    Palette palette = setting.get();
    adopt(0, palette.customFirst());
    adopt(1, palette.customSecond());
  }

  private void adopt(int which, int argb) {
    if (shown[which] != argb || hex[which].isEmpty()) {
      shown[which] = argb;
      hex[which] = Palette.hex(argb);
      if (Hsv.argb(hsv[which][0], hsv[which][1], hsv[which][2]) != argb) {
        Hsv.of(argb, hsv[which]);
      }
    }
  }

  public float dotX(int dot) {
    return x
        + w
        - RIGHT
        - (hover.length - dot) * DOT
        - (hover.length - 1 - dot) * DOT_GAP
        + DOT * 0.5F;
  }

  public float dotY() {
    return y + HEADER * 0.5F;
  }

  public float fieldX() {
    return x + LEFT;
  }

  public float fieldY() {
    return y + HEADER + 6.0F;
  }

  public float fieldWidth() {
    return (float) Math.floor((w - LEFT - RIGHT - GAP) * FIELD_SHARE);
  }

  public float hueX() {
    return fieldX() + fieldWidth() + GAP;
  }

  public float hueY() {
    return fieldY() + HUE_HEIGHT * 0.5F;
  }

  public float hueWidth() {
    return x + w - RIGHT - hueX();
  }

  public float chipX(int which) {
    return hueX() + which * (CHIP_WIDTH + CHIP_GAP) + CHIP_WIDTH * 0.5F;
  }

  public float chipY() {
    return fieldY() + HUE_HEIGHT + 16.0F + CHIP_HEIGHT * 0.5F;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    sync();
    Palette palette = setting.get();
    boolean custom = palette.preset() == Palette.CUSTOM;
    open.set(custom ? 1.0F : 0.0F);
    marker.set(index());
    open.update(dt);
    marker.update(dt);
    float amount = Math.max(0.0F, Math.min(1.0F, open.get()));
    h = HEADER + BODY * amount;

    c.text(
        FontStyle.ROW, setting.title(), x + LEFT, centered(c, FontStyle.ROW, y, HEADER), t.ink());
    String name = Palette.name(palette.preset());
    c.text(
        FontStyle.SMALL,
        name,
        dotX(0) - DOT * 0.5F - 12.0F - c.textWidth(FontStyle.SMALL, name),
        centered(c, FontStyle.SMALL, y, HEADER),
        t.grey());

    float ringX = dotX(0) + marker.get() * (DOT + DOT_GAP) - DOT * 0.5F - RING;
    float ringSize = DOT + RING * 2.0F;
    c.roundRectBorder(
        ringX, dotY() - ringSize * 0.5F, ringSize, ringSize, ringSize * 0.5F, 1.5F, t.ink());
    int over = dotAt(mouseX, mouseY);
    for (int i = 0; i < hover.length; i++) {
      Anim glow = hover[i];
      glow.set(i == over ? 1.0F : 0.0F);
      glow.update(dt);
      float left = dotX(i) - DOT * 0.5F;
      float top = dotY() - DOT * 0.5F;
      boolean own = i == Palette.count();
      c.pushTransform(1.0F + 0.1F * glow.get(), dotX(i), dotY());
      c.roundRectGradient(
          left,
          top,
          DOT,
          DOT,
          DOT * 0.5F,
          own ? palette.customFirst() : Palette.first(i),
          own ? palette.customSecond() : Palette.second(i));
      c.roundRectBorder(left, top, DOT, DOT, DOT * 0.5F, 1.0F, t.hair());
      c.popTransform();
    }

    if (amount <= 0.01F) {
      return;
    }
    c.pushClip(x, y + HEADER, w, BODY * amount);
    c.pushAlpha(amount);
    float fx = fieldX();
    float fy = fieldY();
    float fw = fieldWidth();
    float[] tone = hsv[end];
    c.spectrum(fx, fy, fw, FIELD_HEIGHT, FIELD_RADIUS, tone[0]);
    c.roundRectBorder(fx, fy, fw, FIELD_HEIGHT, FIELD_RADIUS, 1.0F, t.hair());
    knob(c, fx + fw * tone[1], fy + FIELD_HEIGHT * (1.0F - tone[2]), shown[end], dragging == FIELD);

    float hx = hueX();
    float hw = hueWidth();
    c.spectrum(hx, fy, hw, HUE_HEIGHT, HUE_HEIGHT * 0.5F, -1.0F);
    c.roundRectBorder(hx, fy, hw, HUE_HEIGHT, HUE_HEIGHT * 0.5F, 1.0F, t.hair());
    knob(
        c,
        hx + HUE_HEIGHT * 0.5F + (hw - HUE_HEIGHT) * tone[0],
        fy + HUE_HEIGHT * 0.5F,
        Hsv.argb(tone[0], 1.0F, 1.0F),
        dragging == HUE);

    for (int i = 0; i < 2; i++) {
      float left = chipX(i) - CHIP_WIDTH * 0.5F;
      float top = chipY() - CHIP_HEIGHT * 0.5F;
      if (i == end) {
        c.shadow(left, top, CHIP_WIDTH, CHIP_HEIGHT, CHIP_HEIGHT * 0.5F, 3.0F, 1.0F, t.shadow());
        Depth.raised(c, t, left, top, CHIP_WIDTH, CHIP_HEIGHT, CHIP_HEIGHT * 0.5F, t.raised());
      } else {
        Depth.inset(c, t, left, top, CHIP_WIDTH, CHIP_HEIGHT, CHIP_HEIGHT * 0.5F, t.surface2());
      }
      float swatchTop = top + (CHIP_HEIGHT - SWATCH) * 0.5F;
      c.roundRect(left + 8.0F, swatchTop, SWATCH, SWATCH, SWATCH * 0.5F, shown[i]);
      c.roundRectBorder(left + 8.0F, swatchTop, SWATCH, SWATCH, SWATCH * 0.5F, 1.0F, t.hair());
      c.text(
          FontStyle.LABEL,
          ENDS[i],
          left + 8.0F + SWATCH + 8.0F,
          centered(c, FontStyle.LABEL, top, CHIP_HEIGHT),
          i == end ? t.ink() : t.grey());
    }
    c.text(FontStyle.MONO, hex[end], hx, chipY() + CHIP_HEIGHT * 0.5F + 12.0F, t.ink2());
    c.popAlpha();
    c.popClip();
  }

  private static void knob(Canvas c, float centreX, float centreY, int fill, boolean held) {
    float size = held ? KNOB + 3.0F : KNOB;
    float left = centreX - size * 0.5F;
    float top = centreY - size * 0.5F;
    c.shadow(left, top, size, size, size * 0.5F, 4.0F, 1.0F, KNOB_SHADOW);
    Depth.knob(c, left, top, size, size, size * 0.5F, KNOB_COLOR);
    c.roundRect(left + 3.0F, top + 3.0F, size - 6.0F, size - 6.0F, (size - 6.0F) * 0.5F, fill);
    c.roundRectBorder(left, top, size, size, size * 0.5F, 1.0F, KNOB_RING);
  }

  private int dotAt(float mx, float my) {
    if (Math.abs(my - dotY()) > DOT * 0.5F + RING) {
      return -1;
    }
    for (int i = 0; i < hover.length; i++) {
      if (Math.abs(mx - dotX(i)) <= DOT * 0.5F + DOT_GAP * 0.5F) {
        return i;
      }
    }
    return -1;
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (!contains(mx, my)) {
      return false;
    }
    int dot = dotAt(mx, my);
    if (dot >= 0) {
      setting.set(setting.get().choose(dot == Palette.count() ? Palette.CUSTOM : dot));
      return true;
    }
    if (setting.get().preset() != Palette.CUSTOM || open.get() < 0.5F) {
      return true;
    }
    float fy = fieldY();
    if (mx >= fieldX() && mx <= fieldX() + fieldWidth() && my >= fy && my <= fy + FIELD_HEIGHT) {
      dragging = FIELD;
      apply(mx, my);
    } else if (mx >= hueX() - KNOB * 0.5F
        && mx <= hueX() + hueWidth() + KNOB * 0.5F
        && Math.abs(my - hueY()) <= KNOB * 0.5F + 2.0F) {
      dragging = HUE;
      apply(mx, my);
    } else if (Math.abs(my - chipY()) <= CHIP_HEIGHT * 0.5F) {
      for (int i = 0; i < 2; i++) {
        if (Math.abs(mx - chipX(i)) <= CHIP_WIDTH * 0.5F) {
          end = i;
        }
      }
    }
    return true;
  }

  @Override
  public void mouseDrag(float mx, float my) {
    if (dragging != NONE) {
      apply(mx, my);
    }
  }

  @Override
  public void mouseUp(float mx, float my) {
    dragging = NONE;
  }

  private void apply(float mx, float my) {
    float[] tone = hsv[end];
    if (dragging == FIELD) {
      tone[1] = unit((mx - fieldX()) / fieldWidth());
      tone[2] = 1.0F - unit((my - fieldY()) / FIELD_HEIGHT);
    } else {
      tone[0] =
          Math.min(0.9999F, unit((mx - hueX() - HUE_HEIGHT * 0.5F) / (hueWidth() - HUE_HEIGHT)));
    }
    int argb = Hsv.argb(tone[0], tone[1], tone[2]);
    shown[end] = argb;
    hex[end] = Palette.hex(argb);
    Palette palette = setting.get();
    setting.set(
        end == 0
            ? palette.custom(argb, palette.customSecond())
            : palette.custom(palette.customFirst(), argb));
  }

  private static float unit(float value) {
    return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
  }
}
