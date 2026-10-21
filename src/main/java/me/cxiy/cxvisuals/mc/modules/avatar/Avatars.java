package me.cxiy.cxvisuals.mc.modules.avatar;

import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.modules.avatar.AvatarModule;
import me.cxiy.cxvisuals.modules.avatar.anim.ClipMap;
import me.cxiy.cxvisuals.modules.avatar.bake.BakedAvatar;
import me.cxiy.cxvisuals.modules.avatar.bake.Tune;
import me.cxiy.cxvisuals.modules.avatar.library.AvatarEntry;
import me.cxiy.cxvisuals.modules.avatar.library.AvatarLibrary;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.player.Player;

public final class Avatars {
  private static final int MAX_REMOTES = 12;
  private static final long REMOTE_STALE_NANOS = 120_000_000_000L;

  private static final ExecutorService WORKER =
      Executors.newSingleThreadExecutor(
          task -> {
            Thread thread = new Thread(task, "CxVisuals Avatar");
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
          });

  private static AvatarModule module;
  private static AvatarLibrary library;
  private static final AvatarTextures TEXTURES = new AvatarTextures();
  private static AvatarEntry bound;
  private static int boundRevision = -1;
  private static int boundClips = -1;
  private static WorldRenderer renderer;
  private static boolean syncing;
  private static boolean replacing;
  private static boolean broken;
  private static KeyMapping nextKey;
  private static PreviewRenderer previews;

  private static final class RemoteEntry {
    final String hash;
    Tune tune;
    String clipKey;
    final WorldRenderer renderer;
    final AvatarTextures textures;
    long lastSeen;

    RemoteEntry(
        String hash, Tune tune, WorldRenderer renderer, AvatarTextures textures, long lastSeen) {
      this.hash = hash;
      this.tune = tune;
      this.renderer = renderer;
      this.textures = textures;
      this.lastSeen = lastSeen;
    }
  }

  private static final ConcurrentHashMap<String, RemoteEntry> remotes = new ConcurrentHashMap<>();
  private static RemoteEntry trial;

  private Avatars() {}

  public static AvatarModule create(McKeyBinder keys) {
    module = new AvatarModule(keys);
    Path root =
        FabricLoader.getInstance()
            .getConfigDir()
            .resolve(CxVisualsClient.MOD_ID)
            .resolve("avatars");
    library = new AvatarLibrary(root, WORKER, CxVisualsClient.LOG::warn);
    library.scan();
    library.onImported(
        entry -> {
          module.selected.set(entry.id());
          module.enabled.set(true);
        });
    library.onReady(
        entry -> {
          if (entry == selectedEntry()) {
            showTune(entry);
          }
        });
    module.selected.onChange(Avatars::selectionChanged);
    Runnable retune = Avatars::retune;
    module.up.onChange(retune);
    module.turn.onChange(retune);
    module.size.onChange(retune);
    module.lift.onChange(retune);
    module.clips.onChange(retune);
    module.detail.onChange(() -> library.rebakeAll(module.triangles(), module.textureEdge()));
    nextKey = keys.mapping(AvatarModule.ID + ".next");
    previews = new PreviewRenderer(Avatars::byHandle);
    McCanvas.previews(previews);
    return module;
  }

  public static AvatarModule module() {
    return module;
  }

  public static AvatarLibrary library() {
    return library;
  }

  public static void importFiles(List<Path> files) {
    if (library != null && !files.isEmpty()) {
      library.importFiles(files, module.triangles(), module.textureEdge());
    }
  }

  public static void load(AvatarEntry entry) {
    library.load(entry, module.triangles(), module.textureEdge());
  }

  public static void tick(Minecraft mc) {
    if (library == null) {
      return;
    }
    library.drain();
    while (nextKey != null && nextKey.consumeClick()) {
      next();
    }
    AvatarEntry entry = selectedEntry();
    if (entry != null && entry.state() == AvatarEntry.State.LOADING) {
      load(entry);
    }
    if (entry != bound || (entry != null && entry.revision() != boundRevision)) {
      rebind(entry);
    }
    if (renderer != null && bound != null && bound.clipRevision() != boundClips) {
      boundClips = bound.clipRevision();
      renderer.clips(bound.clipMap());
    }
  }

  public static void flushPreviews() {
    if (previews != null) {
      previews.flush();
    }
  }

  private static AvatarEntry byHandle(int handle) {
    List<AvatarEntry> entries = library.entries();
    for (int i = 0; i < entries.size(); i++) {
      if (entries.get(i).handle() == handle) {
        return entries.get(i);
      }
    }
    return library.ghost(handle);
  }

  public static AvatarEntry selectedEntry() {
    String id = module.selected.get();
    return id.isEmpty() ? null : library.byId(id);
  }

  public static boolean replacing() {
    return replacing;
  }

  public static boolean hasRemote(String uuid, String hash) {
    RemoteEntry entry = remotes.get(uuid);
    return entry != null && entry.hash.equals(hash);
  }

  public static void retuneRemote(String uuid, Tune tune, ClipMap clips, String clipKey) {
    RemoteEntry entry = remotes.get(uuid);
    if (entry == null) {
      return;
    }
    entry.tune = tune;
    if (!clipKey.equals(entry.clipKey)) {
      entry.clipKey = clipKey;
      entry.renderer.clips(clips);
    }
  }

  public static void putRemote(
      String uuid, String hash, BakedAvatar avatar, Tune tune, ClipMap clips, String clipKey) {
    RemoteEntry old = remotes.get(uuid);
    if (old != null && old.hash.equals(hash)) {
      old.lastSeen = System.nanoTime();
      retuneRemote(uuid, tune, clips, clipKey);
      return;
    }
    dropRemote(uuid);
    AvatarTextures textures = new AvatarTextures();
    try {
      textures.bind("remote/" + uuid + "/" + hash, avatar);
    } catch (RuntimeException e) {
      CxVisualsClient.LOG.warn("Couldn't bind shared avatar: {}", e.getMessage());
      return;
    }
    WorldRenderer renderer = new WorldRenderer(avatar, textures);
    renderer.clips(clips);
    RemoteEntry entry = new RemoteEntry(hash, tune, renderer, textures, System.nanoTime());
    entry.clipKey = clipKey;
    remotes.put(uuid, entry);
    while (remotes.size() > MAX_REMOTES) {
      String oldest = null;
      long age = Long.MAX_VALUE;
      for (var candidate : remotes.entrySet()) {
        if (candidate.getValue().lastSeen < age) {
          age = candidate.getValue().lastSeen;
          oldest = candidate.getKey();
        }
      }
      if (oldest == null) {
        break;
      }
      dropRemote(oldest);
    }
  }

  public static void tryOn(String hash, BakedAvatar avatar, Tune tune) {
    endTrial();
    AvatarTextures textures = new AvatarTextures();
    try {
      textures.bind("trial/" + hash, avatar);
    } catch (RuntimeException e) {
      CxVisualsClient.LOG.warn("Couldn't bind the avatar to try on: {}", e.getMessage());
      return;
    }
    WorldRenderer worn = new WorldRenderer(avatar, textures);
    worn.clips(ClipMap.AUTO);
    trial = new RemoteEntry(hash, tune, worn, textures, System.nanoTime());
  }

  public static boolean trying(String hash) {
    return trial != null && trial.hash.equals(hash);
  }

  public static void endTrial() {
    if (trial != null) {
      trial.textures.release();
      trial = null;
    }
  }

  public static void dropRemote(String uuid) {
    RemoteEntry entry = remotes.remove(uuid);
    if (entry != null) {
      entry.textures.release();
    }
  }

  public static void clearRemotes() {
    if (remotes.isEmpty()) {
      return;
    }
    for (RemoteEntry entry : remotes.values()) {
      entry.textures.release();
    }
    remotes.clear();
  }

  public static void pruneRemotes(long now) {
    List<String> gone = new ArrayList<>();
    for (var entry : remotes.entrySet()) {
      if (now - entry.getValue().lastSeen > REMOTE_STALE_NANOS) {
        gone.add(entry.getKey());
      }
    }
    for (String uuid : gone) {
      dropRemote(uuid);
    }
  }

  public static void begin(
      LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
    replacing = false;
    if (broken
        || module == null
        || !(state instanceof AvatarRenderState avatar)
        || avatar.isSpectator) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) {
      return;
    }
    if (avatar.id == mc.player.getId()) {
      if (trial != null) {
        try {
          trial.renderer.submit(
              avatar,
              mc.player,
              poseStack,
              collector,
              trial.tune,
              module.motionScale(),
              module.squash.get());
          replacing = true;
        } catch (RuntimeException e) {
          CxVisualsClient.LOG.warn("Trial avatar failed, taking it off: {}", e.getMessage());
          endTrial();
        }
        return;
      }
      if (renderer == null || !module.enabled.get()) {
        return;
      }
      try {
        renderer.submit(
            avatar,
            mc.player,
            poseStack,
            collector,
            bound.tune(),
            module.motionScale(),
            module.squash.get());
        replacing = true;
      } catch (RuntimeException e) {
        broken = true;
        CxVisualsClient.LOG.error(
            "Avatar rendering failed, showing the normal player for this session", e);
      }
      return;
    }
    if (mc.level == null) {
      return;
    }
    var entity = mc.level.getEntity(avatar.id);
    if (!(entity instanceof Player player)) {
      return;
    }
    RemoteEntry remote = remotes.get(player.getUUID().toString().toLowerCase(Locale.ROOT));
    if (remote == null) {
      return;
    }
    remote.lastSeen = System.nanoTime();
    try {
      remote.renderer.submit(
          avatar,
          player,
          poseStack,
          collector,
          remote.tune,
          module.motionScale(),
          module.squash.get());
      replacing = true;
    } catch (RuntimeException e) {
      CxVisualsClient.LOG.warn("Shared avatar failed, hiding it: {}", e.getMessage());
      dropRemote(player.getUUID().toString().toLowerCase(Locale.ROOT));
    }
  }

  public static void end() {
    replacing = false;
  }

  private static void next() {
    List<AvatarEntry> entries = library.entries();
    AvatarEntry current = selectedEntry();
    int index = current == null ? -1 : entries.indexOf(current);
    for (int step = 1; step <= entries.size() + 1; step++) {
      int candidate = (index + step) % (entries.size() + 1);
      if (candidate == entries.size()) {
        module.selected.set("");
        return;
      }
      AvatarEntry entry = entries.get(candidate);
      if (entry.state() == AvatarEntry.State.READY || entry.state() == AvatarEntry.State.LOADING) {
        module.selected.set(entry.id());
        module.enabled.set(true);
        return;
      }
    }
  }

  private static void selectionChanged() {
    AvatarEntry entry = selectedEntry();
    if (entry != null) {
      if (entry.state() == AvatarEntry.State.LOADING) {
        load(entry);
      }
      showTune(entry);
    }
  }

  private static void showTune(AvatarEntry entry) {
    syncing = true;
    try {
      module.show(entry.tune());
    } finally {
      syncing = false;
    }
  }

  private static void retune() {
    if (syncing) {
      return;
    }
    AvatarEntry entry = selectedEntry();
    if (entry != null && entry.state() != AvatarEntry.State.IMPORTING) {
      library.retune(entry, module.tune(), module.triangles(), module.textureEdge());
    }
  }

  private static void rebind(AvatarEntry entry) {
    bound = entry;
    boundRevision = entry == null ? -1 : entry.revision();
    renderer = null;
    TEXTURES.release();
    if (entry == null || entry.state() != AvatarEntry.State.READY || entry.world() == null) {
      if (entry == null) {
        boundRevision = -1;
      }
      return;
    }
    try {
      TEXTURES.bind(entry.handle() + "/" + entry.revision(), entry.world());
      renderer = new WorldRenderer(entry.world(), TEXTURES);
      renderer.clips(entry.clipMap());
      boundClips = entry.clipRevision();
    } catch (RuntimeException e) {
      CxVisualsClient.LOG.error("Avatar " + entry.name() + " couldn't be prepared", e);
      renderer = null;
    }
  }
}
