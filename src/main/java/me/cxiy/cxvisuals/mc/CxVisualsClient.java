package me.cxiy.cxvisuals.mc;

import java.nio.file.Path;
import java.util.List;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.module.ModuleRegistry;
import me.cxiy.cxvisuals.core.settings.ConfigStore;
import me.cxiy.cxvisuals.core.settings.Configs;
import me.cxiy.cxvisuals.core.settings.LegacyConfig;
import me.cxiy.cxvisuals.gui.AccountCard;
import me.cxiy.cxvisuals.gui.AvatarGallery;
import me.cxiy.cxvisuals.gui.ChatPanel;
import me.cxiy.cxvisuals.gui.CircleStage;
import me.cxiy.cxvisuals.gui.ClipEditor;
import me.cxiy.cxvisuals.gui.ConfigPanel;
import me.cxiy.cxvisuals.gui.ConfirmRow;
import me.cxiy.cxvisuals.gui.ContourPad;
import me.cxiy.cxvisuals.gui.DownloadPanel;
import me.cxiy.cxvisuals.gui.FriendsPanel;
import me.cxiy.cxvisuals.gui.IdentityPanel;
import me.cxiy.cxvisuals.gui.ModulesView;
import me.cxiy.cxvisuals.gui.Motion;
import me.cxiy.cxvisuals.gui.PlayerList;
import me.cxiy.cxvisuals.gui.Themes;
import me.cxiy.cxvisuals.gui.TrailStage;
import me.cxiy.cxvisuals.gui.WaypointPanel;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.mc.compat.TextInput;
import me.cxiy.cxvisuals.mc.gui.ModulesScreen;
import me.cxiy.cxvisuals.mc.gui.UiSounds;
import me.cxiy.cxvisuals.mc.menu.MenuStage;
import me.cxiy.cxvisuals.mc.modules.accounts.Accounts;
import me.cxiy.cxvisuals.mc.modules.animations.Animations;
import me.cxiy.cxvisuals.mc.modules.avatar.Avatars;
import me.cxiy.cxvisuals.mc.modules.avatar.LibraryGallery;
import me.cxiy.cxvisuals.mc.modules.chat.Chat;
import me.cxiy.cxvisuals.mc.modules.deaths.DeathEffects;
import me.cxiy.cxvisuals.mc.modules.download.Downloads;
import me.cxiy.cxvisuals.mc.modules.donutsmp.DonutSMP;
import me.cxiy.cxvisuals.mc.modules.environment.Environment;
import me.cxiy.cxvisuals.mc.modules.fullbright.Fullbright;
import me.cxiy.cxvisuals.mc.modules.hands.Hands;
import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import me.cxiy.cxvisuals.mc.modules.hud.Media;
import me.cxiy.cxvisuals.mc.modules.hud.Mirror;
import me.cxiy.cxvisuals.mc.modules.idlecamera.IdleCamera;
import me.cxiy.cxvisuals.mc.modules.inventory.Inventory;
import me.cxiy.cxvisuals.mc.modules.map.Maps;
import me.cxiy.cxvisuals.mc.modules.motionblur.MotionBlur;
import me.cxiy.cxvisuals.mc.modules.nametags.Nametags;
import me.cxiy.cxvisuals.mc.modules.shaders.EntityTrails;
import me.cxiy.cxvisuals.mc.modules.shaders.JumpCircles;
import me.cxiy.cxvisuals.mc.modules.shaders.Shaders;
import me.cxiy.cxvisuals.mc.modules.swings.Swings;
import me.cxiy.cxvisuals.mc.modules.texturemixer.TextureMixer;
import me.cxiy.cxvisuals.mc.modules.social.FriendsLink;
import me.cxiy.cxvisuals.mc.modules.social.Social;
import me.cxiy.cxvisuals.mc.modules.waypoints.Markers;
import me.cxiy.cxvisuals.mc.modules.zoom.Zoom;
import me.cxiy.cxvisuals.modules.avatar.AvatarModule;
import me.cxiy.cxvisuals.modules.avatar.library.LibraryClips;
import me.cxiy.cxvisuals.modules.download.DownloadModule;
import me.cxiy.cxvisuals.modules.friends.FriendsModule;
import me.cxiy.cxvisuals.modules.hud.HudModule;
import me.cxiy.cxvisuals.modules.map.MapModule;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;
import me.cxiy.cxvisuals.modules.social.SocialModule;
import me.cxiy.cxvisuals.modules.waypoints.WaypointsModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CxVisualsClient implements ClientModInitializer {
  public static final String MOD_ID = "cxvisuals";
  private static final String PACKAGE = "me.cxiy.cxvisuals.";
  public static final Logger LOG = LoggerFactory.getLogger("CxVisuals");

  private static final long SAVE_DELAY_NANOS = 1_000_000_000L;
  private static final String CONFIG_FILE = "cxvisuals.json";
  private static final String CONFIGS_FOLDER = "configs";
  private static final String LEGACY_CONFIG_FILE = "caridle.json";

  private static CxVisualsClient instance;
  private static String shownVersion = "dev";

  private ModuleRegistry registry;
  private ModulesView view;
  private long listed;
  private AvatarModule avatars;
  private WaypointsModule waypoints;
  private MapModule maps;
  private DownloadModule downloads;
  private FriendsModule friends;
  private SocialModule social;
  private ShadersModule shaders;
  private ClientSettings options;
  private Configs configs;
  private ConfigStore config;
  private KeyMapping openKey;
  private KeyMapping editKey;
  private KeyMapping playKey;
  private KeyMapping nextKey;
  private KeyMapping previousKey;
  private Module[] toggled;
  private KeyMapping[] toggleKeys;
  private boolean dirty;
  private long dirtySince;

  @Override
  public void onInitializeClient() {
    instance = this;
    McKeyBinder keys = new McKeyBinder();
    String version =
        FabricLoader.getInstance()
            .getModContainer(MOD_ID)
            .map(container -> container.getMetadata().getVersion().getFriendlyString())
            .orElse("dev");
    shownVersion = version.contains("+") ? version.substring(0, version.indexOf('+')) : version;

    registry = new ModuleRegistry(keys, version);
    registry.add(IdleCamera.create(keys));
    registry.add(MotionBlur.create(keys));
    registry.add(Zoom.create(keys, registry.client()));
    registry.add(Hands.create(keys));
    registry.add(Avatars.create(keys));
    registry.add(Nametags.create(keys));
    registry.add(Accounts.create(keys, registry.client()));
    registry.add(Hud.create(keys, registry.client()));
    registry.add(Animations.create(keys));
    registry.add(Environment.create(keys));
    shaders = Shaders.create(keys);
    registry.add(shaders);
    registry.add(DeathEffects.create(keys));
    registry.add(Swings.create(keys));
    registry.add(Fullbright.create(keys));
    registry.add(Chat.create(keys));
    registry.add(Inventory.create(keys, registry.client()));
    registry.add(DonutSMP.create(keys, registry.client()));
    registry.add(TextureMixer.create(keys, registry.client()));
    waypoints = Markers.create(keys);
    registry.add(waypoints);
    maps = Maps.create(keys, registry.client());
    registry.add(maps);
    downloads = Downloads.create(keys);
    registry.add(downloads);
    social = Social.create(keys);
    friends = FriendsLink.create(keys, social);
    registry.add(friends);
    registry.add(social);
    CxVisuals.init(registry);
    avatars = Avatars.module();
    Path directory = FabricLoader.getInstance().getConfigDir();
    options = registry.client();
    Updates.start(options);
    configs =
        new Configs(
            directory.resolve(MOD_ID).resolve(CONFIGS_FOLDER),
            registry.persisted(),
            LOG::warn,
            CxVisualsClient::portable);
    FriendsLink.configs(configs, options.config);

    openKey = keys.mapping("open");
    editKey = keys.mapping(HudModule.EDIT_KEY);
    playKey = keys.mapping(HudModule.PLAY_KEY);
    nextKey = keys.mapping(HudModule.NEXT_KEY);
    previousKey = keys.mapping(HudModule.PREVIOUS_KEY);
    Media.init(directory.resolve(MOD_ID));
    List<Module> modules = registry.modules();
    toggled = modules.toArray(new Module[0]);
    toggleKeys = new KeyMapping[toggled.length];
    for (int i = 0; i < toggled.length; i++) {
      toggleKeys[i] = keys.mapping(toggled[i].id() + ".toggle");
    }

    config = new ConfigStore(directory.resolve(CONFIG_FILE), registry.persisted(), LOG::warn);
    if (config.importLegacy(directory.resolve(LEGACY_CONFIG_FILE), LegacyConfig::mapKey)) {
      LOG.info("Imported settings from {}", LEGACY_CONFIG_FILE);
      markDirty();
    } else {
      config.load();
    }
    Runnable pace = () -> Motion.pace((float) (options.motionSpeed.get() / 100.0));
    options.motionSpeed.onChange(pace);
    pace.run();
    UiSounds.init();
    registry.onAnyChange(this::markDirty);
    Languages.init(options, new Themes(options));
    view = buildView();
    MenuStage.init(friends.friends, social.account, version, new Themes(options));
    Lang.onChange(this::relabel);

    ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    ClientLifecycleEvents.CLIENT_STARTED.register(
        client -> {
          keys.repair();
          McCanvas.prewarm();
        });
    ClientLifecycleEvents.CLIENT_STOPPING.register(
        client -> {
          MenuStage.savePlaytime();
          TextureMixer.shutdown();
          Maps.shutdown();
          flush();
          Media.shutdown();
          Mirror.shutdown();
        });
    LOG.info("CxVisuals {} loaded with {} module(s)", version, toggled.length);
  }

  public static String version() {
    return shownVersion;
  }

  public static void openSettings(Screen parent) {
    Minecraft mc = Minecraft.getInstance();
    if (instance != null && instance.view != null) {
      mc.gui.setScreen(new ModulesScreen(instance.fresh(), parent));
    }
  }

  private ModulesView buildView() {
    listed = listing();
    return new ModulesView(
        registry,
        new Themes(options),
        (setting, host) -> {
          if (setting == avatars.gallery) {
            return new AvatarGallery(
                new LibraryGallery(avatars, Avatars.library()), host, AvatarModule.MODEL_PAGE);
          }
          if (setting == avatars.animations) {
            return new ClipEditor(new LibraryClips(Avatars.library(), Avatars::selectedEntry));
          }
          if (setting == shaders.circlePreview) {
            return new CircleStage(shaders);
          }
          if (setting == shaders.trailPreview) {
            return new TrailStage(shaders);
          }
          if (setting == shaders.circleShape) {
            return new ContourPad(shaders.contour);
          }
          if (setting == downloads.panel) {
            return new DownloadPanel(downloads.recording);
          }
          if (setting == maps.storage) {
            return new ConfirmRow(
                Lang.tr("Delete this map"),
                Lang.tr("Erases everything explored in this world and dimension. Waypoints stay."),
                Lang.tr("Delete"),
                Lang.tr("Confirm delete"),
                Maps::erase);
          }
          if (setting == waypoints.list) {
            return new WaypointPanel(waypoints.waypoints);
          }
          if (setting == friends.people) {
            return new FriendsPanel(friends.friends, host);
          }
          if (setting == friends.chat) {
            return new ChatPanel(friends.friends, host);
          }
          if (setting == friends.link) {
            return new IdentityPanel(friends.identity);
          }
          if (setting == social.profile) {
            return new AccountCard(social.account);
          }
          if (setting == social.nearby) {
            return new PlayerList(
                social.account::nearby,
                Lang.tr("Block"),
                Lang.tr("Nobody nearby is sharing right now."),
                player -> social.account.actions().block(player.uuid(), player.name()));
          }
          if (setting == social.blocked) {
            return new PlayerList(
                social.account::blocked,
                Lang.tr("Unblock"),
                "",
                player -> social.account.actions().unblock(player.uuid()));
          }
          if (setting == social.erase) {
            return new ConfirmRow(
                Lang.tr("Delete cloud data"),
                Lang.tr("Erases your account, avatar and block list."),
                Lang.tr("Delete"),
                Lang.tr("Confirm delete"),
                () -> social.account.actions().erase());
          }
          return setting == options.configs ? new ConfigPanel(configs, options.config) : null;
        });
  }

  private ModulesView fresh() {
    long now = listing();
    if (now != listed) {
      view = buildView();
    }
    return view;
  }

  private long listing() {
    long bits = 0L;
    List<Module> entries = registry.entries();
    for (int i = 0; i < entries.size() && i < Long.SIZE; i++) {
      if (entries.get(i).listed()) {
        bits |= 1L << i;
      }
    }
    return bits;
  }

  private void relabel() {
    ModulesView next = buildView();
    next.adopt(view);
    view = next;
    if (Minecraft.getInstance().gui.screen() instanceof ModulesScreen screen) {
      screen.swap(next);
    }
    MenuStage.relabel();
  }

  private static boolean portable(String key) {
    return !key.startsWith("social.")
        && !key.startsWith("friends.")
        && !key.startsWith("waypoints.")
        && !key.equals("client.config")
        && !key.equals("client.workspace")
        && !key.equals("avatar.selected")
        && !key.equals("hud.toured")
        && !key.equals("client.interface.language")
        && !key.equals("client.language_picked");
  }

  private static boolean wantsText(Screen screen) {
    if (screen == null) {
      return false;
    }
    if (screen instanceof AbstractContainerScreen<?> container) {
      return Inventory.active(container);
    }
    return MenuStage.owns(screen) || screen.getClass().getName().startsWith(PACKAGE);
  }

  private void tick(Minecraft mc) {
    TextInput.sync(mc, wantsText(mc.gui.screen()));
    while (openKey.consumeClick()) {
      if (mc.gui.screen() == null) {
        mc.gui.setScreen(new ModulesScreen(fresh()));
      }
    }
    while (editKey.consumeClick()) {
      if (mc.gui.screen() == null) {
        Hud.openEditor();
      }
    }
    for (int i = 0; i < toggleKeys.length; i++) {
      while (toggleKeys[i].consumeClick()) {
        toggled[i].toggle();
      }
    }
    Languages.tick(mc);
    Updates.tick(mc);
    MenuStage.tickPlaytime(mc.level != null);
    IdleCamera.tick(mc);
    Avatars.tick(mc);
    Hud.tick(mc);
    Environment.tick(mc);
    JumpCircles.tick(mc);
    Accounts.tick(mc);
    EntityTrails.tick(mc);
    DeathEffects.tick(mc);
    Swings.tick(mc);
    Inventory.tick(mc);
    DonutSMP.tick(mc);
    TextureMixer.tick(mc);
    Social.tick(mc);
    FriendsLink.tick(mc);
    Markers.tick(mc);
    Maps.tick(mc);
    Downloads.tick(mc);
    Hud.keys(playKey, nextKey, previousKey);
    if (dirty && System.nanoTime() - dirtySince > SAVE_DELAY_NANOS) {
      flush();
    }
  }

  private void markDirty() {
    dirty = true;
    dirtySince = System.nanoTime();
  }

  private void flush() {
    if (dirty) {
      dirty = false;
      config.save();
    }
  }
}
