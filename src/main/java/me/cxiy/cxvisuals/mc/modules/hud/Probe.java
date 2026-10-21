package me.cxiy.cxvisuals.mc.modules.hud;

import com.mojang.blaze3d.platform.Window;
import java.lang.management.ManagementFactory;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.StyledText;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.gui.HudEditorScreen;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.mc.modules.social.Social;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.hud.HudModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;

public final class Probe {
  private static final EquipmentSlot[] ARMOR = {
    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
  };
  private static final ItemStack[] GEAR = new ItemStack[HudData.SLOTS];
  private static final int CLICK_RING = 64;
  private static final long CLICK_WINDOW = 1_000_000_000L;
  private static final long[][] CLICKS = new long[2][CLICK_RING];
  private static final int[] CLICK_HEAD = new int[2];
  private static final int SLOW_TICKS = 20;
  private static final int SCAN_TICKS = 4;
  private static final int TARGET_HOLD = 30;
  private static final String SINGLEPLAYER = Lang.key("Singleplayer");
  private static final String LAN = Lang.key("LAN world");
  private static final String LOCAL = Lang.key("Local");
  private static final String DEFAULT_NAME = "selectServer.defaultName";
  private static final double VIEW_MARGIN = 1.05;
  private static final float TICKS_PER_SECOND = 20.0F;
  private static final float SPEED_BLEND = 0.35F;
  private static final float TPS_BLEND = 0.3F;
  private static final double SHORTEST_SPAN = 0.5;
  private static final long SAMPLER_SLEEP = 1000L;
  private static final long START = System.nanoTime();

  private static volatile float cpu = HudData.UNKNOWN;
  private static volatile boolean cpuWanted;
  private static boolean samplerStarted;
  private static boolean gpuWanted;
  private static int slow = SLOW_TICKS;
  private static int scan = SCAN_TICKS;
  private static double lastX;
  private static double lastY;
  private static double lastZ;
  private static boolean moved;
  private static Holder<Biome> biome;
  private static ResourceKey<Level> dimension;
  private static int targetHold;
  private static boolean serverActive;
  private static Component motd;
  private static String motdLine = "";
  private static long serverGame;
  private static long serverNanos;
  private static float tps = HudData.UNKNOWN;
  private static double forwardX;
  private static double forwardY;
  private static double forwardZ;
  private static double rightX;
  private static double rightZ;
  private static double upX;
  private static double upY;
  private static double upZ;
  private static double tanX;
  private static double tanY;

  private Probe() {}

  public static boolean gpuWanted() {
    return gpuWanted;
  }

  public static void click(int button) {
    if (button < 0 || button > 1) {
      return;
    }
    CLICKS[button][CLICK_HEAD[button]] = System.nanoTime();
    CLICK_HEAD[button] = (CLICK_HEAD[button] + 1) % CLICK_RING;
  }

  public static void serverTime(long gameTime) {
    long now = System.nanoTime();
    if (serverNanos != 0L) {
      double span = (now - serverNanos) / 1.0e9;
      long ticks = gameTime - serverGame;
      if (span >= SHORTEST_SPAN && ticks > 0L) {
        float rate = (float) Math.min(TICKS_PER_SECOND, ticks / span);
        tps = tps < 0.0F ? rate : tps + (rate - tps) * TPS_BLEND;
      }
    }
    serverGame = gameTime;
    serverNanos = now;
  }

  static void frame(Minecraft mc, HudModule module) {
    HudData data = module.data;
    Options options = mc.options;
    data.fps = mc.getFps();
    data.chatOpen = mc.gui.screen() instanceof ChatScreen;
    Media.frame(module, data, data.chatOpen || mc.gui.screen() instanceof HudEditorScreen);
    data.forward = options.keyUp.isDown();
    data.back = options.keyDown.isDown();
    data.left = options.keyLeft.isDown();
    data.right = options.keyRight.isDown();
    data.jump = options.keyJump.isDown();
    data.attack = options.keyAttack.isDown();
    data.use = options.keyUse.isDown();
    LocalPlayer player = mc.player;
    if (player != null) {
      Vec3 at = player.getPosition(mc.getDeltaTracker().getGameTimeDeltaPartialTick(false));
      data.x = at.x;
      data.y = at.y;
      data.z = at.z;
      data.yaw = player.getYRot();
      data.pitch = player.getXRot();
    }
  }

  static void tick(Minecraft mc, HudModule module) {
    HudData data = module.data;
    LocalPlayer player = mc.player;
    boolean running = module.enabled.get() && player != null && mc.level != null;
    boolean serverWanted = running && (module.server.shown.get() || module.network.shown.get());
    if (serverWanted && !serverActive) {
      slow = SLOW_TICKS;
    }
    serverActive = serverWanted;
    gpuWanted = running && module.gpu.shown.get();
    cpuWanted = running && module.cpu.shown.get();
    if (!running) {
      moved = false;
      if (mc.level == null) {
        serverNanos = 0L;
        tps = HudData.UNKNOWN;
        biome = null;
        dimension = null;
        targetHold = 0;
        data.targetId = HudData.NONE;
        data.chat.clear();
      }
      return;
    }

    double x = player.getX();
    double y = player.getY();
    double z = player.getZ();
    if (moved) {
      double dx = x - lastX;
      double dy = y - lastY;
      double dz = z - lastZ;
      float now = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) * TICKS_PER_SECOND;
      data.speed += (now - data.speed) * SPEED_BLEND;
    } else {
      data.speed = 0.0F;
    }
    lastX = x;
    lastY = y;
    lastZ = z;
    moved = true;

    long nanos = System.nanoTime();
    data.clicksLeft = clicks(0, nanos);
    data.clicksRight = clicks(1, nanos);
    data.tps = tps;

    for (int i = 0; i < ARMOR.length; i++) {
      gear(data, i, player.getItemBySlot(ARMOR[i]));
    }
    gear(data, HudData.FIRST_HAND, player.getMainHandItem());
    gear(data, HudData.FIRST_HAND + 1, player.getOffhandItem());
    if (module.target.shown.get()) {
      target(mc, data, player);
    } else {
      data.targetId = HudData.NONE;
      targetHold = 0;
    }
    McCanvas.items(GEAR);

    if (module.players.shown.get()) {
      if (++scan >= SCAN_TICKS) {
        scan = 0;
        players(mc, module, player);
      }
    } else {
      data.playerCount = 0;
    }

    if (++slow >= SLOW_TICKS) {
      slow = 0;
      slow(mc, module, player, nanos);
    }
  }

  private static void slow(Minecraft mc, HudModule module, LocalPlayer player, long nanos) {
    HudData data = module.data;
    ClientPacketListener connection = mc.getConnection();
    PlayerInfo info = connection == null ? null : connection.getPlayerInfo(player.getUUID());
    float listed = info == null ? 0.0F : Math.max(0, info.getLatency());
    HudModule.PingSource source = module.pingSource.get();
    boolean wanted =
        module.ping.shown.get() || module.server.shown.get() || module.network.shown.get();
    float measured =
        source == HudModule.PingSource.TAB_LIST || !wanted || mc.hasSingleplayerServer()
            ? HudData.UNKNOWN
            : PingProbe.measure(connection);
    data.ping =
        source == HudModule.PingSource.MEASURED && wanted && !mc.hasSingleplayerServer()
            ? Math.max(0.0F, measured)
            : measured >= 0.0F ? measured : listed;

    if (serverActive) {
      server(mc, data, connection);
    }

    Runtime runtime = Runtime.getRuntime();
    data.ram = (runtime.totalMemory() - runtime.freeMemory()) * 100.0F / runtime.maxMemory();
    data.gpu =
        gpuWanted
            ? (float) Math.max(0.0, Math.min(100.0, mc.getGpuUtilization()))
            : HudData.UNKNOWN;
    if (cpuWanted && !samplerStarted) {
      samplerStarted = true;
      startSampler();
    }
    data.cpu = cpuWanted ? cpu : HudData.UNKNOWN;

    LocalTime time = LocalTime.now();
    data.clockMinutes = time.getHour() * 60 + time.getMinute();
    data.sessionSeconds = (nanos - START) / 1_000_000_000L;
    data.dayTime = mc.level.getDefaultClockTime();

    Holder<Biome> current = mc.level.getBiome(player.blockPosition());
    if (current != biome) {
      biome = current;
      data.biome = current.unwrapKey().map(key -> title(key.identifier().getPath())).orElse("");
    }
  }

  private static void server(Minecraft mc, HudData data, ClientPacketListener connection) {
    ServerData server = mc.getCurrentServer();
    boolean remote = server != null && !server.isLan();
    boolean named = remote && !server.name.isBlank() && !server.name.equals(I18n.get(DEFAULT_NAME));
    data.serverName =
        server == null
            ? Lang.tr(SINGLEPLAYER)
            : server.isLan() ? Lang.tr(LAN) : named ? server.name : server.ip;
    if (server == null) {
      data.serverAddress = Lang.tr(LOCAL);
    } else if (named || server.isLan()) {
      data.serverAddress = server.ip;
    } else {
      if (server.motd != motd) {
        motd = server.motd;
        motdLine = motd == null ? "" : firstLine(legacy(motd));
      }
      data.serverAddress = motdLine;
    }
    data.serverIcon = server == null ? null : server.getIconBytes();
    data.maxPlayers = server == null || server.players == null ? 0 : server.players.max();
    String brand = connection == null ? null : connection.serverBrand();
    data.serverBrand = brand == null ? "" : brand;
    data.onlinePlayers = connection == null ? 1 : connection.getOnlinePlayers().size();
    if (connection != null) {
      data.packetsIn = connection.getConnection().getAverageReceivedPackets();
      data.packetsOut = connection.getConnection().getAverageSentPackets();
    }
    ResourceKey<Level> current = mc.level.dimension();
    if (current != dimension) {
      dimension = current;
      data.dimension = title(current.identifier().getPath());
    }
  }

  private static String firstLine(String text) {
    int end = text.indexOf('\n');
    return (end < 0 ? text : text.substring(0, end)).strip();
  }

  private static String legacy(Component text) {
    StringBuilder out = new StringBuilder();
    text.visit(
        (style, part) -> {
          if (!part.isEmpty()) {
            codes(out, style);
            out.append(part);
          }
          return Optional.empty();
        },
        Style.EMPTY);
    return out.toString();
  }

  private static void codes(StringBuilder out, Style style) {
    out.append(StyledText.MARK).append('r');
    TextColor color = style.getColor();
    if (color != null) {
      out.append(StyledText.MARK).append('x');
      for (int shift = 20; shift >= 0; shift -= 4) {
        out.append(StyledText.MARK)
            .append(Character.forDigit((color.getValue() >> shift) & 0xF, 16));
      }
    }
    code(out, style.isBold(), 'l');
    code(out, style.isItalic(), 'o');
    code(out, style.isUnderlined(), 'n');
    code(out, style.isStrikethrough(), 'm');
    code(out, style.isObfuscated(), 'k');
  }

  private static void code(StringBuilder out, boolean on, char code) {
    if (on) {
      out.append(StyledText.MARK).append(code);
    }
  }

  private static void target(Minecraft mc, HudData data, LocalPlayer self) {
    if (mc.crosshairPickEntity instanceof LivingEntity living
        && !living.isInvisibleTo(self)
        && !living.isSpectator()) {
      targetHold = TARGET_HOLD;
      if (data.targetId != living.getId()) {
        data.targetId = living.getId();
        data.targetPlayer = living instanceof AbstractClientPlayer;
        data.targetName =
            data.targetPlayer ? living.getScoreboardName() : living.getName().getString();
      }
      data.targetDistance = self.distanceTo(living);
      data.targetHealth = living.getHealth();
      data.targetMax = living.getMaxHealth();
      data.targetAbsorb = living.getAbsorptionAmount();
      data.targetArmor = living.getArmorValue();
      for (int i = 0; i < ARMOR.length; i++) {
        gear(data, HudData.TARGET_FIRST + i, living.getItemBySlot(ARMOR[i]));
      }
      gear(data, HudData.TARGET_FIRST + HudData.FIRST_HAND, living.getMainHandItem());
      gear(data, HudData.TARGET_FIRST + HudData.FIRST_HAND + 1, living.getOffhandItem());
    } else if (targetHold > 0 && --targetHold == 0) {
      data.targetId = HudData.NONE;
    }
  }

  private static void players(Minecraft mc, HudModule module, LocalPlayer self) {
    HudData data = module.data;
    boolean sight = module.players.sight.get();
    List<AbstractClientPlayer> all = mc.level.players();
    if (sight) {
      aim(mc, self);
    }
    int count = 0;
    for (int i = 0; i < all.size(); i++) {
      AbstractClientPlayer other = all.get(i);
      if (other == self
          || other.isInvisible()
          || other.isSpectator()
          || (sight && !(onScreen(self, other) && self.hasLineOfSight(other)))) {
        continue;
      }
      float distance = self.distanceTo(other);
      int slot = count;
      if (count == HudData.PLAYER_SLOTS) {
        if (distance >= data.playerDistance[count - 1]) {
          continue;
        }
        slot = count - 1;
      } else {
        count++;
      }
      while (slot > 0 && data.playerDistance[slot - 1] > distance) {
        data.playerIds[slot] = data.playerIds[slot - 1];
        data.playerNames[slot] = data.playerNames[slot - 1];
        data.playerTracks[slot] = data.playerTracks[slot - 1];
        data.playerDistance[slot] = data.playerDistance[slot - 1];
        slot--;
      }
      data.playerIds[slot] = other.getId();
      data.playerNames[slot] = other.getScoreboardName();
      data.playerTracks[slot] = Social.trackLine(other.getUUID());
      data.playerDistance[slot] = distance;
    }
    data.playerCount = count;
  }

  private static void aim(Minecraft mc, LocalPlayer self) {
    double yaw = Math.toRadians(self.getYRot());
    double pitch = Math.toRadians(self.getXRot());
    double level = Math.cos(pitch);
    forwardX = -Math.sin(yaw) * level;
    forwardY = -Math.sin(pitch);
    forwardZ = Math.cos(yaw) * level;
    rightX = -Math.cos(yaw);
    rightZ = -Math.sin(yaw);
    upX = -rightZ * forwardY;
    upY = rightZ * forwardX - rightX * forwardZ;
    upZ = rightX * forwardY;
    Window window = mc.getWindow();
    tanY = Math.tan(Math.toRadians(mc.options.fov().get() * 0.5)) * VIEW_MARGIN;
    tanX = tanY * window.getWidth() / Math.max(1, window.getHeight());
  }

  private static boolean onScreen(LocalPlayer self, AbstractClientPlayer other) {
    double dx = other.getX() - self.getX();
    double dy = other.getY() + other.getBbHeight() * 0.5 - self.getEyeY();
    double dz = other.getZ() - self.getZ();
    double depth = dx * forwardX + dy * forwardY + dz * forwardZ;
    if (depth <= 0.0) {
      return false;
    }
    double across = Math.abs(dx * rightX + dz * rightZ);
    double along = Math.abs(dx * upX + dy * upY + dz * upZ);
    double body = other.getBbHeight() * 0.5;
    return across <= depth * tanX + other.getBbWidth() && along <= depth * tanY + body;
  }

  private static void gear(HudData data, int slot, ItemStack stack) {
    boolean present = !stack.isEmpty();
    GEAR[slot] = stack;
    data.gear[slot] = present;
    if (!present) {
      return;
    }
    data.count[slot] = stack.getCount();
    data.durability[slot] =
        stack.isDamageableItem()
            ? 1.0F - (float) stack.getDamageValue() / Math.max(1, stack.getMaxDamage())
            : HudData.UNKNOWN;
  }

  private static float clicks(int button, long now) {
    long[] ring = CLICKS[button];
    int count = 0;
    for (int i = 0; i < CLICK_RING; i++) {
      if (ring[i] != 0L && now - ring[i] <= CLICK_WINDOW) {
        count++;
      }
    }
    return count;
  }

  private static String title(String path) {
    StringBuilder text = new StringBuilder(path.length());
    boolean start = true;
    for (int i = 0; i < path.length(); i++) {
      char ch = path.charAt(i);
      if (ch == '_' || ch == '/') {
        text.append(' ');
        start = true;
      } else {
        text.append(start ? Character.toUpperCase(ch) : ch);
        start = false;
      }
    }
    return text.toString();
  }

  private static void startSampler() {
    Thread thread =
        new Thread(
            () -> {
              try {
                com.sun.management.OperatingSystemMXBean system =
                    (com.sun.management.OperatingSystemMXBean)
                        ManagementFactory.getOperatingSystemMXBean();
                while (true) {
                  if (cpuWanted) {
                    double load = system.getCpuLoad();
                    cpu = load < 0.0 ? HudData.UNKNOWN : (float) Math.min(100.0, load * 100.0);
                  }
                  Thread.sleep(SAMPLER_SLEEP);
                }
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              } catch (RuntimeException | LinkageError e) {
                cpu = HudData.UNKNOWN;
                CxVisualsClient.LOG.warn(
                    "CPU load is not available on this runtime: {}", e.toString());
              }
            },
            "CxVisuals system sampler");
    thread.setDaemon(true);
    thread.start();
  }
}
