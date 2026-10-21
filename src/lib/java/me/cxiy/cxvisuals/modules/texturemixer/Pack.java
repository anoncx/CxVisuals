package me.cxiy.cxvisuals.modules.texturemixer;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public final class Pack {
  public static final String VANILLA = "";
  private static final String TEXTURES = "textures/";
  private static final String PNG = ".png";
  private static final int CACHE = 768;

  private final String id;
  private final String name;
  private final Assets assets;
  private final Set<String> keys = new HashSet<>(1024);
  private final boolean imported;
  private final Map<String, Pixels> cache =
      new LinkedHashMap<>(64, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Pixels> eldest) {
          return size() > CACHE;
        }
      };
  private byte[] icon;

  public Pack(String id, String name, Assets assets, boolean imported) {
    this.id = id;
    this.name = name;
    this.assets = assets;
    this.imported = imported;
    for (String path : assets.paths()) {
      if (path.startsWith(TEXTURES) && path.endsWith(PNG)) {
        keys.add(path.substring(TEXTURES.length(), path.length() - PNG.length()));
      }
    }
  }

  public String id() {
    return id;
  }

  public String name() {
    return name;
  }

  public boolean imported() {
    return imported;
  }

  public int size() {
    return keys.size();
  }

  public boolean has(String key) {
    return keys.contains(key);
  }

  public Assets assets() {
    return assets;
  }

  public byte[] icon() {
    return icon;
  }

  public void icon(byte[] png) {
    icon = png;
  }

  public Pixels pixels(String key) {
    synchronized (cache) {
      Pixels hit = cache.get(key);
      if (hit != null) {
        return hit;
      }
    }
    if (!keys.contains(key)) {
      return null;
    }
    try {
      Pixels decoded = Pixels.decode(assets.read(TEXTURES + key + PNG));
      synchronized (cache) {
        cache.put(key, decoded);
      }
      return decoded;
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  public byte[] raw(String key) throws IOException {
    return assets.read(TEXTURES + key + PNG);
  }

  public byte[] meta(String key) {
    String path = TEXTURES + key + PNG + ".mcmeta";
    try {
      return assets.has(path) ? assets.read(path) : null;
    } catch (IOException e) {
      return null;
    }
  }

  public int coverage(Iterable<String> wanted) {
    int count = 0;
    for (String key : wanted) {
      if (keys.contains(key)) {
        count++;
      }
    }
    return count;
  }
}
