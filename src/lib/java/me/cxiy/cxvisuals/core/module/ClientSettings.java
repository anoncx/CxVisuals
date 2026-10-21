package me.cxiy.cxvisuals.core.module;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.i18n.Language;
import me.cxiy.cxvisuals.core.settings.ActionSetting;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PanelSetting;
import me.cxiy.cxvisuals.core.settings.TextSetting;

public final class ClientSettings extends Module {
  public enum Appearance {
    DARK,
    LIGHT
  }

  public enum NoticeSound {
    OFF,
    BELL,
    CHIME,
    PLING,
    HARP,
    ORB,
    FANFARE;

    public static final String[] LABELS = {
      "Off", "Bell", "Chime", "Pling", "Harp", "Orb", "Fanfare"
    };
  }

  public enum WindowStyle {
    SOLID,
    CLEAR,
    GLASS
  }

  public enum Layout {
    CLASSIC,
    FLOATING
  }

  public enum WindowMotion {
    MORPH,
    POP,
    SLIDE,
    FADE,
    EVAPORATE
  }

  public static final int OPEN_KEY = 344;

  public final EnumSetting<Language> language;
  public final BoolSetting languagePicked;
  public final BoolSetting updateNotice;
  public final EnumSetting<NoticeSound> noticeSound;
  public final NumberSetting noticeVolume;
  public final EnumSetting<Appearance> appearance;
  public final EnumSetting<WindowStyle> style;
  public final NumberSetting opacity;
  public final EnumSetting<WindowMotion> motion;
  public final NumberSetting motionSpeed;
  public final BoolSetting rememberPanels;
  public final EnumSetting<Layout> layout;
  public final BoolSetting spatial;
  public final NumberSetting spatialStrength;
  public final BoolSetting spatialScreens;
  public final BoolSetting spatialSway;
  public final BoolSetting spatialFocus;
  public final BoolSetting spatialSheen;
  public final BoolSetting customMenu;
  public final BoolSetting menuStatus;
  public final BoolSetting wiiCursor;
  public final BoolSetting menuLyrics;
  public final NumberSetting menuVolume;
  public final TextSetting menuLayout;
  public final TextSetting workspace;
  public final TextSetting playtime;
  public final TextSetting config;
  public final PanelSetting configs;

  @Override
  public boolean pinned() {
    return true;
  }

  ClientSettings(KeyBinder keys, String version, Runnable resetAll) {
    super("client", "Settings", false, false, keys);
    Page page = section("interface", "Interface");
    Group ui = page.group("Interface");
    language =
        ui.enumOf(
                "language",
                "Language",
                Language.ENGLISH,
                Language.ENGLISH.nativeName,
                Language.GERMAN.nativeName,
                Language.RUSSIAN.nativeName)
            .keepOnReset();
    language.onChange(() -> Lang.use(language.get()));
    languagePicked = settings.toggle("language_picked", "Language picked", false).keepOnReset();
    appearance = ui.enumOf("appearance", "Appearance", Appearance.DARK, "Dark", "Light");
    style = ui.enumOf("style", "Window style", WindowStyle.SOLID, "Solid", "Clear", "Glass");
    opacity =
        ui.number("opacity", "Opacity", 60, 10, 100, 5, "%").describe("Tint for Clear and Glass.");
    motion =
        ui.enumOf(
            "motion", "Motion", WindowMotion.MORPH, "Morph", "Pop", "Slide", "Fade", "Evaporate");
    motionSpeed =
        ui.number("motion_speed", "Animation speed", 100, 25, 300, 5, "%")
            .describe("How fast windows, the HUD and the item list open and close.");
    layout =
        ui.enumOf("layout", "Settings layout", Layout.CLASSIC, "Classic", "Floating")
            .describe(
                "Floating keeps only the module list and opens every module in its own window.");
    rememberPanels =
        ui.bool("remember_panels", "Remember panels", true)
            .describe(
                "Reopens your floating panels where you left them. Right-click a module or press Ctrl+Shift+D to open it in its own window.");
    ui.key("open_key", "Open settings", keys.bind("open", "Open settings", OPEN_KEY));

    Group depth = section("spatial", "Spatial UI").group("Spatial UI");
    spatial =
        depth
            .bool("spatial", "Spatial UI", true)
            .describe("Depth and tilt that follow your cursor and camera.");
    spatialStrength = depth.number("spatial_strength", "Strength", 50, 0, 100, 5, "%");
    spatialScreens =
        depth
            .bool("spatial_screens", "Game screens", false)
            .describe("Also turns the inventory, chests and menus.");
    spatialSway =
        depth
            .bool("spatial_sway", "Camera sway", true)
            .describe("HUD elements float and lag behind your look.");
    spatialFocus = depth.bool("spatial_focus", "Focus pop", true);
    spatialSheen = depth.bool("spatial_sheen", "Sheen", true);

    Group menu = section("menu", "Menu").group("Menu");
    customMenu =
        menu.bool("custom_menu", "Custom menu", true)
            .describe("Replaces the title, world, server and options screens.");
    wiiCursor =
        menu.bool("wii_cursor", "Wii cursor", true)
            .describe("Swaps the mouse pointer for a glove that closes when you click.");
    menuLyrics =
        menu.bool("menu_lyrics", "Menu lyrics", true)
            .describe(
                "Shows the line being sung under the clock while music plays. Needs online lookups in the HUD media settings.");
    menuVolume =
        menu.number("menu_volume", "Menu sounds", 50.0, 0.0, 100.0, 5.0, "%")
            .describe("How loud the menu ticks and clicks are. Zero turns them off.");
    menuStatus =
        menu.bool("menu_status", "Server status on home", true)
            .describe(
                "Pings your saved servers when the home page opens. Off shows them without ping or player count.");

    menuLayout = settings.text("menu_layout", "");
    workspace = settings.text("workspace", "");
    playtime = settings.text("playtime", "");
    config = settings.text("config", "");
    configs = section("configs", "Configs").group("Saved").panel("configs");

    Group notices = page.group("Notifications");
    updateNotice =
        notices
            .bool("update_notice", "Update notice", true)
            .describe("Shows a notification when a new CxVisuals build is available.");
    noticeSound =
        notices
            .enumOf("notice_sound", "Notification sound", NoticeSound.BELL, NoticeSound.LABELS)
            .describe("Plays when a notification needs your attention. Picking one plays it once.");
    noticeVolume =
        notices.number("notice_volume", "Notification volume", 80.0, 0.0, 100.0, 5.0, "%");

    Group about = page.group("About");
    about.info("version", "Version", version);
    about.action("reset", "Reset everything", "Reset", ActionSetting.Kind.DANGER, resetAll);
  }
}
