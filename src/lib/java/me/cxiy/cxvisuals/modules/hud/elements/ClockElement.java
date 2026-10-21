package me.cxiy.cxvisuals.modules.hud.elements;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.modules.hud.Anchor;
import me.cxiy.cxvisuals.modules.hud.HudData;

public final class ClockElement extends ReadoutElement {
  private static final long DAY = 24000L;
  private static final long DAWN = 6000L;
  private static final int WORLD_STEP = 10;

  public final BoolSetting military;

  private int stamp = Integer.MIN_VALUE;

  public ClockElement(Settings settings, double homeY) {
    super(settings, "clock", "Clock", Lang.key("TIME"), Anchor.TOP_RIGHT, -10.0, homeY);
    this.military = group().bool("military", "24-hour", true);
  }

  @Override
  public void sample(HudData data) {
    boolean full = military.get();
    int world =
        (int) (Math.floorMod(data.dayTime + DAWN, DAY) * 1440L / DAY) / WORLD_STEP * WORLD_STEP;
    int next = (data.clockMinutes * 1440 + world) * 2 + (full ? 1 : 0);
    if (next == stamp) {
      return;
    }
    stamp = next;
    show(
        HudPaint.clock(data.clockMinutes, full),
        Lang.tr("World") + " " + HudPaint.clock(world, full));
  }
}
