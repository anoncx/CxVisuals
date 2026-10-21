package me.cxiy.cxvisuals.gui;

import static me.cxiy.cxvisuals.gui.ModuleMetrics.COMPACT_BARE;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.COMPACT_HEADER;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.CONTROL_INSET;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.GROUP_LABEL;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.HEADER;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.HEADER_GAP;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.HEADER_INSET;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.OFFSCREEN;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.PAGE_SLIDE;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.PANEL_INSET;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.TABS_GAP;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.TABS_HEIGHT;
import static me.cxiy.cxvisuals.gui.ModuleMetrics.TITLE_TOP;

import java.util.ArrayList;
import java.util.List;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.ActionSetting;
import me.cxiy.cxvisuals.core.settings.Choice;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PanelSetting;

final class ModuleView implements Choice {
  interface Painter {
    void draw(Canvas c, Theme t, Widget control, float hoverX, float hoverY, float dt);
  }

  interface Reveal {
    Reveal ALWAYS = order -> 1.0F;

    float at(int order);
  }

  final Module module;
  final Toggle master;
  final List<Button> actions = new ArrayList<>(2);
  final List<PageView> pages = new ArrayList<>(3);
  final List<String> labels = new ArrayList<>(3);
  final Segmented tabs;
  final IconButton detach;
  private final Anim pageFade = Anim.reveal(1.0F);
  private final boolean compact;
  private Widget active;
  private float x;
  private float y;
  private float w;
  private float h;
  int tab;

  ModuleView(
      Module module,
      PanelFactory panels,
      Runnable onDetach,
      PageView.PopOut popOut,
      boolean compact) {
    this.module = module;
    this.compact = compact;
    this.detach =
        onDetach == null
            ? null
            : new IconButton(IconButton.Glyph.DETACH, IconButton.LABEL_OPEN, onDetach);
    this.master = module.switchable() ? new Toggle(module.enabled) : null;
    for (ActionSetting action : module.actions()) {
      actions.add(new Button(action));
    }
    List<Page> sources = module.sections();
    PanelHost host =
        new PanelHost() {
          @Override
          public void openTab(String pageId) {
            for (int i = 0; i < sources.size(); i++) {
              if (sources.get(i).id().equals(pageId)) {
                changeTab(i);
                return;
              }
            }
          }

          @Override
          public float room() {
            return y + h - viewportTop() - GROUP_LABEL - PANEL_INSET * 2.0F;
          }
        };
    for (Page page : sources) {
      pages.add(
          new PageView(
              page,
              setting ->
                  setting instanceof PanelSetting panel
                      ? panels.create(panel, host)
                      : Controls.of(setting),
              popOut));
      labels.add(page.title());
    }
    this.tabs = pages.size() > 1 ? new Segmented(this) : null;
  }

  void place(float x, float y, float w, float h) {
    this.x = x;
    this.y = y;
    this.w = w;
    this.h = h;
  }

  float contentX() {
    return x;
  }

  float contentWidth() {
    return w;
  }

  private float headerHeight() {
    if (!compact) {
      return HEADER;
    }
    return master == null && actions.isEmpty() && detach == null ? COMPACT_BARE : COMPACT_HEADER;
  }

  float tabsTop() {
    return y + headerHeight();
  }

  float viewportTop() {
    return tabs == null ? tabsTop() : tabsTop() + TABS_HEIGHT + TABS_GAP;
  }

  float viewportHeight() {
    return y + h - viewportTop();
  }

  boolean reveal(int pageIndex, String settingId) {
    changeTab(pageIndex);
    return page().reveal(settingId);
  }

  void arrange(Canvas c, float width) {
    page().arrange(c, width);
  }

  float extent() {
    return (tabs == null ? headerHeight() : headerHeight() + TABS_HEIGHT + TABS_GAP)
        + page().contentHeight;
  }

  PageView page() {
    return pages.get(tab);
  }

  float maxScroll() {
    return page().maxScroll(viewportHeight());
  }

  boolean grabbing() {
    return active != null;
  }

  @Override
  public List<String> labels() {
    return labels;
  }

  @Override
  public int index() {
    return tab;
  }

  @Override
  public void setIndex(int index) {
    changeTab(index);
  }

  void changeTab(int index) {
    int next = Math.max(0, Math.min(pages.size() - 1, index));
    if (next == tab) {
      return;
    }
    cancelCapture(null);
    tab = next;
    active = null;
    restartFade();
  }

  void restartFade() {
    pageFade.snap(0.0F);
    pageFade.set(1.0F);
  }

  void settle() {
    cancelCapture(null);
    release();
    pageFade.snap(1.0F);
  }

  void drop() {
    active = null;
  }

  void release() {
    if (active != null) {
      active.mouseUp(OFFSCREEN, OFFSCREEN);
      active = null;
    }
  }

  void adopt(ModuleView previous) {
    tab = Math.min(previous.tab, pages.size() - 1);
    int shared = Math.min(pages.size(), previous.pages.size());
    for (int i = 0; i < shared; i++) {
      pages.get(i).adopt(previous.pages.get(i));
    }
  }

  void advance(float dt) {
    page().advance(viewportHeight(), dt);
  }

  void drawHeader(Canvas c, Theme t, Painter painter, float mx, float my, float dt) {
    float middle =
        compact ? y + headerHeight() * 0.5F : y + TITLE_TOP + c.lineHeight(FontStyle.TITLE) * 0.5F;
    float right = x + w - CONTROL_INSET;
    if (master != null) {
      master.place(right - master.w, middle - master.h * 0.5F);
      right = master.x - HEADER_GAP;
    }
    for (int i = actions.size() - 1; i >= 0; i--) {
      Button button = actions.get(i);
      float width = button.measure(c);
      button.place(right - width, middle - button.h * 0.5F);
      right = button.x - HEADER_GAP;
    }
    if (detach != null) {
      detach.place(right - detach.w, middle - detach.h * 0.5F);
      right = detach.x - HEADER_GAP;
    }
    if (!compact) {
      float titleX = x + HEADER_INSET;
      c.text(
          FontStyle.TITLE,
          c.fit(FontStyle.TITLE, module.name(), right - titleX),
          titleX,
          y + TITLE_TOP,
          t.ink());
    }
    for (int i = 0; i < actions.size(); i++) {
      painter.draw(c, t, actions.get(i), mx, my, dt);
    }
    if (master != null) {
      painter.draw(c, t, master, mx, my, dt);
    }
    if (detach != null) {
      painter.draw(c, t, detach, mx, my, dt);
    }
    if (tabs != null) {
      tabs.limit(w);
      tabs.measure(c);
      tabs.place(x, tabsTop());
      painter.draw(c, t, tabs, mx, my, dt);
    }
  }

  void drawPage(Canvas c, Theme t, Painter painter, Reveal reveal, float mx, float my, float dt) {
    pageFade.update(dt);
    float fade = Math.max(0.0F, Math.min(1.0F, pageFade.get()));
    page()
        .draw(
            c,
            t,
            painter,
            reveal,
            x,
            w,
            viewportTop(),
            viewportHeight(),
            fade,
            (1.0F - fade) * PAGE_SLIDE,
            mx,
            my,
            dt);
  }

  void mouseDown(float mx, float my) {
    Widget hit = null;
    if (master != null && master.mouseDown(mx, my)) {
      hit = master;
    }
    for (int i = 0; hit == null && i < actions.size(); i++) {
      if (actions.get(i).mouseDown(mx, my)) {
        hit = actions.get(i);
      }
    }
    if (hit == null && detach != null && detach.mouseDown(mx, my)) {
      hit = detach;
    }
    if (hit == null && tabs != null && tabs.mouseDown(mx, my)) {
      hit = tabs;
    }
    if (hit == null) {
      hit = page().pick(mx, my, x, w, viewportTop(), viewportHeight());
    }
    cancelCapture(hit);
    active = hit;
  }

  boolean secondaryDown(float mx, float my) {
    float viewTop = viewportTop();
    if (mx < x || mx >= x + w || my < viewTop || my >= viewTop + viewportHeight()) {
      return true;
    }
    return page().secondaryDown(mx, my, viewTop);
  }

  void mouseUp(float mx, float my) {
    Widget released = active;
    active = null;
    if (released != null) {
      released.mouseUp(mx, my);
    }
  }

  void mouseDrag(float mx, float my) {
    if (active != null) {
      active.mouseDrag(mx, my);
    }
  }

  void scroll(float mx, float my, float amount) {
    page().scroll(mx, my, amount, viewportHeight());
  }

  boolean keyDown(int keyCode, int scanCode, boolean control, boolean shift) {
    TextInput field = page().focused();
    if (field != null) {
      return field.keyDown(keyCode, control, shift);
    }
    KeyButton button = page().capturing();
    if (button != null) {
      return button.keyDown(keyCode, scanCode);
    }
    return page().keyDown(keyCode, control, shift);
  }

  boolean charTyped(int codepoint) {
    TextInput field = page().focused();
    if (field != null && field.charTyped(codepoint)) {
      return true;
    }
    return page().charTyped(codepoint);
  }

  boolean capturingKey() {
    return page().capturing() != null;
  }

  boolean typing() {
    return page().focused() != null || page().capturing() != null;
  }

  void cancelCapture(Widget except) {
    page().cancelCapture(except);
  }
}
