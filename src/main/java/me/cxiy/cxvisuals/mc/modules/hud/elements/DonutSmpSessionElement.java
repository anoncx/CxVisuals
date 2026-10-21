package me.cxiy.cxvisuals.mc.modules.hud.elements;

import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.mc.modules.donutsmp.DonutSMP;
import me.cxiy.cxvisuals.modules.donutsmp.Trail;
import me.cxiy.cxvisuals.modules.hud.Anchor;

public final class DonutSmpSessionElement extends DonutSmpReadout {
  private static final double HOME_Y = 164.0;
  private static final String NOTE = "This session";

  public DonutSmpSessionElement(Settings settings) {
    super(
        settings,
        "donutsmp_session",
        "DonutSMP session",
        "Collected",
        Anchor.TOP_RIGHT,
        -10.0,
        HOME_Y);
  }

  @Override
  protected int revision() {
    return DonutSMP.sessionRevision();
  }

  @Override
  protected String value() {
    return DonutSMP.sessionText();
  }

  @Override
  protected String subtitle() {
    return NOTE;
  }

  @Override
  protected Trail trail() {
    return DonutSMP.collectedTrail();
  }
}
