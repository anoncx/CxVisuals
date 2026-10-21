package me.cxiy.cxvisuals.modules.avatar.library;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.stream.Stream;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.modules.avatar.anim.ClipMap;
import me.cxiy.cxvisuals.modules.avatar.anim.ClipSet;
import me.cxiy.cxvisuals.modules.avatar.bake.BakedAvatar;
import me.cxiy.cxvisuals.modules.avatar.bake.Baker;
import me.cxiy.cxvisuals.modules.avatar.bake.Tune;
import me.cxiy.cxvisuals.modules.avatar.format.ImportError;
import me.cxiy.cxvisuals.modules.avatar.format.ModelFormats;
import me.cxiy.cxvisuals.modules.avatar.format.RawModel;
import me.cxiy.cxvisuals.modules.avatar.format.SideFiles;

public final class AvatarLibrary {
  public static final long MAX_BYTES = 64L << 20;
  public static final int PREVIEW_TRIANGLES = 8_000;
  public static final int PREVIEW_EDGE = 256;

  private static final String META = "avatar.json";
  private static final String CACHE = "baked.bin";
  private static final String STAGING = ".import-";
  private static final int CACHE_MAGIC = 0x43584143;
  private static final int CACHE_VERSION = 1;
  private static final int ANY_BUDGET = -1;
  private static final int MAX_GHOSTS = 24;
  private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

  private record Baked(BakedAvatar world, BakedAvatar preview, int budget) {}

  private record Imported(
      String id,
      String name,
      String format,
      String hash,
      String source,
      long created,
      Tune tune,
      Baked baked) {}

  private static final class Recording implements SideFiles {
    final Path folder;
    final Set<Path> used = new LinkedHashSet<>();

    Recording(Path folder) {
      this.folder = folder.toAbsolutePath().normalize();
    }

    @Override
    public byte[] read(String relative) throws IOException {
      Path resolved = folder.resolve(relative.replace('\\', '/')).normalize();
      if (!resolved.startsWith(folder) || resolved.equals(folder)) {
        throw new IOException("outside the model folder: " + relative);
      }
      byte[] data = Files.readAllBytes(resolved);
      used.add(folder.relativize(resolved));
      return data;
    }
  }

  private final Path root;
  private final Executor worker;
  private final Consumer<String> log;
  private final List<AvatarEntry> entries = new ArrayList<>();
  private final List<AvatarEntry> view = Collections.unmodifiableList(entries);
  private final ConcurrentLinkedQueue<Runnable> results = new ConcurrentLinkedQueue<>();
  private final Map<String, String> hashes = new ConcurrentHashMap<>();
  private final Map<String, AvatarEntry> ghosts = new java.util.LinkedHashMap<>();
  private final List<Consumer<AvatarEntry>> importedListeners = new ArrayList<>(1);
  private final List<Consumer<AvatarEntry>> readyListeners = new ArrayList<>(2);
  private String notice = "";
  private int noticeId;
  private int placeholders;

  public AvatarLibrary(Path root, Executor worker, Consumer<String> log) {
    this.root = root;
    this.worker = worker;
    this.log = log;
  }

  public List<AvatarEntry> entries() {
    return view;
  }

  public AvatarEntry byId(String id) {
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i).id().equals(id)) {
        return entries.get(i);
      }
    }
    return null;
  }

  public String notice() {
    return notice;
  }

  public int noticeId() {
    return noticeId;
  }

  public void onImported(Consumer<AvatarEntry> listener) {
    importedListeners.add(listener);
  }

  public void onReady(Consumer<AvatarEntry> listener) {
    readyListeners.add(listener);
  }

  public void drain() {
    Runnable result;
    while ((result = results.poll()) != null) {
      result.run();
    }
  }

  public void scan() {
    entries.clear();
    hashes.clear();
    if (!Files.isDirectory(root)) {
      return;
    }
    List<AvatarEntry> found = new ArrayList<>();
    try (DirectoryStream<Path> folders = Files.newDirectoryStream(root)) {
      for (Path folder : folders) {
        String name = folder.getFileName().toString();
        if (!Files.isDirectory(folder)) {
          continue;
        }
        if (name.startsWith(STAGING)) {
          deleteTree(folder);
          continue;
        }
        if (name.startsWith(".")) {
          continue;
        }
        AvatarEntry entry = readMeta(folder);
        if (entry != null) {
          found.add(entry);
        }
      }
    } catch (IOException e) {
      log.accept("Couldn't list avatars: " + e.getMessage());
    }
    found.sort(Comparator.comparingLong(AvatarEntry::created).thenComparing(AvatarEntry::id));
    for (AvatarEntry entry : found) {
      entries.add(entry);
      if (!entry.hash().isEmpty()) {
        hashes.put(entry.hash(), entry.id());
      }
    }
  }

  public void importFiles(List<Path> files, int budget, int edge) {
    for (Path file : files) {
      String fileName = file.getFileName().toString();
      String extension = ModelFormats.extension(fileName).toUpperCase(Locale.ROOT);
      AvatarEntry placeholder =
          new AvatarEntry(
              "importing-" + ++placeholders,
              baseName(fileName),
              extension,
              "",
              fileName,
              System.currentTimeMillis(),
              AvatarEntry.State.IMPORTING,
              Tune.defaults(RawModel.Axis.Y));
      entries.add(placeholder);
      worker.execute(
          () -> {
            try {
              Imported imported = importOne(file, budget, edge);
              results.add(() -> finishImport(placeholder, imported));
            } catch (ImportError e) {
              results.add(() -> failImport(placeholder, fileName + ": " + e.getMessage()));
            } catch (IOException | RuntimeException e) {
              log.accept("Import of " + fileName + " failed: " + e);
              results.add(
                  () -> failImport(placeholder, Lang.format("{0} couldn't be read.", fileName)));
            }
          });
    }
  }

  public AvatarEntry adopt(String name, String hash, BakedAvatar avatar, Tune tune) {
    String known = hashes.get(hash);
    if (known != null && byId(known) != null) {
      return byId(known);
    }
    String id = "shared-" + hash.substring(0, 16);
    AvatarEntry entry =
        new AvatarEntry(
            id,
            name,
            Lang.key("SHARED"),
            hash,
            "",
            System.currentTimeMillis(),
            AvatarEntry.State.LOADING,
            tune);
    entry.baked(avatar, avatar, avatar.triangles);
    entries.add(entry);
    hashes.put(hash, id);
    Path folder = root.resolve(id);
    JsonObject meta = meta(entry, tune);
    worker.execute(
        () -> {
          try {
            Files.createDirectories(folder);
            writeCache(folder, new Baked(avatar, avatar, avatar.triangles));
            writeAtomically(
                folder.resolve(META), GSON.toJson(meta).getBytes(StandardCharsets.UTF_8));
          } catch (IOException | RuntimeException e) {
            log.accept("Couldn't save shared avatar " + name + ": " + e.getMessage());
          }
        });
    fire(readyListeners, entry);
    return entry;
  }

  public AvatarEntry ghost(String name, String hash, BakedAvatar preview) {
    AvatarEntry known = ghosts.get(hash);
    if (known != null) {
      return known;
    }
    if (ghosts.size() >= MAX_GHOSTS) {
      ghosts.remove(ghosts.keySet().iterator().next());
    }
    AvatarEntry entry =
        new AvatarEntry(
            "ghost-" + hash.substring(0, 16),
            name,
            Lang.key("SHARED"),
            hash,
            "",
            System.currentTimeMillis(),
            AvatarEntry.State.LOADING,
            Tune.defaults(RawModel.Axis.Y));
    entry.baked(preview, preview, preview.triangles);
    ghosts.put(hash, entry);
    return entry;
  }

  public AvatarEntry ghost(int handle) {
    for (AvatarEntry entry : ghosts.values()) {
      if (entry.handle() == handle) {
        return entry;
      }
    }
    return null;
  }

  public void load(AvatarEntry entry, int budget, int edge) {
    if (entry.loading() || entry.state() != AvatarEntry.State.LOADING) {
      return;
    }
    entry.loading(true);
    Path folder = root.resolve(entry.id());
    String source = entry.source();
    Tune tune = entry.tune();
    worker.execute(
        () -> {
          Baked baked = readCache(folder.resolve(CACHE), source.isEmpty() ? ANY_BUDGET : budget);
          String failure = null;
          if (baked == null) {
            try {
              baked = rebake(folder, source, tune, budget, edge);
              writeCache(folder, baked);
            } catch (ImportError e) {
              failure = e.getMessage();
            } catch (IOException | RuntimeException e) {
              failure = "The original model file is missing.";
            }
          }
          Baked done = baked;
          String reason = failure;
          results.add(
              () -> {
                entry.loading(false);
                if (done != null) {
                  entry.baked(done.world, done.preview, done.budget);
                  fire(readyListeners, entry);
                } else {
                  entry.state(AvatarEntry.State.BROKEN);
                  log.accept("Avatar " + entry.name() + " couldn't be loaded: " + reason);
                }
              });
        });
  }

  public void retune(AvatarEntry entry, Tune tune, int budget, int edge) {
    Tune previous = entry.tune();
    entry.tune(tune);
    if (entry.state() == AvatarEntry.State.IMPORTING) {
      return;
    }
    Path folder = root.resolve(entry.id());
    boolean reshape =
        !previous.sameShape(tune)
            && entry.state() == AvatarEntry.State.READY
            && !entry.source().isEmpty();
    String source = entry.source();
    JsonObject meta = meta(entry, tune);
    worker.execute(
        () -> {
          try {
            writeAtomically(
                folder.resolve(META), GSON.toJson(meta).getBytes(StandardCharsets.UTF_8));
            if (reshape) {
              Baked baked = rebake(folder, source, tune, budget, edge);
              writeCache(folder, baked);
              results.add(
                  () -> {
                    if (entry.tune().sameShape(tune)) {
                      entry.baked(baked.world, baked.preview, baked.budget);
                      fire(readyListeners, entry);
                    }
                  });
            }
          } catch (ImportError | IOException | RuntimeException e) {
            log.accept("Couldn't retune " + entry.name() + ": " + e.getMessage());
          }
        });
  }

  public void remap(AvatarEntry entry, ClipMap map) {
    entry.clipMap(map);
    if (entry.state() == AvatarEntry.State.IMPORTING) {
      return;
    }
    Path folder = root.resolve(entry.id());
    JsonObject meta = meta(entry, entry.tune());
    worker.execute(
        () -> {
          try {
            writeAtomically(
                folder.resolve(META), GSON.toJson(meta).getBytes(StandardCharsets.UTF_8));
          } catch (IOException | RuntimeException e) {
            log.accept("Couldn't save animations of " + entry.name() + ": " + e.getMessage());
          }
        });
  }

  public void rebakeAll(int budget, int edge) {
    for (AvatarEntry entry : entries) {
      if (entry.state() != AvatarEntry.State.READY
          || entry.budget() == budget
          || entry.source().isEmpty()) {
        continue;
      }
      Path folder = root.resolve(entry.id());
      String source = entry.source();
      Tune tune = entry.tune();
      worker.execute(
          () -> {
            try {
              Baked baked = rebake(folder, source, tune, budget, edge);
              writeCache(folder, baked);
              results.add(
                  () -> {
                    entry.baked(baked.world, baked.preview, baked.budget);
                    fire(readyListeners, entry);
                  });
            } catch (ImportError | IOException | RuntimeException e) {
              log.accept("Couldn't rebuild " + entry.name() + ": " + e.getMessage());
            }
          });
    }
  }

  public void delete(AvatarEntry entry) {
    entries.remove(entry);
    if (entry.state() == AvatarEntry.State.IMPORTING) {
      return;
    }
    hashes.remove(entry.hash(), entry.id());
    Path folder = root.resolve(entry.id());
    worker.execute(() -> deleteTree(folder));
  }

  private void finishImport(AvatarEntry placeholder, Imported imported) {
    int slot = entries.indexOf(placeholder);
    entries.remove(placeholder);
    AvatarEntry entry = imported.baked == null ? byId(imported.id) : null;
    if (entry == null) {
      if (imported.baked == null) {
        failImport(placeholder, Lang.format("{0} couldn't be imported.", placeholder.source()));
        return;
      }
      entry =
          new AvatarEntry(
              imported.id,
              imported.name,
              imported.format,
              imported.hash,
              imported.source,
              imported.created,
              AvatarEntry.State.LOADING,
              imported.tune);
      entry.baked(imported.baked.world, imported.baked.preview, imported.baked.budget);
      entries.add(slot < 0 ? entries.size() : Math.min(slot, entries.size()), entry);
      hashes.put(imported.hash, imported.id);
      for (String warning : imported.baked.world.warnings) {
        say(imported.name + ": " + warning);
      }
      int source = imported.baked.world.sourceTriangles;
      if (source > imported.baked.world.triangles) {
        say(
            Lang.format(
                "Simplified {0} {1} → {2} triangles.",
                imported.name, compact(source), compact(imported.baked.world.triangles)));
      }
      fire(readyListeners, entry);
    }
    fire(importedListeners, entry);
  }

  private void failImport(AvatarEntry placeholder, String message) {
    entries.remove(placeholder);
    say(message);
  }

  private void say(String message) {
    notice = message;
    noticeId++;
    log.accept(message);
  }

  private static void fire(List<Consumer<AvatarEntry>> listeners, AvatarEntry entry) {
    for (int i = 0; i < listeners.size(); i++) {
      listeners.get(i).accept(entry);
    }
  }

  private Imported importOne(Path file, int budget, int edge) throws ImportError, IOException {
    String fileName = file.getFileName().toString();
    if (Files.size(file) > MAX_BYTES) {
      throw new ImportError(Lang.tr("Files over 64 MB can't be imported."));
    }
    byte[] data = Files.readAllBytes(file);
    String hash = sha1(data);
    String known = hashes.get(hash);
    if (known != null) {
      return new Imported(known, "", "", hash, fileName, 0L, null, null);
    }
    Path folder = file.toAbsolutePath().getParent();
    Recording side = new Recording(folder);
    RawModel model = ModelFormats.read(fileName, data, side);
    Tune tune = Tune.defaults(model.up);
    Baked baked = bake(model, tune, budget, edge);
    String name = baseName(fileName);
    String id = uniqueId(slug(name), hash);
    long created = System.currentTimeMillis();
    Path staging = root.resolve(STAGING + UUID.randomUUID());
    Files.createDirectories(staging);
    try {
      Files.write(staging.resolve(fileName), data);
      for (Path relative : side.used) {
        Path target = staging.resolve(relative.toString()).normalize();
        if (!target.startsWith(staging) || target.equals(staging.resolve(fileName))) {
          continue;
        }
        Files.createDirectories(target.getParent());
        Files.copy(side.folder.resolve(relative), target, StandardCopyOption.REPLACE_EXISTING);
      }
      AvatarEntry draft =
          new AvatarEntry(
              id, name, model.format, hash, fileName, created, AvatarEntry.State.READY, tune);
      draft.stats(baked.world.triangles, baked.world.clips.length);
      Files.writeString(staging.resolve(META), GSON.toJson(meta(draft, tune)));
      writeCacheFile(staging.resolve(CACHE), baked);
      Path destination = root.resolve(id);
      try {
        Files.move(staging, destination, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException e) {
        Files.move(staging, destination);
      }
    } catch (IOException | RuntimeException e) {
      deleteTree(staging);
      throw e;
    }
    return new Imported(id, name, model.format, hash, fileName, created, tune, baked);
  }

  private Baked rebake(Path folder, String source, Tune tune, int budget, int edge)
      throws ImportError, IOException {
    Path file = folder.resolve(source);
    byte[] data = Files.readAllBytes(file);
    RawModel model = ModelFormats.read(source, data, new Recording(folder));
    return bake(model, tune, budget, edge);
  }

  private static Baked bake(RawModel model, Tune tune, int budget, int edge) throws ImportError {
    BakedAvatar world = Baker.bake(model, tune, budget, edge);
    BakedAvatar preview = Baker.bake(model, tune, PREVIEW_TRIANGLES, PREVIEW_EDGE);
    return new Baked(world, preview, budget);
  }

  private static Baked readCache(Path file, int budget) {
    if (!Files.isRegularFile(file)) {
      return null;
    }
    try (DataInputStream in =
        new DataInputStream(new BufferedInputStream(Files.newInputStream(file), 1 << 16))) {
      if (in.readInt() != CACHE_MAGIC || in.readInt() != CACHE_VERSION) {
        return null;
      }
      int stored = in.readInt();
      if (budget != ANY_BUDGET && stored != budget) {
        return null;
      }
      BakedAvatar world = BakedAvatar.read(in);
      BakedAvatar preview = BakedAvatar.read(in);
      return new Baked(world, preview, stored);
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  private static void writeCache(Path folder, Baked baked) throws IOException {
    Path temp = folder.resolve(CACHE + ".tmp");
    writeCacheFile(temp, baked);
    moveOver(temp, folder.resolve(CACHE));
  }

  private static void writeCacheFile(Path file, Baked baked) throws IOException {
    try (OutputStream raw = Files.newOutputStream(file);
        DataOutputStream out = new DataOutputStream(new BufferedOutputStream(raw, 1 << 16))) {
      out.writeInt(CACHE_MAGIC);
      out.writeInt(CACHE_VERSION);
      out.writeInt(baked.budget);
      baked.world.write(out);
      baked.preview.write(out);
    }
  }

  private static void writeAtomically(Path file, byte[] data) throws IOException {
    Path temp = file.resolveSibling(file.getFileName() + ".tmp");
    Files.write(temp, data);
    moveOver(temp, file);
  }

  private static void moveOver(Path from, Path to) throws IOException {
    try {
      Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private AvatarEntry readMeta(Path folder) {
    Path file = folder.resolve(META);
    if (!Files.isRegularFile(file)) {
      return null;
    }
    try (InputStream in = Files.newInputStream(file)) {
      JsonObject json =
          JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8))
              .getAsJsonObject();
      JsonObject tuneJson = json.has("tune") ? json.getAsJsonObject("tune") : new JsonObject();
      RawModel.Axis up = "Z".equals(text(tuneJson, "up", "Y")) ? RawModel.Axis.Z : RawModel.Axis.Y;
      Tune tune =
          new Tune(
              up,
              tuneJson.has("turn") ? tuneJson.get("turn").getAsInt() : 0,
              tuneJson.has("size") ? tuneJson.get("size").getAsDouble() : 100.0,
              tuneJson.has("lift") ? tuneJson.get("lift").getAsDouble() : 0.0,
              !tuneJson.has("clips") || tuneJson.get("clips").getAsBoolean());
      AvatarEntry entry =
          new AvatarEntry(
              folder.getFileName().toString(),
              text(json, "name", folder.getFileName().toString()),
              text(json, "format", "?"),
              text(json, "hash", ""),
              text(json, "source", ""),
              json.has("created") ? json.get("created").getAsLong() : 0L,
              AvatarEntry.State.LOADING,
              tune);
      entry.stats(
          json.has("triangles") ? json.get("triangles").getAsInt() : -1,
          json.has("clips") ? json.get("clips").getAsInt() : 0);
      entry.clipMap(animations(json));
      return entry;
    } catch (IOException
        | JsonParseException
        | IllegalStateException
        | ClassCastException
        | NumberFormatException e) {
      log.accept("Skipping avatar folder " + folder.getFileName() + ": " + e.getMessage());
      return null;
    }
  }

  private static JsonObject meta(AvatarEntry entry, Tune tune) {
    JsonObject json = new JsonObject();
    json.addProperty("name", entry.name());
    json.addProperty("format", entry.format());
    json.addProperty("hash", entry.hash());
    json.addProperty("source", entry.source());
    json.addProperty("created", entry.created());
    BakedAvatar world = entry.world();
    if (world != null) {
      json.addProperty("triangles", world.triangles);
      json.addProperty("clips", world.clips.length);
    }
    JsonObject tuneJson = new JsonObject();
    tuneJson.addProperty("up", tune.up().name());
    tuneJson.addProperty("turn", tune.turn());
    tuneJson.addProperty("size", tune.size());
    tuneJson.addProperty("lift", tune.lift());
    tuneJson.addProperty("clips", tune.clips());
    json.add("tune", tuneJson);
    ClipMap map = entry.clipMap();
    if (!map.allAuto()) {
      JsonObject animations = new JsonObject();
      for (int event = 0; event < ClipSet.EVENTS; event++) {
        String[] names = map.names(event);
        if (names == null) {
          continue;
        }
        JsonArray list = new JsonArray();
        for (String name : names) {
          list.add(name);
        }
        JsonObject eventJson = new JsonObject();
        eventJson.add("clips", list);
        if (names.length > 0) {
          eventJson.addProperty("mode", map.random(event) ? "random" : "order");
        }
        animations.add(eventKey(event), eventJson);
      }
      json.add("animations", animations);
    }
    return json;
  }

  private static String text(JsonObject json, String key, String fallback) {
    return json.has(key) && json.get(key).isJsonPrimitive()
        ? json.get(key).getAsString()
        : fallback;
  }

  private static String eventKey(int event) {
    return ClipSet.state(event).name().toLowerCase(Locale.ROOT);
  }

  private static ClipMap animations(JsonObject json) {
    if (!json.has("animations") || !json.get("animations").isJsonObject()) {
      return ClipMap.AUTO;
    }
    JsonObject animations = json.getAsJsonObject("animations");
    ClipMap map = ClipMap.AUTO;
    for (int event = 0; event < ClipSet.EVENTS; event++) {
      String key = eventKey(event);
      if (!animations.has(key) || !animations.get(key).isJsonObject()) {
        continue;
      }
      JsonObject eventJson = animations.getAsJsonObject(key);
      if (!eventJson.has("clips") || !eventJson.get("clips").isJsonArray()) {
        continue;
      }
      JsonArray list = eventJson.getAsJsonArray("clips");
      String[] names = new String[list.size()];
      int count = 0;
      for (JsonElement element : list) {
        if (element.isJsonPrimitive()) {
          names[count++] = element.getAsString();
        }
      }
      map =
          map.with(
              event,
              Arrays.copyOf(names, count),
              "random".equals(text(eventJson, "mode", "order")));
    }
    return map;
  }

  private String uniqueId(String slug, String hash) {
    for (int length = 6; length <= hash.length(); length += 2) {
      String id = slug + "-" + hash.substring(0, length);
      if (!Files.exists(root.resolve(id))) {
        return id;
      }
    }
    return slug + "-" + UUID.randomUUID();
  }

  static String slug(String name) {
    StringBuilder out = new StringBuilder();
    boolean dash = false;
    for (char c : name.toLowerCase(Locale.ROOT).toCharArray()) {
      if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
        out.append(c);
        dash = false;
      } else if (!dash && !out.isEmpty()) {
        out.append('-');
        dash = true;
      }
      if (out.length() >= 24) {
        break;
      }
    }
    while (!out.isEmpty() && out.charAt(out.length() - 1) == '-') {
      out.setLength(out.length() - 1);
    }
    return out.isEmpty() ? "avatar" : out.toString();
  }

  private static String baseName(String fileName) {
    int dot = fileName.lastIndexOf('.');
    return dot > 0 ? fileName.substring(0, dot) : fileName;
  }

  private static String compact(int triangles) {
    return triangles < 1000 ? Integer.toString(triangles) : Math.round(triangles / 1000.0) + "K";
  }

  private static String sha1(byte[] data) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private static void deleteTree(Path folder) {
    if (!Files.exists(folder)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(folder)) {
      List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
      for (Path path : paths) {
        Files.deleteIfExists(path);
      }
    } catch (IOException e) {
      folder.toFile().deleteOnExit();
    }
  }
}
