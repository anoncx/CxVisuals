package me.cxiy.cxvisuals.gui;

import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.module.ClientSettings;

public final class Themes implements Supplier<Theme> {
  private final ClientSettings client;
  private Theme current;
  private ClientSettings.Appearance appearance;
  private ClientSettings.WindowStyle style;
  private double opacity;

  public Themes(ClientSettings client) {
    this.client = client;
  }

  @Override
  public Theme get() {
    ClientSettings.Appearance wantedAppearance = client.appearance.get();
    ClientSettings.WindowStyle wantedStyle = client.style.get();
    double wantedOpacity = client.opacity.get();
    if (current == null
        || wantedAppearance != appearance
        || wantedStyle != style
        || wantedOpacity != opacity) {
      Theme base = wantedAppearance == ClientSettings.Appearance.LIGHT ? Theme.LIGHT : Theme.DARK;
      float amount = (float) (wantedOpacity / 100.0);
      current =
          switch (wantedStyle) {
            case SOLID -> base;
            case CLEAR -> base.clear(amount);
            case GLASS -> base.glass(amount);
          };
      appearance = wantedAppearance;
      style = wantedStyle;
      opacity = wantedOpacity;
    }
    return current;
  }
}
