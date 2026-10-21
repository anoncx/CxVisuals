package me.cxiy.cxvisuals.mc;

import me.cxiy.cxvisuals.core.module.ModuleRegistry;

public final class CxVisuals {
  private static ModuleRegistry registry;

  private CxVisuals() {}

  static void init(ModuleRegistry modules) {
    registry = modules;
  }

  public static ModuleRegistry registry() {
    return registry;
  }
}
