package me.cxiy.cxvisuals.mc.modules.texturemixer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import me.cxiy.cxvisuals.modules.texturemixer.Assets;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

final class VanillaAssets implements Assets {
  private static final String NAMESPACE = "minecraft";
  private static final String[] ROOTS = {
    "blockstates", "models/block", "textures/block", "textures/item", "textures/entity"
  };

  private final ResourceManager resources;
  private final Set<String> paths = new HashSet<>(4096);

  VanillaAssets(ResourceManager resources) {
    this.resources = resources;
    for (String root : ROOTS) {
      resources
          .listResources(root, id -> NAMESPACE.equals(id.getNamespace()))
          .keySet()
          .forEach(id -> paths.add(id.getPath()));
    }
  }

  @Override
  public Collection<String> paths() {
    return paths;
  }

  @Override
  public boolean has(String path) {
    return paths.contains(path);
  }

  @Override
  public byte[] read(String path) throws IOException {
    Resource resource =
        resources
            .getResource(Identifier.fromNamespaceAndPath(NAMESPACE, path))
            .orElseThrow(() -> new IOException("missing " + path));
    try (InputStream in = resource.open()) {
      return in.readAllBytes();
    }
  }
}
