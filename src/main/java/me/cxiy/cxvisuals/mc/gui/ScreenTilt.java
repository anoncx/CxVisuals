package me.cxiy.cxvisuals.mc.gui;

import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.gui.spatial.Rig;
import me.cxiy.cxvisuals.gui.spatial.Warp;
import me.cxiy.cxvisuals.mc.CxVisuals;
import me.cxiy.cxvisuals.mc.menu.MenuStage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenTilt {
  private static final float MAX_FRAME_SECONDS = 0.1F;
  private static final float ANGLE = 1.2F;
  private static final float DISTANCE = 2.0F;
  private static final Rig RIG = new Rig();
  private static final Warp WARP = new Warp();

  private static long lastNanos;

  private ScreenTilt() {}

  private static boolean applies(Screen screen) {
    if (screen == null
        || screen instanceof ModulesScreen
        || screen instanceof HudEditorScreen
        || screen instanceof ChatScreen) {
      return false;
    }
    ClientSettings client = CxVisuals.registry().client();
    return client.spatial.get() && client.spatialScreens.get() && !MenuStage.owns(screen);
  }

  public static void begin(GuiGraphicsExtractor graphics, Screen screen) {
    if (GuiTilt.active()) {
      return;
    }
    if (!applies(screen)) {
      rest();
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    long now = System.nanoTime();
    float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, MAX_FRAME_SECONDS);
    lastNanos = now;
    double width = Math.max(1, mc.getWindow().getScreenWidth());
    double height = Math.max(1, mc.getWindow().getScreenHeight());
    double x = mc.mouseHandler.xpos();
    double y = mc.mouseHandler.ypos();
    boolean inside = x >= 0.0 && x < width && y >= 0.0 && y < height;
    RIG.configure(true, CxVisuals.registry().client().spatialStrength.get().floatValue() / 100.0F);
    RIG.feedPointer(
        inside ? (float) (x / width * 2.0 - 1.0) : 0.0F,
        inside ? (float) (y / height * 2.0 - 1.0) : 0.0F);
    RIG.update(dt);
    WARP.set(
        RIG.yaw() * ANGLE,
        RIG.pitch() * ANGLE,
        graphics.guiWidth() * 0.5F,
        graphics.guiHeight() * 0.5F,
        graphics.guiHeight() * DISTANCE);
    GuiTilt.begin(graphics, WARP, true);
  }

  public static void end(GuiGraphicsExtractor graphics) {
    GuiTilt.end(graphics);
  }

  public static void rest() {
    if (lastNanos != 0L) {
      lastNanos = 0L;
      RIG.rest();
      WARP.set(0.0F, 0.0F, 0.0F, 0.0F, 1.0F);
    }
  }

  public static boolean active() {
    return !WARP.flat() && applies(Minecraft.getInstance().gui.screen());
  }

  public static double unmapX(double x, double y) {
    WARP.unmap((float) x, (float) y, 0.0F);
    return WARP.x();
  }

  public static double unmapY(double x, double y) {
    WARP.unmap((float) x, (float) y, 0.0F);
    return WARP.y();
  }
}
