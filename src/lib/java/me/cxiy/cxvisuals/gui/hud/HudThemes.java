package me.cxiy.cxvisuals.gui.hud;

import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.modules.hud.HudModule;

public final class HudThemes implements Supplier<Theme> {
  private final ClientSettings client;
  private final HudModule module;
  private final Supplier<Theme> window;
  private final boolean dark;
  private Theme current;
  private ClientSettings.Appearance appearance;
  private HudModule.Plate plate;
  private double opacity;

  public HudThemes(ClientSettings client, HudModule module, Supplier<Theme> window) {
    this(client, module, window, false);
  }

  private HudThemes(ClientSettings client, HudModule module, Supplier<Theme> window, boolean dark) {
    this.client = client;
    this.module = module;
    this.window = window;
    this.dark = dark;
  }

  public HudThemes dark() {
    return new HudThemes(client, module, window, true);
  }

  @Override
  public Theme get() {
    HudModule.Plate wantedPlate = module.plate.get();
    double wantedOpacity = module.opacity.get();
    if (wantedPlate == HudModule.Plate.WINDOW) {
      if (!dark) {
        return window.get();
      }
      wantedPlate =
          switch (client.style.get()) {
            case SOLID -> HudModule.Plate.SOLID;
            case CLEAR -> HudModule.Plate.CLEAR;
            case GLASS -> HudModule.Plate.GLASS;
          };
      wantedOpacity = client.opacity.get();
    }
    ClientSettings.Appearance wantedAppearance =
        dark ? ClientSettings.Appearance.DARK : client.appearance.get();
    if (current == null
        || wantedAppearance != appearance
        || wantedPlate != plate
        || wantedOpacity != opacity) {
      Theme base = wantedAppearance == ClientSettings.Appearance.LIGHT ? Theme.LIGHT : Theme.DARK;
      float amount = (float) (wantedOpacity / 100.0);
      current =
          switch (wantedPlate) {
            case CLEAR -> base.clear(amount);
            case GLASS -> base.glass(amount);
            default -> base;
          };
      appearance = wantedAppearance;
      plate = wantedPlate;
      opacity = wantedOpacity;
    }
    return current;
  }
}
