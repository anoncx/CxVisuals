package me.cxiy.cxvisuals.mc.modules.hud;

import java.nio.file.Path;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.modules.hud.HudModule;
import me.cxiy.cxvisuals.modules.hud.elements.MirrorElement;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorBackends;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorHub;
import net.fabricmc.loader.api.FabricLoader;

public final class Mirror {
  private static final String HELPER_FOLDER = "mirror";

  private static MirrorHub hub;

  private Mirror() {}

  public static MirrorHub hub() {
    if (hub == null) {
      Path folder =
          FabricLoader.getInstance()
              .getConfigDir()
              .resolve(CxVisualsClient.MOD_ID)
              .resolve(HELPER_FOLDER);
      hub =
          new MirrorHub(
              () -> MirrorBackends.platform(folder, CxVisualsClient.LOG::warn),
              CxVisualsClient.LOG::warn);
    }
    return hub;
  }

  static void frame(HudModule module, float uiScale) {
    MirrorElement element = module.mirror;
    String key = module.mirrorWindow.chosen();
    boolean on = element.shown.get() && !key.isEmpty();
    module.mirrorHub.want(on, key);
    if (on && element.w > 0.0F) {
      float shown = element.w * element.factor(module.scale()) * uiScale;
      module.mirrorHub.store().wanted(Math.round(shown * element.quality.get().oversample));
    }
  }

  static void rest(HudModule module) {
    module.mirrorHub.want(false, "");
  }

  public static void shutdown() {
    if (hub != null) {
      hub.shutdown();
    }
  }
}
