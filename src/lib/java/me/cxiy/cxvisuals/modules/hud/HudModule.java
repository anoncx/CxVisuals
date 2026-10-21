package me.cxiy.cxvisuals.modules.hud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.ActionSetting;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.KeySetting;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PickSetting;
import me.cxiy.cxvisuals.modules.hud.elements.AchievementsElement;
import me.cxiy.cxvisuals.modules.hud.elements.ChatFeedElement;
import me.cxiy.cxvisuals.modules.hud.elements.ClockElement;
import me.cxiy.cxvisuals.modules.hud.elements.CompassElement;
import me.cxiy.cxvisuals.modules.hud.elements.GearElement;
import me.cxiy.cxvisuals.modules.hud.elements.KeystrokesElement;
import me.cxiy.cxvisuals.modules.hud.elements.LyricsElement;
import me.cxiy.cxvisuals.modules.hud.elements.MediaElement;
import me.cxiy.cxvisuals.modules.hud.elements.MirrorElement;
import me.cxiy.cxvisuals.modules.hud.elements.NetworkElement;
import me.cxiy.cxvisuals.modules.hud.elements.PlayersElement;
import me.cxiy.cxvisuals.modules.hud.elements.PositionElement;
import me.cxiy.cxvisuals.modules.hud.elements.ServerElement;
import me.cxiy.cxvisuals.modules.hud.elements.SessionElement;
import me.cxiy.cxvisuals.modules.hud.elements.StatElement;
import me.cxiy.cxvisuals.modules.hud.elements.TargetElement;
import me.cxiy.cxvisuals.modules.hud.elements.ToastElement;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorHub;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorWindow;

public final class HudModule extends Module {
  public enum PingSource {
    AUTO,
    MEASURED,
    TAB_LIST
  }

  public enum Plate {
    WINDOW,
    SOLID,
    CLEAR,
    GLASS
  }

  public static final String ID = "hud";
  public static final String EDIT_KEY = ID + ".edit";
  public static final String PLAY_KEY = ID + ".media.play";
  public static final String NEXT_KEY = ID + ".media.next";
  public static final String PREVIOUS_KEY = ID + ".media.previous";

  private static final double STACK_TOP = 10.0;
  private static final double STACK_STEP = 34.0;
  private static final double RIGHT_STACK = 103.0;

  public final BoolSetting snap;
  public final NumberSetting size;
  public final EnumSetting<Plate> plate;
  public final EnumSetting<PingSource> pingSource;
  public final NumberSetting opacity;
  public final BoolSetting contrast;
  public final NumberSetting spacing;
  public final BoolSetting arrange;
  public final BoolSetting animate;
  public final BoolSetting reduce;
  public final BoolSetting toured;
  public final BoolSetting online;
  public final KeySetting playKey;
  public final KeySetting nextKey;
  public final KeySetting previousKey;
  public final PickSetting mirrorWindow;
  public final MirrorHub mirrorHub;
  public final HudData data = new HudData();

  public final StatElement fps;
  public final StatElement ping;
  public final StatElement cpu;
  public final StatElement gpu;
  public final StatElement ram;
  public final StatElement tps;
  public final StatElement speed;
  public final StatElement clicks;
  public final PositionElement position;
  public final ClockElement clock;
  public final SessionElement session;
  public final KeystrokesElement keystrokes;
  public final GearElement armor;
  public final GearElement hands;
  public final PlayersElement players;
  public final ToastElement toasts;
  public final AchievementsElement achievements;
  public final CompassElement compass;
  public final ServerElement server;
  public final NetworkElement network;
  public final TargetElement target;
  public final ChatFeedElement feed;
  public final MediaElement media;
  public final LyricsElement lyrics;
  public final MirrorElement mirror;

  public final HostedElement scoreboard;
  public final HostedElement bossBars;
  public final HostedElement effects;
  public final HostedElement actionBar;
  public final HostedElement title;
  public final HostedElement hotbar;
  public final HostedElement chat;
  public final HostedElement tabList;
  public final HostedElement itemName;

  public final BoolSetting font;

  private final List<HudElement> elements = new ArrayList<>(28);
  private List<String> mirrorIds = List.of();
  private final List<HudElement> elementsView = Collections.unmodifiableList(elements);

  public HudModule(KeyBinder keys, Runnable edit) {
    this(keys, edit, new MirrorHub(() -> null, message -> {}));
  }

  public HudModule(KeyBinder keys, Runnable edit, MirrorHub mirrorHub) {
    super(ID, "HUD", false, keys);
    this.mirrorHub = mirrorHub;
    action("edit", "Edit layout", ActionSetting.Kind.QUIET, edit);

    Page page = section("layout", "Layout");
    Group look = page.group("Look");
    plate =
        look.enumOf("plate", "Background", Plate.CLEAR, "Window", "Solid", "Clear", "Glass")
            .describe("Window follows the settings window style.");
    opacity =
        look.number("opacity", "Opacity", 55.0, 10.0, 100.0, 5.0, "%")
            .describe("Tint for Clear and Glass.");
    contrast =
        look.bool("contrast", "Adaptive text", true)
            .describe(
                "Text turns dark or light per pixel to stay readable over what is behind it.");
    font =
        look.bool("font", "Custom font", true)
            .describe(
                "Scoreboard, player list, chat and names use the CxVisuals font. Symbols it lacks keep the Minecraft font.");
    animate =
        look.bool("animate", "Animate show and hide", true)
            .describe(
                "Elements arrive and leave with the Motion style picked in Interface settings.");
    reduce =
        look.bool("reduce", "Reduce motion", false)
            .describe("Elements appear, move and update without animation.");
    size =
        look.number("size", "Size", 100.0, 60.0, 180.0, 5.0, "%")
            .describe("Scales every info element. Game elements keep their own size.");

    Group editor = page.group("Editor");
    spacing =
        editor
            .number("spacing", "Spacing", 4.0, 0.0, 24.0, 1.0, "")
            .describe("Smallest gap the editor keeps between elements.");
    arrange =
        editor
            .bool("arrange", "Make room", true)
            .describe(
                "Elements step aside when a neighbour grows into them and return when it shrinks.");
    snap =
        editor
            .bool("snap", "Snapping", true)
            .describe("Lines elements up with edges, the centre and each other.");
    editor.key(
        "edit_key", "Edit layout", keys.bind(EDIT_KEY, "Edit HUD layout", KeyBinder.UNBOUND));
    editor.action("reset", "Reset layout", "Reset", ActionSetting.Kind.DANGER, this::resetLayout);

    Group link = page.group("Network");
    pingSource =
        link.enumOf(
                "ping_source",
                "Ping source",
                PingSource.AUTO,
                "Automatic",
                "Measured",
                "Player list")
            .describe(
                "Measured times a real round trip to the server, so it works where the player list hides ping. Automatic uses it and falls back to the player list.");

    Group mediaGroup = page.group("Media");
    online =
        mediaGroup
            .bool("online", "Online lookups", true)
            .describe(
                "Looks up lyrics and missing covers on lrclib.net and deezer.com. Off keeps everything on this computer.");
    playKey =
        mediaGroup.key(
            "media_play",
            "Play / pause",
            keys.bind(PLAY_KEY, "Media play / pause", KeyBinder.UNBOUND));
    nextKey =
        mediaGroup.key(
            "media_next", "Next track", keys.bind(NEXT_KEY, "Media next track", KeyBinder.UNBOUND));
    previousKey =
        mediaGroup.key(
            "media_previous",
            "Previous track",
            keys.bind(PREVIOUS_KEY, "Media previous track", KeyBinder.UNBOUND));

    Group mirrorGroup = page.group("Window mirror");
    mirrorGroup.showWhen(() -> !mirrorHub.chooser());
    mirrorWindow =
        mirrorGroup
            .picks("mirror_window", "Window")
            .single()
            .refresher(this::refreshWindows)
            .describe(
                "Shows a live copy of this window in the Window element. Minimized windows freeze on their last frame.");
    Group chooser = page.group("Window mirror");
    chooser.showWhen(mirrorHub::chooser);
    chooser.action(
        "mirror_choose",
        "Window",
        "Choose",
        ActionSetting.Kind.PRIMARY,
        () -> {
          mirrorWindow.set(Set.of(MirrorHub.PORTAL));
          mirrorHub.choose();
        });

    toured = settings.toggle("toured", "Tour seen", false);

    fps =
        add(
            new StatElement(
                settings,
                "fps",
                "FPS",
                "FPS",
                "",
                false,
                StatElement.NO_LIMIT,
                30.0F,
                StatElement.NO_LIMIT,
                d -> d.fps,
                true,
                stack(0)));
    ping =
        add(
            new StatElement(
                settings,
                "ping",
                "Ping",
                Lang.key("PING"),
                "ms",
                false,
                StatElement.NO_LIMIT,
                StatElement.NO_LIMIT,
                150.0F,
                d -> d.ping,
                true,
                stack(1)));
    cpu =
        add(
            new StatElement(
                settings,
                "cpu",
                "CPU",
                "CPU",
                "%",
                false,
                100.0F,
                StatElement.NO_LIMIT,
                90.0F,
                d -> d.cpu,
                false,
                stack(2)));
    gpu =
        add(
            new StatElement(
                settings,
                "gpu",
                "GPU",
                "GPU",
                "%",
                false,
                100.0F,
                StatElement.NO_LIMIT,
                97.0F,
                d -> d.gpu,
                false,
                stack(3)));
    ram =
        add(
            new StatElement(
                settings,
                "ram",
                "RAM",
                "RAM",
                "%",
                false,
                100.0F,
                StatElement.NO_LIMIT,
                90.0F,
                d -> d.ram,
                false,
                stack(4)));
    tps =
        add(
            new StatElement(
                settings,
                "tps",
                "TPS",
                "TPS",
                "",
                true,
                20.0F,
                15.0F,
                StatElement.NO_LIMIT,
                d -> d.tps,
                false,
                stack(5)));
    speed =
        add(
            new StatElement(
                settings,
                "speed",
                "Speed",
                Lang.key("SPEED"),
                "m/s",
                true,
                StatElement.NO_LIMIT,
                StatElement.NO_LIMIT,
                StatElement.NO_LIMIT,
                d -> d.speed,
                false,
                stack(6)));
    clicks =
        add(
            new StatElement(
                settings,
                "cps",
                "CPS",
                "CPS",
                "",
                false,
                StatElement.NO_LIMIT,
                StatElement.NO_LIMIT,
                StatElement.NO_LIMIT,
                d -> d.clicksLeft,
                false,
                stack(7)));
    position = add(new PositionElement(settings, stack(8)));
    clock = add(new ClockElement(settings, RIGHT_STACK));
    session = add(new SessionElement(settings, RIGHT_STACK + STACK_STEP));
    keystrokes = add(new KeystrokesElement(settings));
    armor =
        add(
            new GearElement(
                settings,
                "armor",
                "Armor",
                0,
                HudData.FIRST_HAND,
                GearElement.Flow.ROW,
                Anchor.BOTTOM,
                -270.0,
                -6.0));
    hands =
        add(
            new GearElement(
                settings,
                "held",
                "Held items",
                HudData.FIRST_HAND,
                HudData.GEAR_SLOTS - HudData.FIRST_HAND,
                GearElement.Flow.ROW,
                Anchor.BOTTOM,
                240.0,
                -6.0));

    players = add(new PlayersElement(settings));
    server = add(new ServerElement(settings));
    network = add(new NetworkElement(settings));
    target = add(new TargetElement(settings));
    feed = add(new ChatFeedElement(settings));
    media = add(new MediaElement(settings));
    lyrics = add(new LyricsElement(settings));
    mirror = add(new MirrorElement(settings, mirrorHub.store(), mirrorWindow));
    toasts = add(new ToastElement(settings));
    achievements = add(new AchievementsElement(settings));
    compass = add(new CompassElement(settings));

    scoreboard = add(new HostedElement(settings, "scoreboard", "Scoreboard", true, true));
    bossBars = add(new HostedElement(settings, "boss_bars", "Boss bars", true, true, true));
    effects = add(new HostedElement(settings, "effects", "Effects", true, true));
    actionBar = add(new HostedElement(settings, "action_bar", "Action bar", false, false));
    title = add(new HostedElement(settings, "title", "Titles", false, false));
    hotbar = add(new HostedElement(settings, "hotbar", "Hotbar", false, true));
    chat = add(new HostedElement(settings, "chat", "Chat", false, false));
    tabList = add(new HostedElement(settings, "tab_list", "Player list", true, false));
    itemName = add(new HostedElement(settings, "item_name", "Item name", false, false));
  }

  private void refreshWindows() {
    mirrorHub.refresh();
    List<MirrorWindow> windows = mirrorHub.windows();
    List<String> ids = new ArrayList<>(windows.size());
    for (int i = 0; i < windows.size(); i++) {
      ids.add(windows.get(i).key());
    }
    if (ids.equals(mirrorIds)) {
      return;
    }
    mirrorIds = ids;
    List<String> labels = new ArrayList<>(windows.size());
    for (int i = 0; i < windows.size(); i++) {
      labels.add(windows.get(i).label());
    }
    mirrorWindow.options(ids, labels);
  }

  public List<HudElement> elements() {
    return elementsView;
  }

  public float scale() {
    return (float) (size.get() / 100.0);
  }

  public float gap() {
    return spacing.get().floatValue();
  }

  public void resetLayout() {
    for (int i = 0; i < elements.size(); i++) {
      elements.get(i).resetPlacement();
    }
  }

  private <E extends HudElement> E add(E element) {
    elements.add(element);
    return element;
  }

  public void registerExtraElement(HudElement element) {
    elements.add(element);
  }

  private static double stack(int index) {
    return STACK_TOP + STACK_STEP * index;
  }

  @Override
  public String group() {
    return "Interface";
  }
}
