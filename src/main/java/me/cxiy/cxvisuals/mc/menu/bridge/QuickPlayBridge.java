package me.cxiy.cxvisuals.mc.menu.bridge;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.imageio.ImageIO;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.StyledText;
import me.cxiy.cxvisuals.mc.CxVisuals;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.Desktop;
import me.cxiy.cxvisuals.menu.model.Item;
import me.cxiy.cxvisuals.menu.model.QuickPlay;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;

public final class QuickPlayBridge implements QuickPlay {
  private static final int MOST_WORLDS = 3;
  private static final int MOST_SERVERS = 6;
  private static final int LARGEST_ICON = 1 << 20;
  private static final int THUMB = 128;
  private static final long FRESH_NANOS = 10_000_000_000L;
  private static final long DAY_MILLIS = 86_400_000L;
  private static final long[] REACH = {150L, 300L, 600L, 1000L};
  private static final String SEPARATOR = "  ·  ";
  private static final String SHOT_DATE = "d MMMM, HH:mm";
  private static final ExecutorService WORK =
      Executors.newFixedThreadPool(
          3,
          task -> {
            Thread thread = new Thread(task, "CxVisuals quick play");
            thread.setDaemon(true);
            return thread;
          });

  private final Item[] worlds = new Item[MOST_WORLDS];
  private final String[] levelIds = new String[MOST_WORLDS];
  private final Item[] servers = new Item[MOST_SERVERS];
  private final ServerData[] hosts = new ServerData[MOST_SERVERS];
  private final ServerStatusPinger pinger = new ServerStatusPinger();
  private int worldCount;
  private int serverCount;
  private int request;
  private long refreshed;
  private boolean ever;
  private boolean live;
  private byte[] shot;
  private String shotLabel = "";
  private File shotFile;
  private long shotStamp;

  public void refresh() {
    long now = System.nanoTime();
    if (ever && now - refreshed < FRESH_NANOS) {
      return;
    }
    ever = true;
    refreshed = now;
    Minecraft mc = Minecraft.getInstance();
    int ticket = ++request;
    readServers(mc);
    readShot(mc, ticket);
    try {
      LevelStorageSource source = mc.getLevelSource();
      source
          .loadLevelSummaries(source.findLevelCandidates())
          .thenAcceptAsync(
              list -> {
                if (ticket == request) {
                  readWorlds(list);
                }
              },
              mc)
          .exceptionally(error -> null);
    } catch (RuntimeException e) {
      worldCount = 0;
      CxVisualsClient.LOG.warn("Could not list worlds for the menu home page", e);
    }
  }

  public void tick() {
    pinger.tick();
  }

  public void rest() {
    pinger.removeAll();
  }

  private void readServers(Minecraft mc) {
    serverCount = 0;
    pinger.removeAll();
    live = CxVisuals.registry().client().menuStatus.get();
    try {
      ServerList list = new ServerList(mc);
      list.load();
      String last = mc.options.lastMpIp;
      ServerData recent = last == null || last.isBlank() ? null : list.get(last);
      if (recent != null) {
        addServer(mc, recent);
      }
      for (int i = 0; i < list.size() && serverCount < MOST_SERVERS; i++) {
        if (list.get(i) != recent) {
          addServer(mc, list.get(i));
        }
      }
    } catch (RuntimeException e) {
      serverCount = 0;
    }
  }

  private void addServer(Minecraft mc, ServerData data) {
    Item item = new Item();
    item.title = data.name == null || data.name.isBlank() ? data.ip : data.name;
    item.first = StyledText.of(data.ip);
    item.signal = live ? Item.BUSY : Item.NO_SIGNAL;
    hosts[serverCount] = data;
    servers[serverCount++] = item;
    if (!live) {
      item.icon = data.getIconBytes();
      return;
    }
    data.setState(ServerData.State.PINGING);
    data.motd = CommonComponents.EMPTY;
    data.status = CommonComponents.EMPTY;
    WORK.submit(
        () -> {
          try {
            pinger.pingServer(
                data,
                () -> {},
                () ->
                    data.setState(
                        data.protocol == SharedConstants.getCurrentVersion().protocolVersion()
                            ? ServerData.State.SUCCESSFUL
                            : ServerData.State.INCOMPATIBLE),
                EventLoopGroupHolder.remote(mc.options.useNativeTransport()));
          } catch (UnknownHostException e) {
            data.setState(ServerData.State.UNREACHABLE);
          } catch (Exception e) {
            data.setState(ServerData.State.UNREACHABLE);
          }
        });
  }

  private static int bars(long ping) {
    for (int i = 0; i < REACH.length; i++) {
      if (ping < REACH[i]) {
        return Item.BARS - i;
      }
    }
    return 1;
  }

  private void readWorlds(List<LevelSummary> list) {
    worldCount = 0;
    long now = System.currentTimeMillis();
    for (int slot = 0; slot < MOST_WORLDS; slot++) {
      LevelSummary latest = null;
      for (LevelSummary summary : list) {
        if (summary.isDisabled() || taken(summary.getLevelId())) {
          continue;
        }
        if (latest == null || summary.getLastPlayed() > latest.getLastPlayed()) {
          latest = summary;
        }
      }
      if (latest == null) {
        return;
      }
      Item item = new Item();
      item.title = latest.getLevelName();
      String mode =
          latest.isHardcore()
              ? Lang.tr("Hardcore")
              : Texts.plain(latest.getGameMode().getShortDisplayName());
      item.first = StyledText.of(mode + SEPARATOR + age(now - latest.getLastPlayed()));
      item.icon = icon(latest.getIcon());
      levelIds[worldCount] = latest.getLevelId();
      worlds[worldCount++] = item;
    }
  }

  private boolean taken(String levelId) {
    for (int i = 0; i < worldCount; i++) {
      if (levelIds[i].equals(levelId)) {
        return true;
      }
    }
    return false;
  }

  private static String age(long millis) {
    long days = Math.max(0L, millis) / DAY_MILLIS;
    if (days <= 1L) {
      return Lang.tr(days == 0L ? "Today" : "Yesterday");
    }
    return Lang.count((int) days, "{0} day ago", "{0} days ago")
        .replace("{0}", Long.toString(days));
  }

  private static byte[] icon(Path file) {
    if (file == null || !Files.isRegularFile(file)) {
      return null;
    }
    try (InputStream in = Files.newInputStream(file)) {
      byte[] data = in.readNBytes(LARGEST_ICON + 1);
      return data.length > LARGEST_ICON ? null : data;
    } catch (IOException | RuntimeException e) {
      return null;
    }
  }

  private static File shots(Minecraft mc) {
    return new File(mc.gameDirectory, Screenshot.SCREENSHOT_DIR);
  }

  private void readShot(Minecraft mc, int ticket) {
    File directory = shots(mc);
    CompletableFuture.supplyAsync(() -> newest(directory), WORK)
        .thenAcceptAsync(
            file -> {
              if (ticket != request) {
                return;
              }
              if (file == null) {
                shot = null;
                shotLabel = "";
                shotFile = null;
                return;
              }
              long stamp = file.lastModified();
              if (file.equals(shotFile) && stamp == shotStamp) {
                return;
              }
              shotFile = file;
              shotStamp = stamp;
              shotLabel =
                  DateTimeFormatter.ofPattern(SHOT_DATE, Lang.locale())
                      .format(Instant.ofEpochMilli(stamp).atZone(ZoneId.systemDefault()));
              CompletableFuture.supplyAsync(() -> thumb(file), WORK)
                  .thenAcceptAsync(
                      bytes -> {
                        if (file.equals(shotFile)) {
                          shot = bytes;
                        }
                      },
                      mc);
            },
            mc);
  }

  private static File newest(File directory) {
    File[] files = directory.listFiles((dir, name) -> name.endsWith(".png"));
    File latest = null;
    if (files != null) {
      for (File file : files) {
        if (file.isFile() && (latest == null || file.lastModified() > latest.lastModified())) {
          latest = file;
        }
      }
    }
    return latest;
  }

  private static byte[] thumb(File file) {
    try {
      BufferedImage source = ImageIO.read(file);
      if (source == null) {
        return null;
      }
      int width = source.getWidth();
      int height = source.getHeight();
      int side = Math.min(width, height);
      BufferedImage out = new BufferedImage(THUMB, THUMB, BufferedImage.TYPE_INT_ARGB);
      Graphics2D graphics = out.createGraphics();
      graphics.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.drawImage(
          source,
          0,
          0,
          THUMB,
          THUMB,
          (width - side) / 2,
          (height - side) / 2,
          (width + side) / 2,
          (height + side) / 2,
          null);
      graphics.dispose();
      ByteArrayOutputStream bytes = new ByteArrayOutputStream(THUMB * THUMB);
      ImageIO.write(out, "png", bytes);
      return bytes.toByteArray();
    } catch (IOException | RuntimeException | LinkageError e) {
      return null;
    }
  }

  @Override
  public int count(int group) {
    return group == WORLDS ? worldCount : serverCount;
  }

  @Override
  public Item item(int group, int index) {
    if (group == WORLDS) {
      return worlds[index];
    }
    Item item = servers[index];
    if (!live) {
      return item;
    }
    ServerData data = hosts[index];
    ServerData.State state = data.state();
    int signal =
        switch (state) {
          case INITIAL, PINGING -> Item.BUSY;
          case UNREACHABLE -> 0;
          case INCOMPATIBLE, SUCCESSFUL -> bars(data.ping);
        };
    if (item.signal != signal || item.stamp != data.status || item.icon != data.getIconBytes()) {
      item.signal = signal;
      item.stamp = data.status;
      item.icon = data.getIconBytes();
      item.flagged =
          state == ServerData.State.INCOMPATIBLE || state == ServerData.State.UNREACHABLE;
      item.trailing =
          state == ServerData.State.SUCCESSFUL || state == ServerData.State.INCOMPATIBLE
              ? Texts.plain(data.status)
              : "";
    }
    return item;
  }

  @Override
  public void open(int group, int index) {
    Minecraft mc = Minecraft.getInstance();
    Screen parent = mc.gui.screen();
    try {
      if (group == WORLDS && index < worldCount) {
        mc.createWorldOpenFlows().openWorld(levelIds[index], () -> mc.gui.setScreen(parent));
      } else if (group == SERVERS && index < serverCount) {
        ServerData data = hosts[index];
        ConnectScreen.startConnecting(
            parent, mc, ServerAddress.parseString(data.ip), data, false, null);
      }
    } catch (RuntimeException e) {
      CxVisualsClient.LOG.error("Could not start quick play from the menu home page", e);
    }
  }

  @Override
  public byte[] shot() {
    return shot;
  }

  @Override
  public String shotLabel() {
    return shotLabel;
  }

  @Override
  public void openShots() {
    File directory = shots(Minecraft.getInstance());
    if (directory.isDirectory() || directory.mkdirs()) {
      Desktop.openPath(directory.toPath());
    }
  }
}
