package me.cxiy.cxvisuals.mc.modules.animations;

import java.util.List;
import me.cxiy.cxvisuals.core.math.Easing;
import me.cxiy.cxvisuals.gui.spatial.Spring;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.gui.HudEditorScreen;
import me.cxiy.cxvisuals.mc.gui.ModulesScreen;
import me.cxiy.cxvisuals.modules.animations.AnimationsModule;
import me.cxiy.cxvisuals.modules.animations.AnimationsModule.HotbarStyle;
import me.cxiy.cxvisuals.modules.animations.AnimationsModule.OpenStyle;
import me.cxiy.cxvisuals.modules.animations.Eased;
import me.cxiy.cxvisuals.modules.animations.Pop;
import me.cxiy.cxvisuals.modules.animations.SlotGlide;
import me.cxiy.cxvisuals.modules.animations.SlotMoves;
import me.cxiy.cxvisuals.modules.animations.Teleports;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class Animations {
  private static final float MAX_FRAME_SECONDS = 0.1F;
  private static final int HOTBAR = 9;
  private static final float SLOT_PITCH = 20.0F;
  private static final float BOUNCE_SECONDS = 0.22F;
  private static final float BOUNCE_AMOUNT = 0.18F;
  private static final float MOVE_SECONDS = 0.18F;
  private static final float MOVE_LIFT = 0.15F;
  private static final float ITEM_HALF = 8.0F;
  private static final float OPEN_SECONDS = 0.22F;
  private static final float OPEN_FROM = 0.9F;
  private static final float OPEN_RISE = 14.0F;
  private static final float LEVEL_SECONDS = 0.3F;
  private static final float LEVEL_AMOUNT = 0.35F;
  private static final float CHAT_SECONDS = 0.16F;
  private static final float APPEAR_SECONDS = 0.14F;
  private static final float APPEAR_FROM = 0.92F;
  private static final float ROUNDING = 0.01F;
  private static final float VEIL_MIN = 0.003F;
  private static final int TOOLTIP = 0;
  private static final int TABLIST = 1;
  private static final int BOARD = 2;

  private static final SlotGlide SELECTOR = new SlotGlide();
  private static final Pop[] BOUNCE = new Pop[HOTBAR];
  private static final int[] COUNTS = new int[HOTBAR];
  private static final Teleports TELEPORT = new Teleports();
  private static final SlotMoves MOVES = new SlotMoves();
  private static final ItemStack[] FLYING = new ItemStack[SlotMoves.CAPACITY];
  private static final Spring CURSOR_X = Spring.critical(600.0F);
  private static final Spring CURSOR_Y = Spring.critical(600.0F);
  private static final Eased HEALTH = new Eased(14.0F, 0.45F, 24.0F);
  private static final Eased FOOD = new Eased(10.0F, 0.0F, 100.0F);
  private static final Eased ARMOR = new Eased(10.0F, 0.0F, 100.0F);
  private static final Eased EXPERIENCE = new Eased(8.0F, 0.0F, 100.0F);
  private static final Pop LEVEL = new Pop();
  private static final float[] APPEAR = {1.0F, 1.0F, 1.0F};
  private static final int[] SEEN = {-2, -2, -2};

  private static AnimationsModule module;
  private static long lastNanos;
  private static float dt;
  private static boolean primed;
  private static double beforeX;
  private static double beforeY;
  private static double beforeZ;
  private static float beforeYaw;
  private static float beforePitch;
  private static int[] slotKeys = new int[0];
  private static int[] slotCounts = new int[0];
  private static ItemStack[] slotStacks = new ItemStack[0];
  private static boolean carrying;
  private static Screen openFor;
  private static float openPhase = 1.0F;
  private static boolean openPushed;
  private static int lastLevel = -1;
  private static int barLevel = -1;
  private static float chatPhase = 1.0F;
  private static int frameIndex;

  static {
    for (int i = 0; i < HOTBAR; i++) {
      BOUNCE[i] = new Pop();
    }
  }

  private Animations() {}

  public static AnimationsModule create(McKeyBinder keys) {
    module = new AnimationsModule(keys);
    return module;
  }

  public static AnimationsModule module() {
    return module;
  }

  public static boolean on() {
    return module != null && module.enabled.get();
  }

  public static void frame() {
    long now = System.nanoTime();
    dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, MAX_FRAME_SECONDS);
    lastNanos = now;
    BlockPopEffects.frame(dt);
    Minecraft mc = Minecraft.getInstance();
    LocalPlayer player = mc.player;
    if (!on() || player == null) {
      primed = false;
      TELEPORT.clear();
      return;
    }
    frameIndex++;
    TELEPORT.update(dt, module.seconds(module.teleportTime.get().floatValue()));
    if (mc.gui.screen() == null) {
      openFor = null;
    }
    chatPhase = Math.min(1.0F, chatPhase + dt / module.seconds(CHAT_SECONDS));
    float appear = dt / module.seconds(APPEAR_SECONDS);
    for (int i = 0; i < APPEAR.length; i++) {
      APPEAR[i] = Math.min(1.0F, APPEAR[i] + appear);
    }
    LEVEL.update(dt, module.seconds(LEVEL_SECONDS));

    Inventory inventory = player.getInventory();
    int selected = inventory.getSelectedSlot();
    SELECTOR.update(dt, selected, module.selector.get() == HotbarStyle.GLIDE);
    if (SELECTOR.changed() && module.selectBounce.get()) {
      BOUNCE[selected].trigger();
    }
    float bounce = module.seconds(BOUNCE_SECONDS);
    boolean pickup = primed && module.pickup.get();
    for (int i = 0; i < HOTBAR; i++) {
      int count = inventory.getItem(i).getCount();
      if (pickup && count > COUNTS[i]) {
        BOUNCE[i].trigger();
      }
      COUNTS[i] = count;
      BOUNCE[i].update(dt, bounce);
    }
    primed = true;
  }

  public static float selectorShift(int selected) {
    return (SELECTOR.position() - selected) * SLOT_PITCH;
  }

  public static float slotScale(int index) {
    return index >= 0 && index < HOTBAR ? BOUNCE[index].scale(BOUNCE_AMOUNT) : 1.0F;
  }

  public static Teleports teleport() {
    return TELEPORT;
  }

  public static int veil() {
    float veil = TELEPORT.veil();
    return veil > VEIL_MIN ? Math.round(veil * 255.0F) << 24 : 0;
  }

  public static void beforeTeleport(Player player) {
    beforeX = player.getX() + TELEPORT.offsetX();
    beforeY = player.getY() + TELEPORT.offsetY();
    beforeZ = player.getZ() + TELEPORT.offsetZ();
    beforeYaw = player.getYRot() + TELEPORT.yawOffset();
    beforePitch = player.getXRot() + TELEPORT.pitchOffset();
  }

  public static void afterTeleport(Player player) {
    if (!on() || player.isSpectator()) {
      TELEPORT.clear();
      return;
    }
    double dx = beforeX - player.getX();
    double dy = beforeY - player.getY();
    double dz = beforeZ - player.getZ();
    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
    Teleports.Kind kind =
        Teleports.classify(
            distance,
            module.teleportMin.get(),
            module.teleportMax.get(),
            module.teleport.get(),
            false);
    TELEPORT.start(kind, dx, dy, dz, beforeYaw - player.getYRot(), beforePitch - player.getXRot());
  }

  public static void dimensionChanged() {
    MOVES.reset();
    primed = false;
    BlockPopEffects.reset();
    if (on()) {
      TELEPORT.start(
          Teleports.classify(0.0, 0.0, 0.0, module.teleport.get(), true),
          0.0,
          0.0,
          0.0,
          0.0F,
          0.0F);
    } else {
      TELEPORT.clear();
    }
  }

  public static boolean moving() {
    return on() && module.moves.get();
  }

  public static void containerReset() {
    MOVES.reset();
    carrying = false;
  }

  public static void observe(List<Slot> slots) {
    int size = slots.size();
    if (slotKeys.length < size) {
      slotKeys = new int[size];
      slotCounts = new int[size];
      slotStacks = new ItemStack[size];
    }
    for (int i = 0; i < size; i++) {
      ItemStack stack = slots.get(i).getItem();
      if (stack.isEmpty()) {
        slotKeys[i] = 0;
      } else if (stack != slotStacks[i] || slotKeys[i] == 0) {
        slotKeys[i] = ItemStack.hashItemAndComponents(stack) | 1;
      }
      slotStacks[i] = stack;
      slotCounts[i] = stack.getCount();
    }
    MOVES.observe(slotKeys, slotCounts, size);
    for (int i = 0; i < SlotMoves.CAPACITY; i++) {
      if (MOVES.fresh(i)) {
        FLYING[i] = slots.get(MOVES.to(i)).getItem();
      }
    }
    MOVES.update(dt, module.seconds(MOVE_SECONDS));
  }

  public static boolean hides(int slot) {
    return MOVES.hides(slot);
  }

  public static void flights(GuiGraphicsExtractor graphics, List<Slot> slots) {
    Font font = Minecraft.getInstance().font;
    int size = slots.size();
    for (int i = 0; i < SlotMoves.CAPACITY; i++) {
      if (!MOVES.live(i) || MOVES.from(i) >= size || MOVES.to(i) >= size || FLYING[i] == null) {
        continue;
      }
      Slot from = slots.get(MOVES.from(i));
      Slot to = slots.get(MOVES.to(i));
      float t = (float) Easing.bezier(0.3, 0.0, 0.2, 1.0, MOVES.progress(i));
      float x = from.x + (to.x - from.x) * t;
      float y = from.y + (to.y - from.y) * t;
      float scale = 1.0F + MOVE_LIFT * (float) Math.sin(Math.PI * t);
      graphics.pose().pushMatrix();
      graphics.pose().translate(x + ITEM_HALF, y + ITEM_HALF);
      graphics.pose().scale(scale, scale);
      graphics.pose().translate(-ITEM_HALF, -ITEM_HALF);
      graphics.item(FLYING[i], 0, 0);
      graphics.itemDecorations(font, FLYING[i], 0, 0);
      graphics.pose().popMatrix();
    }
  }

  public static boolean softCursor() {
    return on() && module.cursor.get();
  }

  public static float cursorX(float mouse) {
    if (!carrying) {
      CURSOR_X.snap(mouse);
    } else {
      CURSOR_X.step(dt, mouse);
    }
    return CURSOR_X.value() - mouse;
  }

  public static float cursorY(float mouse) {
    if (!carrying) {
      CURSOR_Y.snap(mouse);
    } else {
      CURSOR_Y.step(dt, mouse);
    }
    carrying = true;
    return CURSOR_Y.value() - mouse;
  }

  public static void cursorIdle() {
    carrying = false;
  }

  public static boolean screenBegin(GuiGraphicsExtractor graphics, Screen screen) {
    if (openPushed) {
      return false;
    }
    if (screen != openFor) {
      openFor = screen;
      openPhase = 0.0F;
    }
    if (!on()
        || module.screenOpen.get() == OpenStyle.OFF
        || screen instanceof ChatScreen
        || screen instanceof ModulesScreen
        || screen instanceof HudEditorScreen) {
      openPhase = 1.0F;
      return false;
    }
    if (openPhase >= 1.0F) {
      return false;
    }
    openPhase = Math.min(1.0F, openPhase + dt / module.seconds(OPEN_SECONDS));
    float eased = (float) Easing.spring(openPhase);
    graphics.pose().pushMatrix();
    if (module.screenOpen.get() == OpenStyle.POP) {
      float scale = OPEN_FROM + (1.0F - OPEN_FROM) * eased;
      float cx = graphics.guiWidth() * 0.5F;
      float cy = graphics.guiHeight() * 0.5F;
      graphics.pose().translate(cx, cy);
      graphics.pose().scale(scale, scale);
      graphics.pose().translate(-cx, -cy);
    } else {
      graphics.pose().translate(0.0F, (1.0F - eased) * OPEN_RISE);
    }
    openPushed = true;
    return true;
  }

  public static void screenEnd(GuiGraphicsExtractor graphics) {
    if (openPushed) {
      openPushed = false;
      graphics.pose().popMatrix();
    }
  }

  private static int eased(Eased bar, int current) {
    if (!on() || !module.bars.get()) {
      bar.snap(current);
      return current;
    }
    bar.update(dt, current);
    return (int) Math.ceil(bar.value() - ROUNDING);
  }

  public static int health(int current) {
    return eased(HEALTH, current);
  }

  public static int healthGhost(int vanilla) {
    return on() && module.bars.get() && module.ghost.get()
        ? Math.max(vanilla, (int) Math.ceil(HEALTH.ghost() - ROUNDING))
        : vanilla;
  }

  public static int food(int current) {
    return eased(FOOD, current);
  }

  public static int armor(int current) {
    return eased(ARMOR, current);
  }

  public static float experience(float progress) {
    LocalPlayer player = Minecraft.getInstance().player;
    int level = player == null ? 0 : player.experienceLevel;
    boolean levelled = level != barLevel;
    barLevel = level;
    if (!on() || !module.bars.get() || levelled) {
      EXPERIENCE.snap(progress);
      return progress;
    }
    EXPERIENCE.update(dt, progress);
    return EXPERIENCE.value();
  }

  public static float levelScale(int level) {
    boolean changed = lastLevel != -1 && level != lastLevel;
    lastLevel = level;
    if (!on() || !module.numbers.get()) {
      return 1.0F;
    }
    if (changed) {
      LEVEL.trigger();
    }
    return LEVEL.scale(LEVEL_AMOUNT);
  }

  public static void chatLine() {
    if (on() && module.chat.get() && chatPhase >= 1.0F) {
      chatPhase = 0.0F;
    }
  }

  public static float chatRise(float lineHeight) {
    return on() && module.chat.get() && chatPhase < 1.0F
        ? lineHeight * (1.0F - (float) Easing.snappy(chatPhase))
        : 0.0F;
  }

  private static float appear(int which, boolean enabled) {
    if (!enabled) {
      return 1.0F;
    }
    if (SEEN[which] < frameIndex - 1) {
      APPEAR[which] = 0.0F;
    }
    SEEN[which] = frameIndex;
    return APPEAR[which] >= 1.0F
        ? 1.0F
        : APPEAR_FROM + (1.0F - APPEAR_FROM) * (float) Easing.spring(APPEAR[which]);
  }

  public static float tooltipScale() {
    return appear(TOOLTIP, on() && module.tooltips.get());
  }

  public static float tabScale() {
    return appear(TABLIST, on() && module.lists.get());
  }

  public static float boardScale() {
    return appear(BOARD, on() && module.lists.get());
  }
}
