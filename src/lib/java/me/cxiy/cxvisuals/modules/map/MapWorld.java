package me.cxiy.cxvisuals.modules.map;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.modules.map.Relief.Terrain;

public final class MapWorld implements TileBaker.Source {
  public static final String EXTENSION = ".cxm";

  private static final int KEEP_REGIONS = 24;
  private static final int KEEP_THUMBS = 768;
  private static final int KEEP_CAVES = 12;
  private static final long SEARCH_PUBLISH = 70_000_000L;
  private static final long SAVE_NANOS = 20_000_000_000L;

  private final MapAtlas atlas;
  private final BlockPalette palette = new BlockPalette();
  private final TileBaker tiles = new TileBaker(this);
  private final TileBaker thumbnails = new TileBaker(this);
  private final ExecutorService searcher =
      Executors.newSingleThreadExecutor(
          run -> {
            Thread thread = new Thread(run, "CxVisuals Map Search");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
          });
  private volatile int searchTicket;
  private final Map<Long, Index> indexes = new ConcurrentHashMap<>();
  private final ExecutorService worker;
  private final Consumer<String> warn;
  private final Map<Long, Region> surface = new ConcurrentHashMap<>();
  private final Map<Long, Region> caves = new ConcurrentHashMap<>();
  private final Set<Long> known = ConcurrentHashMap.newKeySet();
  private final Set<Long> loading = new HashSet<>();
  private final Map<Long, Long> touched = new LinkedHashMap<>(64, 0.75F, true);
  private final Map<Long, int[]> thumbs =
      Collections.synchronizedMap(
          new LinkedHashMap<>(256, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) {
              return size() > KEEP_THUMBS;
            }
          });
  private final Map<Long, Integer> thumbRevisions = new ConcurrentHashMap<>();
  private final ConcurrentLinkedQueue<Runnable> later = new ConcurrentLinkedQueue<>();
  private volatile Terrain terrain = Terrain.RELIEF;
  private volatile Path folder;
  private String scope = "";
  private String dimension = "";
  private long lastSave = System.nanoTime();
  private int place;
  private boolean indexed;
  private volatile long bytes;

  public MapWorld(MapAtlas atlas, Consumer<String> warn) {
    this.atlas = atlas;
    this.warn = warn;
    this.worker =
        Executors.newSingleThreadExecutor(
            run -> {
              Thread thread = new Thread(run, "CxVisuals Map");
              thread.setDaemon(true);
              thread.setPriority(Thread.MIN_PRIORITY + 1);
              return thread;
            });
    atlas.baker(this::bake);
    atlas.palette(palette);
  }

  public static Path folder(Path root, String scope, String dimension) {
    return root.resolve(safe(scope)).resolve(safe(dimension));
  }

  static String safe(String value) {
    String lower = value.toLowerCase(Locale.ROOT);
    StringBuilder out = new StringBuilder(Math.min(lower.length(), 48) + 9);
    for (int i = 0; i < lower.length() && out.length() < 48; i++) {
      char c = lower.charAt(i);
      boolean plain = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '.' || c == '-';
      out.append(plain ? c : '_');
    }
    return out.append('-').append(Integer.toHexString(value.hashCode())).toString();
  }

  public BlockPalette palette() {
    return palette;
  }

  public String scope() {
    return scope;
  }

  public String dimension() {
    return dimension;
  }

  public boolean active() {
    return folder != null;
  }

  public boolean indexed() {
    return indexed;
  }

  public long bytes() {
    return bytes;
  }

  public int regions() {
    return known.size();
  }

  public Terrain terrain() {
    return terrain;
  }

  public void terrain(Terrain next) {
    if (next != terrain) {
      terrain = next;
      thumbs.clear();
      thumbRevisions.clear();
      atlas.clear();
    }
  }

  public void place(Path root, String nextScope, String nextDimension) {
    String s = nextScope == null ? "" : nextScope;
    String d = nextDimension == null ? "" : nextDimension;
    if (s.equals(scope) && d.equals(dimension)) {
      return;
    }
    leave();
    scope = s;
    dimension = d;
    if (s.isEmpty() || d.isEmpty() || root == null) {
      return;
    }
    Path next = folder(root, s, d);
    folder = next;
    int ticket = ++place;
    worker.execute(() -> index(next, ticket));
  }

  public void leave() {
    saveAll();
    folder = null;
    indexed = false;
    place++;
    surface.clear();
    caves.clear();
    known.clear();
    loading.clear();
    touched.clear();
    thumbs.clear();
    thumbRevisions.clear();
    bytes = 0L;
    scope = "";
    dimension = "";
    atlas.clear();
  }

  public void tick(long nanos, long nearRegion) {
    Runnable task;
    while ((task = later.poll()) != null) {
      task.run();
    }
    if (folder == null) {
      return;
    }
    if (nanos - lastSave > SAVE_NANOS) {
      lastSave = nanos;
      saveAll();
    }
    if (surface.size() > KEEP_REGIONS) {
      List<Long> order = new ArrayList<>(touched.keySet());
      for (int i = 0; i < order.size() && surface.size() > KEEP_REGIONS; i++) {
        long key = order.get(i);
        Region region = surface.get(key);
        if (region != null && !region.dirty() && region.flushed() && key != nearRegion) {
          surface.remove(key);
          touched.remove(key);
        }
      }
    }
  }

  public Region writable(int layer, int regionX, int regionZ) {
    long key = Region.key(regionX, regionZ);
    if (layer == TileKey.CAVE) {
      Region cave = caves.get(key);
      if (cave == null) {
        if (caves.size() >= KEEP_CAVES) {
          return null;
        }
        cave = new Region(regionX, regionZ);
        caves.put(key, cave);
      }
      return cave;
    }
    if (folder == null || !indexed) {
      return null;
    }
    Region region = surface.get(key);
    if (region != null) {
      touched.put(key, 0L);
      return region;
    }
    if (known.contains(key)) {
      load(key);
      return null;
    }
    region = new Region(regionX, regionZ);
    surface.put(key, region);
    touched.put(key, 0L);
    known.add(key);
    return region;
  }

  public void written(int layer, int chunkX, int chunkZ) {
    int blockX = chunkX << 4;
    int blockZ = chunkZ << 4;
    int top = layer == TileKey.CAVE ? TileKey.REGION_LEVEL : TileKey.MAX_LEVEL;
    for (int level = 0; level <= top; level++) {
      int shift = TileKey.EDGE_SHIFT + level;
      atlas.invalidate(TileKey.of(layer, level, blockX >> shift, blockZ >> shift));
    }
  }

  public void clearCaves() {
    if (caves.isEmpty()) {
      return;
    }
    for (Region cave : caves.values()) {
      for (int slot = 0; slot < Region.CHUNKS * Region.CHUNKS; slot++) {
        if (cave.stamps[slot] != 0L) {
          written(TileKey.CAVE, (cave.x << 5) + (slot & 31), (cave.z << 5) + (slot >> 5));
        }
      }
    }
    caves.clear();
  }

  public boolean seen(int layer, int chunkX, int chunkZ) {
    Region region =
        (layer == TileKey.CAVE ? caves : surface).get(Region.key(chunkX >> 5, chunkZ >> 5));
    return region != null && region.stamp(chunkX & 31, chunkZ & 31) != 0L;
  }

  public int height(int x, int z) {
    Region region = surface.get(Region.key(x >> Region.SHIFT, z >> Region.SHIFT));
    int lx = x & Region.MASK;
    int lz = z & Region.MASK;
    return region != null && region.seen(lx, lz) ? region.height(lx, lz) : Integer.MIN_VALUE;
  }

  public boolean known(int regionX, int regionZ) {
    return known.contains(Region.key(regionX, regionZ));
  }

  public boolean ready(int regionX, int regionZ) {
    long key = Region.key(regionX, regionZ);
    return surface.containsKey(key) || !known.contains(key);
  }

  public void importChunks(
      Path root, String intoScope, String intoDimension, List<MapShare.Chunk> chunks) {
    Path dir = folder(root, intoScope, intoDimension);
    boolean here = dir.equals(folder);
    List<MapShare.Chunk> files = new ArrayList<>();
    for (MapShare.Chunk chunk : chunks) {
      Region region = here ? surface.get(Region.key(chunk.x() >> 5, chunk.z() >> 5)) : null;
      if (region == null) {
        files.add(chunk);
        continue;
      }
      long have = region.stamp(chunk.x() & 31, chunk.z() & 31);
      if (have == 0L || have < chunk.stamp()) {
        region.write(chunk.x() & 31, chunk.z() & 31, chunk.column(), chunk.stamp());
        written(TileKey.SURFACE, chunk.x(), chunk.z());
      }
    }
    if (files.isEmpty()) {
      return;
    }
    int ticket = place;
    worker.execute(
        () -> {
          mergeInto(dir, files, palette, warn);
          later.add(
              () -> {
                if (ticket != place || !here) {
                  return;
                }
                for (MapShare.Chunk chunk : files) {
                  long key = Region.key(chunk.x() >> 5, chunk.z() >> 5);
                  known.add(key);
                  thumbs.remove(key);
                }
                atlas.clear();
              });
        });
  }

  public void collect(
      int minChunkX,
      int minChunkZ,
      int maxChunkX,
      int maxChunkZ,
      int limit,
      Consumer<List<MapShare.Chunk>> result) {
    Path from = folder;
    if (from == null) {
      result.accept(List.of());
      return;
    }
    Map<Long, Region> snapshot = new LinkedHashMap<>();
    for (Region region : surface.values()) {
      snapshot.put(region.key(), region.copy());
    }
    Set<Long> regions = new HashSet<>(known);
    worker.execute(
        () -> {
          List<MapShare.Chunk> out = new ArrayList<>();
          for (int rz = minChunkZ >> 5; rz <= maxChunkZ >> 5 && out.size() < limit; rz++) {
            for (int rx = minChunkX >> 5; rx <= maxChunkX >> 5 && out.size() < limit; rx++) {
              long key = Region.key(rx, rz);
              if (!regions.contains(key)) {
                continue;
              }
              Region region = snapshot.get(key);
              if (region == null) {
                region = read(from, rx, rz, palette);
              }
              if (region == null) {
                continue;
              }
              for (int slot = 0; slot < 1024 && out.size() < limit; slot++) {
                int cx = (rx << 5) + (slot & 31);
                int cz = (rz << 5) + (slot >> 5);
                long stamp = region.stamps[slot];
                if (stamp == 0L
                    || cx < minChunkX
                    || cx > maxChunkX
                    || cz < minChunkZ
                    || cz > maxChunkZ) {
                  continue;
                }
                Region.Column column = new Region.Column();
                region.read(slot & 31, slot >> 5, column);
                out.add(new MapShare.Chunk(cx, cz, stamp, column));
              }
            }
          }
          later.add(() -> result.accept(out));
        });
  }

  public static void mergeInto(
      Path dir, List<MapShare.Chunk> chunks, BlockPalette palette, Consumer<String> warn) {
    Map<Long, Region> touched = new LinkedHashMap<>();
    for (MapShare.Chunk chunk : chunks) {
      int rx = chunk.x() >> 5;
      int rz = chunk.z() >> 5;
      Region region =
          touched.computeIfAbsent(
              Region.key(rx, rz),
              key -> {
                Region read = read(dir, rx, rz, palette);
                return read != null ? read : new Region(rx, rz);
              });
      long have = region.stamp(chunk.x() & 31, chunk.z() & 31);
      if (have == 0L || have < chunk.stamp()) {
        region.write(chunk.x() & 31, chunk.z() & 31, chunk.column(), chunk.stamp());
      }
    }
    TileBaker baker = new TileBaker(new Isolated(touched));
    for (Region region : touched.values()) {
      write(dir, region, baker, Terrain.RELIEF, palette, warn);
    }
  }

  private record Index(long size, long stamp, List<String> keys) {}

  public void search(String query, double nearX, double nearZ, Consumer<BlockSearch.Result> done) {
    Path dir = folder;
    BlockSearch.Accumulator accumulator =
        dir == null ? null : BlockSearch.accumulator(palette, query, nearX, nearZ);
    int ticket = ++searchTicket;
    if (accumulator == null) {
      done.accept(BlockSearch.Result.NONE);
      return;
    }
    Map<Long, Region> live = new HashMap<>();
    for (Region region : surface.values()) {
      live.put(region.key(), region);
    }
    List<Long> order = new ArrayList<>(live.keySet());
    for (long key : known) {
      if (!live.containsKey(key)) {
        order.add(key);
      }
    }
    order.sort(
        Comparator.comparingDouble(
            key -> {
              double dx = (Region.keyX(key) + 0.5) * Region.SIZE - nearX;
              double dz = (Region.keyZ(key) + 0.5) * Region.SIZE - nearZ;
              return dx * dx + dz * dz;
            }));
    searcher.execute(
        () -> {
          long published = System.nanoTime();
          boolean fresh = false;
          for (long key : order) {
            if (ticket != searchTicket) {
              return;
            }
            Region region = live.get(key);
            if (region == null) {
              region = load(dir, key, accumulator);
            }
            if (region != null) {
              accumulator.add(region);
              fresh = true;
            }
            long now = System.nanoTime();
            if (fresh && now - published > SEARCH_PUBLISH) {
              publish(ticket, accumulator.snapshot(false), done);
              published = now;
              fresh = false;
            }
          }
          publish(ticket, accumulator.snapshot(true), done);
        });
  }

  private Region load(Path dir, long key, BlockSearch.Accumulator accumulator) {
    Path file = file(dir, Region.keyX(key), Region.keyZ(key));
    try {
      long size = Files.size(file);
      long stamp = Files.getLastModifiedTime(file).toMillis();
      Index index = indexes.get(key);
      byte[] bytes = null;
      if (index == null || index.size != size || index.stamp != stamp) {
        bytes = Files.readAllBytes(file);
        index = new Index(size, stamp, RegionCodec.keys(bytes));
        indexes.put(key, index);
      }
      boolean any = false;
      for (String name : index.keys) {
        if (accumulator.accepts(name)) {
          any = true;
          break;
        }
      }
      if (!any) {
        return null;
      }
      return RegionCodec.decode(bytes != null ? bytes : Files.readAllBytes(file), palette);
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  private void publish(int ticket, BlockSearch.Result result, Consumer<BlockSearch.Result> done) {
    later.add(
        () -> {
          if (ticket == searchTicket) {
            done.accept(result);
          }
        });
  }

  public void execute(Runnable task) {
    worker.execute(task);
  }

  public void later(Runnable task) {
    later.add(task);
  }

  public void erase(Path root) {
    Path doomed = folder;
    String s = scope;
    String d = dimension;
    leave();
    scope = "";
    dimension = "";
    if (doomed != null) {
      worker.execute(
          () -> {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(doomed, "*" + EXTENSION)) {
              for (Path file : files) {
                Files.deleteIfExists(file);
              }
            } catch (IOException e) {
              warn.accept("Couldn't erase the map: " + e.getMessage());
            }
          });
    }
    place(root, s, d);
  }

  public void saveAll() {
    Path to = folder;
    if (to == null) {
      return;
    }
    Terrain style = terrain;
    for (Region region : surface.values()) {
      if (!region.dirty() || region.empty()) {
        continue;
      }
      int revision = region.revision();
      Region copy = region.copy();
      Region source = region;
      region.saved(revision);
      worker.execute(
          () -> {
            Map<Long, Region> own = new LinkedHashMap<>();
            own.put(copy.key(), copy);
            int[] thumb = write(to, copy, new TileBaker(new Isolated(own)), style, palette, warn);
            if (thumb != null) {
              source.flushed(revision);
              if (to.equals(folder)) {
                thumbs.put(copy.key(), thumb);
              }
            }
          });
    }
  }

  public void shutdown() {
    saveAll();
    searcher.shutdownNow();
    worker.shutdown();
  }

  @Override
  public Region region(int layer, int regionX, int regionZ) {
    long key = Region.key(regionX, regionZ);
    if (layer == TileKey.CAVE) {
      return caves.get(key);
    }
    Region region = surface.get(key);
    if (region != null || !known.contains(key)) {
      return region;
    }
    Path from = folder;
    return from == null ? null : arrive(key, read(from, regionX, regionZ, palette), from);
  }

  @Override
  public int[] thumbnail(int regionX, int regionZ) {
    long key = Region.key(regionX, regionZ);
    if (!known.contains(key)) {
      return null;
    }
    Region region = surface.get(key);
    if (region != null && !region.empty()) {
      Integer at = thumbRevisions.get(key);
      int[] cached = thumbs.get(key);
      if (cached != null && at != null && at == region.revision()) {
        return cached;
      }
      int[] made = new int[TileBaker.PIXELS];
      if (thumbnails.thumbnail(region, terrain, made)) {
        TileBaker.bleed(made);
      }
      thumbs.put(key, made);
      thumbRevisions.put(key, region.revision());
      return made;
    }
    int[] cached = thumbs.get(key);
    if (cached != null) {
      return cached;
    }
    Path from = folder;
    if (from == null) {
      return null;
    }
    try {
      int[] read = RegionCodec.thumbnail(Files.readAllBytes(file(from, regionX, regionZ)));
      thumbs.put(key, read);
      return read;
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  private void bake(long key, int generation) {
    worker.execute(
        () -> {
          if (generation != atlas.generation()) {
            return;
          }
          int[] pixels = atlas.buffer();
          int[] ids = null;
          int[] grounds = null;
          boolean any;
          try {
            any = tiles.bake(key, terrain, pixels);
            if (any && TileKey.level(key) <= TileKey.REGION_LEVEL) {
              ids = atlas.buffer();
              grounds = atlas.buffer();
              tiles.details(key, ids, grounds);
            }
          } catch (RuntimeException e) {
            warn.accept("Couldn't draw a map tile: " + e.getMessage());
            any = false;
          }
          atlas.finish(key, generation, any ? pixels : null, ids, grounds);
        });
  }

  private void load(long key) {
    if (!loading.add(key)) {
      return;
    }
    Path from = folder;
    int rx = Region.keyX(key);
    int rz = Region.keyZ(key);
    worker.execute(() -> arrive(key, read(from, rx, rz, palette), from));
  }

  private Region arrive(long key, Region region, Path from) {
    int ticket = place;
    later.add(
        () -> {
          if (ticket != place || !from.equals(folder)) {
            return;
          }
          loading.remove(key);
          if (region == null) {
            known.remove(key);
          } else if (!surface.containsKey(key)) {
            surface.put(key, region);
            touched.put(key, 0L);
          }
        });
    return region;
  }

  private void index(Path dir, int ticket) {
    Set<Long> found = new HashSet<>();
    long size = 0L;
    if (Files.isDirectory(dir)) {
      try (DirectoryStream<Path> files = Files.newDirectoryStream(dir, "r.*" + EXTENSION)) {
        for (Path file : files) {
          String name = file.getFileName().toString();
          String[] parts = name.substring(2, name.length() - EXTENSION.length()).split("\\.");
          if (parts.length == 2) {
            try {
              found.add(Region.key(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
              size += Files.size(file);
            } catch (NumberFormatException ignored) {
              continue;
            }
          }
        }
      } catch (IOException e) {
        warn.accept("Couldn't list the map: " + e.getMessage());
      }
    }
    long total = size;
    later.add(
        () -> {
          if (ticket == place) {
            indexed = true;
            known.addAll(found);
            bytes = total;
            atlas.clear();
          }
        });
  }

  static Path file(Path dir, int regionX, int regionZ) {
    return dir.resolve("r." + regionX + "." + regionZ + EXTENSION);
  }

  static Region read(Path dir, int regionX, int regionZ, BlockPalette palette) {
    Path file = file(dir, regionX, regionZ);
    try {
      return Files.isRegularFile(file)
          ? RegionCodec.decode(Files.readAllBytes(file), palette)
          : null;
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  private static int[] write(
      Path dir,
      Region region,
      TileBaker baker,
      Terrain style,
      BlockPalette palette,
      Consumer<String> warn) {
    int[] thumb = new int[TileBaker.PIXELS];
    if (baker.thumbnail(region, style, thumb)) {
      TileBaker.bleed(thumb);
    }
    try {
      Files.createDirectories(dir);
      Path file = file(dir, region.x, region.z);
      Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
      Files.write(tmp, RegionCodec.encode(region, thumb, palette));
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
      return thumb;
    } catch (IOException | RuntimeException e) {
      warn.accept("Couldn't save the map: " + e.getMessage());
      return null;
    }
  }

  private record Isolated(Map<Long, Region> regions) implements TileBaker.Source {
    @Override
    public Region region(int layer, int regionX, int regionZ) {
      return layer == TileKey.SURFACE ? regions.get(Region.key(regionX, regionZ)) : null;
    }

    @Override
    public int[] thumbnail(int regionX, int regionZ) {
      return null;
    }
  }
}
