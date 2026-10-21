package me.cxiy.cxvisuals.gui.hud;

import java.util.List;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.core.module.ClientSettings.WindowMotion;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Motion;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.spatial.Rig;
import me.cxiy.cxvisuals.modules.hud.HostedElement;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudElement;
import me.cxiy.cxvisuals.modules.hud.HudModule;
import me.cxiy.cxvisuals.modules.hud.Reflow;

public final class HudStage {
  private static final float SAMPLE_SECONDS = 0.25F;
  private static final float POP_FROM = 0.86F;
  private static final float SLIDE_FROM = 12.0F;
  private static final float SHIFT_RESPONSE = 11.0F;
  private static final float SHIFT_REST = 0.05F;

  private final HudModule module;
  private final Supplier<Theme> theme;
  private static final float DISSOLVE_PAD = 18.0F;

  private final Supplier<WindowMotion> motion;
  private final List<HudElement> elements;
  private final boolean[] active;
  private final boolean[] visible;
  private final Reflow reflow;
  private final ClientSettings client;
  private final Rig rig = new Rig();
  private float sinceSample = SAMPLE_SECONDS;

  public HudStage(HudModule module, Supplier<Theme> theme) {
    this(module, theme, () -> WindowMotion.FADE, null);
  }

  public HudStage(HudModule module, Supplier<Theme> theme, Supplier<WindowMotion> motion) {
    this(module, theme, motion, null);
  }

  public HudStage(
      HudModule module,
      Supplier<Theme> theme,
      Supplier<WindowMotion> motion,
      ClientSettings client) {
    this.module = module;
    this.theme = theme;
    this.motion = motion;
    this.client = client;
    this.elements = module.elements();
    this.active = new boolean[elements.size()];
    this.visible = new boolean[elements.size()];
    this.reflow = new Reflow(elements.size());
  }

  public HudModule module() {
    return module;
  }

  public Rig rig() {
    return rig;
  }

  public void place(float screenW, float screenH) {
    for (int i = 0; i < elements.size(); i++) {
      HudElement element = elements.get(i);
      if (element instanceof HostedElement) {
        element.resolve(screenW, screenH, element.factor(1.0F));
      }
    }
  }

  public void draw(
      Canvas c, float screenW, float screenH, float dt, boolean editing, boolean info) {
    HudData data = module.data;
    sinceSample += dt;
    boolean sample = sinceSample >= SAMPLE_SECONDS;
    if (sample) {
      sinceSample = 0.0F;
    }
    Theme t = theme.get();
    float global = module.scale();
    boolean adaptive = module.contrast.get();
    boolean still = module.reduce.get();
    boolean animate = !still && module.animate.get();
    WindowMotion style = motion.get();
    data.still = still;
    data.editing = editing;
    boolean swaying = client != null && client.spatial.get() && client.spatialSway.get() && !still;
    rig.configure(
        swaying, client == null ? 0.0F : client.spatialStrength.get().floatValue() / 100.0F);
    rig.gate(editing ? 0.0F : 1.0F);
    rig.feedLook(data.yaw, data.pitch, dt);
    rig.update(dt);
    float centreX = screenW * 0.5F;
    float centreY = screenH * 0.5F;
    for (int i = 0; i < elements.size(); i++) {
      HudElement element = elements.get(i);
      if (element instanceof HostedElement hosted) {
        if (editing && hosted.shown.get() && !hosted.live() && hosted.w > 0.0F) {
          float factor = hosted.factor(global);
          c.pushTransform(factor, hosted.left, hosted.top);
          hosted.draw(c, t, data, hosted.left, hosted.top, dt);
          c.popTransform();
        }
        active[i] = false;
        visible[i] = false;
        continue;
      }
      boolean wanted = element.shown.get() && (editing || (info && element.present(data)));
      boolean entering = wanted && element.appear <= 0.0F;
      element.appear =
          animate && !element.instant()
              ? Motion.advance(style, element.appear, !wanted, dt)
              : wanted ? 1.0F : 0.0F;
      if (sample || entering) {
        element.sample(data);
      }
      active[i] = wanted;
      visible[i] = element.appear > 0.0F;
      if (visible[i]) {
        element.measure(c, data);
        element.resolve(screenW, screenH, element.factor(global));
      }
    }

    boolean arrange = !editing && module.arrange.get();
    if (arrange) {
      reflow.solve(elements, active, screenW, screenH, module.gap(), global);
    }
    float follow = still ? 1.0F : 1.0F - (float) Math.exp(-dt * SHIFT_RESPONSE);
    for (int i = 0; i < elements.size(); i++) {
      HudElement element = elements.get(i);
      if (element instanceof HostedElement) {
        continue;
      }
      if (!arrange) {
        element.shiftX = 0.0F;
        element.shiftY = 0.0F;
      } else if (active[i]) {
        element.shiftX = approach(element.shiftX, element.pushX, follow);
        element.shiftY = approach(element.shiftY, element.pushY, follow);
      }
      if (!visible[i]) {
        continue;
      }
      float shown = element.appear;
      float factor = element.factor(global);
      float width = element.w * factor;
      float height = element.h * factor;
      float left = element.left + element.shiftX;
      float top = element.top + element.shiftY;
      float pop = 1.0F;
      boolean folded = false;
      element.foldW = 1.0F;
      element.foldH = 1.0F;
      element.reveal = 1.0F;
      if (shown < 1.0F) {
        boolean leaving = !active[i];
        switch (style) {
          case MORPH -> {
            element.foldW = Motion.foldWidth(shown, leaving);
            element.foldH = Motion.foldHeight(shown, leaving);
            element.reveal = Motion.reveal(shown, leaving);
            folded = element.foldW < 1.0F || element.foldH < 1.0F;
          }
          case POP -> pop = Motion.pop(POP_FROM, shown, leaving);
          case SLIDE -> top += Motion.slide(SLIDE_FROM, shown);
          default -> {}
        }
        c.pushAlpha(style == WindowMotion.EVAPORATE ? 1.0F : Motion.alpha(style, shown));
      } else {
        c.pushAlpha(1.0F);
      }
      float x = left + (width - element.w) * 0.5F;
      float y = top + (height - element.h) * 0.5F;
      float erosion = shown < 1.0F ? Motion.erosion(style, shown) : 0.0F;
      boolean rigged = erosion <= 0.0F;
      if (rigged) {
        rig.push(c, element.depth(), centreX, centreY);
      }
      c.pushTransform(factor * pop, left + width * 0.5F, top + height * 0.5F);
      c.beginDissolve(
          x - DISSOLVE_PAD,
          y - DISSOLVE_PAD,
          element.w + DISSOLVE_PAD * 2.0F,
          element.h + DISSOLVE_PAD * 2.0F,
          erosion);
      if (folded) {
        float clipW =
            Math.min(element.w, HudPaint.folded(element.w, element.h, element.foldW, element.w));
        float clipH =
            Math.min(element.h, HudPaint.folded(element.w, element.h, element.foldH, element.h));
        c.pushClip(x + (element.w - clipW) * 0.5F, y + (element.h - clipH) * 0.5F, clipW, clipH);
      }
      if (adaptive) {
        c.pushContrast(t.paper());
      }
      element.draw(c, t, data, x, y, dt);
      if (element.veiled) {
        element.veiled = false;
        c.popAlpha();
      }
      if (adaptive) {
        c.popContrast();
      }
      if (folded) {
        c.popClip();
      }
      c.endDissolve();
      c.popTransform();
      if (rigged) {
        rig.pop(c);
      }
      c.popAlpha();
    }
  }

  private static float approach(float current, float target, float follow) {
    float next = current + (target - current) * follow;
    return Math.abs(target - next) < SHIFT_REST ? target : next;
  }
}
