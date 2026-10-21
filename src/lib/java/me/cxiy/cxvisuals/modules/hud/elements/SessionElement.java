package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;

public final class SessionElement extends ReadoutElement {
  private long stamp = -1L;

  public SessionElement(Settings settings, double homeY) {
    super(settings, "session", "Session", Lang.key("SESSION"), Anchor.TOP_RIGHT, -10.0, homeY);
  }

  @Override
  public void sample(HudData data) {
    long seconds = data.sessionSeconds;
    if (seconds == stamp) {
      return;
    }
    stamp = seconds;
    long hours = seconds / 3600L;
    long minutes = seconds / 60L % 60L;
    long rest = seconds % 60L;
    StringBuilder text = new StringBuilder(8);
    if (hours > 0L) {
      text.append(hours).append(':');
      if (minutes < 10L) {
        text.append('0');
      }
    }
    text.append(minutes).append(':');
    if (rest < 10L) {
      text.append('0');
    }
    text.append(rest);
    show(text.toString(), Lang.tr(hours > 0L ? "Hours played" : "Minutes played"));
  }
}
