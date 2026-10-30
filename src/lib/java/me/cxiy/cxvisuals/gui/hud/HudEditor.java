package me.cxiy.cxvisuals.gui.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.ActionSetting;
import me.cxiy.cxvisuals.core.settings.Setting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Button;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Controls;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.Toggle;
import me.cxiy.cxvisuals.gui.Widget;
import me.cxiy.cxvisuals.modules.hud.HudElement;
import me.cxiy.cxvisuals.modules.hud.HudModule;
import me.cxiy.cxvisuals.modules.hud.Snap;
import me.cxiy.cxvisuals.modules.hud.Spacing;

public final class HudEditor {
  private enum Drag {
    NONE,
    MOVE,
    SCALE,
    CHIP,
    WIDGET,
    MARQUEE
  }

  public static final int KEY_RIGHT = 262;
  public static final int KEY_LEFT = 263;
  public static final int KEY_DOWN = 264;
  public static final int KEY_UP = 265;
  public static final int KEY_DELETE = 261;
  public static final int KEY_BACKSPACE = 259;

  private static final String WORDMARK = "HUD";
  private static final String[] TOUR_TITLES =
      Lang.keys("Move and resize", "Options and groups", "Add and remove");
  private static final String[][] TOUR_LINES = {
    Lang.keys(
        "Drag any element. Scroll over it or pull its corner",
        "knob to resize. Red lines show when it lines up."),
    Lang.keys(
        "Right-click an element for its options. Drag across",
        "empty space or Shift-click to move several at once."),
    Lang.keys(
        "Elements opens the tray: click a chip to show or hide,",
        "or drag it out to place it. Delete hides the selection.")
  };
  private static final String[] TOUR_STEPS = {"1 / 3", "2 / 3", "3 / 3"};
  private static final float TOUR_WIDTH = 372.0F;
  private static final float TOUR_HEIGHT = 124.0F;
  private static final float TOUR_INSET = 18.0F;
  private static final float RULER_LABEL = 16.0F;
  private static final float RULER_LINE = 0.55F;
  private static final float REDUCED_DT = 10.0F;
  private static final int FAR = 4096;
  private static final String[] DISTANCES = new String[FAR + 1];
  private static final String[] SECTIONS = Lang.keys("Info", "Game");
  private static final float BAR_HEIGHT = 46.0F;
  private static final float BAR_PAD = 8.0F;
  private static final float STACK_GAP = 10.0F;
  private static final float TRAY_WIDTH = 452.0F;
  private static final float TRAY_PAD = 14.0F;
  private static final float CHIP_HEIGHT = 28.0F;
  private static final float CHIP_GAP = 6.0F;
  private static final float SECTION_LABEL = 24.0F;
  private static final float PANEL_WIDTH = 356.0F;
  private static final float PANEL_HEAD = 50.0F;
  private static final float PANEL_ROW = 46.0F;
  private static final float PANEL_FOOT = 50.0F;
  private static final float PANEL_INSET = 16.0F;
  private static final float PANEL_RADIUS = 20.0F;
  private static final float PANEL_GAP = 14.0F;
  private static final float EDGE = 8.0F;
  private static final float OUTLINE = 4.0F;
  private static final float HANDLE = 12.0F;
  private static final float HANDLE_REACH = 10.0F;
  private static final float SLACK = 3.0F;
  private static final float DRAG_START = 3.0F;
  private static final float TAG_HEIGHT = 22.0F;
  private static final float NUDGE = 1.0F;
  private static final float HIDDEN = 0.004F;
  private static final int LINE = 0xFFFFFFFF;
  private static final int LINE_SHADE = 0x59000000;
  private static final int CHROME_SHADOW = 0x80000000;
  private static final int KNOB_SHADOW = 0x40000000;
  private static final float MARQUEE_FILL = 0.10F;
  private static final float MARQUEE_LINE = 0.65F;
  private static final float MARQUEE_RADIUS = 5.0F;

  private static final class Chip extends Widget {
    final HudElement element;
    final Anim lit;
    final Anim hover = Anim.hover(0.0F);
    float labelWidth = -1.0F;

    Chip(HudElement element) {
      this.element = element;
      this.lit = Anim.reveal(element.shown.get() ? 1.0F : 0.0F);
      this.h = CHIP_HEIGHT;
    }

    @Override
    public float measure(Canvas c) {
      if (labelWidth < 0.0F) {
        labelWidth = c.textWidth(FontStyle.LABEL, element.name());
        w = (float) Math.ceil(12.0F + labelWidth + 10.0F + HudPaint.DOT + 12.0F);
      }
      return w;
    }

    @Override
    public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
      lit.set(element.shown.get() ? 1.0F : 0.0F);
      hover.set(contains(mouseX, mouseY) ? 1.0F : 0.0F);
      lit.update(dt);
      hover.update(dt);
      Depth.raised(
          c, t, x, y, w, h, h * 0.5F, Theme.mix(t.surface2(), t.ink(), 0.08F * hover.get()));
      c.text(
          FontStyle.LABEL,
          element.name(),
          x + 12.0F,
          centered(c, FontStyle.LABEL, y, h),
          Theme.mix(t.grey(), t.ink(), lit.get()));
      HudPaint.dot(
          c,
          x + w - 12.0F - HudPaint.DOT,
          y + (h - HudPaint.DOT) * 0.5F,
          Theme.mix(t.dotOff(), t.red(), lit.get()));
    }
  }

  private static final class Panel {
    final HudElement element;
    final Toggle shown;
    final Button reset;
    final List<Setting<?>> settings = new ArrayList<>(4);
    final List<Widget> controls = new ArrayList<>(4);
    float x;
    float y;
    float h;
    boolean placed;
    double goodScale;

    Panel(HudElement element) {
      this.element = element;
      this.shown = new Toggle(element.shown);
      this.reset =
          new Button(
              new Settings()
                  .command("reset", "Reset", ActionSetting.Kind.QUIET, element::resetPlacement));
      add(element.scale);
      List<Setting<?>> options = element.options();
      for (int i = 0; i < options.size(); i++) {
        add(options.get(i));
      }
      this.h = PANEL_HEAD + PANEL_ROW * settings.size() + PANEL_FOOT;
    }

    private void add(Setting<?> setting) {
      Widget control = Controls.of(setting);
      if (control != null) {
        settings.add(setting);
        controls.add(control);
      }
    }

    boolean contains(float mx, float my) {
      return mx >= x && mx < x + PANEL_WIDTH && my >= y && my < y + h;
    }

    Widget press(float mx, float my) {
      if (shown.mouseDown(mx, my)) {
        return shown;
      }
      if (reset.mouseDown(mx, my)) {
        return reset;
      }
      for (int i = 0; i < controls.size(); i++) {
        if (controls.get(i).mouseDown(mx, my)) {
          return controls.get(i);
        }
      }
      return null;
    }
  }

  private final HudModule module;
  private final Supplier<Theme> theme;
  private final List<HudElement> elements;
  private final List<Chip> chips = new ArrayList<>(24);
  private final Panel[] panels;
  private final Anim[] hovers;
  private final boolean[] picked;
  private final boolean[] kept;
  private final Snap snap = new Snap();
  private final Button trayButton;
  private final Button doneButton;
  private final Button helpButton;
  private final Button skipButton;
  private final Button nextButton;
  private final Button finishButton;
  private final Anim tourFade = Anim.reveal(0.0F);
  private final Anim intro = Anim.move(0.0F);
  private final Anim trayFade = Anim.reveal(0.0F);
  private final Anim panelFade = Anim.state(0.0F);

  private float screenW = 640.0F;
  private float screenH = 360.0F;
  private float barX;
  private float barY;
  private float barW;
  private float trayX;
  private float trayY;
  private float trayH;
  private float wordmarkWidth = -1.0F;
  private float tourX;
  private float tourY;
  private int tourStep = -1;
  private int tourShown;
  private HudElement selected;
  private Panel inspected;
  private Panel fading;
  private Chip pressedChip;
  private Widget active;
  private Drag drag = Drag.NONE;
  private float grabX;
  private float grabY;
  private float pressX;
  private float pressY;
  private boolean moved;
  private float bandX;
  private float bandY;
  private float bandToX;
  private float bandToY;
  private String groupLabel = "";
  private int groupCount;
  private boolean trayOpen;
  private boolean done;

  public HudEditor(HudModule module, Supplier<Theme> theme) {
    this.module = module;
    this.theme = theme;
    this.elements = module.elements();
    this.panels = new Panel[elements.size()];
    this.hovers = new Anim[elements.size()];
    this.picked = new boolean[elements.size()];
    this.kept = new boolean[elements.size()];
    for (int i = 0; i < hovers.length; i++) {
      hovers[i] = Anim.hover(0.0F);
      chips.add(new Chip(elements.get(i)));
    }
    Settings commands = new Settings();
    this.trayButton =
        new Button(
            commands.command(
                "elements", "Elements", ActionSetting.Kind.QUIET, () -> trayOpen = !trayOpen));
    this.doneButton =
        new Button(commands.command("done", "Done", ActionSetting.Kind.PRIMARY, () -> done = true));
    this.helpButton =
        new Button(commands.command("help", "Help", ActionSetting.Kind.QUIET, () -> tourStep = 0));
    this.skipButton =
        new Button(commands.command("skip", "Skip", ActionSetting.Kind.QUIET, this::endTour));
    this.nextButton =
        new Button(commands.command("next", "Next", ActionSetting.Kind.PRIMARY, () -> tourStep++));
    this.finishButton =
        new Button(commands.command("finish", "Got it", ActionSetting.Kind.PRIMARY, this::endTour));
  }

  public void open() {
    done = false;
    drag = Drag.NONE;
    active = null;
    pressedChip = null;
    clearPicks();
    inspected = null;
    fading = null;
    trayOpen = false;
    snap.clear();
    intro.snap(0.0F);
    intro.set(1.0F);
    trayFade.snap(0.0F);
    panelFade.snap(0.0F);
    tourFade.snap(0.0F);
    tourStep = module.toured.get() ? -1 : 0;
  }

  private void endTour() {
    tourStep = -1;
    module.toured.set(true);
  }

  public int tourStep() {
    return tourStep;
  }

  public float tourNextX() {
    Button button = tourStep == TOUR_TITLES.length - 1 ? finishButton : nextButton;
    return button.x + button.w * 0.5F;
  }

  public float tourNextY() {
    return nextButton.y + nextButton.h * 0.5F;
  }

  private boolean inTour(float mx, float my) {
    return tourStep >= 0
        && mx >= tourX
        && mx < tourX + TOUR_WIDTH
        && my >= tourY
        && my < tourY + TOUR_HEIGHT;
  }

  public boolean closed() {
    return done;
  }

  public void resize(float width, float height) {
    screenW = width;
    screenH = height;
  }

  public HudElement selected() {
    return selected;
  }

  public int selectedCount() {
    int count = 0;
    for (int i = 0; i < picked.length; i++) {
      if (picked[i]) {
        count++;
      }
    }
    return count;
  }

  public boolean selects(HudElement element) {
    return picked[elements.indexOf(element)];
  }

  private void clearPicks() {
    for (int i = 0; i < picked.length; i++) {
      picked[i] = false;
    }
    selected = null;
  }

  private void pickOnly(HudElement element) {
    clearPicks();
    picked[elements.indexOf(element)] = true;
    selected = element;
  }

  private void dropHidden() {
    for (int i = 0; i < picked.length; i++) {
      if (picked[i] && !elements.get(i).shown.get()) {
        picked[i] = false;
      }
    }
    if (selected != null && !picked[elements.indexOf(selected)]) {
      selected = null;
      for (int i = 0; i < picked.length; i++) {
        if (picked[i]) {
          selected = elements.get(i);
          break;
        }
      }
    }
  }

  public boolean inspecting() {
    return inspected != null;
  }

  public boolean trayOpen() {
    return trayOpen;
  }

  public float centreX(HudElement element) {
    return element.left + element.w * factor(element) * 0.5F;
  }

  public float centreY(HudElement element) {
    return element.top + element.h * factor(element) * 0.5F;
  }

  public float handleX(HudElement element) {
    return element.left + element.w * factor(element);
  }

  public float handleY(HudElement element) {
    return element.top + element.h * factor(element);
  }

  public float trayButtonX() {
    return trayButton.x + trayButton.w * 0.5F;
  }

  public float trayButtonY() {
    return trayButton.y + trayButton.h * 0.5F;
  }

  public float chipX(HudElement element) {
    Chip chip = chips.get(elements.indexOf(element));
    return chip.x + chip.w * 0.5F;
  }

  public float chipY(HudElement element) {
    Chip chip = chips.get(elements.indexOf(element));
    return chip.y + chip.h * 0.5F;
  }

  private float factor(HudElement element) {
    return element.factor(module.scale());
  }

  private Panel panel(HudElement element) {
    int index = elements.indexOf(element);
    Panel panel = panels[index];
    if (panel == null) {
      panel = new Panel(element);
      panels[index] = panel;
    }
    return panel;
  }

  private HudElement hit(float mx, float my) {
    HudElement best = null;
    float bestArea = Float.MAX_VALUE;
    for (int i = 0; i < elements.size(); i++) {
      HudElement element = elements.get(i);
      if (!element.shown.get() || element.w <= 0.0F) {
        continue;
      }
      float factor = factor(element);
      float width = element.w * factor;
      float height = element.h * factor;
      if (mx < element.left - SLACK
          || mx >= element.left + width + SLACK
          || my < element.top - SLACK
          || my >= element.top + height + SLACK) {
        continue;
      }
      float area = width * height;
      if (area < bestArea) {
        bestArea = area;
        best = element;
      }
    }
    return best;
  }

  private boolean onHandle(HudElement element, float mx, float my) {
    return Math.abs(mx - handleX(element)) <= HANDLE_REACH
        && Math.abs(my - handleY(element)) <= HANDLE_REACH;
  }

  private boolean inTray(float mx, float my) {
    return trayOpen && mx >= trayX && mx < trayX + TRAY_WIDTH && my >= trayY && my < trayY + trayH;
  }

  private boolean inBar(float mx, float my) {
    return !inTray(mx, my) && mx >= barX && mx < barX + barW && my >= barY && my < barY + BAR_HEIGHT;
  }

  public void draw(Canvas c, float mx, float my, float dt) {
    Theme t = theme.get();
    if (module.reduce.get()) {
      dt = REDUCED_DT;
    }
    intro.update(dt);
    float shown = Math.max(0.0F, Math.min(1.0F, intro.get()));
    boolean dragging = drag == Drag.MOVE || drag == Drag.SCALE;
    dropHidden();
    int count = selectedCount();

    if (drag == Drag.MOVE) {
      if (!Float.isNaN(snap.guideX)) {
        c.roundRect(snap.guideX - 0.5F, 0.0F, 1.0F, screenH, 0.0F, t.red());
      }
      if (!Float.isNaN(snap.guideY)) {
        c.roundRect(0.0F, snap.guideY - 0.5F, screenW, 1.0F, 0.0F, t.red());
      }
    }

    boolean overChrome =
        inBar(mx, my)
            || inTray(mx, my)
            || inTour(mx, my)
            || (inspected != null && inspected.contains(mx, my));
    HudElement hovered =
        dragging ? selected : overChrome || drag == Drag.MARQUEE ? null : hit(mx, my);
    c.pushAlpha(shown);
    for (int i = 0; i < elements.size(); i++) {
      HudElement element = elements.get(i);
      Anim hover = hovers[i];
      boolean lit = element == hovered || picked[i];
      hover.set(lit && element.shown.get() ? 1.0F : 0.0F);
      hover.update(dt);
      float amount = hover.get();
      if (amount <= HIDDEN || element.w <= 0.0F) {
        continue;
      }
      float factor = factor(element);
      float x = element.left - OUTLINE;
      float y = element.top - OUTLINE;
      float width = element.w * factor + OUTLINE * 2.0F;
      float height = element.h * factor + OUTLINE * 2.0F;
      float radius = Math.min(element.radius() * factor + OUTLINE, Math.min(width, height) * 0.5F);
      float strength = picked[i] ? amount : amount * 0.55F;
      c.roundRectBorder(
          x - 1.0F,
          y - 1.0F,
          width + 2.0F,
          height + 2.0F,
          radius + 1.0F,
          1.0F,
          Theme.withAlpha(LINE_SHADE, strength));
      c.roundRectBorder(x, y, width, height, radius, 1.0F, Theme.withAlpha(LINE, strength));
    }
    if (count == 1 && selected.w > 0.0F) {
      drawSelection(c, t);
    } else if (count > 1) {
      drawGroup(c, t, count);
    }
    if (drag == Drag.MARQUEE) {
      float x = Math.min(bandX, bandToX);
      float y = Math.min(bandY, bandToY);
      float width = Math.abs(bandToX - bandX);
      float height = Math.abs(bandToY - bandY);
      float radius = Math.min(MARQUEE_RADIUS, Math.min(width, height) * 0.5F);
      c.roundRect(x, y, width, height, radius, Theme.withAlpha(LINE, MARQUEE_FILL));
      c.roundRectBorder(x, y, width, height, radius, 1.0F, Theme.withAlpha(LINE, MARQUEE_LINE));
    }

    if (drag == Drag.MOVE && moved) {
      drawRulers(c, t);
    }
    drawBar(c, t, mx, my, dt);
    drawTour(c, t, mx, my, dt);
    drawTray(c, t, mx, my, dt);
    drawPanel(c, t, mx, my, dt);
    c.popAlpha();
  }

  private void drawSelection(Canvas c, Theme t) {
    float factor = factor(selected);
    float width = selected.w * factor;
    float height = selected.h * factor;
    float knobX = selected.left + width + OUTLINE - HANDLE * 0.5F - 2.0F;
    float knobY = selected.top + height + OUTLINE - HANDLE * 0.5F - 2.0F;
    c.shadow(knobX, knobY, HANDLE, HANDLE, HANDLE * 0.5F, 4.0F, 1.0F, KNOB_SHADOW);
    Depth.knob(c, knobX, knobY, HANDLE, HANDLE, HANDLE * 0.5F, LINE);

    String name = selected.name();
    String size = selected.scale.format();
    float nameWidth = c.textWidth(FontStyle.CAPTION, name);
    float tagWidth =
        (float) Math.ceil(10.0F + nameWidth + 8.0F + c.textWidth(FontStyle.MONO, size) + 10.0F);
    float tagX = Math.max(EDGE, Math.min(screenW - EDGE - tagWidth, selected.left - OUTLINE));
    float tagY = selected.top - OUTLINE - 6.0F - TAG_HEIGHT;
    if (tagY < EDGE) {
      tagY = selected.top + height + OUTLINE + 6.0F;
    }
    c.roundRect(
        tagX, tagY, tagWidth, TAG_HEIGHT, TAG_HEIGHT * 0.5F, 0xFF000000 | (t.paper() & 0x00FFFFFF));
    c.roundRectBorder(tagX, tagY, tagWidth, TAG_HEIGHT, TAG_HEIGHT * 0.5F, 1.0F, t.hair());
    c.text(
        FontStyle.CAPTION,
        name,
        tagX + 10.0F,
        HudPaint.centred(c, FontStyle.CAPTION, tagY, TAG_HEIGHT),
        t.ink());
    c.text(
        FontStyle.MONO,
        size,
        tagX + 10.0F + nameWidth + 8.0F,
        HudPaint.centred(c, FontStyle.MONO, tagY, TAG_HEIGHT),
        t.grey());
  }

  private void drawGroup(Canvas c, Theme t, int count) {
    if (count != groupCount) {
      groupCount = count;
      groupLabel = count + " " + Lang.tr("selected");
    }
    float left = Float.MAX_VALUE;
    float top = Float.MAX_VALUE;
    float bottom = 0.0F;
    for (int i = 0; i < picked.length; i++) {
      if (!picked[i]) {
        continue;
      }
      HudElement element = elements.get(i);
      left = Math.min(left, element.left);
      top = Math.min(top, element.top);
      bottom = Math.max(bottom, element.top + element.h * factor(element));
    }
    float tagWidth = (float) Math.ceil(c.textWidth(FontStyle.CAPTION, groupLabel) + 20.0F);
    float tagX = Math.max(EDGE, Math.min(screenW - EDGE - tagWidth, left - OUTLINE));
    float tagY = top - OUTLINE - 6.0F - TAG_HEIGHT;
    if (tagY < EDGE) {
      tagY = bottom + OUTLINE + 6.0F;
    }
    c.roundRect(
        tagX, tagY, tagWidth, TAG_HEIGHT, TAG_HEIGHT * 0.5F, 0xFF000000 | (t.paper() & 0x00FFFFFF));
    c.roundRectBorder(tagX, tagY, tagWidth, TAG_HEIGHT, TAG_HEIGHT * 0.5F, 1.0F, t.hair());
    c.text(
        FontStyle.CAPTION,
        groupLabel,
        tagX + 10.0F,
        HudPaint.centred(c, FontStyle.CAPTION, tagY, TAG_HEIGHT),
        t.ink());
  }

  private static void chrome(Canvas c, Theme t, float x, float y, float w, float h, float radius) {
    c.shadow(x, y, w, h, radius, 30.0F, 12.0F, CHROME_SHADOW);
    if (t.glass()) {
      c.glass(x, y, w, h, radius, t.paper());
    } else {
      c.roundRect(x, y, w, h, radius, t.paper());
    }
    c.roundRectBorder(x, y, w, h, radius, 1.0F, t.hair());
    c.rim(x, y, w, h, radius, 1.0F, Math.min(h, radius * 1.4F), Depth.rimColor(t));
  }

  private void drawBar(Canvas c, Theme t, float mx, float my, float dt) {
    if (wordmarkWidth < 0.0F) {
      wordmarkWidth = c.textWidth(FontStyle.DOTS, WORDMARK);
    }
    float trayWidth = trayButton.measure(c);
    float helpWidth = helpButton.measure(c);
    float doneWidth = doneButton.measure(c);
    barW =
        (float)
            Math.ceil(
                16.0F
                    + wordmarkWidth
                    + 14.0F
                    + trayWidth
                    + BAR_PAD
                    + helpWidth
                    + BAR_PAD
                    + doneWidth
                    + BAR_PAD);
    barX = (screenW - barW) * 0.5F;
    barY = (screenH - BAR_HEIGHT) * 0.5F;
    chrome(c, t, barX, barY, barW, BAR_HEIGHT, BAR_HEIGHT * 0.5F);
    c.text(
        FontStyle.DOTS,
        WORDMARK,
        barX + 16.0F,
        HudPaint.centred(c, FontStyle.DOTS, barY, BAR_HEIGHT),
        t.ink());
    float buttonY = barY + (BAR_HEIGHT - trayButton.h) * 0.5F;
    trayButton.place(barX + 16.0F + wordmarkWidth + 14.0F, buttonY);
    helpButton.place(trayButton.x + trayWidth + BAR_PAD, buttonY);
    doneButton.place(helpButton.x + helpWidth + BAR_PAD, buttonY);
    float barMx = inTray(mx, my) ? -1.0F : mx;
    float barMy = inTray(mx, my) ? -1.0F : my;
    trayButton.draw(c, t, barMx, barMy, dt);
    helpButton.draw(c, t, barMx, barMy, dt);
    doneButton.draw(c, t, barMx, barMy, dt);
    if (tourStep == TOUR_TITLES.length - 1) {
      c.roundRectBorder(
          trayButton.x - 2.0F,
          trayButton.y - 2.0F,
          trayButton.w + 4.0F,
          trayButton.h + 4.0F,
          trayButton.h * 0.5F + 2.0F,
          1.5F,
          t.red());
    }
  }

  private void drawTour(Canvas c, Theme t, float mx, float my, float dt) {
    if (tourStep >= TOUR_TITLES.length) {
      endTour();
    }
    if (tourStep >= 0) {
      tourShown = tourStep;
    }
    tourFade.set(tourStep >= 0 ? 1.0F : 0.0F);
    tourFade.update(dt);
    float fade = Math.max(0.0F, Math.min(1.0F, tourFade.get()));
    tourX = (screenW - TOUR_WIDTH) * 0.5F;
    tourY = Math.max(EDGE, barY - STACK_GAP - TOUR_HEIGHT);
    if (fade <= HIDDEN) {
      return;
    }
    float top = tourY + (1.0F - fade) * 8.0F;
    c.pushAlpha(fade);
    chrome(c, t, tourX, top, TOUR_WIDTH, TOUR_HEIGHT, PANEL_RADIUS);
    c.text(
        FontStyle.ROW, Lang.tr(TOUR_TITLES[tourShown]), tourX + TOUR_INSET, top + 14.0F, t.ink());
    float line = c.lineHeight(FontStyle.SMALL) + 1.0F;
    String[] lines = TOUR_LINES[tourShown];
    for (int i = 0; i < lines.length; i++) {
      c.text(
          FontStyle.SMALL, Lang.tr(lines[i]), tourX + TOUR_INSET, top + 38.0F + i * line, t.grey());
    }
    boolean last = tourShown == TOUR_TITLES.length - 1;
    Button forward = last ? finishButton : nextButton;
    float buttonY = top + TOUR_HEIGHT - 12.0F - forward.h;
    c.text(
        FontStyle.MONO,
        TOUR_STEPS[tourShown],
        tourX + TOUR_INSET,
        HudPaint.centred(c, FontStyle.MONO, buttonY, forward.h),
        t.grey());
    float forwardWidth = forward.measure(c);
    forward.place(tourX + TOUR_WIDTH - 12.0F - forwardWidth, buttonY);
    nextButton.y = buttonY;
    forward.draw(c, t, mx, my, dt);
    if (!last) {
      float skipWidth = skipButton.measure(c);
      skipButton.place(forward.x - BAR_PAD - skipWidth, buttonY);
      skipButton.draw(c, t, mx, my, dt);
    }
    c.popAlpha();
  }

  private void drawRulers(Canvas c, Theme t) {
    float left = Float.MAX_VALUE;
    float top = Float.MAX_VALUE;
    float right = -Float.MAX_VALUE;
    float bottom = -Float.MAX_VALUE;
    for (int i = 0; i < picked.length; i++) {
      if (!picked[i]) {
        continue;
      }
      HudElement element = elements.get(i);
      float factor = factor(element);
      left = Math.min(left, element.left);
      top = Math.min(top, element.top);
      right = Math.max(right, element.left + element.w * factor);
      bottom = Math.max(bottom, element.top + element.h * factor);
    }
    if (left > right) {
      return;
    }
    float nearLeft = 0.0F;
    float nearRight = screenW;
    float nearTop = 0.0F;
    float nearBottom = screenH;
    for (int i = 0; i < elements.size(); i++) {
      HudElement other = elements.get(i);
      if (picked[i] || !Spacing.solid(other)) {
        continue;
      }
      float factor = factor(other);
      float otherRight = other.left + other.w * factor;
      float otherBottom = other.top + other.h * factor;
      if (other.top < bottom && otherBottom > top) {
        if (otherRight <= left) {
          nearLeft = Math.max(nearLeft, otherRight);
        } else if (other.left >= right) {
          nearRight = Math.min(nearRight, other.left);
        }
      }
      if (other.left < right && otherRight > left) {
        if (otherBottom <= top) {
          nearTop = Math.max(nearTop, otherBottom);
        } else if (other.top >= bottom) {
          nearBottom = Math.min(nearBottom, other.top);
        }
      }
    }
    float middleX = (left + right) * 0.5F;
    float middleY = (top + bottom) * 0.5F;
    ruler(c, t, nearLeft, middleY, left - OUTLINE, middleY);
    ruler(c, t, right + OUTLINE, middleY, nearRight, middleY);
    ruler(c, t, middleX, nearTop, middleX, top - OUTLINE);
    ruler(c, t, middleX, bottom + OUTLINE, middleX, nearBottom);
  }

  private void ruler(Canvas c, Theme t, float fromX, float fromY, float toX, float toY) {
    boolean across = fromY == toY;
    float length = across ? toX - fromX : toY - fromY;
    float distance = length + OUTLINE;
    if (distance < 1.0F) {
      return;
    }
    int line = Theme.withAlpha(LINE, RULER_LINE);
    if (length > 0.0F) {
      if (across) {
        c.roundRect(fromX, fromY - 0.5F, length, 1.0F, 0.0F, line);
      } else {
        c.roundRect(fromX - 0.5F, fromY, 1.0F, length, 0.0F, line);
      }
    }
    String label = distance(Math.round(distance));
    float width = (float) Math.ceil(c.textWidth(FontStyle.MONO, label) + 10.0F);
    float x = (fromX + toX) * 0.5F - width * 0.5F;
    float y = (fromY + toY) * 0.5F - RULER_LABEL * 0.5F;
    x = Math.max(EDGE * 0.5F, Math.min(screenW - EDGE * 0.5F - width, x));
    y = Math.max(EDGE * 0.5F, Math.min(screenH - EDGE * 0.5F - RULER_LABEL, y));
    c.roundRect(
        x, y, width, RULER_LABEL, RULER_LABEL * 0.5F, 0xFF000000 | (t.paper() & 0x00FFFFFF));
    c.text(
        FontStyle.MONO,
        label,
        x + 5.0F,
        HudPaint.centred(c, FontStyle.MONO, y, RULER_LABEL),
        t.ink());
  }

  private static String distance(int units) {
    int index = Math.max(0, Math.min(FAR, units));
    String cached = DISTANCES[index];
    if (cached == null) {
      cached = Integer.toString(index);
      DISTANCES[index] = cached;
    }
    return cached;
  }

  private void drawTray(Canvas c, Theme t, float mx, float my, float dt) {
    trayFade.set(trayOpen ? 1.0F : 0.0F);
    trayFade.update(dt);
    float fade = Math.max(0.0F, Math.min(1.0F, trayFade.get()));
    trayX = (screenW - TRAY_WIDTH) * 0.5F;
    trayY = Math.min(barY + BAR_HEIGHT + STACK_GAP, screenH - EDGE - trayH);

    float cursorY = trayY + TRAY_PAD;
    float inner = TRAY_WIDTH - TRAY_PAD * 2.0F;
    for (int section = 0; section < SECTIONS.length; section++) {
      cursorY += SECTION_LABEL;
      float cursorX = 0.0F;
      for (int i = 0; i < chips.size(); i++) {
        Chip chip = chips.get(i);
        if (chip.element.category().ordinal() != section) {
          continue;
        }
        float width = chip.measure(c);
        if (cursorX > 0.0F && cursorX + width > inner) {
          cursorX = 0.0F;
          cursorY += CHIP_HEIGHT + CHIP_GAP;
        }
        chip.place(trayX + TRAY_PAD + cursorX, cursorY);
        cursorX += width + CHIP_GAP;
      }
      cursorY += CHIP_HEIGHT + CHIP_GAP;
    }
    trayH = cursorY - trayY + TRAY_PAD - CHIP_GAP;
    if (fade <= HIDDEN) {
      return;
    }

    float rise = (1.0F - fade) * 8.0F;
    c.pushAlpha(fade);
    chrome(c, t, trayX, trayY - rise, TRAY_WIDTH, trayH, PANEL_RADIUS);
    float captionLine = c.lineHeight(FontStyle.CAPTION);
    boolean[] labeled = new boolean[SECTIONS.length];
    for (int i = 0; i < chips.size(); i++) {
      Chip chip = chips.get(i);
      int section = chip.element.category().ordinal();
      if (!labeled[section]) {
        labeled[section] = true;
        c.text(
            FontStyle.CAPTION,
            Lang.tr(SECTIONS[section]),
            trayX + TRAY_PAD + 2.0F,
            chip.y - rise - captionLine - 5.0F,
            t.grey());
      }
      float chipY = chip.y;
      chip.y = chipY - rise;
      chip.draw(c, t, trayOpen ? mx : -1.0F, trayOpen ? my : -1.0F, dt);
      chip.y = chipY;
    }
    c.popAlpha();
  }

  private void drawPanel(Canvas c, Theme t, float mx, float my, float dt) {
    if (inspected != null) {
      fading = inspected;
    }
    panelFade.set(inspected != null ? 1.0F : 0.0F);
    panelFade.update(dt);
    float fade = Math.max(0.0F, Math.min(1.0F, panelFade.get()));
    Panel panel = fading;
    if (panel == null || fade <= HIDDEN) {
      return;
    }
    HudElement element = panel.element;
    if (!panel.placed) {
      panel.placed = true;
      float x = element.left + element.w * factor(element) + PANEL_GAP;
      if (x + PANEL_WIDTH > screenW - EDGE) {
        x = element.left - PANEL_GAP - PANEL_WIDTH;
      }
      panel.x = Math.max(EDGE, Math.min(screenW - EDGE - PANEL_WIDTH, x));
      panel.y = Math.max(EDGE, Math.min(screenH - EDGE - panel.h, element.top));
    }

    float left = panel.x;
    float top = panel.y;
    c.pushAlpha(fade);
    c.pushTransform(0.96F + 0.04F * fade, left + PANEL_WIDTH * 0.5F, top + panel.h * 0.5F);
    chrome(c, t, left, top, PANEL_WIDTH, panel.h, PANEL_RADIUS);
    c.text(
        FontStyle.ROW,
        element.name(),
        left + PANEL_INSET,
        HudPaint.centred(c, FontStyle.ROW, top, PANEL_HEAD),
        t.ink());
    panel.shown.place(
        left + PANEL_WIDTH - PANEL_INSET - panel.shown.w,
        top + (PANEL_HEAD - panel.shown.h) * 0.5F);
    panel.shown.draw(c, t, mx, my, dt);

    float rowLine = c.lineHeight(FontStyle.ROW);
    for (int i = 0; i < panel.controls.size(); i++) {
      float rowTop = top + PANEL_HEAD + i * PANEL_ROW;
      c.roundRect(left + PANEL_INSET, rowTop, PANEL_WIDTH - PANEL_INSET, 1.0F, 0.0F, t.hair());
      c.text(
          FontStyle.ROW,
          panel.settings.get(i).title(),
          left + PANEL_INSET,
          rowTop + (PANEL_ROW - rowLine) * 0.5F,
          t.ink());
      Widget control = panel.controls.get(i);
      float controlWidth = control.measure(c);
      control.place(
          left + PANEL_WIDTH - PANEL_INSET - controlWidth, rowTop + (PANEL_ROW - control.h) * 0.5F);
      control.draw(c, t, mx, my, dt);
    }

    float footTop = top + panel.h - PANEL_FOOT;
    c.roundRect(left + PANEL_INSET, footTop, PANEL_WIDTH - PANEL_INSET, 1.0F, 0.0F, t.hair());
    c.text(
        FontStyle.SMALL,
        Lang.tr("Position and size"),
        left + PANEL_INSET,
        HudPaint.centred(c, FontStyle.SMALL, footTop, PANEL_FOOT),
        t.grey());
    float resetWidth = panel.reset.measure(c);
    panel.reset.place(
        left + PANEL_WIDTH - PANEL_INSET - resetWidth,
        footTop + (PANEL_FOOT - panel.reset.h) * 0.5F);
    panel.reset.draw(c, t, mx, my, dt);
    c.popTransform();
    c.popAlpha();
  }

  private void inspect(HudElement element) {
    Panel panel = panel(element);
    panel.placed = false;
    panel.goodScale = element.scale.get();
    inspected = panel;
  }

  private void guardScale() {
    if (inspected == null) {
      return;
    }
    HudElement element = inspected.element;
    double now = element.scale.get();
    if (now == inspected.goodScale) {
      return;
    }
    element.resolve(screenW, screenH, factor(element));
    if (now > inspected.goodScale
        && Spacing.crowded(elements, element, module.gap(), module.scale())) {
      element.scale.set(inspected.goodScale);
      element.resolve(screenW, screenH, factor(element));
    } else {
      inspected.goodScale = now;
    }
  }

  private void rescale(HudElement element, double percent) {
    double before = element.scale.get();
    element.resize(percent, screenW, screenH, module.scale());
    if (element.scale.get() > before
        && Spacing.crowded(elements, element, module.gap(), module.scale())) {
      element.resize(before, screenW, screenH, module.scale());
    }
  }

  private void moveGroup(float dx, float dy) {
    float left = Float.MAX_VALUE;
    float top = Float.MAX_VALUE;
    float right = -Float.MAX_VALUE;
    float bottom = -Float.MAX_VALUE;
    for (int i = 0; i < picked.length; i++) {
      if (!picked[i]) {
        continue;
      }
      HudElement element = elements.get(i);
      float factor = factor(element);
      left = Math.min(left, element.left);
      top = Math.min(top, element.top);
      right = Math.max(right, element.left + element.w * factor);
      bottom = Math.max(bottom, element.top + element.h * factor);
    }
    dx = Math.max(-left, Math.min(screenW - right, dx));
    dy = Math.max(-top, Math.min(screenH - bottom, dy));
    float gap = module.gap();
    float global = module.scale();
    if (!Spacing.free(elements, picked, dx, dy, gap, global)) {
      dx = Spacing.reach(elements, picked, dx, dy, true, 0.0F, gap, global);
      dy = Spacing.reach(elements, picked, dx, dy, false, dx, gap, global);
    }
    if (dx == 0.0F && dy == 0.0F) {
      return;
    }
    for (int i = 0; i < picked.length; i++) {
      if (picked[i]) {
        HudElement element = elements.get(i);
        element.moveTo(element.left + dx, element.top + dy, screenW, screenH, factor(element));
      }
    }
  }

  public boolean mouseDown(float mx, float my) {
    return mouseDown(mx, my, false);
  }

  public boolean mouseDown(float mx, float my, boolean additive) {
    if (inspected != null && inspected.contains(mx, my)) {
      active = inspected.press(mx, my);
      drag = Drag.WIDGET;
      guardScale();
      return true;
    }
    if (inTour(mx, my)) {
      Button forward = tourStep == TOUR_TITLES.length - 1 ? finishButton : nextButton;
      active =
          forward.mouseDown(mx, my) ? forward : skipButton.mouseDown(mx, my) ? skipButton : null;
      drag = Drag.WIDGET;
      return true;
    }
    if (inBar(mx, my)) {
      active =
          trayButton.mouseDown(mx, my)
              ? trayButton
              : helpButton.mouseDown(mx, my)
                  ? helpButton
                  : doneButton.mouseDown(mx, my) ? doneButton : null;
      drag = Drag.WIDGET;
      inspected = null;
      return true;
    }
    if (inTray(mx, my)) {
      pressedChip = null;
      for (int i = 0; i < chips.size(); i++) {
        if (chips.get(i).contains(mx, my)) {
          pressedChip = chips.get(i);
          break;
        }
      }
      pressX = mx;
      pressY = my;
      drag = pressedChip == null ? Drag.NONE : Drag.CHIP;
      inspected = null;
      return true;
    }
    trayOpen = false;
    if (!additive && selectedCount() == 1 && onHandle(selected, mx, my)) {
      drag = Drag.SCALE;
      inspected = null;
      return true;
    }
    HudElement target = hit(mx, my);
    pressX = mx;
    pressY = my;
    if (target == null) {
      inspected = null;
      if (!additive) {
        clearPicks();
      }
      System.arraycopy(picked, 0, kept, 0, picked.length);
      bandX = mx;
      bandY = my;
      bandToX = mx;
      bandToY = my;
      drag = Drag.MARQUEE;
      return false;
    }
    int index = elements.indexOf(target);
    if (additive) {
      inspected = null;
      picked[index] = !picked[index];
      selected = picked[index] ? target : null;
      dropHidden();
      return true;
    }
    if (inspected != null && inspected.element != target) {
      inspected = null;
    }
    if (!picked[index]) {
      pickOnly(target);
    }
    selected = target;
    drag = Drag.MOVE;
    moved = false;
    grabX = mx - target.left;
    grabY = my - target.top;
    return true;
  }

  public boolean secondaryDown(float mx, float my) {
    if ((inspected != null && inspected.contains(mx, my))
        || inBar(mx, my)
        || inTray(mx, my)
        || inTour(mx, my)) {
      return true;
    }
    HudElement target = hit(mx, my);
    if (target == null) {
      inspected = null;
      return false;
    }
    trayOpen = false;
    boolean same = inspected != null && inspected.element == target;
    pickOnly(target);
    if (same) {
      inspected = null;
    } else {
      inspect(target);
    }
    return true;
  }

  public void mouseDrag(float mx, float my) {
    switch (drag) {
      case WIDGET -> {
        if (active != null) {
          active.mouseDrag(mx, my);
          guardScale();
        }
      }
      case CHIP -> {
        if (Math.abs(mx - pressX) + Math.abs(my - pressY) > DRAG_START * 2.0F) {
          HudElement element = pressedChip.element;
          element.shown.set(true);
          pickOnly(element);
          pressedChip = null;
          trayOpen = false;
          float factor = factor(element);
          grabX = element.w * factor * 0.5F;
          grabY = element.h * factor * 0.5F;
          moved = true;
          drag = Drag.MOVE;
          move(mx, my);
        }
      }
      case MOVE -> {
        if (!moved && Math.abs(mx - pressX) + Math.abs(my - pressY) < DRAG_START) {
          return;
        }
        moved = true;
        inspected = null;
        move(mx, my);
      }
      case SCALE -> {
        float base = factor(selected) / (float) (selected.scale.get() / 100.0);
        float across = (mx - selected.left) / (selected.w * base);
        float down = (my - selected.top) / (selected.h * base);
        rescale(selected, Math.max(across, down) * 100.0);
      }
      case MARQUEE -> {
        bandToX = mx;
        bandToY = my;
        float left = Math.min(bandX, mx);
        float top = Math.min(bandY, my);
        float right = Math.max(bandX, mx);
        float bottom = Math.max(bandY, my);
        selected = null;
        for (int i = 0; i < picked.length; i++) {
          HudElement element = elements.get(i);
          boolean inside = false;
          if (element.shown.get() && element.w > 0.0F) {
            float factor = factor(element);
            inside =
                element.left < right
                    && element.left + element.w * factor > left
                    && element.top < bottom
                    && element.top + element.h * factor > top;
          }
          picked[i] = kept[i] || inside;
          if (picked[i] && selected == null) {
            selected = element;
          }
        }
      }
      default -> {}
    }
  }

  private void move(float mx, float my) {
    float factor = factor(selected);
    float left = mx - grabX;
    float top = my - grabY;
    boolean snapping = module.snap.get();
    if (snapping) {
      snap.solve(
          picked,
          left,
          top,
          selected.w * factor,
          selected.h * factor,
          screenW,
          screenH,
          elements,
          module.scale(),
          module.gap());
      left = snap.x;
      top = snap.y;
    } else {
      snap.clear();
    }
    moveGroup(left - selected.left, top - selected.top);
    if (snapping) {
      if (Math.abs(selected.left - snap.x) > 0.5F) {
        snap.guideX = Float.NaN;
      }
      if (Math.abs(selected.top - snap.y) > 0.5F) {
        snap.guideY = Float.NaN;
      }
    }
  }

  public void mouseUp(float mx, float my) {
    Drag ended = drag;
    drag = Drag.NONE;
    snap.clear();
    if (ended == Drag.WIDGET) {
      Widget released = active;
      active = null;
      if (released != null) {
        released.mouseUp(mx, my);
        guardScale();
      }
    } else if (ended == Drag.CHIP && pressedChip != null) {
      HudElement element = pressedChip.element;
      pressedChip = null;
      element.shown.set(!element.shown.get());
      if (element.shown.get()) {
        pickOnly(element);
      }
    } else if (ended == Drag.MOVE && !moved && selected != null && selectedCount() > 1) {
      pickOnly(selected);
    }
  }

  public void scroll(float mx, float my, float amount) {
    if (inBar(mx, my)
        || inTray(mx, my)
        || inTour(mx, my)
        || (inspected != null && inspected.contains(mx, my))) {
      return;
    }
    HudElement target = hit(mx, my);
    if (target == null) {
      return;
    }
    if (!picked[elements.indexOf(target)]) {
      pickOnly(target);
    }
    rescale(target, target.scale.get() + Math.signum(amount) * HudElement.SCALE_STEP);
    if (inspected != null) {
      inspected.goodScale = inspected.element.scale.get();
    }
  }

  public boolean keyDown(int keyCode, boolean coarse) {
    dropHidden();
    if (selected == null) {
      return false;
    }
    float step = coarse ? NUDGE * 10.0F : NUDGE;
    float dx = keyCode == KEY_LEFT ? -step : keyCode == KEY_RIGHT ? step : 0.0F;
    float dy = keyCode == KEY_UP ? -step : keyCode == KEY_DOWN ? step : 0.0F;
    if (dx != 0.0F || dy != 0.0F) {
      moveGroup(dx, dy);
      return true;
    }
    if (keyCode == KEY_DELETE || keyCode == KEY_BACKSPACE) {
      for (int i = 0; i < picked.length; i++) {
        if (picked[i]) {
          elements.get(i).shown.set(false);
        }
      }
      inspected = null;
      clearPicks();
      return true;
    }
    return false;
  }
}
