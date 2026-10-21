package me.cxiy.cxvisuals.modules.texturemixer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ZipAssets implements Assets {
  private static final String ROOT = "assets/minecraft/";
  private static final String MARKER = "pack.mcmeta";

  private final ZipFile zip;
  private final Map<String, ZipEntry> entries = new HashMap<>(1024);
  private ZipEntry iconEntry;

  public ZipAssets(Path file) throws IOException {
    zip = new ZipFile(file.toFile());
    String prefix = null;
    Enumeration<? extends ZipEntry> all = zip.entries();
    while (all.hasMoreElements()) {
      ZipEntry entry = all.nextElement();
      if (entry.isDirectory()) {
        continue;
      }
      String name = entry.getName();
      if (name.endsWith(MARKER)
          && name.indexOf('/') == name.lastIndexOf('/')
          && (prefix == null || name.length() - MARKER.length() < prefix.length())) {
        prefix = name.substring(0, name.length() - MARKER.length());
      }
    }
    String base = (prefix == null ? "" : prefix) + ROOT;
    iconEntry = zip.getEntry((prefix == null ? "" : prefix) + "pack.png");
    all = zip.entries();
    while (all.hasMoreElements()) {
      ZipEntry entry = all.nextElement();
      String name = entry.getName();
      if (!entry.isDirectory() && name.startsWith(base)) {
        entries.put(name.substring(base.length()), entry);
      }
    }
  }

  @Override
  public Collection<String> paths() {
    return entries.keySet();
  }

  @Override
  public boolean has(String path) {
    return entries.containsKey(path);
  }

  @Override
  public byte[] read(String path) throws IOException {
    ZipEntry entry = entries.get(path);
    if (entry == null) {
      throw new IOException("missing " + path);
    }
    synchronized (zip) {
      try (InputStream in = zip.getInputStream(entry)) {
        return in.readAllBytes();
      }
    }
  }

  @Override
  public byte[] icon() {
    if (iconEntry == null) {
      return null;
    }
    synchronized (zip) {
      try (InputStream in = zip.getInputStream(iconEntry)) {
        return in.readAllBytes();
      } catch (IOException e) {
        return null;
      }
    }
  }

  @Override
  public void close() {
    try {
      zip.close();
    } catch (IOException ignored) {
    }
  }
}
