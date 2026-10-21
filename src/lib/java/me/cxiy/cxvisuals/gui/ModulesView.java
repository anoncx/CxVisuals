package me.cxiy.cxvisuals.gui;

import static me.cxiy.cxvisuals.gui.ModuleMetrics.CONTENT_HEIGHT;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.HIDDEN;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.OFFSCREEN;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.REVEAL_RISE;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.clamp01;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.module.ModuleRegistry;
import me.cxiy.cxvisuals.gui.PageView.GroupView;
import me.cxiy.cxvisuals.gui.PageView.RowView;
import me.cxiy.cxvisuals.gui.spatial.Layer;
import me.cxiy.cxvisuals.gui.spatial.Rig;
import me.cxiy.cxvisuals.gui.spatial.Warp;
import me.cxiy.cxvisuals.gui.window.Rect;

public final class ModulesView {
  public record RowInfo(String settingId, Class<?> widget, float x, float y, float w, float h) {}

  public static final float WIDTH = 640.0F;
  public static final float HEIGHT = 522.0F;
  public static final float SIDEBAR = ModulesSidebar.WIDTH;
  public static final float PAD = 12.0F;

  static final float DISTANCE = Rig.DISTANCE;
  static final int KEY_D = 68;
  static final int KEY_ESCAPE = 256;
  static final int KEY_RIGHT = 262;
  static final int KEY_LEFT = 263;
  static final int KEY_DOWN = 264;
  static final int KEY_UP = 265;
  static final int KEY_K = 75;
  static final int KEY_L = 76;
  static final int KEY_F = 70;

  private static final float MARGIN = 16.0F;
  private static final float CONTENT_LIVE = 0.5F;
  private static final float DOCK_SPAN = SIDEBAR + PAD * 2.0F;
  private static final float SPAN_RESPONSE = 12.0F;
  private static final float SPAN_SETTLED = 0.3F;
  private static final float SPOTLIGHT_TOP = 0.14F;
  private static final float SPOTLIGHT_TOP_MIN = 24.0F;
  private static final float PANEL_RADIUS = 22.0F;
  private static final float CONTENT_GAP = 10.0F;
  private static final int BACKDROP = 0x8C000000;
  private static final int BACKDROP_LIGHT = 0x33000000;
  private static final int PANEL_SHADOW = 0x99000000;
  private static final float SHADOW_HEIGHT = Layer.height(Layer.SHADOW);
  private static final float SHEEN_ALPHA = 0.07F;
  private static final float SHEEN_SIZE = 220.0F;
  private static final float SHEEN_BLUR = 120.0F;
  private static final float SHEEN_REACH = 0.42F;
  private static final float POP_SCALE = 0.04F;
  private static final int POP_SHADOW = 0x66000000;
  private static final float PILL_WIDTH = 120.0F;
  private static final float PILL_HEIGHT = 40.0F;
  private static final float POP_FROM = 0.86F;
  private static final float DISSOLVE_PAD = 56.0F;
  private static final float SLIDE_FROM = 56.0F;
  private static final float REVEAL_STAGGER = 0.05F;
  private static final float REVEAL_SPAN = 0.3F;
  private static final int REVEAL_STEPS = 5;

  private final Supplier<Theme> theme;
  private final PanelFactory panels;
  private final ClientSettings client;
  private final List<ModuleView> entries = new ArrayList<>();
  private final ModulesSidebar sidebar;
  private final PanelDeck deck;
  private final Spotlight spotlight = new Spotlight(this::jump);
  private final Rig rig = new Rig();
  private final Warp warp = new Warp();
  private final ModuleView.Painter painter = this::drawControl;
  private final ModuleView.Reveal revealer = this::revealAt;

  private ClientSettings.WindowMotion motion = ClientSettings.WindowMotion.FADE;
  private float screenW = WIDTH;
  private float screenH = HEIGHT;
  private float fit = 1.0F;
  private float unmappedX;
  private float unmappedY;
  private int selected;
  private boolean closing;
  private boolean floating;
  private float span = WIDTH;
  private boolean searchHeld;
  private float phase;
  private float lift;

  public ModulesView(ModuleRegistry registry, Supplier<Theme> theme) {
    this(registry, theme, PanelFactory.NONE);
  }

  public ModulesView(ModuleRegistry registry, Supplier<Theme> theme, PanelFactory panels) {
    this.theme = theme;
    this.panels = panels;
    this.client = registry.client();
    this.deck =
        new PanelDeck(
            panels,
            client.workspace,
            client.rememberPanels,
            id -> moduleById(registry, id),
            this::showModule);
    for (Module module : registry.entries()) {
      if (module.listed() && !module.pinned()) {
        entries.add(entryFor(module));
      }
    }
    for (Module module : registry.entries()) {
      if (module.listed() && module.pinned()) {
        entries.add(entryFor(module));
      }
    }
    this.sidebar =
        new ModulesSidebar(entries, this::openSpotlight, module -> deck.opened(module.id()));
    this.floating = client.layout.get() == ClientSettings.Layout.FLOATING;
    this.span = floating ? DOCK_SPAN : WIDTH;
  }

  private static Module moduleById(ModuleRegistry registry, String id) {
    for (Module module : registry.entries()) {
      if (module.id().equals(id) && module.listed()) {
        return module;
      }
    }
    return null;
  }

  private ModuleView entryFor(Module module) {
    return new ModuleView(
        module,
        panels,
        () -> detach(module),
        (page, setting) -> deck.popOut(module, page, setting),
        false);
  }

  public void detach(String moduleId) {
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i).module.id().equals(moduleId)) {
        detach(entries.get(i).module);
        return;
      }
    }
  }

  public void popOut(String moduleId, String pageId, String panelId) {
    if (!closing) {
      deck.popOut(moduleId, pageId, panelId);
    }
  }

  private boolean navigate(int keyCode, ModuleView entry) {
    switch (keyCode) {
      case KEY_DOWN -> selectEntry(Math.min(entries.size() - 1, selected + 1));
      case KEY_UP -> selectEntry(Math.max(0, selected - 1));
      case KEY_RIGHT -> entry.changeTab(entry.tab + 1);
      case KEY_LEFT -> entry.changeTab(entry.tab - 1);
      default -> {
        return false;
      }
    }
    return true;
  }

  private void openSpotlight() {
    spotlight.show(new SettingsIndex(entries));
  }

  private void jump(SettingsIndex.Hit hit) {
    selectEntry(hit.entry());
    if (floating) {
      deck.reveal(entries.get(hit.entry()).module, hit.page(), hit.settingId());
    } else {
      entries.get(hit.entry()).reveal(hit.page(), hit.settingId());
    }
  }

  public boolean searching() {
    return spotlight.open();
  }

  public void detach(Module module) {
    if (!closing) {
      deck.detach(module);
    }
  }

  public void resize(float width, float height) {
    boolean changed = Math.abs(screenW - width) > 0.5F || Math.abs(screenH - height) > 0.5F;
    screenW = width;
    screenH = height;
    fit =
        Math.max(
            0.1F,
            Math.min(
                1.0F,
                Math.min((width - MARGIN * 2.0F) / WIDTH, (height - MARGIN * 2.0F) / HEIGHT)));
    if (changed) {
      deck.reflow();
    }
  }

  Rig rig() {
    return rig;
  }

  public float fitScale() {
    return fit;
  }

  public float panelX() {
    return centreX() - span * fit * 0.5F;
  }

  public float panelY() {
    return (screenH - HEIGHT * fit) * 0.5F;
  }

  public Rect mainBounds() {
    return frame();
  }

  public List<Rect> windowBounds() {
    return deck.bounds();
  }

  private Rect frame() {
    return new Rect(panelX(), panelY(), span * fit, HEIGHT * fit);
  }

  private float centreX() {
    return screenW * 0.5F + deck.shift();
  }

  public int entryCount() {
    return entries.size();
  }

  public int entryIndex() {
    return selected;
  }

  private void choose(int index) {
    Module module = entries.get(index).module;
    if (floating && index == selected && deck.opened(module.id())) {
      deck.toggle(module);
      Sound.play(Sound.Cue.TAB);
      return;
    }
    selectEntry(index);
    if (floating) {
      deck.detach(module);
    }
  }

  public void selectEntry(int index) {
    int next = Math.max(0, Math.min(entries.size() - 1, index));
    if (next == selected) {
      return;
    }
    ModuleView previous = entries.get(selected);
    previous.cancelCapture(null);
    previous.drop();
    selected = next;
    entries.get(selected).restartFade();
    deck.main(entries.get(selected).module.id());
    if (floating) {
      deck.detach(entries.get(selected).module);
    }
    Sound.play(Sound.Cue.TAB);
  }

  public void adopt(ModulesView previous) {
    selected = Math.min(previous.selected, entries.size() - 1);
    int shared = Math.min(entries.size(), previous.entries.size());
    for (int i = 0; i < shared; i++) {
      entries.get(i).adopt(previous.entries.get(i));
    }
    screenW = previous.screenW;
    screenH = previous.screenH;
    fit = previous.fit;
    phase = previous.phase;
    closing = previous.closing;
    sidebar.adopt(previous.sidebar);
    entries.get(selected).restartFade();
    deck.restore(true);
    deck.main(entries.get(selected).module.id());
  }

  public void showModule(String moduleId) {
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i).module.id().equals(moduleId)) {
        selectEntry(i);
        entries.get(i).changeTab(0);
        return;
      }
    }
  }

  public int tabCount() {
    ModuleView entry = entries.get(selected);
    return entry.tabs == null ? 0 : entry.pages.size();
  }

  public int tabIndex() {
    return entries.get(selected).tab;
  }

  public void selectTab(int index) {
    entries.get(selected).changeTab(index);
  }

  public float scrollOffset() {
    return entries.get(selected).page().scroll;
  }

  public float maxScroll() {
    return current().maxScroll();
  }

  public float sidebarScroll() {
    return sidebar.scroll();
  }

  public float sidebarMaxScroll() {
    layout();
    return sidebar.maxScroll();
  }

  public void open() {
    Sound.play(Sound.Cue.OPEN);
    closing = false;
    phase = 0.0F;
    entries.get(selected).settle();
    int restored = deck.restore(false);
    selectSaved();
    if (restored == 0 && floating) {
      deck.detach(entries.get(selected).module);
    }
  }

  private void selectSaved() {
    String id = deck.saved();
    for (int i = 0; i < entries.size() && !id.isEmpty(); i++) {
      if (entries.get(i).module.id().equals(id)) {
        selected = i;
        entries.get(i).restartFade();
        return;
      }
    }
    deck.main(entries.get(selected).module.id());
  }

  public void close() {
    spotlight.hide();
    Sound.play(Sound.Cue.CLOSE);
    closing = true;
    entries.get(selected).cancelCapture(null);
    deck.closeAll();
  }

  public boolean expanded(String settingId) {
    RowView row = entries.get(selected).page().row(settingId);
    return row != null && row.open;
  }

  public RowInfo optionBounds(String settingId, int index) {
    RowView row = entries.get(selected).page().row(settingId);
    if (row == null || !row.open) {
      return null;
    }
    OptionGrid grid = row.grid;
    return new RowInfo(
        settingId,
        OptionGrid.class,
        screenX(grid.cellX(index)),
        screenY(grid.cellY(index)),
        grid.cellWidth() * fit,
        grid.cellHeight() * fit);
  }

  public boolean closed() {
    return closing && phase <= 0.0F && deck.empty();
  }

  public boolean capturingKey() {
    return entries.get(selected).capturingKey();
  }

  public int actionCount() {
    return entries.get(selected).actions.size();
  }

  public RowInfo masterBounds() {
    ModuleView entry = current();
    return entry.master == null ? null : bounds(entry.module.enabled.id(), entry.master);
  }

  public RowInfo actionBounds(int index) {
    ModuleView entry = current();
    return bounds(entry.module.actions().get(index).id(), entry.actions.get(index));
  }

  public RowInfo tabBounds(int index) {
    ModuleView entry = current();
    if (entry.tabs == null) {
      return null;
    }
    Segmented tabs = entry.tabs;
    return new RowInfo(
        entry.labels.get(index),
        Segmented.class,
        screenX(tabs.x + tabs.segmentX(index)),
        screenY(tabs.y),
        tabs.segmentWidth(index) * fit,
        tabs.h * fit);
  }

  public RowInfo entryBounds(int index) {
    layout();
    return new RowInfo(
        entries.get(index).module.id(),
        null,
        screenX(sidebar.itemLeft()),
        screenY(sidebar.itemTop(index)),
        sidebar.itemWidth() * fit,
        sidebar.itemHeight() * fit);
  }

  private RowInfo bounds(String id, Widget widget) {
    return new RowInfo(
        id,
        widget.getClass(),
        screenX(widget.x),
        screenY(widget.y),
        widget.w * fit,
        widget.h * fit);
  }

  public RowInfo viewport() {
    ModuleView entry = current();
    return new RowInfo(
        "viewport",
        null,
        screenX(entry.contentX()),
        screenY(entry.viewportTop()),
        entry.contentWidth() * fit,
        entry.viewportHeight() * fit);
  }

  public List<RowInfo> rows() {
    ModuleView entry = current();
    PageView page = entry.page();
    List<RowInfo> infos = new ArrayList<>(page.rows.size());
    float rowsLeft = entry.contentX();
    for (GroupView group : page.groups) {
      if (group.presence <= HIDDEN) {
        continue;
      }
      float cardTop = entry.viewportTop() - page.scroll + group.top + ModuleMetrics.GROUP_LABEL;
      for (RowView row : group.rows) {
        Widget control = row.control;
        if (control == null) {
          infos.add(
              new RowInfo(
                  row.setting.id(),
                  null,
                  screenX(rowsLeft),
                  screenY(cardTop + row.top),
                  entry.contentWidth() * fit,
                  row.height * fit));
        } else {
          infos.add(bounds(row.setting.id(), control));
        }
      }
    }
    return infos;
  }

  private float left() {
    return centreX() - span * 0.5F;
  }

  private float top() {
    return (screenH - HEIGHT) * 0.5F + lift;
  }

  private float contentX() {
    return left() + PAD + SIDEBAR + CONTENT_GAP;
  }

  private float contentWidth() {
    return Math.max(0.0F, span - PAD * 2.0F - SIDEBAR - CONTENT_GAP);
  }

  private void layout() {
    float areaTop = top() + PAD;
    float areaHeight = HEIGHT - PAD * 2.0F;
    sidebar.place(left() + PAD, areaTop, areaHeight);
    entries.get(selected).place(contentX(), areaTop, contentWidth(), areaHeight);
  }

  private ModuleView current() {
    layout();
    return entries.get(selected);
  }

  private void syncLayout(float dt) {
    boolean wanted = client.layout.get() == ClientSettings.Layout.FLOATING;
    if (wanted != floating) {
      floating = wanted;
      Module module = entries.get(selected).module;
      if (floating) {
        deck.detach(module);
      } else {
        deck.closeModules();
      }
      deck.reflow();
    }
    deck.mode(floating);
    float goal = goalSpan();
    float next = span + (goal - span) * (1.0F - (float) Math.exp(-dt * SPAN_RESPONSE));
    span = Math.abs(goal - next) < SPAN_SETTLED ? goal : next;
  }

  private float goalSpan() {
    return floating ? DOCK_SPAN : WIDTH;
  }

  private float contentAlpha() {
    float share = clamp01((span - DOCK_SPAN) / (WIDTH - DOCK_SPAN));
    return share * share;
  }

  private void feedTilt(float pointerX, float pointerY, boolean overPanel) {
    float halfW = span * fit * 0.5F;
    float halfH = HEIGHT * fit * 0.5F;
    float dx = (pointerX - centreX()) / halfW;
    float dy = (pointerY - screenH * 0.5F) / halfH;
    boolean inside = !overPanel && Math.abs(dx) <= 1.0F && Math.abs(dy) <= 1.0F;
    rig.feedPointer(inside ? dx : 0.0F, inside ? dy : 0.0F);
  }

  private float revealAt(int order) {
    if (phase >= 1.0F
        || motion == ClientSettings.WindowMotion.FADE
        || motion == ClientSettings.WindowMotion.EVAPORATE) {
      return 1.0F;
    }
    if (closing) {
      return clamp01((phase - 0.55F) / 0.35F);
    }
    float start = motion == ClientSettings.WindowMotion.MORPH ? 0.33F : 0.12F;
    return (float)
        Easing.smootherstep(
            (phase - start - Math.min(order, REVEAL_STEPS) * REVEAL_STAGGER) / REVEAL_SPAN);
  }

  private void unmap(float screenX, float screenY, float height) {
    warp.unmap(screenX, screenY, height);
    unmappedX = centreX() + (warp.x() - centreX()) / fit;
    unmappedY = screenH * 0.5F + (warp.y() - screenH * 0.5F) / fit;
  }

  private float screenX(float localX) {
    return centreX() + (localX - centreX()) * fit;
  }

  private float screenY(float localY) {
    return screenH * 0.5F + (localY - screenH * 0.5F) * fit;
  }

  public void draw(Canvas c, float mouseScreenX, float mouseScreenY, float dt) {
    Theme t = theme.get();
    motion = client.motion.get();
    phase = Motion.advance(motion, phase, closing, dt);
    float p = phase;
    lift = 0.0F;
    syncLayout(dt);
    deck.advance(c, dt, screenW, screenH, goalSpan() * fit, HEIGHT * fit);
    rig.configure(client.spatial.get(), client.spatialStrength.get().floatValue() / 100.0F);
    rig.gate(p);
    ModuleView entry = entries.get(selected);
    boolean overPanel = deck.owns(mouseScreenX, mouseScreenY);
    if (!entry.grabbing()) {
      feedTilt(mouseScreenX, mouseScreenY, overPanel);
    }
    rig.update(dt);
    warp.set(rig.yaw(), rig.pitch(), centreX(), screenH * 0.5F, DISTANCE);

    layout();
    entry.advance(dt);
    sidebar.advance(dt);

    unmap(mouseScreenX, mouseScreenY, CONTENT_HEIGHT);
    float mx = overPanel ? OFFSCREEN : unmappedX;
    float my = overPanel ? OFFSCREEN : unmappedY;

    float alpha = motion == ClientSettings.WindowMotion.EVAPORATE ? 1.0F : Motion.alpha(motion, p);
    float scale = 1.0F;
    float rise = 0.0F;
    float width = span;
    float height = HEIGHT;
    switch (motion) {
      case MORPH -> {
        width = PILL_WIDTH + (span - PILL_WIDTH) * Motion.foldWidth(p, closing);
        height = PILL_HEIGHT + (HEIGHT - PILL_HEIGHT) * Motion.foldHeight(p, closing);
      }
      case POP -> scale = Motion.pop(POP_FROM, p, closing);
      case SLIDE -> rise = Motion.slide(SLIDE_FROM, p);
      default -> {}
    }
    float radius = Math.min(PANEL_RADIUS, Math.min(width, height) * 0.5F);
    float x0 = centreX() - width * 0.5F;
    float y0 = (screenH - height) * 0.5F + rise;
    int dim = t.translucent() ? BACKDROP_LIGHT : BACKDROP;

    c.roundRect(
        0.0F, 0.0F, screenW, screenH, 0.0F, Theme.withAlpha(dim, (float) Easing.smoothstep(p)));
    float erosion = Motion.erosion(motion, p);
    c.perspective(
        erosion > 0.0F ? 0.0F : rig.yaw(),
        erosion > 0.0F ? 0.0F : rig.pitch(),
        DISTANCE,
        centreX(),
        screenH * 0.5F);
    c.pushAlpha(alpha);
    c.pushTransform(fit * scale, centreX(), screenH * 0.5F);
    c.beginDissolve(
        x0 - DISSOLVE_PAD,
        y0 - DISSOLVE_PAD,
        width + DISSOLVE_PAD * 2.0F,
        height + DISSOLVE_PAD * 2.0F,
        erosion);

    c.pushLayer(SHADOW_HEIGHT);
    c.shadow(x0, y0, width, height, radius, 44.0F, 18.0F, PANEL_SHADOW);
    c.popLayer();
    if (t.glass()) {
      c.glass(x0, y0, width, height, radius, t.paper());
    } else {
      c.roundRect(x0, y0, width, height, radius, t.paper());
    }
    c.roundRectBorder(x0, y0, width, height, radius, 1.0F, t.hair());
    c.rim(x0, y0, width, height, radius, 1.0F, radius * 1.4F, Depth.rimColor(t));
    sheen(c, x0, y0, width, height);

    boolean folded = width < span - 0.5F || height < HEIGHT - 0.5F;
    if (folded) {
      c.pushClip(x0, y0, width, height);
    }

    float sidebarReveal = revealAt(0);
    if (sidebarReveal > HIDDEN) {
      lift = rise + (1.0F - sidebarReveal) * REVEAL_RISE;
      layout();
      c.pushLayer(CONTENT_HEIGHT);
      c.pushAlpha(sidebarReveal);
      sidebar.draw(c, t, selected, mx, my, dt);
      c.popAlpha();
      c.popLayer();
    }
    float shown = contentAlpha();
    float header = revealAt(1);
    if (header > HIDDEN && shown > HIDDEN) {
      lift = rise + (1.0F - header) * REVEAL_RISE;
      layout();
      c.pushLayer(CONTENT_HEIGHT);
      c.pushAlpha(header * shown);
      entry.drawHeader(c, t, painter, mx, my, dt);
      c.popAlpha();
      c.popLayer();
    }
    lift = rise;
    layout();
    if (shown > HIDDEN) {
      c.pushLayer(CONTENT_HEIGHT);
      c.pushAlpha(shown);
      entry.drawPage(c, t, painter, revealer, mx, my, dt);
      c.popAlpha();
      c.popLayer();
    }
    lift = 0.0F;

    if (folded) {
      c.popClip();
    }
    c.endDissolve();
    c.popTransform();
    c.popAlpha();
    deck.draw(
        c,
        t,
        motion,
        client.spatial.get(),
        client.spatialStrength.get().floatValue() / 100.0F,
        mouseScreenX,
        mouseScreenY,
        dt);
    c.perspective(0.0F, 0.0F, DISTANCE);
    if (spotlight.visible()) {
      spotlight.draw(
          c,
          t,
          screenW * 0.5F,
          Math.max(SPOTLIGHT_TOP_MIN, screenH * SPOTLIGHT_TOP),
          screenW,
          screenH,
          mouseScreenX,
          mouseScreenY,
          dt);
    }
  }

  private void drawControl(
      Canvas c, Theme t, Widget control, float hoverX, float hoverY, float dt) {
    if (!client.spatialFocus.get() || !rig.active()) {
      control.draw(c, t, hoverX, hoverY, dt);
      return;
    }
    float lift = control.lift(control.contains(hoverX, hoverY), dt);
    if (lift < 0.01F) {
      control.draw(c, t, hoverX, hoverY, dt);
      return;
    }
    c.shadow(
        control.x,
        control.y + 1.0F,
        control.w,
        control.h,
        control.h * 0.5F,
        6.0F + 8.0F * lift,
        2.0F + 3.0F * lift,
        Theme.withAlpha(POP_SHADOW, lift * rig.gain()));
    c.pushLayer(CONTENT_HEIGHT + Layer.height(Layer.POP) * lift);
    c.pushTransform(
        1.0F + POP_SCALE * lift, control.x + control.w * 0.5F, control.y + control.h * 0.5F);
    control.draw(c, t, hoverX, hoverY, dt);
    c.popTransform();
    c.popLayer();
  }

  private void sheen(Canvas c, float x0, float y0, float width, float height) {
    float glint = rig.glint();
    if (glint < 0.02F || !client.spatialSheen.get()) {
      return;
    }
    float cx = x0 + width * 0.5F + rig.tiltX() * width * SHEEN_REACH;
    float cy = y0 + height * 0.5F + rig.tiltY() * height * SHEEN_REACH;
    c.pushClip(x0, y0, width, height);
    c.shadow(
        cx - SHEEN_SIZE * 0.5F,
        cy - SHEEN_SIZE * 0.5F,
        SHEEN_SIZE,
        SHEEN_SIZE,
        SHEEN_SIZE * 0.5F,
        SHEEN_BLUR,
        0.0F,
        Theme.withAlpha(0xFFFFFFFF, glint * SHEEN_ALPHA));
    c.popClip();
  }

  private boolean onPanel(float screenX, float screenY) {
    unmap(screenX, screenY, 0.0F);
    float x0 = left();
    float y0 = top();
    return unmappedX >= x0 && unmappedX < x0 + span && unmappedY >= y0 && unmappedY < y0 + HEIGHT;
  }

  public boolean mouseDown(float screenX, float screenY) {
    if (closing) {
      return false;
    }
    if (spotlight.visible()) {
      return spotlight.mouseDown(screenX, screenY);
    }
    if (deck.mouseDown(screenX, screenY)) {
      entries.get(selected).cancelCapture(null);
      return true;
    }
    if (!onPanel(screenX, screenY)) {
      entries.get(selected).cancelCapture(null);
      return false;
    }
    unmap(screenX, screenY, CONTENT_HEIGHT);
    layout();
    if (sidebar.search().mouseDown(unmappedX, unmappedY)) {
      searchHeld = true;
      return true;
    }
    int hit = sidebar.hit(unmappedX, unmappedY);
    if (hit >= 0) {
      choose(hit);
      return true;
    }
    if (hit != ModulesSidebar.CONSUMED && contentAlpha() > CONTENT_LIVE) {
      entries.get(selected).mouseDown(unmappedX, unmappedY);
    }
    return true;
  }

  public boolean secondaryDown(float screenX, float screenY) {
    if (closing) {
      return false;
    }
    if (spotlight.visible()) {
      return true;
    }
    if (deck.secondaryDown(screenX, screenY)) {
      return true;
    }
    if (!onPanel(screenX, screenY)) {
      return false;
    }
    unmap(screenX, screenY, CONTENT_HEIGHT);
    layout();
    int hit = sidebar.hit(unmappedX, unmappedY);
    if (hit >= 0) {
      deck.detach(entries.get(hit).module);
      return true;
    }
    return hit == ModulesSidebar.CONSUMED || current().secondaryDown(unmappedX, unmappedY);
  }

  public void mouseUp(float screenX, float screenY) {
    spotlight.mouseUp();
    deck.mouseUp(screenX, screenY);
    unmap(screenX, screenY, CONTENT_HEIGHT);
    if (searchHeld) {
      searchHeld = false;
      sidebar.search().mouseUp(unmappedX, unmappedY);
    }
    if (entries.get(selected).grabbing()) {
      entries.get(selected).mouseUp(unmappedX, unmappedY);
    }
  }

  public void mouseDrag(float screenX, float screenY) {
    if (spotlight.open()) {
      spotlight.mouseDrag(screenX);
      return;
    }
    deck.mouseDrag(screenX, screenY, goalSpan() * fit, HEIGHT * fit);
    unmap(screenX, screenY, CONTENT_HEIGHT);
    if (entries.get(selected).grabbing()) {
      entries.get(selected).mouseDrag(unmappedX, unmappedY);
    }
  }

  public void scroll(float screenX, float screenY, float amount) {
    if (spotlight.visible()) {
      return;
    }
    if (deck.scroll(screenX, screenY, amount)) {
      return;
    }
    unmap(screenX, screenY, CONTENT_HEIGHT);
    if (unmappedX < left() + PAD + SIDEBAR) {
      sidebar.scrollBy(amount);
    } else {
      entries.get(selected).scroll(unmappedX, unmappedY, amount);
    }
  }

  public boolean keyDown(int keyCode, int scanCode) {
    return keyDown(keyCode, scanCode, false, false);
  }

  public boolean keyDown(int keyCode, int scanCode, boolean control, boolean shift) {
    if (spotlight.open()) {
      return spotlight.keyDown(keyCode, control, shift);
    }
    ModuleView focus = entries.get(selected);
    if (control && shift && keyCode == KEY_L && !focus.typing() && !deck.typing()) {
      client.layout.set(floating ? ClientSettings.Layout.CLASSIC : ClientSettings.Layout.FLOATING);
      return true;
    }
    if (control
        && !shift
        && (keyCode == KEY_K || keyCode == KEY_F)
        && !focus.typing()
        && !deck.typing()) {
      openSpotlight();
      return true;
    }
    if (deck.keyDown(keyCode, scanCode, control, shift)) {
      return true;
    }
    if (keyCode == KEY_ESCAPE && deck.closeFocused()) {
      return true;
    }
    ModuleView entry = entries.get(selected);
    if (control && shift && keyCode == KEY_D && !entry.typing()) {
      deck.detach(entry.module);
      return true;
    }
    if (entry.keyDown(keyCode, scanCode, control, shift)) {
      return true;
    }
    return !control && !shift && !entry.typing() && !deck.typing() && navigate(keyCode, entry);
  }

  public boolean charTyped(int codepoint) {
    if (spotlight.open()) {
      return spotlight.charTyped(codepoint);
    }
    return deck.charTyped(codepoint) || entries.get(selected).charTyped(codepoint);
  }
}
