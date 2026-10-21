package me.cxiy.cxvisuals.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import me.cxiy.cxvisuals.core.module.ClientSettings.WindowMotion;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PanelSetting;
import me.cxiy.cxvisuals.core.settings.Setting;
import me.cxiy.cxvisuals.core.settings.TextSetting;
import me.cxiy.cxvisuals.gui.window.FloatingWindow;
import me.cxiy.cxvisuals.gui.window.Rect;
import me.cxiy.cxvisuals.gui.window.WindowManager;

final class PanelDeck {
  static final String MODULE = "module:";
  static final String PANEL = "panel:";

  private static final float MARGIN = 16.0F;
  private static final float GAP = 12.0F;
  private static final float DOCK_MARGIN = 28.0F;
  private static final float SHIFT_RESPONSE = 14.0F;
  private static final float SHIFT_SETTLED = 0.4F;
  private static final int PATH_PARTS = 3;
  private static final float POP_WIDTH = 340.0F;
  private static final float POP_MINIMUM = 264.0F;
  private static final String TITLE_JOIN = " · ";

  private record Request(String id, float x, float y, boolean instant) {}

  private record Reveal(String moduleId, int page, String setting) {}

  private record Found(PanelSetting setting, String group) {}

  private final WindowManager windows = new WindowManager();
  private final List<Request> pending = new ArrayList<>();
  private final List<Reveal> reveals = new ArrayList<>();
  private final PanelFactory panels;
  private final TextSetting store;
  private final BoolSetting remember;
  private final Function<String, Module> modules;
  private final Consumer<String> dock;
  private float shift;
  private float shiftGoal;
  private float screenW = 1.0F;
  private float screenH = 1.0F;
  private boolean reflow;
  private boolean floating;
  private String main = "";

  PanelDeck(
      PanelFactory panels,
      TextSetting store,
      BoolSetting remember,
      Function<String, Module> modules,
      Consumer<String> dock) {
    this.panels = panels;
    this.store = store;
    this.remember = remember;
    this.modules = modules;
    this.dock = dock;
  }

  float shift() {
    return shift;
  }

  boolean empty() {
    return windows.empty();
  }

  boolean owns(float rawX, float rawY) {
    return windows.owns(rawX, rawY);
  }

  void closeAll() {
    pending.clear();
    windows.closeAll();
  }

  void reflow() {
    reflow = true;
  }

  void main(String id) {
    if (!id.equals(main)) {
      main = id;
      save();
    }
  }

  String saved() {
    return Workspace.decode(store.get()).main();
  }

  void detach(Module module) {
    queue(MODULE + module.id(), Float.NaN, Float.NaN, false);
  }

  void popOut(Module module, Page page, PanelSetting setting) {
    popOut(module.id(), page.id(), setting.id());
  }

  void popOut(String moduleId, String pageId, String settingId) {
    queue(PANEL + moduleId + "/" + pageId + "/" + settingId, Float.NaN, Float.NaN, false);
  }

  int restore(boolean instant) {
    if (!instant && !remember.get()) {
      return 0;
    }
    int queued = 0;
    for (Workspace.Slot slot : Workspace.decode(store.get()).slots()) {
      queue(slot.id(), slot.x(), slot.y(), instant);
      queued++;
    }
    return queued;
  }

  void mode(boolean floatingMode) {
    floating = floatingMode;
    windows.across(floatingMode);
  }

  boolean opened(String moduleId) {
    return windows.find(MODULE + moduleId) != null;
  }

  void toggle(Module module) {
    FloatingWindow window = windows.find(MODULE + module.id());
    if (window != null) {
      window.close();
    } else {
      detach(module);
    }
  }

  void closeModules() {
    for (FloatingWindow window : windows.windows()) {
      if (!window.closing() && window.id().startsWith(MODULE)) {
        window.close();
      }
    }
  }

  void closeModule(String moduleId) {
    FloatingWindow window = windows.find(MODULE + moduleId);
    if (window != null) {
      window.close();
    }
  }

  void reveal(Module module, int page, String setting) {
    detach(module);
    reveals.add(new Reveal(module.id(), page, setting));
  }

  List<Rect> bounds() {
    List<Rect> bounds = new ArrayList<>(windows.windows().size());
    for (FloatingWindow window : windows.windows()) {
      if (!window.closing()) {
        bounds.add(window.bounds());
      }
    }
    return bounds;
  }

  private void queue(String id, float x, float y, boolean instant) {
    if (windows.find(id) != null) {
      windows.raise(windows.find(id));
      return;
    }
    for (int i = 0; i < pending.size(); i++) {
      if (pending.get(i).id().equals(id)) {
        return;
      }
    }
    pending.add(new Request(id, x, y, instant));
  }

  void advance(Canvas c, float dt, float width, float height, float mainWidth, float mainHeight) {
    screenW = width;
    screenH = height;
    if (reflow) {
      reflow = false;
      shiftGoal = goal(mainWidth, false);
      windows.relayout(settled(mainWidth, mainHeight), c, width, height);
    }
    openPending(c, mainWidth, mainHeight);
    applyReveals();
    shiftGoal = goal(mainWidth, false);
    float next = shift + (shiftGoal - shift) * (1.0F - (float) Math.exp(-dt * SHIFT_RESPONSE));
    shift = Math.abs(shiftGoal - next) < SHIFT_SETTLED ? shiftGoal : next;
  }

  private void openPending(Canvas c, float mainWidth, float mainHeight) {
    if (pending.isEmpty()) {
      return;
    }
    for (int i = 0; i < pending.size(); i++) {
      Request request = pending.get(i);
      FloatingWindow window = create(request.id());
      if (window == null) {
        continue;
      }
      shiftGoal = goal(mainWidth, true);
      windows.open(window, settled(mainWidth, mainHeight), c, screenW, screenH);
      if (!Float.isNaN(request.x())) {
        window.moveTo(request.x() * screenW, request.y() * screenH);
        window.markMoved();
      }
      if (request.instant()) {
        window.settle();
      } else {
        Sound.play(Sound.Cue.OPEN);
      }
    }
    pending.clear();
    save();
  }

  private FloatingWindow create(String id) {
    if (id.startsWith(MODULE)) {
      Module module = modules.apply(id.substring(MODULE.length()));
      if (module == null) {
        return null;
      }
      ModuleView content = new ModuleView(module, panels, null, null, true);
      ModulePane pane = new ModulePane(content, floating ? ModulePane.WIDE : ModulePane.WIDTH);
      return new FloatingWindow(
          id, pane, floating ? null : () -> dock.accept(module.id()), this::save);
    }
    if (!id.startsWith(PANEL)) {
      return null;
    }
    String[] path = id.substring(PANEL.length()).split("/");
    Module module = path.length == PATH_PARTS ? modules.apply(path[0]) : null;
    Found found = module == null ? null : find(module, path[1], path[2]);
    if (found == null) {
      return null;
    }
    WidgetPane pane =
        new WidgetPane(
            module.name() + TITLE_JOIN + found.group(),
            POP_WIDTH,
            POP_MINIMUM,
            host -> panels.create(found.setting(), host));
    return pane.empty()
        ? null
        : new FloatingWindow(id, pane, () -> dock.accept(module.id()), this::save);
  }

  private static Found find(Module module, String pageId, String settingId) {
    for (Page page : module.sections()) {
      if (!page.id().equals(pageId)) {
        continue;
      }
      for (Group group : page.groups()) {
        for (Setting<?> setting : group.settings()) {
          if (setting instanceof PanelSetting panel && panel.id().equals(settingId)) {
            return new Found(panel, group.title());
          }
        }
      }
    }
    return null;
  }

  void save() {
    List<Workspace.Slot> slots = new ArrayList<>(windows.windows().size());
    for (FloatingWindow window : windows.windows()) {
      if (window.closing()) {
        continue;
      }
      boolean placed = window.moved() && screenW > 1.0F && screenH > 1.0F;
      slots.add(
          new Workspace.Slot(
              window.id(),
              placed ? window.x() / screenW : Float.NaN,
              placed ? window.y() / screenH : Float.NaN));
    }
    for (int i = 0; i < pending.size(); i++) {
      Request request = pending.get(i);
      slots.add(new Workspace.Slot(request.id(), request.x(), request.y()));
    }
    String encoded = Workspace.encode(main, slots);
    if (!encoded.equals(store.get())) {
      store.set(encoded);
    }
  }

  Rect settled(float mainWidth, float mainHeight) {
    return new Rect(
        screenW * 0.5F + shiftGoal - mainWidth * 0.5F,
        (screenH - mainHeight) * 0.5F,
        mainWidth,
        mainHeight);
  }

  private void applyReveals() {
    for (int i = reveals.size() - 1; i >= 0; i--) {
      Reveal reveal = reveals.get(i);
      FloatingWindow window = windows.find(MODULE + reveal.moduleId());
      if (window != null && window.pane() instanceof ModulePane pane) {
        pane.reveal(reveal.page(), reveal.setting());
        windows.raise(window);
        reveals.remove(i);
      } else if (window == null && pending.isEmpty()) {
        reveals.remove(i);
      }
    }
  }

  private float goal(float mainWidth, boolean opening) {
    if (floating) {
      return -Math.max(0.0F, screenW * 0.5F - mainWidth * 0.5F - DOCK_MARGIN);
    }
    return opening || windows.live() ? columnShift(mainWidth) : 0.0F;
  }

  private float columnShift(float mainWidth) {
    float half = mainWidth * 0.5F;
    float centre = screenW * 0.5F;
    float room = screenW - MARGIN - (centre + half) - GAP;
    if (room >= ModulePane.MIN_WIDTH) {
      return 0.0F;
    }
    float most = centre - half - MARGIN;
    return -Math.max(0.0F, Math.min(ModulePane.MIN_WIDTH - room, most));
  }

  void draw(
      Canvas c,
      Theme t,
      WindowMotion style,
      boolean spatial,
      float strength,
      float rawX,
      float rawY,
      float dt) {
    windows.draw(c, t, style, spatial, strength, screenW, screenH, rawX, rawY, dt);
  }

  boolean mouseDown(float rawX, float rawY) {
    return windows.mouseDown(rawX, rawY);
  }

  void mouseUp(float rawX, float rawY) {
    if (windows.mouseUp(rawX, rawY)) {
      save();
    }
  }

  void mouseDrag(float rawX, float rawY, float mainWidth, float mainHeight) {
    windows.mouseDrag(rawX, rawY, settled(mainWidth, mainHeight), screenW, screenH);
  }

  boolean scroll(float rawX, float rawY, float amount) {
    return windows.scroll(rawX, rawY, amount);
  }

  boolean secondaryDown(float rawX, float rawY) {
    return windows.secondaryDown(rawX, rawY);
  }

  boolean keyDown(int keyCode, int scanCode, boolean control, boolean shiftKey) {
    return windows.keyDown(keyCode, scanCode, control, shiftKey);
  }

  boolean typing() {
    return windows.typing();
  }

  boolean closeFocused() {
    return windows.closeFocused();
  }

  boolean charTyped(int codepoint) {
    return windows.charTyped(codepoint);
  }
}
