package me.cxiy.cxvisuals.mc.modules.texturemixer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import me.cxiy.cxvisuals.modules.texturemixer.Assets;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.VanillaPackResources;
import net.minecraft.server.packs.resources.IoSupplier;

final class VanillaAssets implements Assets {
  private static final String NAMESPACE = "minecraft";
  private static final String[] ROOTS = {
    "blockstates", "models/block", "textures/block", "textures/item"
  };

  private final VanillaPackResources resources;
  private final Set<String> paths = new HashSet<>(4096);

  VanillaAssets(VanillaPackResources resources) {
    this.resources = resources;
    for (String root : ROOTS) {
      resources.listResources(
          PackType.CLIENT_RESOURCES, NAMESPACE, root, (id, supplier) -> paths.add(id.getPath()));
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
    IoSupplier<InputStream> supplier =
        resources.getResource(
            PackType.CLIENT_RESOURCES, Identifier.fromNamespaceAndPath(NAMESPACE, path));
    if (supplier == null) {
      throw new IOException("missing " + path);
    }
    try (InputStream in = supplier.get()) {
      return in.readAllBytes();
    }
  }
}
