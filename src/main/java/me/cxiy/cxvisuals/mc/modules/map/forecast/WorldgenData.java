package me.cxiy.cxvisuals.mc.modules.map.forecast;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Stream;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.LayeredRegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.server.RegistryLayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.CloseableResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.tags.TagLoader;

final class WorldgenData {
  private static CompletableFuture<HolderLookup.Provider> loaded;

  private WorldgenData() {}

  static synchronized CompletableFuture<HolderLookup.Provider> access(Executor executor) {
    if (loaded == null) {
      loaded = CompletableFuture.supplyAsync(() -> open(), executor).thenCompose(resources -> load(resources, executor));
    }
    return loaded;
  }

  private static CloseableResourceManager open() {
    PackRepository repository = ServerPacksSource.createVanillaTrustedRepository();
    repository.reload();
    repository.setSelected(List.of("vanilla"));
    List<PackResources> packs = repository.openAllSelected();
    return new MultiPackResourceManager(PackType.SERVER_DATA, packs);
  }

  private static CompletableFuture<HolderLookup.Provider> load(CloseableResourceManager resources, Executor executor) {
    LayeredRegistryAccess<RegistryLayer> layers = RegistryLayer.createRegistryAccess();
    List<Registry.PendingTags<?>> tags =
        TagLoader.loadTagsForExistingRegistries(resources, layers.getLayer(RegistryLayer.STATIC));
    RegistryAccess.Frozen context = layers.getAccessForLoading(RegistryLayer.WORLD);
    List<HolderLookup.RegistryLookup<?>> lookups = TagLoader.buildUpdatedLookups(context, tags);
    return RegistryDataLoader.load(resources, lookups, RegistryDataLoader.WORLD_REGISTRIES, executor)
        .thenApply(
            worldRegistries ->
                HolderLookup.Provider.create(
                    Stream.concat(lookups.stream(), worldRegistries.listRegistries())));
  }
}
