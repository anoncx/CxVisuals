package me.cxiy.cxvisuals.mc.compat;

import java.util.Collection;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.util.Util;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeMap;

public final class Recipes {
  private Recipes() {}

  public static Collection<RecipeHolder<?>> vanilla(ClientLevel level) {
    VanillaPackResources pack = ServerPacksSource.createVanillaPackSource();
    List<RegistryDataLoader.RegistryData<?>> wanted =
        RegistryDataLoader.RELOADABLE_REGISTRIES.stream()
            .filter(data -> data.key().equals(Registries.RECIPE))
            .toList();
    try (MultiPackResourceManager manager =
        new MultiPackResourceManager(PackType.SERVER_DATA, List.of(pack.fullResources()))) {
      RegistryAccess.Frozen loaded =
          RegistryDataLoader.load(
                  manager,
                  level.registryAccess().listRegistries().toList(),
                  wanted,
                  Util.backgroundExecutor())
              .join();
      HolderLookup<Recipe<?>> lookup = loaded.lookupOrThrow(Registries.RECIPE);
      return RecipeMap.create(lookup).values();
    }
  }
}
