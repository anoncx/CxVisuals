package me.cxiy.cxvisuals.gui;

import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.core.settings.Choice;
import me.cxiy.cxvisuals.modules.shaders.circles.Contour;
import me.cxiy.cxvisuals.modules.shaders.circles.ContourSetting;

public final class ContourPad extends Widget {
  public static final int SMOOTH = 0;
  public static final int RESET = 1;

  private static final String[] FORMS =
      Lang.keys("Circle", "Hexagon", "Star", "Heart", "Flower", "Square");
  private static final String FREE = Lang.key("Free");
  private static final String FOLDS_LABEL = Lang.key("Symmetry");
  private static final String[] SYMMETRY = {"×2", "×3", "×4", "×6"};
  private static final int[] FOLDS = {1, 2, 3, 4, 6};
  private static final String[] ACTIONS = {Lang.key("Smooth"), Lang.key("Reset")};
  private static final String FRONT = Lang.key("Front");
  private static final String DRAW = Lang.key("Drag the outline to shape it");
  private static final String CUSTOM = Lang.key("Custom");
  private static final float HEIGHT = 150.0F;
  private static final float GAP = 16.0F;
  private static final float TILE_HEIGHT = 34.0F;
  private static final float TILE_GAP = 6.0F;
  private static final float NAME_TOP = 44.0F;
  private static final float FOLDS_TOP = 70.0F;
  private static final float BUTTON_HEIGHT = 30.0F;
  private static final float ICON = 16.0F;
  private static final float GRID = 14.0F;
  private static final float GRID_DOT = 2.0F;
  private static final float FILL = 0.94F;
  private static final float REACH = 1.6F;
  private static final float GRAB = 18.0F;
  private static final float MORPH = 0.36F;
  private static final int SEGMENTS = 96;
  private static final int ICON_SEGMENTS = 40;
  private static final int KNOB_COLOR = 0xFFFFFFFF;
  private static final int KNOB_SHADOW = 0x40000000;

  private final ContourSetting setting;
  private final Contour[] forms = new Contour[FORMS.length];
  private final Anim[] tileHover = new Anim[FORMS.length];
  private final Anim[] buttonHover = new Anim[ACTIONS.length];
  private final Anim grab = Anim.pop(0.0F);
  private final Segmented folds;
  private final float[] xs = new float[SEGMENTS + 1];
  private final float[] ys = new float[SEGMENTS + 1];
  private final float[] iconXs = new float[ICON_SEGMENTS + 1];
  private final float[] iconYs = new float[ICON_SEGMENTS + 1];
  private int fold;
  private Contour from;
  private Contour to;
  private float morph = 1.0F;
  private boolean painting;
  private boolean pressingFolds;
  private int pressed = -1;
  private int knot = -1;
  private float knobX;
  private float knobY;

  public ContourPad(ContourSetting setting) {
    this.setting = setting;
    this.h = HEIGHT;
    Contour.Form[] all = Contour.Form.values();
    for (int i = 0; i < forms.length; i++) {
      forms[i] = all[i].contour();
      tileHover[i] = Anim.hover(0.0F);
    }
    for (int i = 0; i < buttonHover.length; i++) {
      buttonHover[i] = Anim.hover(0.0F);
    }
    folds =
        new Segmented(
            new Choice() {
              @Override
              public List<String> labels() {
                return List.of(Lang.tr(FREE), SYMMETRY[0], SYMMETRY[1], SYMMETRY[2], SYMMETRY[3]);
              }

              @Override
              public int index() {
                return fold;
              }

              @Override
              public void setIndex(int index) {
                fold = index;
                morphTo(setting.get().mirrored(FOLDS[index]));
              }
            });
  }

  public float padSize() {
    return Math.min(h, (float) Math.floor((w - GAP) * 0.42F));
  }

  public float padCentreX() {
    return x + padSize() * 0.5F;
  }

  public float padCentreY() {
    return y + h * 0.5F;
  }

  public float unit() {
    return padSize() * 0.5F * FILL / Contour.HIGHEST;
  }

  public float pointX(int index, float radius) {
    return padCentreX() + (float) Math.sin(Contour.angle(index)) * radius * unit();
  }

  public float pointY(int index, float radius) {
    return padCentreY() - (float) Math.cos(Contour.angle(index)) * radius * unit();
  }

  public float columnX() {
    return x + padSize() + GAP;
  }

  public float columnWidth() {
    return x + w - columnX();
  }

  public float tileX(int index) {
    return columnX() + index * (tileWidth() + TILE_GAP);
  }

  public float tileY(int index) {
    return y;
  }

  public float tileWidth() {
    return (columnWidth() - TILE_GAP * (FORMS.length - 1)) / FORMS.length;
  }

  public float buttonX(int index) {
    return columnX() + index * (buttonWidth() + TILE_GAP);
  }

  public float buttonY() {
    return y + h - BUTTON_HEIGHT;
  }

  public float buttonWidth() {
    return (columnWidth() - TILE_GAP) * 0.5F;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    step(dt);
    Contour contour = setting.get();
    pad(c, t, contour, mouseX, mouseY, dt);
    tiles(c, t, contour, mouseX, mouseY, dt);
    folds.limit(columnWidth());
    folds.measure(c);
    folds.place(columnX(), y + FOLDS_TOP);
    folds.draw(c, t, mouseX, mouseY, dt);
    buttons(c, t, mouseX, mouseY, dt);
  }

  private void step(float dt) {
    if (morph >= 1.0F) {
      return;
    }
    morph = Math.min(1.0F, morph + dt / MORPH);
    setting.set(from.blend(to, (float) Easing.snappy(morph)));
  }

  private void morphTo(Contour target) {
    from = setting.get();
    to = target;
    morph = 0.0F;
  }

  private void pad(Canvas c, Theme t, Contour contour, float mouseX, float mouseY, float dt) {
    float size = padSize();
    float cx = padCentreX();
    float cy = padCentreY();
    float left = cx - size * 0.5F;
    float top = cy - size * 0.5F;
    Depth.inset(c, t, left, top, size, size, t.radius() - 2.0F, t.well());
    int dots = Theme.withAlpha(t.ink(), 0.1F);
    float limit = size * 0.5F - GRID * 0.5F;
    for (float gy = -limit + (limit % GRID); gy <= limit; gy += GRID) {
      for (float gx = -limit + (limit % GRID); gx <= limit; gx += GRID) {
        if (gx * gx + gy * gy <= limit * limit) {
          c.roundRect(
              cx + gx - GRID_DOT * 0.5F,
              cy + gy - GRID_DOT * 0.5F,
              GRID_DOT,
              GRID_DOT,
              GRID_DOT * 0.5F,
              dots);
        }
      }
    }
    int folding = FOLDS[fold];
    if (folding > 1) {
      int spoke = Theme.withAlpha(t.ink(), 0.12F);
      for (int i = 0; i < folding; i++) {
        int index = i * Contour.KNOTS / folding;
        xs[0] = cx;
        ys[0] = cy;
        xs[1] = pointX(index, Contour.HIGHEST);
        ys[1] = pointY(index, Contour.HIGHEST);
        c.stroke(xs, ys, 2, 1.0F, 0.5F, spoke);
      }
    }
    outline(cx, cy, unit(), xs, ys, SEGMENTS, null);
    c.stroke(xs, ys, SEGMENTS + 1, 1.0F, 0.5F, Theme.withAlpha(t.ink(), 0.16F));
    outline(cx, cy, unit(), xs, ys, SEGMENTS, contour);
    c.stroke(xs, ys, SEGMENTS + 1, 10.0F, 8.0F, Theme.withAlpha(t.ink(), 0.08F));
    c.stroke(xs, ys, SEGMENTS + 1, 2.0F, 1.0F, t.ink());

    String front = Lang.tr(FRONT);
    c.text(
        FontStyle.CAPTION,
        front,
        cx - c.textWidth(FontStyle.CAPTION, front) * 0.5F,
        top + 6.0F,
        t.grey());

    int near = painting ? knot : nearest(mouseX, mouseY, contour);
    grab.set(near >= 0 ? 1.0F : 0.0F);
    grab.update(dt);
    int tick = Theme.withAlpha(t.ink(), 0.35F);
    for (int i = 0; i < Contour.KNOTS; i += 2) {
      float px = pointX(i, contour.knot(i));
      float py = pointY(i, contour.knot(i));
      c.roundRect(px - 1.5F, py - 1.5F, 3.0F, 3.0F, 1.5F, tick);
    }
    if (near >= 0) {
      knobX = pointX(near, contour.knot(near));
      knobY = pointY(near, contour.knot(near));
      float knob = (10.0F + (painting ? 4.0F : 0.0F)) * Math.max(0.0F, grab.get());
      c.shadow(
          knobX - knob * 0.5F,
          knobY - knob * 0.5F,
          knob,
          knob,
          knob * 0.5F,
          4.0F,
          1.0F,
          KNOB_SHADOW);
      Depth.knob(c, knobX - knob * 0.5F, knobY - knob * 0.5F, knob, knob, knob * 0.5F, KNOB_COLOR);
      c.roundRect(knobX - 2.0F, knobY - 2.0F, 4.0F, 4.0F, 2.0F, t.red());
    } else if (contour.near(forms[0], 0.002F)) {
      String hint = Lang.tr(DRAW);
      float hintW = c.textWidth(FontStyle.CAPTION, hint);
      if (hintW < size - 12.0F) {
        c.text(
            FontStyle.CAPTION,
            hint,
            cx - hintW * 0.5F,
            top + size - c.lineHeight(FontStyle.CAPTION) - 6.0F,
            t.grey());
      }
    }
  }

  private static void outline(
      float cx, float cy, float scale, float[] outX, float[] outY, int segments, Contour contour) {
    for (int i = 0; i <= segments; i++) {
      float turn = (float) i / segments;
      double angle = turn * Math.PI * 2.0;
      float r = (contour == null ? 1.0F : contour.at(turn)) * scale;
      outX[i] = cx + (float) Math.sin(angle) * r;
      outY[i] = cy - (float) Math.cos(angle) * r;
    }
  }

  private void tiles(Canvas c, Theme t, Contour contour, float mouseX, float mouseY, float dt) {
    int shown = -1;
    for (int i = 0; i < forms.length; i++) {
      float tx = tileX(i);
      float ty = tileY(i);
      float tw = tileWidth();
      boolean over = inside(mouseX, mouseY, tx, ty, tw, TILE_HEIGHT);
      boolean chosen = contour.near(forms[i], 0.01F);
      if (over || (chosen && shown < 0)) {
        shown = i;
      }
      Anim glow = tileHover[i];
      glow.set(over ? 1.0F : 0.0F);
      glow.update(dt);
      float radius = TILE_HEIGHT * 0.32F;
      if (chosen) {
        c.shadow(tx, ty, tw, TILE_HEIGHT, radius, 3.0F, 1.0F, t.shadow());
        Depth.raised(c, t, tx, ty, tw, TILE_HEIGHT, radius, t.raised());
      } else {
        Depth.inset(
            c,
            t,
            tx,
            ty,
            tw,
            TILE_HEIGHT,
            radius,
            Theme.mix(t.surface2(), t.raised(), glow.get() * 0.5F));
      }
      outline(
          tx + tw * 0.5F,
          ty + TILE_HEIGHT * 0.5F,
          ICON * 0.5F,
          iconXs,
          iconYs,
          ICON_SEGMENTS,
          forms[i]);
      c.stroke(
          iconXs,
          iconYs,
          ICON_SEGMENTS + 1,
          1.5F,
          0.8F,
          Theme.mix(t.grey(), t.ink(), chosen ? 1.0F : glow.get()));
    }
    String name = Lang.tr(shown < 0 ? CUSTOM : FORMS[shown]);
    c.text(FontStyle.LABEL, name, columnX() + 2.0F, y + NAME_TOP, t.ink());
    String folding = Lang.tr(FOLDS_LABEL);
    c.text(
        FontStyle.CAPTION,
        folding,
        x + w - c.textWidth(FontStyle.CAPTION, folding) - 2.0F,
        y + NAME_TOP + 1.0F,
        t.grey());
  }

  private void buttons(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    for (int i = 0; i < ACTIONS.length; i++) {
      float bx = buttonX(i);
      float by = buttonY();
      float bw = buttonWidth();
      boolean over = inside(mouseX, mouseY, bx, by, bw, BUTTON_HEIGHT);
      Anim glow = buttonHover[i];
      glow.set(over ? 1.0F : 0.0F);
      glow.update(dt);
      float press = pressed == i ? 0.96F : 1.0F;
      c.pushTransform(press, bx + bw * 0.5F, by + BUTTON_HEIGHT * 0.5F);
      Depth.raised(
          c,
          t,
          bx,
          by,
          bw,
          BUTTON_HEIGHT,
          BUTTON_HEIGHT * 0.5F,
          Theme.mix(t.surface2(), t.raised(), glow.get()));
      String label = Lang.tr(ACTIONS[i]);
      c.text(
          FontStyle.LABEL,
          label,
          bx + (bw - c.textWidth(FontStyle.LABEL, label)) * 0.5F,
          centered(c, FontStyle.LABEL, by, BUTTON_HEIGHT),
          t.ink());
      c.popTransform();
    }
  }

  private int nearest(float mx, float my, Contour contour) {
    float dx = mx - padCentreX();
    float dy = my - padCentreY();
    float size = padSize();
    if (Math.abs(dx) > size * 0.5F || Math.abs(dy) > size * 0.5F) {
      return -1;
    }
    int index = knotAt(dx, dy);
    float px = pointX(index, contour.knot(index)) - mx;
    float py = pointY(index, contour.knot(index)) - my;
    return px * px + py * py <= GRAB * GRAB ? index : -1;
  }

  private static int knotAt(float dx, float dy) {
    double angle = Math.atan2(dx, -dy);
    int index = (int) Math.round(angle / (Math.PI * 2.0) * Contour.KNOTS);
    return Math.floorMod(index, Contour.KNOTS);
  }

  private static boolean inside(float mx, float my, float bx, float by, float bw, float bh) {
    return mx >= bx && mx < bx + bw && my >= by && my < by + bh;
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (!contains(mx, my)) {
      return false;
    }
    float size = padSize();
    if (Math.abs(mx - padCentreX()) <= size * 0.5F && Math.abs(my - padCentreY()) <= size * 0.5F) {
      painting = true;
      morph = 1.0F;
      paint(mx, my);
      return true;
    }
    for (int i = 0; i < forms.length; i++) {
      if (inside(mx, my, tileX(i), tileY(i), tileWidth(), TILE_HEIGHT)) {
        morphTo(fold == 0 ? forms[i] : forms[i].mirrored(FOLDS[fold]));
        return true;
      }
    }
    for (int i = 0; i < ACTIONS.length; i++) {
      if (inside(mx, my, buttonX(i), buttonY(), buttonWidth(), BUTTON_HEIGHT)) {
        pressed = i;
        return true;
      }
    }
    pressingFolds = folds.mouseDown(mx, my);
    return true;
  }

  @Override
  public void mouseDrag(float mx, float my) {
    if (painting) {
      paint(mx, my);
    }
  }

  @Override
  public void mouseUp(float mx, float my) {
    if (pressingFolds) {
      folds.mouseUp(mx, my);
    }
    if (pressed >= 0 && inside(mx, my, buttonX(pressed), buttonY(), buttonWidth(), BUTTON_HEIGHT)) {
      morphTo(pressed == SMOOTH ? setting.get().smooth().smooth() : forms[0]);
    }
    pressed = -1;
    painting = false;
    pressingFolds = false;
    knot = -1;
  }

  private void paint(float mx, float my) {
    float dx = mx - padCentreX();
    float dy = my - padCentreY();
    knot = knotAt(dx, dy);
    float radius = (float) Math.sqrt(dx * dx + dy * dy) / unit();
    setting.set(setting.get().pull(knot, radius, REACH, FOLDS[fold]));
  }
}
