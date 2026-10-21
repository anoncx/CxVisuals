package me.cxiy.cxvisuals.mc.menu;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.Window;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.mc.CxVisuals;
import me.cxiy.cxvisuals.mc.gui.AccountsScreen;
import me.cxiy.cxvisuals.mc.gui.DotField;
import me.cxiy.cxvisuals.mc.gui.GuiTilt;
import me.cxiy.cxvisuals.mc.gui.LanguageScreen;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.mc.gui.ModulesScreen;
import me.cxiy.cxvisuals.mc.gui.ScreenTilt;
import me.cxiy.cxvisuals.mc.gui.SwitchScreen;
import me.cxiy.cxvisuals.mc.gui.WiiCursor;
import me.cxiy.cxvisuals.mc.menu.bridge.QuickPlayBridge;
import me.cxiy.cxvisuals.mc.modules.hud.Media;
import me.cxiy.cxvisuals.menu.model.Playtime;
import me.cxiy.cxvisuals.menu.model.Pulse;
import me.cxiy.cxvisuals.menu.model.QuickPlay;
import me.cxiy.cxvisuals.menu.model.TextStore;
import me.cxiy.cxvisuals.menu.nav.Section;
import me.cxiy.cxvisuals.menu.views.Dock;
import me.cxiy.cxvisuals.menu.views.MenuView;
import me.cxiy.cxvisuals.menu.views.Sfx;
import me.cxiy.cxvisuals.menu.views.Shell;
import me.cxiy.cxvisuals.menu.views.Side;
import me.cxiy.cxvisuals.menu.views.Stage;
import me.cxiy.cxvisuals.modules.friends.Friends;
import me.cxiy.cxvisuals.modules.social.Account;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;

public final class MenuStage {
  private static final float MAX_FRAME_SECONDS = 0.1F;
  private static final float FROST = 22.0F;
  private static final int HIDDEN = -10000;
  private static final int TRAIL = 16;
  private static final int FRAME_ROOM_X = 440;
  private static final int FRAME_ROOM_Y = 250;
  private static final int OPAQUE = 0xFF000000;
  private static final float[] PLACE = {1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F};
  private static final float[] ROOM = new float[4];
  private static final Screen[] SCREENS = new Screen[TRAIL];
  private static final McCanvas CANVAS = new McCanvas();
  private static final QuickPlayBridge QUICK = new QuickPlayBridge();
  private static final Sfx SFX = new McSfx();
  private static final TextStore LAYOUT = new McText(settings -> settings.menuLayout);
  private static final Playtime PLAYTIME = new Playtime(new McText(settings -> settings.playtime));
  private static final McPulse PULSE = new McPulse();

  private static Supplier<Theme> theme;
  private static Friends people;
  private static Account profile;
  private static Shell shell;
  private static long lastNanos;
  private static float dt;
  private static float fixed;
  private static float seconds;
  private static float pointerScale = 1.0F;
  private static Screen shown;
  private static int trail;
  private static int frameX;
  private static int frameY;

  private MenuStage() {}

  public static void init(
      Friends friends, Account account, String version, Supplier<Theme> themes) {
    Skins.init(version);
    people = friends;
    PULSE.friends(friends);
    PULSE.sources(QUICK, PLAYTIME);
    profile = account;
    shell = new Shell(new Dock(account, SFX, PULSE), new Stage(), new Side(friends, Media::hub));
    theme = themes;
  }

  public static void relabel() {
    if (theme == null) {
      return;
    }
    if (shown != null) {
      leave();
    }
    Skins.clear();
    shell = new Shell(new Dock(profile, SFX, PULSE), new Stage(), new Side(people, Media::hub));
  }

  public static boolean backs(Screen screen) {
    return (screen instanceof LanguageScreen
            || screen instanceof ModulesScreen
            || screen instanceof AccountsScreen
            || screen instanceof SwitchScreen)
        && enabled()
        && Minecraft.getInstance().level == null
        && DotField.usable();
  }

  public static boolean enabled() {
    return theme != null && CxVisuals.registry().client().customMenu.get();
  }

  public static boolean owns(Screen screen) {
    return Skins.of(screen) != null;
  }

  public static MenuView view(Screen screen) {
    Skins.Skin skin = Skins.of(screen);
    return skin != null && skin.kind == Skins.Kind.NATIVE ? shell : null;
  }

  public static Screen shown() {
    return shown;
  }

  public static boolean settled() {
    return shell != null && !shell.moving();
  }

  public static Sfx sfx() {
    return SFX;
  }

  public static TextStore layout() {
    return LAYOUT;
  }

  public static void tickPlaytime(boolean playing) {
    PLAYTIME.tick(playing, System.nanoTime(), LocalDate.now().toEpochDay());
  }

  public static void savePlaytime() {
    PLAYTIME.save();
  }

  public static Pulse pulse() {
    return PULSE;
  }

  public static QuickPlay quick() {
    return QUICK;
  }

  public static void fixStep(float seconds) {
    fixed = seconds;
  }

  public static float pointerScale() {
    return pointerScale;
  }

  public static void frame() {
    long now = System.nanoTime();
    dt =
        fixed > 0.0F
            ? fixed
            : lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, MAX_FRAME_SECONDS);
    lastNanos = now;
    WiiCursor.update(Minecraft.getInstance());
    if (shown != null) {
      Screen screen = Minecraft.getInstance().gui.screen();
      if (screen == null || Skins.of(screen) == null) {
        leave();
      }
    }
  }

  public static void backdrop(RenderTarget main) {
    Minecraft mc = Minecraft.getInstance();
    Screen screen = mc.gui.screen();
    if (theme == null
        || screen == null
        || !mc.isGameLoadFinished()
        || (Skins.of(screen) == null && !backs(screen))) {
      return;
    }
    Window window = mc.getWindow();
    seconds += dt;
    float x =
        (float) (mc.mouseHandler.xpos() * window.getWidth() / Math.max(1, window.getScreenWidth()));
    float y =
        (float)
            (mc.mouseHandler.ypos() * window.getHeight() / Math.max(1, window.getScreenHeight()));
    DotField.draw(main, theme.get(), x, y, seconds, McCanvas.interfaceScale(window.getHeight()));
  }

  private static void leave() {
    shown = null;
    while (trail > 0) {
      SCREENS[--trail] = null;
    }
    shell.rest();
    QUICK.rest();
  }

  private static float interfaceScale(Window window) {
    float scale = McCanvas.interfaceScale(window.getHeight());
    pointerScale = Math.max(1, window.getGuiScale()) / scale;
    return scale;
  }

  private static boolean frameRoom() {
    Window window = Minecraft.getInstance().getWindow();
    float scale = interfaceScale(window);
    float width = window.getWidth() / scale;
    float height = window.getHeight() / scale;
    Shell.room(width, height, false, ROOM);
    if (ROOM[2] / pointerScale >= FRAME_ROOM_X && ROOM[3] / pointerScale >= FRAME_ROOM_Y) {
      return false;
    }
    Shell.room(width, height, true, ROOM);
    return true;
  }

  public static int frameWidth() {
    frameRoom();
    return Math.round(ROOM[2] / pointerScale);
  }

  public static int frameHeight() {
    frameRoom();
    return Math.round(ROOM[3] / pointerScale);
  }

  public static double pointerX(Screen screen, double x) {
    return Skins.framed(screen) ? x - frameX : x;
  }

  public static double pointerY(Screen screen, double y) {
    return Skins.framed(screen) ? y - frameY : y;
  }

  static boolean frameDown(float x, float y) {
    return shell.mouseDown((x + frameX) * pointerScale, (y + frameY) * pointerScale);
  }

  static void frameUp(float x, float y) {
    shell.mouseUp((x + frameX) * pointerScale, (y + frameY) * pointerScale);
  }

  private static void home(Screen title) {
    while (trail > 1) {
      SCREENS[--trail] = null;
    }
    Minecraft.getInstance().gui.setScreen(title);
  }

  private static void arrive(Screen screen, Skins.Skin skin) {
    if (screen == shown) {
      return;
    }
    shown = screen;
    int depth = -1;
    if (skin.section == Section.HOME) {
      while (trail > 0) {
        SCREENS[--trail] = null;
      }
    }
    for (int i = 0; i < trail; i++) {
      if (SCREENS[i] == screen) {
        depth = i;
      }
    }
    if (depth >= 0) {
      while (trail > depth + 1) {
        SCREENS[--trail] = null;
      }
    } else {
      if (trail == TRAIL) {
        System.arraycopy(SCREENS, 1, SCREENS, 0, TRAIL - 1);
        trail--;
      }
      depth = trail;
      SCREENS[trail++] = screen;
    }
    if (skin.title != null) {
      shell.dock().bind(skin.title, () -> home(screen));
      QUICK.refresh();
    }
    shell.show(skin.page, skin.section, depth, skin.kind == Skins.Kind.FRAME && frameRoom());
  }

  public static boolean render(
      GuiGraphicsExtractor graphics, Screen screen, int mouseX, int mouseY) {
    Skins.Skin skin = Skins.of(screen);
    if (skin == null) {
      return false;
    }
    boolean framed = skin.kind == Skins.Kind.FRAME;
    if (screen != Minecraft.getInstance().gui.screen()) {
      return !framed;
    }
    Window window = Minecraft.getInstance().getWindow();
    int guiScale = Math.max(1, window.getGuiScale());
    float scale = interfaceScale(window);
    if (framed && !skin.shrunk) {
      skin.shrunk = true;
      screen.resize(graphics.guiWidth(), graphics.guiHeight());
    }
    if (!framed) {
      pulse(graphics, screen);
    }
    arrive(screen, skin);
    if (skin.title != null) {
      QUICK.tick();
    }
    Theme t = theme.get();
    if (!DotField.usable()) {
      graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), t.paper() | OPAQUE);
    }
    shell.motion(CxVisuals.registry().client().motion.get());
    shell.resize(window.getWidth() / scale, window.getHeight() / scale);
    float pointerX = mouseX * pointerScale;
    float pointerY = mouseY * pointerScale;
    ScreenTilt.rest();
    if (framed) {
      pointerX += frameX * pointerScale;
      pointerY += frameY * pointerScale;
    }
    McCanvas.glassBlur(FROST);
    CANVAS.begin(graphics, scale, guiScale);
    shell.draw(CANVAS, t, pointerX, pointerY, dt);
    CANVAS.end();
    McCanvas.glassBlur(0.0F);
    if (!framed) {
      return true;
    }
    shell.stage().target(ROOM);
    frameX = Math.round(ROOM[0] / pointerScale);
    frameY = Math.round(ROOM[1] / pointerScale);
    PLACE[2] = frameX;
    PLACE[5] = frameY;
    GuiTilt.place(graphics, PLACE, shell.stage().alphaIn());
    return false;
  }

  private static void pulse(GuiGraphicsExtractor graphics, Screen screen) {
    List<? extends GuiEventListener> parts = screen.children();
    boolean open = false;
    for (int i = 0; i < parts.size(); i++) {
      if (parts.get(i) instanceof AbstractSelectionList<?> list) {
        if (!open) {
          open = true;
          graphics.enableScissor(0, 0, 0, 0);
        }
        list.extractRenderState(graphics, HIDDEN, HIDDEN, 0.0F);
      }
    }
    if (open) {
      graphics.disableScissor();
    }
  }
}
