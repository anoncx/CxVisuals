package me.cxiy.cxvisuals.mc.modules.texturemixer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.gui.TextureMixerView;
import me.cxiy.cxvisuals.gui.Themes;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.gui.TextureMixerScreen;
import me.cxiy.cxvisuals.modules.texturemixer.Live;
import me.cxiy.cxvisuals.modules.texturemixer.PackWriter;
import me.cxiy.cxvisuals.modules.texturemixer.Studio;
import me.cxiy.cxvisuals.modules.texturemixer.TextureMixerModule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class TextureMixer implements Studio.Host, Live {
  private static final String MIX_FOLDER = "CxVisuals Mix";
  private static final String MIX_ID = "file/" + MIX_FOLDER;
  private static final String FOLDER = "texturemixer";
  private static final String PACKS = "packs";
  private static final long APPLY_NANOS = 700_000_000L;
  private static final long SAVE_NANOS = 450_000_000L;
  private static final String DIALOG_TITLE = Lang.key("Add texture packs");
  private static final String DIALOG_FILTER = Lang.key("Resource packs (ZIP)");
  private static final boolean MAC =
      System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

  private static volatile boolean quiet;
  private static TextureMixer self;

  private final TextureMixerModule module;
  private final KeyMapping openKey;
  private final ClientSettings client;
  private final Themes themes;
  private final Path packs;
  private final AtomicBoolean dialog = new AtomicBoolean();
  private Studio studio;
  private TextureMixerView view;
  private State state;
  private int count;
  private boolean dirty;
  private boolean busy;
  private boolean saved = true;
  private long dirtyAt;

  private TextureMixer(TextureMixerModule module, KeyMapping openKey, ClientSettings client) {
    this.module = module;
    this.openKey = openKey;
    this.client = client;
    this.themes = new Themes(client);
    this.packs =
        FabricLoader.getInstance()
            .getConfigDir()
            .resolve(CxVisualsClient.MOD_ID)
            .resolve(FOLDER)
            .resolve(PACKS);
    this.state = module.live.get() ? State.APPLIED : State.OFF;
  }

  public static TextureMixerModule create(McKeyBinder keys, ClientSettings client) {
    TextureMixerModule module =
        new TextureMixerModule(keys, TextureMixer::open, TextureMixer::reset);
    self = new TextureMixer(module, keys.mapping(TextureMixerModule.OPEN_KEY), client);
    return module;
  }

  public static boolean quiet() {
    return quiet;
  }

  public static void open() {
    if (self != null) {
      self.show();
    }
  }

  public static void reset() {
    if (self != null) {
      self.ensure().mix().clear();
    }
  }

  public static void tick(Minecraft mc) {
    if (self != null) {
      self.update(mc);
    }
  }

  private Studio ensure() {
    if (studio == null) {
      studio = new Studio(this);
      studio.mix().read(module.selection.get());
      count = studio.mix().size();
      studio
          .mix()
          .onChange(
              () -> {
                dirty = true;
                saved = false;
                dirtyAt = System.nanoTime();
                if (module.live.get()) {
                  state = State.WAITING;
                }
              });
      view = new TextureMixerView(studio, this, client.motion::get);
    }
    return studio;
  }

  private void show() {
    ensure();
    Minecraft mc = Minecraft.getInstance();
    mc.gui.setScreen(new TextureMixerScreen(mc.gui.screen(), view, themes));
  }

  private void update(Minecraft mc) {
    while (openKey.consumeClick()) {
      if (mc.gui.screen() == null) {
        show();
      }
    }
    if (studio == null) {
      return;
    }
    long now = System.nanoTime();
    if (!saved && now - dirtyAt > SAVE_NANOS) {
      saved = true;
      String json = studio.mix().write();
      if (!json.equals(module.selection.get())) {
        module.selection.set(json);
      }
    }
    if (!module.live.get()) {
      if (state != State.OFF && !busy) {
        state = State.OFF;
      }
      return;
    }
    if (state == State.OFF) {
      state = dirty ? State.WAITING : State.APPLIED;
    }
    if (!busy && dirty && now - dirtyAt > APPLY_NANOS) {
      apply(mc);
    }
  }

  public static void shutdown() {
    if (self != null) {
      self.flush();
    }
  }

  private void flush() {
    Minecraft mc = Minecraft.getInstance();
    if (studio == null || !dirty || busy) {
      return;
    }
    try {
      int written =
          PackWriter.write(
              mc.getResourcePackDirectory().resolve(MIX_FOLDER),
              studio.mix().snapshot(),
              studio::pack,
              meta());
      select(mc, written);
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.warn("Texture mix could not be saved: {}", e.toString());
    }
  }

  private void apply(Minecraft mc) {
    busy = true;
    dirty = false;
    state = State.APPLYING;
    Map<String, String> picks = studio.mix().snapshot();
    Path folder = mc.getResourcePackDirectory().resolve(MIX_FOLDER);
    String meta = meta();
    studio.run(
        () -> {
          try {
            int written = PackWriter.write(folder, picks, studio::pack, meta);
            mc.execute(() -> reload(mc, written));
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.warn("Texture mix could not be written: {}", e.toString());
            mc.execute(
                () -> {
                  busy = false;
                  state = State.FAILED;
                });
          }
        });
  }

  private void reload(Minecraft mc, int written) {
    try {
      select(mc, written);
      count = written;
      quiet = true;
      mc.reloadResourcePacks()
          .whenComplete(
              (ignored, error) ->
                  mc.execute(
                      () -> {
                        quiet = false;
                        busy = false;
                        state =
                            error != null
                                ? State.FAILED
                                : dirty ? State.WAITING : State.APPLIED;
                      }));
    } catch (RuntimeException e) {
      CxVisualsClient.LOG.warn("Texture mix could not be applied: {}", e.toString());
      quiet = false;
      busy = false;
      state = State.FAILED;
    }
  }

  private void select(Minecraft mc, int written) {
    PackRepository repository = mc.getResourcePackRepository();
    repository.reload();
    List<String> ids = new ArrayList<>(repository.getSelectedIds());
    if (written == 0) {
      ids.remove(MIX_ID);
    } else if (!ids.contains(MIX_ID) && repository.getPack(MIX_ID) != null) {
      ids.add(MIX_ID);
    }
    if (ids.equals(new ArrayList<>(repository.getSelectedIds()))) {
      return;
    }
    repository.setSelected(ids);
    mc.options.resourcePacks.clear();
    for (Pack pack : repository.getSelectedPacks()) {
      if (!pack.isFixedPosition()) {
        mc.options.resourcePacks.add(pack.getId());
      }
    }
    mc.options.save();
  }

  private static String meta() {
    PackFormat format = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
    return "{\"pack\":{\"description\":\"CxVisuals Texture Mixer\",\"min_format\":"
        + format.major()
        + ",\"max_format\":"
        + format.major()
        + "}}";
  }

  @Override
  public me.cxiy.cxvisuals.modules.texturemixer.Assets vanilla() {
    return new VanillaAssets(Minecraft.getInstance().getVanillaPackResources());
  }

  @Override
  public Path packsFolder() {
    return packs;
  }

  @Override
  public List<Path> installed() {
    List<Path> found = new ArrayList<>();
    Path folder = Minecraft.getInstance().getResourcePackDirectory();
    if (Files.isDirectory(folder)) {
      try (Stream<Path> list = Files.list(folder)) {
        list.forEach(found::add);
      } catch (IOException ignored) {
      }
    }
    return found;
  }

  @Override
  public String gameVersion() {
    return SharedConstants.getCurrentVersion().name();
  }

  @Override
  public void pickFiles() {
    if (!dialog.compareAndSet(false, true)) {
      return;
    }
    if (MAC) {
      ask();
      return;
    }
    Thread thread = new Thread(this::ask, "CxVisuals File Dialog");
    thread.setDaemon(true);
    thread.start();
  }

  private void ask() {
    try {
      String chosen;
      try (MemoryStack stack = MemoryStack.stackPush()) {
        PointerBuffer patterns = stack.mallocPointer(1);
        patterns.put(stack.UTF8("*.zip"));
        patterns.flip();
        chosen =
            TinyFileDialogs.tinyfd_openFileDialog(
                Lang.tr(DIALOG_TITLE), home(), patterns, Lang.tr(DIALOG_FILTER), true);
      }
      if (chosen == null || chosen.isBlank()) {
        return;
      }
      List<Path> files = new ArrayList<>();
      for (String part : chosen.split("\\|")) {
        if (!part.isBlank()) {
          files.add(Path.of(part));
        }
      }
      studio.importFiles(files);
    } catch (RuntimeException | LinkageError e) {
      CxVisualsClient.LOG.error("The file dialog could not open, drop packs onto the window", e);
    } finally {
      dialog.set(false);
    }
  }

  private static String home() {
    String user = System.getProperty("user.home", ".");
    Path downloads = Path.of(user, "Downloads");
    String folder = Files.isDirectory(downloads) ? downloads.toString() : user;
    return folder.endsWith(java.io.File.separator) ? folder : folder + java.io.File.separator;
  }

  @Override
  public void failed(String message) {
    CxVisualsClient.LOG.warn("Texture Mixer could not use {}", message);
  }

  @Override
  public State state() {
    return state;
  }

  @Override
  public int applied() {
    return count;
  }

  @Override
  public boolean enabled() {
    return module.live.get();
  }

  @Override
  public void enabled(boolean on) {
    module.live.set(on);
    if (on && studio != null) {
      dirty = true;
      dirtyAt = System.nanoTime();
      state = State.WAITING;
    }
  }
}
