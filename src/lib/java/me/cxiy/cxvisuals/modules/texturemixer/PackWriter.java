package me.cxiy.cxvisuals.modules.texturemixer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Stream;

public final class PackWriter {
  private static final String TEXTURES = "assets/minecraft/textures/";

  private PackWriter() {}

  public static int write(
      Path folder, Map<String, String> picks, Function<String, Pack> packs, String meta)
      throws IOException {
    if (Files.isDirectory(folder)) {
      try (Stream<Path> walk = Files.walk(folder)) {
        walk.sorted(Comparator.reverseOrder())
            .filter(path -> !path.equals(folder))
            .forEach(
                path -> {
                  try {
                    Files.delete(path);
                  } catch (IOException ignored) {
                  }
                });
      }
    }
    Files.createDirectories(folder);
    Files.writeString(folder.resolve("pack.mcmeta"), meta, StandardCharsets.UTF_8);
    int written = 0;
    for (Map.Entry<String, String> pick : picks.entrySet()) {
      Pack pack = packs.apply(pick.getValue());
      if (pack == null || !pack.has(pick.getKey())) {
        continue;
      }
      Path target = folder.resolve(TEXTURES + pick.getKey() + ".png");
      Files.createDirectories(target.getParent());
      Files.write(target, pack.raw(pick.getKey()));
      byte[] animation = pack.meta(pick.getKey());
      if (animation != null) {
        Files.write(target.resolveSibling(target.getFileName() + ".mcmeta"), animation);
      }
      written++;
    }
    return written;
  }
}
