package me.cxiy.cxvisuals.modules.texturemixer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public final class Studio {
  public interface Host {
    Assets vanilla();

    Path packsFolder();

    List<Path> installed();

    String gameVersion();

    void pickFiles();

    void failed(String message);
  }

  public enum Phase {
    IDLE,
    LOADING,
    READY
  }

  public static final class Download {
    public final Modrinth.Hit hit;
    public volatile int percent;
    public volatile boolean finished;
    public volatile boolean failed;

    Download(Modrinth.Hit hit) {
      this.hit = hit;
    }
  }

  private static final String ZIP = ".zip";
  private static final int POOL_MAX = 4;

  private final Host host;
  private final Mix mix = new Mix();
  private final List<Pack> packs = new CopyOnWriteArrayList<>();
  private final Map<String, Pack> byId = new ConcurrentHashMap<>();
  private final Map<String, Download> downloads = new ConcurrentHashMap<>();
  private final ExecutorService pool;
  private final Thumbs thumbs;
  private final Modrinth modrinth = new Modrinth();
  private final Pack vanilla;
  private final List<Modrinth.Hit> results = new CopyOnWriteArrayList<>();
  private final Map<String, byte[]> covers = new ConcurrentHashMap<>();
  private volatile Catalog catalog;
  private volatile Phase phase = Phase.IDLE;
  private volatile int packRevision;
  private volatile boolean searching;
  private volatile boolean offline;
  private volatile int total;
  private volatile int searchGeneration;
  private Pack brush;
  private String lastQuery = "";
  private Modrinth.Sort lastSort = Modrinth.Sort.DOWNLOADS;

  public Studio(Host host) {
    this.host = host;
    int threads = Math.max(2, Math.min(POOL_MAX, Runtime.getRuntime().availableProcessors() - 1));
    this.pool =
        Executors.newFixedThreadPool(
            threads,
            runnable -> {
              Thread thread = new Thread(runnable, "CxVisuals Texture Mixer");
              thread.setDaemon(true);
              thread.setPriority(Thread.NORM_PRIORITY - 1);
              return thread;
            });
    this.vanilla = new Pack(Pack.VANILLA, "Vanilla", host.vanilla(), false);
    this.byId.put(Pack.VANILLA, vanilla);
    this.thumbs = new Thumbs(pool, byId::get);
  }

  public Mix mix() {
    return mix;
  }

  public Thumbs thumbs() {
    return thumbs;
  }

  public Pack vanilla() {
    return vanilla;
  }

  public List<Pack> packs() {
    return packs;
  }

  public Pack pack(String id) {
    return byId.get(id);
  }

  public int packRevision() {
    return packRevision;
  }

  public Catalog catalog() {
    return catalog;
  }

  public Phase phase() {
    return phase;
  }

  public Pack brush() {
    return brush;
  }

  public void brush(Pack pack) {
    brush = pack == null || pack == vanilla ? null : pack;
  }

  public void pick() {
    host.pickFiles();
  }

  public void run(Runnable job) {
    pool.execute(job);
  }

  public void open() {
    if (phase != Phase.IDLE) {
      return;
    }
    phase = Phase.LOADING;
    pool.execute(
        () -> {
          catalog = Catalog.build(vanilla.assets());
          scan();
          mix.retain(new java.util.HashSet<>(byId.keySet()));
          phase = Phase.READY;
        });
  }

  public void rescan() {
    pool.execute(this::scan);
  }

  private void scan() {
    List<Path> found = new ArrayList<>(host.installed());
    Path own = host.packsFolder();
    try {
      if (Files.isDirectory(own)) {
        try (Stream<Path> list = Files.list(own)) {
          list.forEach(found::add);
        }
      }
    } catch (IOException ignored) {
    }
    for (Path path : found) {
      load(path, own != null && path.startsWith(own));
    }
  }

  private void load(Path path, boolean imported) {
    String name = path.getFileName().toString();
    String lower = name.toLowerCase(Locale.ROOT);
    boolean zip = lower.endsWith(ZIP);
    boolean usable = zip || Files.isDirectory(path);
    if (!usable || lower.endsWith(".part") || name.startsWith("CxVisuals Mix")) {
      return;
    }
    if (byId.containsKey(name)) {
      return;
    }
    try {
      Assets assets = zip ? new ZipAssets(path) : new FolderAssets(path);
      String title = zip ? name.substring(0, name.length() - ZIP.length()) : name;
      Pack pack = new Pack(name, title, assets, imported);
      if (pack.size() == 0) {
        assets.close();
        return;
      }
      pack.icon(assets.icon());
      byId.put(name, pack);
      packs.add(pack);
      packRevision++;
    } catch (IOException | RuntimeException e) {
      host.failed(name);
    }
  }

  public void importFiles(List<Path> files) {
    pool.execute(
        () -> {
          for (Path file : files) {
            try {
              Path own = host.packsFolder();
              Path target = file;
              if (Files.isRegularFile(file) && !file.startsWith(own)) {
                Files.createDirectories(own);
                target = own.resolve(file.getFileName());
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
              }
              load(target, true);
            } catch (IOException e) {
              host.failed(file.getFileName().toString());
            }
          }
        });
  }

  public void remove(Pack pack) {
    if (!pack.imported()) {
      return;
    }
    if (brush == pack) {
      brush = null;
    }
    packs.remove(pack);
    byId.remove(pack.id());
    packRevision++;
    mix.retain(new java.util.HashSet<>(byId.keySet()));
    thumbs.forget();
    pool.execute(
        () -> {
          pack.assets().close();
          try {
            Files.deleteIfExists(host.packsFolder().resolve(pack.id()));
          } catch (IOException ignored) {
          }
        });
  }

  public String[] chosen(Entry entry) {
    List<String> textures = entry.textures();
    String[] ids = new String[textures.size()];
    for (int i = 0; i < ids.length; i++) {
      ids[i] = mix.pack(textures.get(i));
    }
    return ids;
  }

  public Pixels texture(String key, String packId) {
    Pack pack = byId.get(packId);
    Pixels pixels = pack == null ? null : pack.pixels(key);
    return pixels != null ? pixels : vanilla.pixels(key);
  }

  public Pixels texture(String key) {
    return texture(key, mix.pack(key));
  }

  public void search(String query, Modrinth.Sort sort, boolean more) {
    if (searching && more) {
      return;
    }
    int generation = ++searchGeneration;
    if (!more) {
      lastQuery = query;
      lastSort = sort;
      results.clear();
      total = 0;
    }
    searching = true;
    offline = false;
    int offset = more ? results.size() : 0;
    String q = more ? lastQuery : query;
    Modrinth.Sort s = more ? lastSort : sort;
    pool.execute(
        () -> {
          try {
            Modrinth.Page page = modrinth.search(q, s, offset);
            if (generation == searchGeneration) {
              results.addAll(page.hits());
              total = page.total();
            }
          } catch (IOException | RuntimeException e) {
            if (generation == searchGeneration) {
              offline = true;
            }
          } finally {
            if (generation == searchGeneration) {
              searching = false;
            }
          }
        });
  }

  public List<Modrinth.Hit> results() {
    return results;
  }

  public boolean searching() {
    return searching;
  }

  public boolean offline() {
    return offline;
  }

  public boolean exhausted() {
    return results.size() >= total;
  }

  public byte[] cover(Modrinth.Hit hit) {
    if (hit.icon().isEmpty()) {
      return null;
    }
    byte[] known = covers.get(hit.id());
    if (known != null) {
      return known.length == 0 ? null : known;
    }
    covers.put(hit.id(), new byte[0]);
    pool.execute(
        () -> {
          try {
            byte[] data = modrinth.icon(hit.icon());
            if (isPng(data)) {
              covers.put(hit.id(), data);
            }
          } catch (IOException | RuntimeException ignored) {
          }
        });
    return null;
  }

  private static boolean isPng(byte[] data) {
    return data.length > 8 && data[0] == (byte) 0x89 && data[1] == 'P' && data[2] == 'N';
  }

  public Download download(Modrinth.Hit hit) {
    Download current = downloads.get(hit.id());
    if (current != null && !current.failed) {
      return current;
    }
    Download job = new Download(hit);
    downloads.put(hit.id(), job);
    pool.execute(
        () -> {
          try {
            Path file =
                modrinth.download(
                    hit, host.gameVersion(), host.packsFolder(), percent -> job.percent = percent);
            load(file, true);
            job.finished = true;
          } catch (IOException | RuntimeException e) {
            job.failed = true;
            host.failed(hit.title());
          }
        });
    return job;
  }

  public Download downloadOf(Modrinth.Hit hit) {
    Download job = downloads.get(hit.id());
    if (job != null) {
      return job;
    }
    for (Pack pack : packs) {
      if (pack.id().equalsIgnoreCase(hit.slug() + ZIP)) {
        Download done = new Download(hit);
        done.finished = true;
        done.percent = 100;
        downloads.put(hit.id(), done);
        return done;
      }
    }
    return null;
  }

  public void close() {
    pool.shutdownNow();
  }
}
