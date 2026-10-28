package me.cxiy.cxvisuals.mc.compat;

import java.util.Collection;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;

public final class Recipes {
  private Recipes() {}

  public static Collection<RecipeHolder<?>> vanilla(ClientLevel level) {
    VanillaPackResources pack = ServerPacksSource.createVanillaPackSource();
    try (MultiPackResourceManager manager =
        new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack))) {
      return new Loader(level.registryAccess()).load(manager).values();
    }
  }

  private static final class Loader extends RecipeManager {
    Loader(HolderLookup.Provider registries) {
      super(registries);
    }

    RecipeMap load(ResourceManager manager) {
      return prepare(manager, InactiveProfiler.INSTANCE);
    }
  }
}
