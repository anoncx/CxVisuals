package me.cxiy.cxvisuals.mc.modules.waypoints;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.hud.WorldPins;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import me.cxiy.cxvisuals.mc.modules.social.FriendsLink;
import me.cxiy.cxvisuals.mc.modules.social.RoomLink;
import me.cxiy.cxvisuals.modules.friends.Friends;
import me.cxiy.cxvisuals.modules.hud.HudData;
import me.cxiy.cxvisuals.modules.waypoints.Waypoints;
import me.cxiy.cxvisuals.modules.waypoints.WaypointsModule;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

public final class Markers {
  private static final String FILE = "waypoints.json";
  private static final long MAX_FILE = 1L << 20;
  private static final int FRIEND = 0xFFFFFFFF;
  private static final double HEAD = 2.3;
  private static final double ABOVE = 1.2;
  private static final McCanvas CANVAS = new McCanvas();
  private static final Vector4f POINT = new Vector4f();

  private static WaypointsModule module;
  private static Waypoints waypoints;
  private static KeyMapping addKey;
  private static Path file;
  private static int saved;
  private static boolean wasDead;

  private Markers() {}

  public static WaypointsModule create(McKeyBinder keys) {
    module = new WaypointsModule(keys);
    waypoints = module.waypoints;
    addKey = keys.mapping(WaypointsModule.ADD_KEY);
    file = FabricLoader.getInstance().getConfigDir().resolve(CxVisualsClient.MOD_ID).resolve(FILE);
    try {
      if (Files.isRegularFile(file) && Files.size(file) < MAX_FILE) {
        waypoints.fromJson(Files.readString(file, StandardCharsets.UTF_8));
      }
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.warn("Couldn't read waypoints: {}", e.getMessage());
    }
    saved = waypoints.revision();
    waypoints.actions(
        new Waypoints.Actions() {
          @Override
          public void addHere(String name) {
            Markers.addHere(name);
          }

          @Override
          public List<Waypoints.Target> targets() {
            return FriendsLink.targets();
          }

          @Override
          public boolean share(Waypoints.Waypoint waypoint, String uuid) {
            return FriendsLink.shareWaypoint(waypoint, uuid);
          }
        });
    return module;
  }

  public static WaypointsModule module() {
    return module;
  }

  public static String scope(Minecraft mc) {
    if (mc.level == null) {
      return "";
    }
    if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
      return "local:" + mc.getSingleplayerServer().getWorldData().getLevelName();
    }
    ServerData server = mc.getCurrentServer();
    return server == null ? "" : "server:" + server.ip.strip().toLowerCase(Locale.ROOT);
  }

  public static String dimension(Minecraft mc) {
    return mc.level == null ? "" : mc.level.dimension().identifier().toString();
  }

  private static void addHere(String name) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null) {
      waypoints.notice(Lang.tr("Join a world first."));
      return;
    }
    waypoints.add(
        name, mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), dimension(mc));
  }

  public static boolean adopt(String name, int x, int y, int z, String dimension) {
    return waypoints.add(name, x, y, z, dimension) != null;
  }

  public static void tick(Minecraft mc) {
    if (module == null) {
      return;
    }
    waypoints.place(scope(mc), dimension(mc));
    boolean dead = mc.player != null && mc.player.isDeadOrDying();
    if (dead && !wasDead && module.enabled.get() && module.deaths.get()) {
      waypoints.death(
          mc.player.getBlockX(),
          mc.player.getBlockY(),
          mc.player.getBlockZ(),
          dimension(mc),
          System.currentTimeMillis());
    }
    wasDead = dead;
    while (addKey.consumeClick()) {
      if (module.enabled.get() && mc.gui.screen() == null) {
        addHere("");
      }
    }
    if (waypoints.revision() != saved) {
      saved = waypoints.revision();
      String json = waypoints.toJson();
      CompletableFuture.runAsync(() -> write(json));
    }
    if (Hud.module() != null) {
      fill(mc, Hud.module().data);
    }
  }

  public static void frame() {
    Minecraft mc = Minecraft.getInstance();
    if (module == null || Hud.module() == null || mc.level == null) {
      return;
    }
    HudData data = Hud.module().data;
    float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    for (int i = 0; i < data.markCount; i++) {
      Entity entity =
          data.markEntity[i] == HudData.NONE ? null : mc.level.getEntity(data.markEntity[i]);
      if (entity != null) {
        Vec3 at = entity.getPosition(partial);
        data.markX[i] = at.x;
        data.markY[i] = at.y + HEAD;
        data.markZ[i] = at.z;
      }
    }
  }

  public static void overlay(GuiGraphicsExtractor graphics) {
    Minecraft mc = Minecraft.getInstance();
    if (module == null
        || Hud.module() == null
        || mc.level == null
        || mc.gui.hud.isHidden()
        || mc.gui.screen() instanceof LevelLoadingScreen) {
      return;
    }
    HudData data = Hud.module().data;
    boolean places = module.enabled.get() && module.world.get();
    boolean people = module.enabled.get() && module.friends.get();
    if (data.markCount == 0 || (!places && !people)) {
      return;
    }
    CameraRenderState camera = mc.gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
    float uiScale = McCanvas.interfaceScale(mc.getWindow().getHeight());
    float width = mc.getWindow().getWidth() / uiScale;
    float height = mc.getWindow().getHeight() / uiScale;
    boolean any = false;
    for (int i = 0; i < data.markCount; i++) {
      data.pinOn[i] = false;
      if (data.markFriend[i] ? !people : !places) {
        continue;
      }
      double x = data.markX[i];
      double y = data.markY[i];
      double z = data.markZ[i];
      float dx = (float) (x - camera.pos.x);
      float dy = (float) (y - camera.pos.y);
      float dz = (float) (z - camera.pos.z);
      POINT.set(dx, dy, dz, 1.0F);
      camera.viewRotationMatrix.transform(POINT);
      camera.projectionMatrix.transform(POINT);
      if (POINT.w <= 0.05F) {
        continue;
      }
      float nx = POINT.x / POINT.w;
      float ny = POINT.y / POINT.w;
      if (nx < -1.0F || nx > 1.0F || ny < -1.0F || ny > 1.0F) {
        continue;
      }
      data.pinOn[i] = true;
      data.pinX[i] = (nx * 0.5F + 0.5F) * width;
      data.pinY[i] = (0.5F - ny * 0.5F) * height;
      data.pinDistance[i] = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
      any = true;
    }
    if (!any) {
      return;
    }
    graphics.nextStratum();
    CANVAS.begin(graphics, uiScale, Math.max(1, mc.getWindow().getGuiScale()));
    WorldPins.draw(CANVAS, Hud.theme(), data, width, height);
    CANVAS.end();
  }

  private static synchronized void write(String json) {
    try {
      Files.createDirectories(file.getParent());
      Path tmp = file.resolveSibling(FILE + ".tmp");
      Files.writeString(tmp, json, StandardCharsets.UTF_8);
      Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.warn("Couldn't save waypoints: {}", e.getMessage());
    }
  }

  private static void fill(Minecraft mc, HudData data) {
    data.markCount = 0;
    if (mc.level == null) {
      return;
    }
    String here = waypoints.dimension();
    Set<String> placed = new HashSet<>();
    if (FriendsLink.module() != null && FriendsLink.module().active()) {
      List<Friends.Friend> friends = FriendsLink.module().friends.friends();
      for (int i = 0; i < friends.size(); i++) {
        Friends.Friend friend = friends.get(i);
        if (!friend.online || !friend.located || !friend.dimension.equals(here)) {
          continue;
        }
        placed.add(friend.uuid);
        Entity entity =
            friend.entityId == Friends.NO_ENTITY ? null : mc.level.getEntity(friend.entityId);
        data.mark(
            friend.name,
            friend.x,
            friend.y + HEAD,
            friend.z,
            FRIEND,
            true,
            entity == null ? HudData.NONE : entity.getId());
      }
    }
    for (RoomLink.Spot spot : RoomLink.spots(here)) {
      if (placed.contains(spot.uuid())) {
        continue;
      }
      Entity entity = mc.level.getPlayerByUUID(UUID.fromString(spot.uuid()));
      int alpha = Math.round(spot.alpha() * 255.0F);
      data.mark(
          spot.name(),
          spot.x(),
          spot.y() + HEAD,
          spot.z(),
          (alpha << 24) | (FRIEND & 0xFFFFFF),
          true,
          entity == null ? HudData.NONE : entity.getId());
    }
    if (module.enabled.get()) {
      long now = System.currentTimeMillis();
      List<Waypoints.Waypoint> list = waypoints.list();
      for (int i = 0; i < list.size(); i++) {
        Waypoints.Waypoint waypoint = list.get(i);
        if (waypoint.dimension().equals(here)) {
          data.mark(
              waypoint.name(),
              waypoints.ago(waypoint, now),
              waypoint.x() + 0.5,
              waypoint.y() + ABOVE,
              waypoint.z() + 0.5,
              waypoint.color(),
              false,
              HudData.NONE);
        }
      }
    }
  }
}
