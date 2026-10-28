package me.cxiy.cxvisuals.mc.modules.idlecamera;

import java.util.Random;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.compat.Cursors;
import me.cxiy.cxvisuals.modules.idlecamera.IdleCameraModule;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Director;
import me.cxiy.cxvisuals.modules.idlecamera.idle.CinematicController;
import me.cxiy.cxvisuals.modules.idlecamera.idle.IdleTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.player.LocalPlayer;

public final class IdleCamera {
  public static final IdleTracker IDLE = new IdleTracker();

  public static float weight;

  private static IdleCameraModule module;
  private static CinematicController controller;
  private static KeyMapping startKey;
  private static float lastHealth = Float.MAX_VALUE;
  private static int lastHurtTime;
  private static boolean cursorHidden;

  private IdleCamera() {}

  public static IdleCameraModule create(McKeyBinder keys) {
    module =
        new IdleCameraModule(
            keys,
            new IdleCameraModule.Hooks(
                IdleCamera::preview, () -> Minecraft.getInstance().options.fov().get()));
    controller = new CinematicController(module, new Director(module, new Random()), IDLE);
    startKey = keys.mapping(IdleCameraModule.START_KEY);
    return module;
  }

  public static IdleCameraModule module() {
    return module;
  }

  public static CinematicController controller() {
    return controller;
  }

  public static void tick(Minecraft mc) {
    while (startKey.consumeClick()) {
      controller.requestStart();
    }
    boolean hide = menuHidden();
    if (hide != cursorHidden) {
      cursorHidden = hide;
      if (hide || mc.gui.screen() != null) {
        Cursors.hidden(hide);
      }
    }
    LocalPlayer player = mc.player;
    if (player == null || mc.level == null) {
      lastHealth = Float.MAX_VALUE;
      lastHurtTime = 0;
      if (controller.active()) {
        controller.director().abort();
      }
      return;
    }
    float health = player.getHealth();
    int hurtTime = player.hurtTime;
    if (health < lastHealth || hurtTime > lastHurtTime) {
      controller.onDamage();
    }
    lastHealth = health;
    lastHurtTime = hurtTime;
  }

  private static boolean playing() {
    if (controller == null) {
      return false;
    }
    Director.State state = controller.director().state();
    return state == Director.State.ENTERING || state == Director.State.RUNNING;
  }

  public static boolean hudHidden() {
    return playing() && module.hideHud.get();
  }

  public static boolean menuHidden() {
    return playing() && Minecraft.getInstance().gui.screen() instanceof PauseScreen;
  }

  public static float letterboxAmount() {
    return module != null && module.letterbox.get() ? weight : 0.0F;
  }

  public static float letterboxRatio() {
    return (float) module.letterboxRatio.get().value;
  }

  private static void preview() {
    Minecraft.getInstance().gui.setScreen(null);
    controller.requestStart();
  }
}
