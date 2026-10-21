package me.cxiy.cxvisuals.modules.texturemixer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public final class FolderAssets implements Assets {
  private final Map<String, Path> files = new HashMap<>(1024);
  private final Path icon;

  public FolderAssets(Path folder) throws IOException {
    icon = folder.resolve("pack.png");
    Path root = folder.resolve("assets").resolve("minecraft");
    if (Files.isDirectory(root)) {
      try (Stream<Path> walk = Files.walk(root)) {
        walk.filter(Files::isRegularFile)
            .forEach(
                p ->
                    files.put(
                        root.relativize(p).toString().replace(File.separatorChar, '/'), p));
      }
    }
  }

  @Override
  public Collection<String> paths() {
    return files.keySet();
  }

  @Override
  public boolean has(String path) {
    return files.containsKey(path);
  }

  @Override
  public byte[] read(String path) throws IOException {
    Path file = files.get(path);
    if (file == null) {
      throw new IOException("missing " + path);
    }
    return Files.readAllBytes(file);
  }

  @Override
  public byte[] icon() {
    try {
      return Files.isRegularFile(icon) ? Files.readAllBytes(icon) : null;
    } catch (IOException e) {
      return null;
    }
  }
}
