package me.cxiy.cxvisuals.mc.modules.zoom;

import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.Themes;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.mc.modules.idlecamera.IdleCamera;
import me.cxiy.cxvisuals.modules.zoom.ZoomModule;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class Zoom {
  private static final McCanvas CANVAS = new McCanvas();
  private static final float MAX_FRAME_SECONDS = 0.25F;
  private static final float MAX_FOV = 179.0F;
  private static final float PILL_HEIGHT = 36.0F;
  private static final float PILL_MIN_WIDTH = 92.0F;
  private static final float PILL_PADDING = 16.0F;
  private static final float PILL_DROP = 22.0F;
  private static final float BAR_HEIGHT = 3.0F;
  private static final float BAR_INSET = 7.0F;
  private static final float TEXT_TOP = 3.0F;
  private static final float POP = 0.08F;

  private static ZoomModule module;
  private static KeyMapping key;
  private static Themes themes;
  private static long lastNanos;

  private Zoom() {}

  public static ZoomModule create(McKeyBinder keys, ClientSettings client) {
    module = new ZoomModule(keys);
    key = keys.mapping(ZoomModule.ZOOM_KEY);
    themes = new Themes(client);
    return module;
  }

  public static ZoomModule module() {
    return module;
  }

  public static float fov(float fov) {
    if (module == null) {
      return fov;
    }
    long now = System.nanoTime();
    float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, MAX_FRAME_SECONDS);
    lastNanos = now;
    Minecraft mc = Minecraft.getInstance();
    if (!module.enabled.get()
        || mc.gui.screen() != null
        || mc.player == null
        || IdleCamera.weight > 0.0F) {
      module.release();
    } else {
      module.key(key.isDown());
    }
    module.frame(dt);
    float divisor = module.divisor();
    if (divisor == 1.0F) {
      return fov;
    }
    double half = Math.tan(Math.toRadians(Math.min(fov, MAX_FOV)) * 0.5);
    return (float) Math.toDegrees(Math.atan(half / divisor) * 2.0);
  }

  public static boolean scroll(double notches) {
    if (module == null || !module.active()) {
      return false;
    }
    Minecraft mc = Minecraft.getInstance();
    return mc.gui.screen() == null
        && mc.gui.overlay() == null
        && mc.player != null
        && module.scroll(notches);
  }

  public static double turn(double amount) {
    return module == null ? amount : amount * module.turnScale();
  }

  public static boolean smoothTurn() {
    return module != null && module.smoothTurn();
  }

  public static void overlay(GuiGraphicsExtractor graphics) {
    if (module == null || module.shown() <= 0.0F) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc.gui.hud.isHidden()) {
      return;
    }
    float shown = module.shown();
    float uiScale = McCanvas.interfaceScale(mc.getWindow().getHeight());
    Theme t = themes.get();
    String label = module.label();
    graphics.nextStratum();
    CANVAS.begin(graphics, uiScale, Math.max(1, mc.getWindow().getGuiScale()));
    float width =
        Math.max(PILL_MIN_WIDTH, CANVAS.textWidth(FontStyle.DOTS, label) + PILL_PADDING * 2.0F);
    float left = (mc.getWindow().getWidth() / uiScale - width) * 0.5F;
    float top = mc.getWindow().getHeight() / uiScale * 0.5F + PILL_DROP;
    float radius = Math.min(t.radius(), PILL_HEIGHT * 0.5F);
    CANVAS.pushAlpha(shown);
    CANVAS.pushTransform(
        1.0F - POP * (1.0F - shown), left + width * 0.5F, top + PILL_HEIGHT * 0.5F);
    CANVAS.shadow(left, top, width, PILL_HEIGHT, radius, 18.0F, 6.0F, t.shadow());
    if (t.glass()) {
      CANVAS.glass(left, top, width, PILL_HEIGHT, radius, t.paper());
    } else {
      CANVAS.roundRect(left, top, width, PILL_HEIGHT, radius, t.paper());
    }
    CANVAS.roundRectBorder(left, top, width, PILL_HEIGHT, radius, 1.0F, t.hair());
    CANVAS.text(
        FontStyle.DOTS,
        label,
        left + (width - CANVAS.textWidth(FontStyle.DOTS, label)) * 0.5F,
        top + TEXT_TOP,
        t.ink());
    float track = width - PILL_PADDING * 2.0F;
    float barTop = top + PILL_HEIGHT - BAR_INSET - BAR_HEIGHT;
    CANVAS.roundRect(left + PILL_PADDING, barTop, track, BAR_HEIGHT, BAR_HEIGHT * 0.5F, t.dotOff());
    float fill = Math.max(BAR_HEIGHT, track * module.fill());
    CANVAS.roundRect(left + PILL_PADDING, barTop, fill, BAR_HEIGHT, BAR_HEIGHT * 0.5F, t.red());
    CANVAS.popTransform();
    CANVAS.popAlpha();
    CANVAS.end();
  }
}
