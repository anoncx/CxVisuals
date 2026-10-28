package me.cxiy.cxvisuals.mc.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManager;

public final class Packs {
  private Packs() {}

  public static ResourceManager vanillaClient() {
    return Minecraft.getInstance().getVanillaPackResources().asResourceManager();
  }
}
