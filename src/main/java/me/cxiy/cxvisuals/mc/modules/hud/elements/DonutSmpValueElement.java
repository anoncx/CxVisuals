package me.cxiy.cxvisuals.mc.modules.hud.elements;

import me.cxiy.cxvisuals.core.settings.Settings;
import me.cxiy.cxvisuals.mc.modules.donutsmp.DonutSMP;
import me.cxiy.cxvisuals.modules.donutsmp.Trail;
import me.cxiy.cxvisuals.modules.hud.Anchor;

public final class DonutSmpValueElement extends DonutSmpReadout {
  private static final double HOME_Y = 130.0;

  public DonutSmpValueElement(Settings settings) {
    super(
        settings,
        "donutsmp_value",
        "DonutSMP value",
        "Inventory value",
        Anchor.TOP_RIGHT,
        -10.0,
        HOME_Y);
  }

  @Override
  protected int revision() {
    return DonutSMP.inventoryRevision();
  }

  @Override
  protected String value() {
    return DonutSMP.inventoryText();
  }

  @Override
  protected String subtitle() {
    return DonutSMP.inventoryNote();
  }

  @Override
  protected Trail trail() {
    return DonutSMP.inventoryTrail();
  }
}
