package me.cxiy.cxvisuals.gui;

import static me.cxiy.cxvisuals.gui.ModuleMetrics.CONTENT_HEIGHT;

import me.cxiy.cxvisuals.gui.window.Pane;

final class ModulePane implements Pane {
  static final float WIDTH = 288.0F;
  static final float WIDE = 460.0F;
  static final float MIN_WIDTH = 264.0F;

  private static final float INSET = 12.0F;
  private static final float TOP = 2.0F;

  private final ModuleView view;
  private final float preferred;
  private final ModuleView.Painter painter =
      (c, t, control, hoverX, hoverY, dt) -> control.draw(c, t, hoverX, hoverY, dt);

  ModulePane(ModuleView view) {
    this(view, WIDTH);
  }

  ModulePane(ModuleView view, float preferred) {
    this.view = view;
    this.preferred = preferred;
  }

  boolean reveal(int pageIndex, String settingId) {
    return view.reveal(pageIndex, settingId);
  }

  @Override
  public float preferredWidth() {
    return preferred;
  }

  @Override
  public String title() {
    return view.module.name();
  }

  @Override
  public float minimumWidth() {
    return MIN_WIDTH;
  }

  @Override
  public void measure(Canvas c, float width) {
    view.arrange(c, width - INSET * 2.0F);
  }

  @Override
  public float preferredHeight(float limit) {
    return Math.min(limit, TOP + view.extent() + INSET);
  }

  @Override
  public void draw(
      Canvas c, Theme t, float x, float y, float w, float h, float mouseX, float mouseY, float dt) {
    view.place(x + INSET, y + TOP, w - INSET * 2.0F, h - TOP - INSET);
    view.advance(dt);
    c.pushLayer(CONTENT_HEIGHT);
    view.drawHeader(c, t, painter, mouseX, mouseY, dt);
    view.drawPage(c, t, painter, ModuleView.Reveal.ALWAYS, mouseX, mouseY, dt);
    c.popLayer();
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    view.mouseDown(mx, my);
    return view.grabbing();
  }

  @Override
  public void mouseUp(float mx, float my) {
    view.mouseUp(mx, my);
  }

  @Override
  public void mouseDrag(float mx, float my) {
    view.mouseDrag(mx, my);
  }

  @Override
  public boolean scroll(float mx, float my, float amount) {
    view.scroll(mx, my, amount);
    return true;
  }

  @Override
  public boolean secondaryDown(float mx, float my) {
    return view.secondaryDown(mx, my);
  }

  @Override
  public boolean keyDown(int keyCode, int scanCode, boolean control, boolean shift) {
    return view.keyDown(keyCode, scanCode, control, shift);
  }

  @Override
  public boolean charTyped(int codepoint) {
    return view.charTyped(codepoint);
  }

  @Override
  public boolean typing() {
    return view.typing();
  }

  @Override
  public void blur() {
    view.cancelCapture(null);
    view.release();
  }
}
