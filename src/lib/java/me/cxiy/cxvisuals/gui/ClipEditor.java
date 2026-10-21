package me.cxiy.cxvisuals.gui;

import java.util.List;
import java.util.Locale;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.Choice;

public final class ClipEditor extends Widget {
  private static final float PREVIEW = 148.0F;
  private static final float PREVIEW_RADIUS = 16.0F;
  private static final float PREVIEW_INSET = 14.0F;
  private static final float GAP = 10.0F;
  private static final float ROW = 38.0F;
  private static final float NAME = 62.0F;
  private static final float CHIP = 24.0F;
  private static final float CHIP_GAP = 6.0F;
  private static final float CHIP_PAD = 10.0F;
  private static final float ADD = 24.0F;
  private static final float MODE = 26.0F;
  private static final float MODE_GAP = 6.0F;
  private static final float DRAWER_PAD = 8.0F;
  private static final float DRAWER_RADIUS = 12.0F;
  private static final float DOT = 6.0F;
  private static final float COLLAPSED = 40.0F;
  private static final float SPIN = 0.35F;
  private static final int KIND = 1 << 16;
  private static final int CHIP_HIT = KIND;
  private static final int ADD_HIT = 2 * KIND;
  private static final int OPTION_HIT = 3 * KIND;
  private static final int MODE_HIT = 4 * KIND;
  private static final int ROW_HIT = 5 * KIND;
  private static final String CROSS = "×";
  private static final List<String> MODES = List.of(Lang.key("Order"), Lang.key("Random"));
  private static final String[] NOTES =
      Lang.keys(
          "Pick an avatar in Wardrobe to set up its animations.",
          "This model has no animations.",
          "Switch on Use model animations to edit them.");

  private final Clips clips;
  private final Anim drawer = Anim.reveal(0.0F);
  private Segmented[] modes = new Segmented[0];
  private String[] eventTitles = new String[0];
  private String[] autoNotes = new String[0];
  private String[] shortNames = new String[0];
  private String[] titles = new String[0];
  private String[] optionLabels = new String[0];
  private float[] shortWidths = new float[0];
  private float[] optionX = new float[0];
  private float[] optionY = new float[0];
  private float[] optionW = new float[0];
  private float[] rowTop = new float[0];
  private float[] rowHeight = new float[0];
  private float[] modeTop = new float[0];
  private float[] chipX = new float[0];
  private float[] chipY = new float[0];
  private float[] chipW = new float[0];
  private int[] chipEvent = new int[0];
  private int[] chipClip = new int[0];
  private int chips;
  private int options;
  private int events;
  private float crossWidth;
  private float drawerTop;
  private float drawerHeight;
  private int open = -1;
  private int shown = -1;
  private int pressed = -1;
  private int hovered = -1;
  private int hoverEvent = -1;
  private int hoverClip = -1;
  private float yaw;
  private int labelRevision = Integer.MIN_VALUE;
  private float labelWidth = -1.0F;
  private int laidRevision = Integer.MIN_VALUE;
  private float laidWidth = -1.0F;
  private int laidShown = -2;
  private float laidUnfold = -1.0F;
  private boolean ready;
  private int state;
  private int layouts;

  public ClipEditor(Clips clips) {
    this.clips = clips;
    this.h = COLLAPSED;
  }

  int layouts() {
    return layouts;
  }

  int chipCount() {
    return chips;
  }

  float addX(int event) {
    return x + w - ADD * 0.5F;
  }

  float addY(int event) {
    return y + rowTop[event] + ROW * 0.5F;
  }

  float chipX(int chip) {
    return x + chipX[chip] + chipW[chip] * 0.5F;
  }

  float chipY(int chip) {
    return y + chipY[chip] + CHIP * 0.5F;
  }

  float optionX(int option) {
    return x + optionX[option] + optionW[option] * 0.5F;
  }

  float optionY(int option) {
    return y + drawerTop + optionY[option] + CHIP * 0.5F;
  }

  float rowBottom(int event) {
    return y + rowTop[event] + rowHeight[event];
  }

  Segmented mode(int event) {
    return modes[event];
  }

  private static float clamp01(float value) {
    return value < 0.0F ? 0.0F : Math.min(value, 1.0F);
  }

  private void labels(Canvas c) {
    int count = clips.clipCount();
    events = clips.eventCount();
    options = count + 1;
    crossWidth = c.textWidth(FontStyle.CAPTION, CROSS);
    float chipRoom =
        Math.max(24.0F, w - NAME - ADD - CHIP_GAP - CHIP_PAD * 2.0F - crossWidth - 6.0F);
    float optionRoom = Math.max(24.0F, w - DRAWER_PAD * 2.0F - CHIP_PAD * 2.0F - DOT - 6.0F);
    shortNames = new String[count];
    shortWidths = new float[count];
    titles = new String[count];
    optionLabels = new String[options];
    optionX = new float[options];
    optionY = new float[options];
    optionW = new float[options];
    optionLabels[0] = Lang.tr("Off");
    for (int clip = 0; clip < count; clip++) {
      String name = clips.clipName(clip);
      shortNames[clip] = c.fit(FontStyle.CAPTION, name, chipRoom);
      shortWidths[clip] = c.textWidth(FontStyle.CAPTION, shortNames[clip]);
      titles[clip] = name.toUpperCase(Locale.ROOT);
      String length = String.format(Locale.ROOT, " · %.1fs", clips.clipSeconds(clip));
      optionLabels[clip + 1] =
          c.fit(FontStyle.CAPTION, name, optionRoom - c.textWidth(FontStyle.CAPTION, length))
              + length;
    }
    if (modes.length != events) {
      modes = new Segmented[events];
      eventTitles = new String[events];
      rowTop = new float[events];
      rowHeight = new float[events];
      modeTop = new float[events];
      for (int event = 0; event < events; event++) {
        int index = event;
        modes[event] =
            new Segmented(
                new Choice() {
                  @Override
                  public List<String> labels() {
                    return MODES;
                  }

                  @Override
                  public int index() {
                    return clips.random(index) ? 1 : 0;
                  }

                  @Override
                  public void setIndex(int value) {
                    clips.setRandom(index, value == 1);
                  }
                });
        modes[event].h = MODE;
        eventTitles[event] = clips.eventName(event).toUpperCase(Locale.ROOT);
      }
    }
    autoNotes = new String[events];
    int total = 0;
    for (int event = 0; event < events; event++) {
      int auto = clips.autoClip(event);
      String lead = Lang.tr("auto") + " · ";
      autoNotes[event] =
          lead
              + (auto >= 0 && auto < count
                  ? c.fit(
                      FontStyle.CAPTION,
                      clips.clipName(auto),
                      Math.max(
                          24.0F, w - NAME - ADD - CHIP_GAP - c.textWidth(FontStyle.SMALL, lead)))
                  : Lang.tr("none"));
      total += clips.assignedCount(event);
    }
    if (total > chipX.length) {
      chipX = new float[total];
      chipY = new float[total];
      chipW = new float[total];
      chipEvent = new int[total];
      chipClip = new int[total];
    }
    float left = DRAWER_PAD;
    float right = w - DRAWER_PAD;
    float cx = left;
    float cy = DRAWER_PAD;
    for (int option = 0; option < options; option++) {
      float ow =
          Math.min(
              right - left,
              c.textWidth(FontStyle.CAPTION, optionLabels[option]) + CHIP_PAD * 2.0F + DOT + 6.0F);
      if (cx + ow > right && cx > left) {
        cx = left;
        cy += CHIP + CHIP_GAP;
      }
      optionX[option] = cx;
      optionY[option] = cy;
      optionW[option] = ow;
      cx += ow + CHIP_GAP;
    }
    drawerHeight = cy + CHIP + DRAWER_PAD;
    if (open >= events) {
      open = -1;
    }
    if (shown >= events) {
      shown = -1;
    }
    pressed = -1;
  }

  private void layout(Canvas c) {
    state = clips.state();
    ready = state == Clips.READY;
    if (!ready) {
      h = COLLAPSED;
      open = -1;
      shown = -1;
      pressed = -1;
      chips = 0;
      laidRevision = Integer.MIN_VALUE;
      labelRevision = Integer.MIN_VALUE;
      return;
    }
    int revision = clips.revision();
    if (revision != labelRevision || w != labelWidth) {
      labelRevision = revision;
      labelWidth = w;
      labels(c);
    }
    float unfold = clamp01(drawer.get());
    if (revision == laidRevision && w == laidWidth && shown == laidShown && unfold == laidUnfold) {
      return;
    }
    laidRevision = revision;
    laidWidth = w;
    laidShown = shown;
    laidUnfold = unfold;
    layouts++;
    float right = w - ADD - CHIP_GAP;
    float top = PREVIEW + GAP;
    int count = clips.clipCount();
    chips = 0;
    for (int event = 0; event < events; event++) {
      rowTop[event] = top;
      int assigned = clips.assignedCount(event);
      float cx = NAME;
      float cy = top + (ROW - CHIP) * 0.5F;
      for (int slot = 0; slot < assigned && chips < chipX.length; slot++) {
        int clip = clips.assigned(event, slot);
        if (clip < 0 || clip >= count) {
          continue;
        }
        float cw = shortWidths[clip] + CHIP_PAD * 2.0F + crossWidth + 6.0F;
        if (cx + cw > right && cx > NAME) {
          cx = NAME;
          cy += CHIP + CHIP_GAP;
        }
        chipX[chips] = cx;
        chipY[chips] = cy;
        chipW[chips] = cw;
        chipEvent[chips] = event;
        chipClip[chips] = clip;
        chips++;
        cx += cw + CHIP_GAP;
      }
      float height = cy + CHIP + (ROW - CHIP) * 0.5F - top;
      if (assigned >= 2) {
        modeTop[event] = top + height - (ROW - CHIP) * 0.5F + MODE_GAP;
        height = modeTop[event] + MODE + (ROW - CHIP) * 0.5F - top;
      } else {
        modeTop[event] = -1.0F;
      }
      if (event == shown) {
        drawerTop = top + height;
        height += drawerHeight * unfold;
      }
      rowHeight[event] = height;
      top += height;
    }
    h = top;
  }

  private int hit(float mx, float my) {
    if (!ready) {
      return -1;
    }
    float lx = mx - x;
    float ly = my - y;
    if (lx < 0.0F || lx >= w || ly < PREVIEW + GAP || ly >= h) {
      return -1;
    }
    for (int i = 0; i < chips; i++) {
      if (lx >= chipX[i] && lx < chipX[i] + chipW[i] && ly >= chipY[i] && ly < chipY[i] + CHIP) {
        return CHIP_HIT | i;
      }
    }
    int event = -1;
    for (int e = 0; e < events; e++) {
      if (ly >= rowTop[e] && ly < rowTop[e] + rowHeight[e]) {
        event = e;
        break;
      }
    }
    if (event < 0) {
      return -1;
    }
    float addTop = rowTop[event] + (ROW - ADD) * 0.5F;
    if (lx >= w - ADD && ly >= addTop && ly < addTop + ADD) {
      return ADD_HIT | event;
    }
    if (modeTop[event] >= 0.0F && modes[event].contains(mx, my)) {
      return MODE_HIT | event;
    }
    if (event == open && ly >= drawerTop) {
      float oy = ly - drawerTop;
      for (int option = 0; option < options; option++) {
        if (lx >= optionX[option]
            && lx < optionX[option] + optionW[option]
            && oy >= optionY[option]
            && oy < optionY[option] + CHIP) {
          return OPTION_HIT | option;
        }
      }
    }
    return ROW_HIT | event;
  }

  private void hover(float mx, float my) {
    hovered = hit(mx, my);
    hoverEvent = -1;
    hoverClip = -1;
    if (hovered < 0) {
      return;
    }
    int kind = hovered & -KIND;
    int index = hovered & (KIND - 1);
    if (kind == CHIP_HIT) {
      hoverEvent = chipEvent[index];
      hoverClip = chipClip[index];
    } else if (kind == OPTION_HIT) {
      hoverEvent = open;
      hoverClip = index - 1;
    } else {
      hoverEvent = index;
    }
  }

  private boolean assigned(int event, int clip) {
    for (int slot = clips.assignedCount(event) - 1; slot >= 0; slot--) {
      if (clips.assigned(event, slot) == clip) {
        return true;
      }
    }
    return false;
  }

  private String meta(int event) {
    if (clips.off(event)) {
      return Lang.tr("OFF");
    }
    int count = clips.assignedCount(event);
    if (count == 0) {
      return Lang.tr("AUTO");
    }
    if (count == 1) {
      return Lang.tr("1 CLIP");
    }
    return clips.random(event) ? Lang.tr("RANDOM") : Lang.tr("IN ORDER");
  }

  @Override
  public float measure(Canvas c) {
    layout(c);
    return w;
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    drawer.set(open >= 0 ? 1.0F : 0.0F);
    drawer.update(dt);
    if (open < 0 && drawer.get() <= 0.004F) {
      shown = -1;
    }
    layout(c);
    if (!ready) {
      c.text(
          FontStyle.SMALL,
          Lang.tr(NOTES[state]),
          x + 4.0F,
          centered(c, FontStyle.SMALL, y, COLLAPSED),
          t.grey());
      return;
    }
    yaw += SPIN * dt;
    hover(mouseX, mouseY);
    drawPreview(c, t);
    float unfold = clamp01(drawer.get());
    for (int event = 0; event < events; event++) {
      float top = y + rowTop[event];
      if (event > 0) {
        c.roundRect(x, top, w, 1.0F, 0.0F, t.hair());
      }
      boolean over = hoverEvent == event;
      c.text(
          FontStyle.LABEL,
          clips.eventName(event),
          x + 4.0F,
          centered(c, FontStyle.LABEL, top, ROW),
          over ? t.ink() : t.ink2());
      if (clips.assignedCount(event) == 0) {
        c.text(
            FontStyle.SMALL,
            clips.off(event) ? "off" : autoNotes[event],
            x + NAME,
            centered(c, FontStyle.SMALL, top, ROW),
            t.grey());
      }
      drawAdd(c, t, event, top);
      if (modeTop[event] >= 0.0F) {
        Segmented mode = modes[event];
        mode.place(x + w - mode.measure(c), y + modeTop[event]);
        mode.draw(c, t, mouseX, mouseY, dt);
      }
      if (event == shown && unfold > 0.004F) {
        drawDrawer(c, t, event, unfold);
      }
    }
    float chipText = centered(c, FontStyle.CAPTION, 0.0F, CHIP);
    for (int i = 0; i < chips; i++) {
      float cx = x + chipX[i];
      float cy = y + chipY[i];
      boolean over = hovered == (CHIP_HIT | i);
      Depth.raised(
          c,
          t,
          cx,
          cy,
          chipW[i],
          CHIP,
          CHIP * 0.5F,
          Theme.mix(t.surface2(), t.raised(), over ? 0.6F : 0.0F));
      c.text(FontStyle.CAPTION, shortNames[chipClip[i]], cx + CHIP_PAD, cy + chipText, t.ink());
      c.text(
          FontStyle.CAPTION,
          CROSS,
          cx + chipW[i] - CHIP_PAD - crossWidth,
          cy + chipText,
          over ? t.alert() : t.grey());
    }
  }

  private void drawPreview(Canvas c, Theme t) {
    Depth.inset(c, t, x, y, w, PREVIEW, PREVIEW_RADIUS, t.well());
    c.pushClip(x, y, w, PREVIEW);
    Stage.floor(c, t, x, y, w, PREVIEW);
    float floorY = y + PREVIEW * 0.84F;
    float size = PREVIEW - 16.0F;
    float shadowW = w * 0.3F;
    c.shadow(x + (w - shadowW) * 0.5F, floorY - 4.0F, shadowW, 8.0F, 4.0F, 14.0F, 0.0F, 0x66000000);
    int event = hoverEvent >= 0 ? hoverEvent : 0;
    int audition = hoverClip >= 0 ? Canvas.AUDITION_CLIP + hoverClip : event;
    c.model(
        clips.handle(),
        x + (w - size * 1.6F) * 0.5F,
        floorY + size * 0.1F - size,
        size * 1.6F,
        size,
        yaw,
        true,
        audition);
    c.pushClip(x, y, w - PREVIEW_INSET, PREVIEW);
    c.text(
        FontStyle.DOTS,
        hoverClip >= 0 ? titles[hoverClip] : eventTitles[event],
        x + PREVIEW_INSET,
        y + PREVIEW_INSET - 2.0F,
        t.ink());
    c.text(
        FontStyle.MONO,
        hoverClip >= 0 ? Lang.tr("CLIP") : meta(event),
        x + PREVIEW_INSET,
        y + PREVIEW_INSET + c.lineHeight(FontStyle.DOTS) + 2.0F,
        t.grey());
    c.popClip();
    c.popClip();
  }

  private void drawAdd(Canvas c, Theme t, int event, float top) {
    float ax = x + w - ADD;
    float ay = top + (ROW - ADD) * 0.5F;
    boolean over = hovered == (ADD_HIT | event);
    boolean active = open == event;
    Depth.raised(
        c,
        t,
        ax,
        ay,
        ADD,
        ADD,
        ADD * 0.5F,
        Theme.mix(t.surface2(), t.raised(), over || active ? 0.6F : 0.0F));
    float arm = 9.0F;
    float bar = 2.0F;
    float mx = ax + ADD * 0.5F;
    float my = ay + ADD * 0.5F;
    int ink = over || active ? t.ink() : t.grey();
    c.roundRect(mx - arm * 0.5F, my - bar * 0.5F, arm, bar, bar * 0.5F, ink);
    if (!active) {
      c.roundRect(mx - bar * 0.5F, my - arm * 0.5F, bar, arm, bar * 0.5F, ink);
    }
  }

  private void drawDrawer(Canvas c, Theme t, int event, float unfold) {
    float top = y + drawerTop;
    c.pushClip(x, top, w, drawerHeight * unfold);
    c.pushAlpha(unfold);
    Depth.inset(c, t, x, top + 2.0F, w, drawerHeight - 6.0F, DRAWER_RADIUS, t.well());
    float text = centered(c, FontStyle.CAPTION, 0.0F, CHIP);
    for (int option = 0; option < options; option++) {
      float ox = x + optionX[option];
      float oy = top + optionY[option];
      boolean on = option == 0 ? clips.off(event) : assigned(event, option - 1);
      boolean over = hovered == (OPTION_HIT | option);
      if (on) {
        Depth.raised(c, t, ox, oy, optionW[option], CHIP, CHIP * 0.5F, t.raised());
      } else {
        c.roundRect(
            ox,
            oy,
            optionW[option],
            CHIP,
            CHIP * 0.5F,
            Theme.mix(t.well(), t.surface2(), over ? 1.0F : 0.45F));
      }
      c.roundRect(
          ox + CHIP_PAD, oy + (CHIP - DOT) * 0.5F, DOT, DOT, DOT * 0.5F, on ? t.red() : t.dotOff());
      c.text(
          FontStyle.CAPTION,
          optionLabels[option],
          ox + CHIP_PAD + DOT + 6.0F,
          oy + text,
          on || over ? t.ink() : t.ink2());
    }
    c.popAlpha();
    c.popClip();
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    int target = hit(mx, my);
    if (target < 0 || target >= ROW_HIT) {
      return false;
    }
    if ((target & -KIND) == MODE_HIT) {
      modes[target & (KIND - 1)].mouseDown(mx, my);
    }
    pressed = target;
    return true;
  }

  @Override
  public void mouseUp(float mx, float my) {
    int was = pressed;
    pressed = -1;
    if (was < 0 || !ready) {
      return;
    }
    int kind = was & -KIND;
    int index = was & (KIND - 1);
    if (kind == MODE_HIT) {
      if (index < modes.length) {
        modes[index].mouseUp(mx, my);
      }
      return;
    }
    if (hit(mx, my) != was) {
      return;
    }
    if (kind == CHIP_HIT) {
      clips.toggle(chipEvent[index], chipClip[index]);
    } else if (kind == ADD_HIT) {
      toggleDrawer(index);
    } else if (index == 0) {
      clips.toggleOff(open);
    } else {
      clips.toggle(open, index - 1);
    }
  }

  @Override
  public boolean secondaryDown(float mx, float my) {
    int target = hit(mx, my);
    if (target < 0) {
      return false;
    }
    int kind = target & -KIND;
    int index = target & (KIND - 1);
    toggleDrawer(kind == CHIP_HIT ? chipEvent[index] : kind == OPTION_HIT ? open : index);
    return true;
  }

  private void toggleDrawer(int event) {
    open = open == event ? -1 : event;
    if (open >= 0) {
      shown = open;
    }
  }
}
