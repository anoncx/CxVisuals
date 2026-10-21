package me.cxiy.cxvisuals.mc.modules.hud;

import com.mojang.blaze3d.platform.Window;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.Themes;
import me.cxiy.cxvisuals.gui.ToastStack;
import me.cxiy.cxvisuals.gui.hud.HudEditor;
import me.cxiy.cxvisuals.gui.hud.HudPaint;
import me.cxiy.cxvisuals.gui.hud.HudStage;
import me.cxiy.cxvisuals.gui.hud.HudThemes;
import me.cxiy.cxvisuals.gui.spatial.Layer;
import me.cxiy.cxvisuals.gui.spatial.Rig;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.gui.HudEditorScreen;
import me.cxiy.cxvisuals.mc.gui.HudText;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.mc.modules.hud.elements.DonutSmpSessionElement;
import me.cxiy.cxvisuals.mc.modules.hud.elements.DonutSmpValueElement;
import me.cxiy.cxvisuals.mc.modules.hud.elements.DonutSmpWatchElement;
import me.cxiy.cxvisuals.modules.hud.HostedElement;
import me.cxiy.cxvisuals.modules.hud.HudModule;
import me.cxiy.cxvisuals.modules.hud.elements.ChatFeedElement;
import me.cxiy.cxvisuals.modules.hud.elements.MediaElement;
import me.cxiy.cxvisuals.modules.hud.elements.ToastElement;
import me.cxiy.cxvisuals.modules.hud.media.MediaCommand;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

public final class Hud {
  private static final float MAX_FRAME_SECONDS = 0.1F;
  private static final float TEXT_MATCH = 1.5F;
  private static final float PLATE_PAD = 3.0F;
  private static final float PLATE_RADIUS = 7.0F;
  private static final float TILE_RADIUS = 6.0F;
  public static final float TITLE_WIDTH = 360.0F;
  public static final float TITLE_HEIGHT = 68.0F;
  public static final float CARD_PAD = 5.0F;
  private static final float BAR_WIDTH = 182.0F;
  private static final float BAR_HEIGHT = 5.0F;
  private static final float NOTCH_TINT = 0.55F;
  private static final int SCROLL_ROWS = 3;
  private static final int[] BOSS_COLORS = {
    0xFFEC4FB8, 0xFF3FB7EC, 0xFFD71921, 0xFF3DDC5A, 0xFFE9D53A, 0xFF9B5CF0, 0xFFF2F2F2
  };
  private static final int[] NOTCHES = {0, 6, 10, 12, 20};

  private static final McCanvas CANVAS = new McCanvas();

  public static VanillaSlot scoreboard;
  public static VanillaSlot bossBars;
  public static VanillaSlot effects;
  public static VanillaSlot actionBar;
  public static VanillaSlot title;
  public static VanillaSlot hotbar;
  public static VanillaSlot chat;
  public static VanillaSlot tabList;
  public static VanillaSlot itemName;

  private static VanillaSlot[] slots;
  private static HudModule module;
  private static HudStage stage;
  private static HudEditor editor;
  private static Supplier<Theme> theme;
  private static boolean live;
  private static HudThemes plates;
  private static boolean editing;
  private static int frame;
  private static int guiScale = 1;
  private static float uiScale = 1.0F;
  private static float unit = 1.0F;
  private static long lastNanos;
  private static int fills;
  private static float boxLeft;
  private static float boxTop;
  private static float boxRight;
  private static float boxBottom;
  private static float headerBottom;

  private Hud() {}

  public static HudModule create(McKeyBinder keys, ClientSettings client) {
    module = new HudModule(keys, Hud::openEditor, Mirror.hub());
    module.registerExtraElement(new DonutSmpValueElement(module.settings));
    module.registerExtraElement(new DonutSmpSessionElement(module.settings));
    module.registerExtraElement(new DonutSmpWatchElement(module.settings));
    Themes window = new Themes(client);
    plates = new HudThemes(client, module, window);
    theme = plates.dark();
    stage = new HudStage(module, plates, client.motion::get, client);
    editor = new HudEditor(module, window);

    scoreboard =
        new VanillaSlot(
            module.scoreboard,
            (slot, w, h) -> slot.set(w - 96.0F, h * 0.5F - 60.0F, 95.0F, 110.0F));
    bossBars =
        new VanillaSlot(
            module.bossBars, (slot, w, h) -> slot.set(w * 0.5F - 91.0F, 3.0F, BAR_WIDTH, 14.0F));
    effects =
        new VanillaSlot(module.effects, (slot, w, h) -> slot.set(w - 50.0F, 1.0F, 49.0F, 24.0F));
    actionBar =
        new VanillaSlot(
            module.actionBar, (slot, w, h) -> slot.set(w * 0.5F - 60.0F, h - 74.0F, 120.0F, 13.0F));
    title =
        new VanillaSlot(
            module.title,
            (slot, w, h) ->
                slot.set(
                    w * 0.5F - TITLE_WIDTH * 0.5F, h * 0.5F - 40.0F, TITLE_WIDTH, TITLE_HEIGHT));
    hotbar =
        new VanillaSlot(
            module.hotbar, (slot, w, h) -> slot.set(w * 0.5F - 91.0F, h - 49.0F, BAR_WIDTH, 49.0F));
    chat = new VanillaSlot(module.chat, (slot, w, h) -> slot.set(0.0F, h - 140.0F, 240.0F, 100.0F));
    tabList =
        new VanillaSlot(
            module.tabList, (slot, w, h) -> slot.set(w * 0.5F - 80.0F, 9.0F, 160.0F, 56.0F));
    itemName =
        new VanillaSlot(
            module.itemName, (slot, w, h) -> slot.set(w * 0.5F - 45.0F, h - 61.0F, 90.0F, 13.0F));
    slots =
        new VanillaSlot[] {
          scoreboard, bossBars, effects, actionBar, title, hotbar, chat, tabList, itemName
        };
    hotbar.layer = Layer.HUD_NEAR;
    itemName.layer = Layer.HUD_NEAR;
    scoreboard.layer = Layer.HUD_FAR;
    chat.floats = false;
    tabList.floats = false;
    return module;
  }

  public static Theme theme() {
    return plates.get();
  }

  public static boolean live() {
    return live;
  }

  public static HudModule module() {
    return module;
  }

  public static HudEditor editor() {
    return editor;
  }

  static int frameIndex() {
    return frame;
  }

  public static void openEditor() {
    Minecraft mc = Minecraft.getInstance();
    if (module == null || mc.level == null) {
      return;
    }
    module.enabled.set(true);
    mc.gui.setScreen(new HudEditorScreen(editor));
  }

  public static void feed(Component message, boolean system) {
    if (module == null || !module.enabled.get() || !module.feed.shown.get()) {
      return;
    }
    ChatVisiblity visibility = Minecraft.getInstance().options.chatVisibility().get();
    if (visibility == ChatVisiblity.HIDDEN || (visibility == ChatVisiblity.SYSTEM && !system)) {
      return;
    }
    String text = message.getString();
    if (!text.isBlank()) {
      McCanvas.chat(module.data.chat.add(text, system), message);
    }
  }

  public static void tick(Minecraft mc) {
    if (module != null) {
      Probe.tick(mc, module);
      if (mc.level == null) {
        Media.rest(module.data);
      }
    }
  }

  public static void keys(KeyMapping play, KeyMapping next, KeyMapping previous) {
    boolean on = module != null && module.enabled.get();
    while (play.consumeClick()) {
      if (on) {
        Media.send(MediaCommand.PLAY_PAUSE, 0L);
      }
    }
    while (next.consumeClick()) {
      if (on) {
        Media.send(MediaCommand.NEXT, 0L);
      }
    }
    while (previous.consumeClick()) {
      if (on) {
        Media.send(MediaCommand.PREVIOUS, 0L);
      }
    }
  }

  public static boolean click(double mouseX, double mouseY) {
    if (!live || !module.media.shown.get() || module.media.appear < 1.0F) {
      return false;
    }
    MediaElement media = module.media;
    float factor = media.factor(module.scale());
    float x = ((float) mouseX / unit - media.left - media.shiftX) / factor;
    float y = ((float) mouseY / unit - media.top - media.shiftY) / factor;
    MediaCommand command = media.hit(x, y);
    if (command == null) {
      return false;
    }
    Media.send(command, command == MediaCommand.SEEK ? media.seekTarget() : 0L);
    return true;
  }

  public static void frame(GuiGraphicsExtractor graphics) {
    McCanvas.interactive(false);
    live = module != null && module.enabled.get();
    HudText.configure(live && module.font.get());
    if (!live) {
      if (module != null) {
        Media.rest(module.data);
        Mirror.rest(module);
      }
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    Window window = mc.getWindow();
    guiScale = Math.max(1, window.getGuiScale());
    uiScale = McCanvas.interfaceScale(window.getHeight());
    unit = uiScale / guiScale;
    editing = mc.gui.screen() instanceof HudEditorScreen;
    float width = graphics.guiWidth();
    float height = graphics.guiHeight();
    float match = Math.min(1.0F, TEXT_MATCH * unit);
    for (int i = 0; i < slots.length; i++) {
      slots[i].element.normalize(match);
      slots[i].settle(frame, width, height, unit);
      slots[i].depth = 0;
    }
    frame++;
    stage.place(window.getWidth() / uiScale, window.getHeight() / uiScale);
    Mirror.frame(module, uiScale);
    Probe.frame(mc, module);
  }

  public static void overlay(GuiGraphicsExtractor graphics) {
    Minecraft mc = Minecraft.getInstance();
    if (!live) {
      alone(graphics, mc);
      return;
    }
    if (mc.gui.screen() instanceof LevelLoadingScreen || (mc.gui.hud.isHidden() && !editing)) {
      lastNanos = 0L;
      return;
    }
    long now = System.nanoTime();
    float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, MAX_FRAME_SECONDS);
    lastNanos = now;
    Window window = mc.getWindow();
    graphics.nextStratum();
    McCanvas.interactive(module.data.chatOpen);
    CANVAS.begin(graphics, uiScale, guiScale);
    stage.draw(
        CANVAS,
        window.getWidth() / uiScale,
        window.getHeight() / uiScale,
        dt,
        editing,
        !mc.debugEntries.isOverlayVisible());
    CANVAS.end();
    HudText.reset();
  }

  private static void alone(GuiGraphicsExtractor graphics, Minecraft mc) {
    ToastStack stack = module == null ? null : module.toasts.stack;
    if (stack == null
        || stack.size() == 0
        || mc.gui.hud.isHidden()
        || mc.gui.screen() instanceof LevelLoadingScreen) {
      lastNanos = 0L;
      return;
    }
    long now = System.nanoTime();
    float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, MAX_FRAME_SECONDS);
    lastNanos = now;
    Window window = mc.getWindow();
    float scale = McCanvas.interfaceScale(window.getHeight());
    graphics.nextStratum();
    CANVAS.begin(graphics, scale, Math.max(1, window.getGuiScale()));
    stack.draw(
        CANVAS,
        plates.get(),
        (window.getWidth() / scale - ToastStack.WIDTH) * 0.5F,
        (float) ToastElement.HOME_Y,
        ToastStack.extent(stack.size()),
        0.5F,
        false,
        dt);
    CANVAS.end();
  }

  public static boolean begin(VanillaSlot slot, GuiGraphicsExtractor graphics) {
    return begin(slot, graphics, 1.0F, 0.0F, 0.0F);
  }

  public static boolean begin(
      VanillaSlot slot, GuiGraphicsExtractor graphics, float fit, float pivotX, float pivotY) {
    if (!live) {
      return false;
    }
    settle(graphics, slot);
    HostedElement element = slot.element;
    if (!element.shown.get()) {
      return true;
    }
    if (!slot.lettered) {
      slot.lettered = true;
      HudText.enter();
    }
    boolean displaced = element.transformed() && slot.w > 0.0F;
    Rig rig = stage.rig();
    boolean sway = slot.floats && rig.active();
    if (!displaced && fit >= 1.0F && !sway) {
      return false;
    }
    Matrix3x2fStack pose = graphics.pose();
    pose.pushMatrix();
    if (sway) {
      pose.translate(rig.shiftX(slot.layer) * unit, rig.shiftY(slot.layer) * unit);
    }
    if (displaced) {
      float factor = element.factor(1.0F);
      pose.translate(element.left * unit, element.top * unit);
      pose.scale(factor, factor);
      pose.translate(-slot.x, -slot.y);
    }
    if (fit < 1.0F) {
      pose.translate(pivotX, pivotY);
      pose.scale(fit, fit);
      pose.translate(-pivotX, -pivotY);
    }
    slot.depth++;
    return false;
  }

  public static void end(VanillaSlot slot, GuiGraphicsExtractor graphics) {
    if (slot.lettered && slot.depth <= 1) {
      slot.lettered = false;
      HudText.exit();
    }
    if (slot.depth > 0) {
      slot.depth--;
      graphics.pose().popMatrix();
    }
  }

  public static void settle(GuiGraphicsExtractor graphics, VanillaSlot keep) {
    if (!live) {
      return;
    }
    for (int i = 0; i < slots.length; i++) {
      VanillaSlot slot = slots[i];
      if (slot == keep) {
        continue;
      }
      while (slot.depth > 0) {
        end(slot, graphics);
      }
      if (slot.lettered) {
        slot.lettered = false;
        HudText.exit();
      }
    }
  }

  public static void remap(VanillaSlot slot, ActiveTextCollector collector) {
    HostedElement element = slot.element;
    if (!live || !element.shown.get() || !element.transformed() || slot.w <= 0.0F) {
      return;
    }
    ActiveTextCollector.Parameters parameters = collector.defaultParameters();
    float factor = element.factor(1.0F);
    Matrix3x2f pose =
        new Matrix3x2f(parameters.pose())
            .translate(element.left * unit, element.top * unit)
            .scale(factor, factor)
            .translate(-slot.x, -slot.y);
    collector.defaultParameters(parameters.withPose(pose));
  }

  public static boolean feedActive() {
    return live && module.feed.shown.get();
  }

  public static boolean clicks(ActiveTextCollector collector) {
    if (!live) {
      return false;
    }
    McCanvas.clicks(collector);
    return !chat.element.shown.get() || module.feed.shown.get();
  }

  public static boolean scroll(double mouseX, double mouseY, double amount) {
    if (!live || amount == 0.0 || !module.feed.shown.get() || !module.feed.present(module.data)) {
      return false;
    }
    ChatFeedElement feed = module.feed;
    float factor = feed.factor(module.scale());
    float x = (float) mouseX / unit - feed.left - feed.shiftX;
    float y = (float) mouseY / unit - feed.top - feed.shiftY;
    if (x < 0.0F || y < 0.0F || x > feed.w * factor || y > feed.h * factor) {
      return false;
    }
    int step = Minecraft.getInstance().hasShiftDown() ? 1 : SCROLL_ROWS;
    feed.scroll(amount > 0.0 ? step : -step);
    return true;
  }

  private static boolean themed(VanillaSlot slot) {
    return live && slot.element.themed();
  }

  private static void plate(
      GuiGraphicsExtractor graphics,
      float left,
      float top,
      float right,
      float bottom,
      float radius) {
    CANVAS.begin(graphics, uiScale, guiScale);
    HudPaint.plate(
        CANVAS,
        theme.get(),
        left / unit,
        top / unit,
        (right - left) / unit,
        (bottom - top) / unit,
        radius / unit);
    CANVAS.end();
  }

  public static void scoreboardStart() {
    fills = 0;
  }

  public static boolean scoreboardFill(
      GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1) {
    if (!live) {
      return false;
    }
    if (fills++ == 0) {
      boxLeft = x0 - PLATE_PAD;
      boxTop = y0 - PLATE_PAD;
      boxRight = x1 + PLATE_PAD;
      headerBottom = y1;
      return themed(scoreboard);
    }
    boxBottom = y1 + PLATE_PAD;
    scoreboard.natural(boxLeft, boxTop, boxRight - boxLeft, boxBottom - boxTop);
    if (!themed(scoreboard)) {
      return false;
    }
    Theme t = theme.get();
    CANVAS.begin(graphics, uiScale, guiScale);
    HudPaint.plate(
        CANVAS,
        t,
        boxLeft / unit,
        boxTop / unit,
        (boxRight - boxLeft) / unit,
        (boxBottom - boxTop) / unit,
        PLATE_RADIUS / unit);
    CANVAS.roundRect(
        (boxLeft + PLATE_PAD) / unit,
        headerBottom / unit,
        (boxRight - boxLeft - PLATE_PAD * 2.0F) / unit,
        1.0F / unit,
        0.0F,
        t.hair());
    CANVAS.end();
    return true;
  }

  public static boolean bossBar(GuiGraphicsExtractor graphics, int x, int y, BossEvent event) {
    if (!themed(bossBars)) {
      return false;
    }
    Theme t = theme.get();
    float scale = 1.0F / unit;
    float left = x * scale;
    float top = y * scale;
    float width = BAR_WIDTH * scale;
    float height = BAR_HEIGHT * scale;
    float filled = Math.max(0.0F, Math.min(1.0F, event.getProgress())) * width;
    CANVAS.begin(graphics, uiScale, guiScale);
    CANVAS.roundRect(left, top, width, height, height * 0.5F, t.surface2() | 0xFF000000);
    CANVAS.roundRectBorder(left, top, width, height, height * 0.5F, 1.0F, t.hair());
    if (filled > 0.0F) {
      CANVAS.roundRect(
          left,
          top,
          Math.max(height, filled),
          height,
          height * 0.5F,
          BOSS_COLORS[event.getColor().ordinal()]);
    }
    int notches = NOTCHES[event.getOverlay().ordinal()];
    int gap = Theme.withAlpha(0xFF000000, NOTCH_TINT);
    for (int i = 1; i < notches; i++) {
      CANVAS.roundRect(left + width * i / notches - 0.5F, top, 1.0F, height, 0.0F, gap);
    }
    CANVAS.end();
    return true;
  }

  public static boolean effectTile(
      GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
    if (!themed(effects)) {
      return false;
    }
    plate(graphics, x, y, x + width, y + height, TILE_RADIUS);
    return true;
  }

  public static void tabStart() {
    fills = 0;
  }

  public static boolean tabFill(
      GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color) {
    if (!live) {
      return false;
    }
    boolean panel = color == Integer.MIN_VALUE;
    if (panel) {
      float left = x0 - PLATE_PAD;
      float top = y0 - PLATE_PAD;
      float right = x1 + PLATE_PAD;
      float bottom = y1 + PLATE_PAD;
      if (fills++ == 0) {
        boxLeft = left;
        boxTop = top;
        boxRight = right;
        boxBottom = bottom;
      } else {
        boxLeft = Math.min(boxLeft, left);
        boxTop = Math.min(boxTop, top);
        boxRight = Math.max(boxRight, right);
        boxBottom = Math.max(boxBottom, bottom);
      }
      if (!themed(tabList)) {
        return false;
      }
      if (fills == 1) {
        if (tabList.stamp == frame - 1 && tabList.w > 0.0F) {
          plate(
              graphics,
              tabList.x,
              tabList.y,
              tabList.x + tabList.w,
              tabList.y + tabList.h,
              PLATE_RADIUS);
        } else {
          plate(graphics, left, top, right, bottom, PLATE_RADIUS);
        }
      }
      return true;
    }
    return themed(tabList);
  }

  public static void bossCard(GuiGraphicsExtractor graphics) {
    if (live && bossBars.element.backed()) {
      plate(
          graphics,
          bossBars.x,
          bossBars.y,
          bossBars.x + bossBars.w,
          bossBars.y + bossBars.h,
          PLATE_RADIUS);
    }
  }

  public static void tabEnd() {
    if (live && fills > 0) {
      tabList.natural(boxLeft, boxTop, boxRight - boxLeft, boxBottom - boxTop);
    }
  }
}
