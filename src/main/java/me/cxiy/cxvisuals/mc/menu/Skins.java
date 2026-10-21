package me.cxiy.cxvisuals.mc.menu;

import java.util.Map;
import java.util.WeakHashMap;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.gui.AccountsScreen;
import me.cxiy.cxvisuals.mc.gui.GlassPipeline;
import me.cxiy.cxvisuals.mc.gui.HudEditorScreen;
import me.cxiy.cxvisuals.mc.gui.LanguageScreen;
import me.cxiy.cxvisuals.mc.gui.ModulesScreen;
import me.cxiy.cxvisuals.mc.gui.ShapePipeline;
import me.cxiy.cxvisuals.mc.gui.SwitchScreen;
import me.cxiy.cxvisuals.mc.menu.bridge.FormBridge;
import me.cxiy.cxvisuals.mc.menu.bridge.TitleBridge;
import me.cxiy.cxvisuals.mc.mixin.ScreenAccessor;
import me.cxiy.cxvisuals.menu.nav.Section;
import me.cxiy.cxvisuals.menu.views.FormPage;
import me.cxiy.cxvisuals.menu.views.FramePage;
import me.cxiy.cxvisuals.menu.views.Page;
import me.cxiy.cxvisuals.menu.views.WiiHome;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.CreateBuffetWorldScreen;
import net.minecraft.client.gui.screens.CreateFlatWorldScreen;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.ErrorScreen;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.OutOfMemoryScreen;
import net.minecraft.client.gui.screens.PresetFlatWorldScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.gui.screens.worldselection.OptimizeWorldScreen;

public final class Skins {
  public enum Kind {
    NONE,
    NATIVE,
    FRAME
  }

  static final class Skin {
    final Kind kind;
    final Page page;
    final Section section;
    final TitleBridge title;
    boolean shrunk;

    Skin(Kind kind, Page page, Section section, TitleBridge title) {
      this.kind = kind;
      this.page = page;
      this.section = section;
      this.title = title;
    }
  }

  private static final String VANILLA = "net.minecraft.client.gui.screens.";
  private static final String[] BESPOKE = {
    ".inventory.",
    ".advancements.",
    ".achievement.",
    ".recipebook.",
    ".reporting.",
    ".social.",
    ".debug.",
    ".packs.",
    ".telemetry.",
    ".dialog.",
    ".friends."
  };
  private static final Skin PLAIN = new Skin(Kind.NONE, null, Section.OTHER, null);
  private static final Map<Screen, Skin> SKINS = new WeakHashMap<>();

  private static String version = "";
  private static Screen recent;
  private static Skin recentSkin;

  private Skins() {}

  static void init(String modVersion) {
    version = modVersion;
  }

  static void clear() {
    SKINS.clear();
    recent = null;
    recentSkin = null;
  }

  static Skin of(Screen screen) {
    if (screen == null || !MenuStage.enabled() || Minecraft.getInstance().level != null) {
      return null;
    }
    Skin skin = screen == recent ? recentSkin : SKINS.get(screen);
    if (skin == null) {
      if (screen.children().isEmpty()
          && ((ScreenAccessor) screen).cxvisuals$renderables().isEmpty()) {
        return null;
      }
      skin = create(screen);
      SKINS.put(screen, skin);
    }
    recent = screen;
    recentSkin = skin;
    return skin.kind == Kind.NONE ? null : skin;
  }

  public static boolean framed(Screen screen) {
    Skin skin = SKINS.get(screen);
    return skin != null
        && skin.kind == Kind.FRAME
        && skin.shrunk
        && MenuStage.enabled()
        && Minecraft.getInstance().level == null;
  }

  private static Skin create(Screen screen) {
    if (screen instanceof ModulesScreen
        || screen instanceof AccountsScreen
        || screen instanceof SwitchScreen
        || screen instanceof HudEditorScreen
        || screen instanceof LanguageScreen
        || screen instanceof ChatScreen
        || screen instanceof DeathScreen
        || screen instanceof WinScreen
        || screen instanceof AccessibilityOnboardingScreen
        || screen instanceof OutOfMemoryScreen) {
      return PLAIN;
    }
    ShapePipeline.recheck();
    GlassPipeline.recheck();
    try {
      if (screen instanceof TitleScreen) {
        TitleBridge bridge = new TitleBridge(screen, version);
        return new Skin(
            Kind.NATIVE,
            new WiiHome(
                bridge, MenuStage.quick(), MenuStage.sfx(), MenuStage.layout(), MenuStage.pulse()),
            Section.HOME,
            bridge);
      }
      if (vanilla(screen) && FormBridge.adaptable(screen)) {
        return new Skin(Kind.NATIVE, new FormPage(new FormBridge(screen)), section(screen), null);
      }
    } catch (RuntimeException e) {
      CxVisualsClient.LOG.error(
          "Custom menu could not dress {}, it keeps its own look", screen.getClass().getName(), e);
      return PLAIN;
    }
    return new Skin(Kind.FRAME, new FramePage(), section(screen), null);
  }

  private static boolean vanilla(Screen screen) {
    String name = screen.getClass().getName();
    if (!name.startsWith(VANILLA)) {
      return false;
    }
    for (String part : BESPOKE) {
      if (name.contains(part)) {
        return false;
      }
    }
    return !(screen instanceof ErrorScreen
        || screen instanceof OptimizeWorldScreen
        || screen instanceof CreateFlatWorldScreen
        || screen instanceof PresetFlatWorldScreen
        || screen instanceof CreateBuffetWorldScreen);
  }

  private static Section section(Screen screen) {
    String name = screen.getClass().getName();
    if (name.contains(".worldselection.")
        || screen instanceof CreateFlatWorldScreen
        || screen instanceof PresetFlatWorldScreen
        || screen instanceof CreateBuffetWorldScreen) {
      return Section.WORLDS;
    }
    if (name.contains(".multiplayer.")
        || screen instanceof DirectJoinServerScreen
        || screen instanceof ManageServerScreen) {
      return Section.SERVERS;
    }
    if (name.contains(".options.") || name.contains(".packs.")) {
      return Section.OPTIONS;
    }
    return Section.OTHER;
  }
}
