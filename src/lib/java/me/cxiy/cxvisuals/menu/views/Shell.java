package me.cxiy.cxvisuals.menu.views;

import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.gui.Anim;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.menu.nav.Section;

public final class Shell extends MenuView {
  public static final float SIDE_FROM = 1100.0F;

  private static final float EDGE = 32.0F;
  private static final float GAP = 16.0F;
  private static final float BARE = 12.0F;
  private static final int NOTHING = 0;
  private static final int DOCK = 1;
  private static final int SIDE = 2;
  private static final int STAGE = 3;

  private final Dock dock;
  private final Stage stage;
  private final Side side;
  private final Anim sideOn = Anim.reveal(0.0F);
  private final Anim dockOn = Anim.reveal(1.0F);
  private final Anim boot = new Anim(0.0F, 0.8F, Anim.Curve.SNAPPY);
  private Section section = Section.HOME;
  private boolean bare;
  private boolean first = true;
  private int held = NOTHING;
  private float dockY;
  private float sideX;

  public Shell(Dock dock, Stage stage, Side side) {
    this.dock = dock;
    this.stage = stage;
    this.side = side;
  }

  public Dock dock() {
    return dock;
  }

  public Stage stage() {
    return stage;
  }

  public void motion(ClientSettings.WindowMotion motion) {
    stage.motion(motion);
  }

  public boolean moving() {
    return stage.moving() || !sideOn.settled() || !dockOn.settled();
  }

  public void show(Page page, Section next, int depth, boolean bareRoom) {
    section = next;
    bare = bareRoom;
    dock.section(next);
    stage.show(page, depth);
  }

  public void rest() {
    dock.rest();
    if (side != null) {
      side.rest();
    }
  }

  public static void room(float screenW, float screenH, boolean bare, float[] out) {
    if (bare) {
      out[0] = BARE;
      out[1] = BARE;
      out[2] = screenW - BARE * 2.0F;
      out[3] = screenH - BARE * 2.0F;
      return;
    }
    out[0] = EDGE;
    out[1] = EDGE;
    out[2] = screenW - EDGE * 2.0F;
    out[3] = screenH - EDGE * 2.0F - Dock.HEIGHT - GAP;
  }

  private boolean sided() {
    return section == Section.HOME
        && !bare
        && side != null
        && screenW >= SIDE_FROM
        && side.present();
  }

  private boolean overSide(float mx, float my) {
    return sided()
        && Kit.inside(mx, my, sideX, EDGE, Side.WIDTH, screenH - EDGE * 2.0F - Dock.HEIGHT - GAP);
  }

  private boolean overDock(float mx, float my) {
    return !bare && dock.contains(mx, my);
  }

  @Override
  public void draw(Canvas c, Theme t, float mouseX, float mouseY, float dt) {
    sideOn.set(sided() ? 1.0F : 0.0F);
    dockOn.set(bare ? 0.0F : 1.0F);
    if (first) {
      first = false;
      sideOn.snap(sideOn.target());
      dockOn.snap(dockOn.target());
      boot.set(1.0F);
    }
    sideOn.update(dt);
    dockOn.update(dt);
    boot.update(dt);
    float on = dockOn.get();
    float edge = BARE + (EDGE - BARE) * on;
    float below = (Dock.HEIGHT + GAP) * on;
    float roomW = screenW - edge * 2.0F - (Side.WIDTH + GAP) * sideOn.get();
    float roomH = screenH - edge * 2.0F - below;
    stage.draw(c, t, edge, edge, roomW, roomH, mouseX, mouseY, dt);
    if (side != null) {
      if (sideOn.get() > 0.01F) {
        float arrival = sideOn.get() * Math.min(1.0F, boot.get());
        sideX = screenW - EDGE - Side.WIDTH + (Side.WIDTH + EDGE) * (1.0F - arrival);
        c.pushAlpha(arrival);
        side.draw(c, t, sideX, EDGE, Side.WIDTH, roomH, mouseX, mouseY, dt);
        c.popAlpha();
      } else {
        side.rest();
      }
    }
    float arrived = on * Math.min(1.0F, boot.get());
    if (arrived > 0.01F) {
      dockY = screenH - edge - Dock.HEIGHT + (Dock.HEIGHT + edge) * (1.0F - arrived);
      c.pushAlpha(arrived);
      dock.draw(c, t, EDGE, dockY, screenW - EDGE * 2.0F, Dock.HEIGHT, mouseX, mouseY, dt);
      c.popAlpha();
    } else {
      dock.rest();
    }
  }

  @Override
  public boolean mouseDown(float mx, float my) {
    if (overDock(mx, my)) {
      if (side != null) {
        side.blur();
      }
      held = DOCK;
      return dock.mouseDown(mx, my);
    }
    if (overSide(mx, my)) {
      held = SIDE;
      side.mouseDown(mx, my);
      return true;
    }
    if (side != null) {
      side.blur();
    }
    Page page = stage.page();
    if (page == null) {
      return false;
    }
    held = STAGE;
    return page.mouseDown(mx, my);
  }

  @Override
  public void mouseUp(float mx, float my) {
    int target = held;
    held = NOTHING;
    if (target == DOCK) {
      dock.mouseUp(mx, my);
    } else if (target == SIDE) {
      side.mouseUp(mx, my);
    } else if (target == STAGE && stage.page() != null) {
      stage.page().mouseUp(mx, my);
    }
  }

  @Override
  public void mouseDrag(float mx, float my) {
    if (held == SIDE) {
      side.mouseDrag(mx, my);
    } else if (held == STAGE && stage.page() != null) {
      stage.page().mouseDrag(mx, my);
    }
  }

  @Override
  public boolean scroll(float mx, float my, float amount) {
    if (overSide(mx, my)) {
      return side.scroll(mx, my, amount);
    }
    Page page = stage.page();
    return !overDock(mx, my) && page != null && page.scroll(mx, my, amount);
  }

  @Override
  public boolean keyDown(int keyCode, int scanCode, int modifiers) {
    if (sided() && side.keyDown(keyCode, modifiers)) {
      return true;
    }
    Page page = stage.page();
    return page != null && page.keyDown(keyCode, scanCode, modifiers);
  }

  @Override
  public boolean charTyped(int codepoint) {
    if (sided() && side.charTyped(codepoint)) {
      return true;
    }
    Page page = stage.page();
    return page != null && page.charTyped(codepoint);
  }

  @Override
  public boolean typing() {
    Page page = stage.page();
    return (sided() && side.typing()) || (page != null && page.typing());
  }
}
