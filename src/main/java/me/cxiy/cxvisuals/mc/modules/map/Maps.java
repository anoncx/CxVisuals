package me.cxiy.cxvisuals.mc.modules.map;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.gui.Themes;
import me.cxiy.cxvisuals.gui.map.MapPlayer;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import me.cxiy.cxvisuals.mc.modules.inventory.Stacks;
import me.cxiy.cxvisuals.mc.modules.social.FriendsLink;
import me.cxiy.cxvisuals.mc.modules.social.RoomLink;
import me.cxiy.cxvisuals.mc.modules.waypoints.Markers;
import me.cxiy.cxvisuals.modules.map.AreaPreview;
import me.cxiy.cxvisuals.modules.map.BlockPalette;
import me.cxiy.cxvisuals.modules.map.MapAtlas;
import me.cxiy.cxvisuals.modules.map.MapModule;
import me.cxiy.cxvisuals.modules.map.MapShare;
import me.cxiy.cxvisuals.modules.map.MapSource;
import me.cxiy.cxvisuals.modules.map.MapWorld;
import me.cxiy.cxvisuals.modules.map.Region;
import me.cxiy.cxvisuals.modules.map.TileKey;
import me.cxiy.cxvisuals.modules.waypoints.Waypoints;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class Maps {
  private static final String FOLDER = "maps";

  private static MapModule module;
  private static MapWorld world;
  private static Cartographer cartographer;
  private static KeyMapping openKey;
  private static KeyMapping zoomInKey;
  private static KeyMapping zoomOutKey;
  private static Path root;
  private static String placed = "";
  private static long lastFrame;
  private static final long MAX_FRAME_NANOS = 100_000_000L;
  private static final int BUSY_BACKLOG = 24;
  private static String notice = "";
  private static Themes themes;
  private static final MapSource SOURCE =
      new MapSource() {
        @Override
        public MapAtlas atlas() {
          return module.atlas;
        }

        @Override
        public boolean active() {
          return live();
        }

        @Override
        public int layer() {
          return cartographer.underground() ? TileKey.CAVE : TileKey.SURFACE;
        }

        @Override
        public boolean clouds() {
          return module.clouds.get();
        }

        @Override
        public boolean solid() {
          return module.solid.get();
        }

        @Override
        public int terrain() {
          return module.terrain.get().ordinal();
        }

        @Override
        public boolean waypoints() {
          return module.waypoints.get();
        }

        @Override
        public boolean friends() {
          return module.friends.get();
        }

        @Override
        public int players(double[] x, double[] z, int[] ids, String[] names, int max) {
          Minecraft mc = Minecraft.getInstance();
          if (mc.level == null || !module.players.get()) {
            return 0;
          }
          int count = 0;
          List<AbstractClientPlayer> players = mc.level.players();
          for (int i = 0; i < players.size() && count < max; i++) {
            AbstractClientPlayer player = players.get(i);
            if (player != mc.player && !player.isInvisible()) {
              x[count] = player.getX();
              z[count] = player.getZ();
              ids[count] = player.getId();
              names[count] = player.getGameProfile().name();
              count++;
            }
          }
          return count;
        }

        @Override
        public boolean mobHeads() {
          return module.mobIcons.get() == MapModule.MobIcons.HEADS;
        }

        @Override
        public int mobs(double[] x, double[] z, boolean[] hostile, int[] ids, int max) {
          Minecraft mc = Minecraft.getInstance();
          if (mc.level == null || mc.player == null || !module.mobs.get()) {
            return 0;
          }
          int count = 0;
          for (Entity entity : mc.level.entitiesForRendering()) {
            if (count >= max) {
              break;
            }
            if (entity instanceof Mob mob && !mob.isInvisible() && mob.isAlive()) {
              x[count] = mob.getX();
              z[count] = mob.getZ();
              hostile[count] = mob instanceof Enemy;
              ids[count] = mob.getId();
              count++;
            }
          }
          return count;
        }
      };

  private Maps() {}

  public static MapModule create(McKeyBinder keys, ClientSettings client) {
    module = new MapModule(keys, Maps::open);
    themes = new Themes(client);
    world = new MapWorld(module.atlas, CxVisualsClient.LOG::warn);
    cartographer = new Cartographer(world);
    root =
        FabricLoader.getInstance().getConfigDir().resolve(CxVisualsClient.MOD_ID).resolve(FOLDER);
    openKey = keys.mapping(MapModule.OPEN_KEY);
    zoomInKey = keys.mapping(MapModule.ZOOM_IN_KEY);
    zoomOutKey = keys.mapping(MapModule.ZOOM_OUT_KEY);
    ClientChunkEvents.CHUNK_LOAD.register(
        (level, chunk) -> {
          if (live()) {
            cartographer.loaded(chunk);
          }
        });
    return module;
  }

  public static MapModule module() {
    return module;
  }

  public static MapWorld world() {
    return world;
  }

  public static MapSource source() {
    return SOURCE;
  }

  public static Path root() {
    return root;
  }

  public static boolean live() {
    return module != null && module.live() && world.active();
  }

  public static boolean underground() {
    return cartographer != null && cartographer.underground();
  }

  static boolean closes(KeyEvent event) {
    return openKey.matches(event);
  }

  public static void changed(Level level, BlockPos pos) {
    if (level.isClientSide() && live()) {
      cartographer.changed(pos);
    }
  }

  public static void open() {
    Minecraft mc = Minecraft.getInstance();
    if (live() && mc.level != null) {
      MapScreen.open(mc.gui.screen(), themes);
    }
  }

  public static void tick(Minecraft mc) {
    if (module == null) {
      return;
    }
    if (Hud.module() != null) {
      Hud.module().minimap.source(SOURCE);
    }
    while (openKey.consumeClick()) {
      if (mc.gui.screen() == null) {
        open();
      }
    }
    while (zoomInKey.consumeClick()) {
      Hud.module().minimap.zoomBy(true);
    }
    while (zoomOutKey.consumeClick()) {
      Hud.module().minimap.zoomBy(false);
    }
    if (!module.live() || mc.level == null || mc.player == null) {
      if (world.active()) {
        world.leave();
        cartographer.reset();
      }
      placed = "";
      RoomLink.leaveWorld();
      world.tick(System.nanoTime(), Long.MIN_VALUE);
      return;
    }
    String scope = Markers.scope(mc);
    String dimension = Markers.dimension(mc);
    String key = scope + "|" + dimension;
    world.terrain(module.terrain.get());
    if (!key.equals(placed)) {
      placed = key;
      cartographer.reset();
      world.place(root, scope, dimension);
      cartographer.everything(mc.level, mc.player, mc.options.getEffectiveRenderDistance());
    }
    module.atlas.frame();
    cartographer.sprites();
    boolean solid = module.solid.get();
    if (solid && !cartographer.solid()) {
      cartographer.everything(mc.level, mc.player, mc.options.getEffectiveRenderDistance());
    }
    cartographer.solid(solid);
    cartographer.tick(mc.level, mc.player, module.caves.get() == MapModule.Caves.AUTO);
    world.tick(
        System.nanoTime(),
        Region.key(mc.player.getBlockX() >> Region.SHIFT, mc.player.getBlockZ() >> Region.SHIFT));
    RoomLink.tick(mc);
  }

  public static String notice() {
    String out = notice;
    notice = "";
    return out;
  }

  public static MapPlayer player(int entityId) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || !(mc.level.getEntity(entityId) instanceof AbstractClientPlayer who)) {
      return null;
    }
    PlayerInfo info =
        mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(who.getUUID());
    List<String> states = new ArrayList<>(4);
    if (who.isShiftKeyDown()) {
      states.add(Lang.tr("Sneaking"));
    }
    if (who.isSprinting()) {
      states.add(Lang.tr("Sprinting"));
    }
    if (who.isFallFlying()) {
      states.add(Lang.tr("Gliding"));
    }
    if (who.isSwimming() || who.isInWater()) {
      states.add(Lang.tr("Swimming"));
    }
    if (who.isOnFire()) {
      states.add(Lang.tr("Burning"));
    }
    if (who.isUsingItem()) {
      states.add(Lang.tr("Using an item"));
    }
    ItemStack[] worn = {
      who.getMainHandItem(),
      who.getOffhandItem(),
      who.getItemBySlot(EquipmentSlot.HEAD),
      who.getItemBySlot(EquipmentSlot.CHEST),
      who.getItemBySlot(EquipmentSlot.LEGS),
      who.getItemBySlot(EquipmentSlot.FEET)
    };
    int[] gear = new int[worn.length];
    String[] gearNames = new String[worn.length];
    for (int i = 0; i < worn.length; i++) {
      gear[i] = Stacks.of(worn[i]);
      gearNames[i] = worn[i].isEmpty() ? "" : worn[i].getHoverName().getString();
    }
    String mode =
        info == null || info.getGameMode() == null
            ? ""
            : info.getGameMode().getShortDisplayName().getString();
    return new MapPlayer(
        entityId,
        who.getGameProfile().name(),
        who.getUUID().toString(),
        mode,
        info == null ? -1 : info.getLatency(),
        who.getHealth(),
        who.getMaxHealth(),
        who.getAbsorptionAmount(),
        who.getArmorValue(),
        who.getFoodData().getFoodLevel(),
        who.experienceLevel,
        gear,
        gearNames,
        who.getTeam() == null ? "" : who.getTeam().getName(),
        who.getX(),
        who.getY(),
        who.getZ(),
        states);
  }

  public static final int PUBLISH_RADIUS = 63;

  public static void collectForPublish(
      boolean whole, int[] selection, Consumer<List<MapShare.Chunk>> done) {
    Minecraft mc = Minecraft.getInstance();
    int[] box = whole || selection == null ? null : selection;
    if (box == null && world != null && mc.player != null) {
      int[] bounds = world.exploredBounds();
      int centreX = mc.player.getBlockX() >> 4;
      int centreZ = mc.player.getBlockZ() >> 4;
      if (bounds != null) {
        box =
            new int[] {
              Math.max(bounds[0], centreX - PUBLISH_RADIUS),
              Math.max(bounds[1], centreZ - PUBLISH_RADIUS),
              Math.min(bounds[2], centreX + PUBLISH_RADIUS),
              Math.min(bounds[3], centreZ + PUBLISH_RADIUS)
            };
      }
    }
    if (box == null || world == null) {
      done.accept(List.of());
      return;
    }
    world.collect(box[0], box[1], box[2], box[3], MapShare.MAX_CHUNKS, done);
  }

  public static void say(String text) {
    notice = text;
  }

  public static List<MapShare.Chunk> readChunks(
      String dimension, int regionX, int regionZ, List<long[]> coords) {
    return world == null ? List.of() : world.readChunks(dimension, regionX, regionZ, coords);
  }

  public static List<MapShare.Chunk> readRegion(String dimension, int regionX, int regionZ) {
    return world == null ? List.of() : world.readRegion(dimension, regionX, regionZ);
  }

  public static void importRemote(String dimension, List<MapShare.Chunk> chunks) {
    String scope = Markers.scope(Minecraft.getInstance());
    if (world != null && !scope.isEmpty()) {
      world.importChunks(root, scope, dimension, chunks);
    }
  }

  public static BlockPalette palette() {
    return world.palette();
  }

  public static int height(int x, int z) {
    return world.height(x, z);
  }

  public static boolean importArea(MapShare.Area area) {
    if (world == null || area.chunks().isEmpty()) {
      return false;
    }
    Minecraft mc = Minecraft.getInstance();
    String scope = area.scope().isEmpty() ? Markers.scope(mc) : area.scope();
    if (scope.isEmpty()) {
      return false;
    }
    world.importChunks(root, scope, area.dimension(), area.chunks());
    return true;
  }

  public static void shareArea(
      int minChunkX, int minChunkZ, int maxChunkX, int maxChunkZ, Waypoints.Target to) {
    Minecraft mc = Minecraft.getInstance();
    String scope = Markers.scope(mc);
    String dimension = Markers.dimension(mc);
    int centreX = ((minChunkX + maxChunkX + 1) << 4) / 2;
    int centreZ = ((minChunkZ + maxChunkZ + 1) << 4) / 2;
    String name = Lang.format("Around {0} {1}", centreX, centreZ);
    notice = Lang.format("Packing the map for {0}", to.name());
    world.collect(
        minChunkX,
        minChunkZ,
        maxChunkX,
        maxChunkZ,
        MapShare.MAX_CHUNKS,
        chunks -> {
          if (chunks.isEmpty()) {
            notice = Lang.tr("Nothing explored there yet.");
            return;
          }
          world.execute(
              () -> {
                byte[] blob;
                try {
                  blob = MapShare.encode(scope, dimension, chunks, world.palette());
                } catch (IOException e) {
                  blob = new byte[0];
                }
                byte[] packed = blob;
                byte[] preview = AreaPreview.render(chunks, world.terrain());
                world.later(
                    () ->
                        notice =
                            FriendsLink.shareMap(to.uuid(), name, packed, chunks.size(), preview)
                                ? Lang.format("Map sent to {0}", to.name())
                                : Lang.tr("Couldn't share the map."));
              });
        });
  }

  public static void frame() {
    long now = System.nanoTime();
    long elapsed = lastFrame == 0L ? 0L : Math.min(now - lastFrame, MAX_FRAME_NANOS);
    lastFrame = now;
    Minecraft mc = Minecraft.getInstance();
    if (live() && mc.level != null && mc.player != null) {
      module.atlas.pace(cartographer.backlog() > BUSY_BACKLOG);
      cartographer.frame(mc.level, mc.player, System.currentTimeMillis(), elapsed);
    }
  }

  public static void erase() {
    Minecraft mc = Minecraft.getInstance();
    world.erase(root);
    cartographer.reset();
    if (mc.level != null && mc.player != null) {
      cartographer.everything(mc.level, mc.player, mc.options.getEffectiveRenderDistance());
    }
  }

  public static void shutdown() {
    RoomLink.shutdown();
    if (world != null) {
      world.shutdown();
    }
  }
}
