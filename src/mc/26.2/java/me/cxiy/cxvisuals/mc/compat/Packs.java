package me.cxiy.cxvisuals.mc.compat;

import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;

public final class Packs {
  private Packs() {}

  public static ResourceManager vanillaClient() {
    return new MultiPackResourceManager(
        PackType.CLIENT_RESOURCES, List.of(Minecraft.getInstance().getVanillaPackResources()));
  }
}
