package me.cxiy.cxvisuals.mc.modules.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.core.settings.Configs;
import me.cxiy.cxvisuals.core.settings.TextSetting;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.ToastStack;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.mc.modules.avatar.Avatars;
import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import me.cxiy.cxvisuals.mc.modules.waypoints.Markers;
import me.cxiy.cxvisuals.modules.avatar.bake.BakedAvatar;
import me.cxiy.cxvisuals.modules.avatar.bake.Tune;
import me.cxiy.cxvisuals.modules.avatar.format.RawModel;
import me.cxiy.cxvisuals.modules.avatar.library.AvatarEntry;
import me.cxiy.cxvisuals.modules.friends.Friends;
import me.cxiy.cxvisuals.modules.friends.FriendsModule;
import me.cxiy.cxvisuals.modules.friends.Note;
import me.cxiy.cxvisuals.modules.friends.Sealer;
import me.cxiy.cxvisuals.modules.social.Account;
import me.cxiy.cxvisuals.modules.social.SharedAvatar;
import me.cxiy.cxvisuals.modules.social.SocialModule;
import me.cxiy.cxvisuals.modules.social.SocialProtocol;
import me.cxiy.cxvisuals.modules.waypoints.Waypoints;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

public final class FriendsLink {
  private static final String IDENTITY_FILE = "identity";
  private static final long RETRY_NANOS = 8_000_000_000L;
  private static final float MAX_FRAME_SECONDS = 0.1F;
  private static final float FALLBACK_TOP = 10.0F;
  private static final float CHIME_PITCH = 1.35F;
  private static final int MAX_PREVIEWS = 256;
  private static final java.util.Map<String, Integer> PREVIEWS = new java.util.HashMap<>();
  private static final float CHIME_VOLUME = 0.7F;
  private static final int MAX_CONFIG = 1 << 20;
  private static final McCanvas CANVAS = new McCanvas();

  private static FriendsModule module;
  private static Friends friends;
  private static SocialModule social;
  private static Account account;
  private static volatile Sealer sealer;
  private static volatile boolean keyed;
  private static String sealerOwner = "";
  private static boolean circled;
  private static Path identityFile;
  private static String self = "";
  private static volatile boolean streaming;
  private static volatile boolean circleDue;
  private static volatile boolean mailDue;
  private static boolean linked;
  private static boolean fetching;
  private static long cursor;
  private static long streamAt;
  private static long frameNanos;
  private static int linkedGeneration = -1;
  private static String pendingJoin = "";
  private static Configs configs;
  private static TextSetting activeConfig;

  private FriendsLink() {}

  public static FriendsModule create(McKeyBinder keys, SocialModule socialModule) {
    social = socialModule;
    account = socialModule.account;
    module = new FriendsModule(keys, account);
    friends = module.friends;
    friends.favorites(module.favorites.get());
    friends.read(module.read.get());
    identityFile =
        FabricLoader.getInstance()
            .getConfigDir()
            .resolve(CxVisualsClient.MOD_ID)
            .resolve(IDENTITY_FILE);
    friends.actions(
        new Friends.Actions() {
          @Override
          public void add(String name) {
            FriendsLink.add(name);
          }

          @Override
          public void accept(String uuid) {
            call("PUT", uuid);
          }

          @Override
          public void remove(String uuid) {
            call("DELETE", uuid);
          }

          @Override
          public void send(String uuid, Note note) {
            FriendsLink.send(uuid, note);
          }

          @Override
          public List<Friends.Item> items(Friends.Share kind) {
            return FriendsLink.items(kind);
          }

          @Override
          public void share(String uuid, Friends.Share kind, String id) {
            switch (kind) {
              case AVATAR -> FriendsLink.shareAvatar(uuid, id);
              case CONFIG -> FriendsLink.shareConfig(uuid, id);
              case WAYPOINT -> {
                for (Waypoints.Waypoint waypoint : Markers.module().waypoints.list()) {
                  if (waypoint.id().equals(id)) {
                    FriendsLink.shareWaypoint(waypoint, uuid);
                  }
                }
              }
            }
          }

          @Override
          public void invite(String uuid) {
            String server = address(Minecraft.getInstance());
            if (server.isEmpty()) {
              friends.notice(Lang.tr("Join a multiplayer server first to invite someone."));
            } else {
              FriendsLink.send(uuid, Note.invite(server));
            }
          }

          @Override
          public void join(String server) {
            pendingJoin = server;
          }

          @Override
          public void shareSpot(String uuid) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) {
              friends.notice(Lang.tr("Join a world first to send your location."));
            } else {
              FriendsLink.send(
                  uuid,
                  Note.spot(
                      Lang.format("{0} is here", mc.getUser().getName()),
                      mc.player.getBlockX(),
                      mc.player.getBlockY(),
                      mc.player.getBlockZ(),
                      Markers.dimension(mc),
                      address(mc),
                      true));
            }
          }

          @Override
          public void config(Friends.Line line, boolean apply) {
            FriendsLink.config(line, apply);
          }

          @Override
          public void waypoint(Friends.Line line) {
            Note note = line.note();
            if (Markers.adopt(note.text(), note.x(), note.y(), note.z(), note.place())) {
              friends.parcel(line, Friends.Parcel.KEPT);
            } else {
              friends.notice(Markers.module().waypoints.notice());
            }
          }

          @Override
          public int preview(Friends.Line line) {
            return FriendsLink.preview(line);
          }

          @Override
          public void tryOn(Friends.Line line) {
            FriendsLink.unpack(line, false);
          }

          @Override
          public void keep(Friends.Line line) {
            FriendsLink.unpack(line, true);
          }

          @Override
          public void forget(String uuid) {
            friends.forget(uuid);
          }
        });
    Social.companion(
        new Social.Companion() {
          @Override
          public String mode() {
            return module.enabled.get() ? module.mode.get().wire : FriendsModule.Mode.HIDDEN.wire;
          }

          @Override
          public String status() {
            return module.enabled.get() ? module.statusLine() : "";
          }

          @Override
          public String server(Minecraft mc) {
            return module.enabled.get() && module.shareServer.get() ? address(mc) : "";
          }

          @Override
          public JsonObject spot(Minecraft mc) {
            String server = address(mc);
            if (!module.enabled.get()
                || !module.shareLocation.get()
                || server.isEmpty()
                || mc.player == null) {
              return null;
            }
            JsonObject spot = new JsonObject();
            spot.addProperty(
                "s",
                SocialProtocol.sha256Hex(
                        server.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8))
                    .substring(0, 32));
            spot.addProperty("d", Markers.dimension(mc));
            spot.addProperty("x", Math.round(mc.player.getX() * 10.0) / 10.0);
            spot.addProperty("y", Math.round(mc.player.getY() * 10.0) / 10.0);
            spot.addProperty("z", Math.round(mc.player.getZ() * 10.0) / 10.0);
            return spot;
          }

          @Override
          public boolean brisk(Minecraft mc) {
            return module.enabled.get() && module.shareLocation.get() && !address(mc).isEmpty();
          }

          @Override
          public void friends(JsonElement rows) {
            presence(rows);
          }
        });
    return module;
  }

  public static void configs(Configs store, TextSetting active) {
    configs = store;
    activeConfig = active;
  }

  public static List<Waypoints.Target> targets() {
    List<Waypoints.Target> out = new ArrayList<>();
    if (module == null || !module.active()) {
      return out;
    }
    List<Friends.Friend> list = friends.friends();
    for (int i = 0; i < list.size(); i++) {
      Friends.Friend friend = list.get(i);
      out.add(new Waypoints.Target(friend.uuid, friend.name, friend.entityId, friend.online));
    }
    return out;
  }

  public static boolean shareWaypoint(Waypoints.Waypoint waypoint, String uuid) {
    Friends.Friend friend = module != null && module.active() ? friends.friend(uuid) : null;
    if (friend == null || friend.key.isEmpty()) {
      return false;
    }
    send(
        uuid,
        Note.spot(
            waypoint.name(),
            waypoint.x(),
            waypoint.y(),
            waypoint.z(),
            waypoint.dimension(),
            address(Minecraft.getInstance()),
            false));
    return true;
  }

  private static List<Friends.Item> items(Friends.Share kind) {
    List<Friends.Item> out = new ArrayList<>();
    switch (kind) {
      case AVATAR -> {
        List<AvatarEntry> entries = Avatars.library().entries();
        for (int i = 0; i < entries.size(); i++) {
          AvatarEntry entry = entries.get(i);
          if (entry.state() == AvatarEntry.State.LOADING) {
            Avatars.load(entry);
          }
          if (entry.state() == AvatarEntry.State.READY
              || entry.state() == AvatarEntry.State.LOADING) {
            out.add(new Friends.Item(entry.id(), entry.name(), entry.handle()));
          }
        }
      }
      case CONFIG -> {
        out.add(new Friends.Item("", Lang.tr("Current settings"), 0));
        if (configs != null) {
          for (String name : configs.names()) {
            out.add(new Friends.Item(name, name, 0));
          }
        }
      }
      case WAYPOINT -> {
        for (Waypoints.Waypoint waypoint : Markers.module().waypoints.list()) {
          out.add(new Friends.Item(waypoint.id(), waypoint.name(), 0));
        }
      }
    }
    return out;
  }

  private static void parcel(
      String uuid,
      long token,
      byte[] blob,
      String what,
      java.util.function.Function<String[], Note> note) {
    String base = social.baseUrl();
    String session = Social.token();
    Social.work(
        () -> {
          try {
            byte[] key = Sealer.newKey();
            String[] stored = upload(base, session, uuid, key, SharedAvatar.pack(blob));
            Note made =
                note.apply(
                    new String[] {stored[0], Base64.getEncoder().encodeToString(key), stored[1]});
            Social.later(() -> post(uuid, made, token));
          } catch (Cloud.Refused refused) {
            String notice =
                "quota".equals(refused.code)
                    ? Lang.tr("You've shared a lot lately. Older shares free up after 30 days.")
                    : Lang.tr(what);
            Social.later(
                () -> {
                  friends.fail(uuid, token);
                  friends.notice(notice);
                });
          } catch (IOException | RuntimeException e) {
            Social.later(() -> friends.fail(uuid, token));
          }
        });
  }

  private static byte[] open(String parcel, String key, String hash, int limit) {
    try {
      byte[] sealed =
          Social.cloud()
              .download(
                  social.baseUrl() + "/v2/parcels/" + parcel,
                  Social.token(),
                  limit + Sealer.OVERHEAD);
      byte[] blob =
          Sealer.unlock(
              Base64.getDecoder().decode(key), sealed, hash.getBytes(StandardCharsets.UTF_8));
      return blob != null && SocialProtocol.sha256Hex(blob).equals(hash)
          ? SharedAvatar.unpack(blob, SharedAvatar.MAX_BYTES)
          : null;
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.warn("Couldn't fetch a shared item: {}", e.getMessage());
      return null;
    }
  }

  private static byte[] fetch(Note note, int limit) {
    return open(note.parcel(), note.key(), note.hash(), limit);
  }

  private static String[] upload(
      String base, String session, String uuid, byte[] key, byte[] packed) throws IOException {
    String hash = SocialProtocol.sha256Hex(packed);
    byte[] sealed = Sealer.lock(key, packed, hash.getBytes(StandardCharsets.UTF_8));
    JsonObject reply = Social.cloud().send(base + "/v2/parcels/" + uuid, session, sealed);
    return new String[] {SocialProtocol.text(reply, "id", ""), hash};
  }

  public static void seedPreview(String miniHash, String name, BakedAvatar avatar) {
    PREVIEWS.put(miniHash, Avatars.library().ghost(name, miniHash, avatar).handle());
  }

  private static int preview(Friends.Line line) {
    Note note = line.note();
    String hash = note.miniHash();
    if (hash.isEmpty()) {
      return Friends.PREVIEW_NONE;
    }
    Integer known = PREVIEWS.get(hash);
    if (known != null) {
      return known;
    }
    if (PREVIEWS.size() >= MAX_PREVIEWS) {
      PREVIEWS.clear();
    }
    PREVIEWS.put(hash, Social.held() ? Friends.PREVIEW_NONE : Friends.PREVIEW_LOADING);
    if (Social.held()) {
      return Friends.PREVIEW_NONE;
    }
    String name = note.text();
    Social.work(
        () -> {
          byte[] blob = open(note.mini(), note.key(), hash, SharedAvatar.MINI_BYTES);
          BakedAvatar avatar = blob == null ? null : SharedAvatar.fromBytes(blob);
          Social.later(
              () ->
                  PREVIEWS.put(
                      hash,
                      avatar == null
                          ? Friends.PREVIEW_NONE
                          : Avatars.library().ghost(name, hash, avatar).handle()));
        });
    return Friends.PREVIEW_LOADING;
  }

  private static void shareConfig(String uuid, String saved) {
    if (configs == null) {
      return;
    }
    boolean current = saved.isEmpty();
    String name =
        !current
            ? saved
            : activeConfig.get().isBlank()
                ? Lang.format("{0} setup", Minecraft.getInstance().getUser().getName())
                : activeConfig.get();
    byte[] blob =
        (current ? configs.export() : configs.export(saved)).getBytes(StandardCharsets.UTF_8);
    if (blob.length == 0 || blob.length > MAX_CONFIG) {
      friends.notice(
          Lang.tr(
              blob.length == 0
                  ? "That config couldn't be read."
                  : "That config is too large to share."));
      return;
    }
    long token = friends.pend(uuid, Note.config(name, "", "", ""));
    parcel(
        uuid,
        token,
        blob,
        Lang.key("Couldn't share the config."),
        parts -> Note.config(name, parts[0], parts[1], parts[2]));
  }

  private static void config(Friends.Line line, boolean apply) {
    Note note = line.note();
    if (note.kind() != Note.Kind.CONFIG || configs == null) {
      return;
    }
    friends.parcel(line, Friends.Parcel.WORKING);
    Social.work(
        () -> {
          byte[] blob = fetch(note, MAX_CONFIG);
          Social.later(
              () -> {
                String name =
                    blob == null
                        ? ""
                        : configs.adopt(note.text(), new String(blob, StandardCharsets.UTF_8));
                if (name.isEmpty()) {
                  friends.parcel(line, Friends.Parcel.FAILED);
                  return;
                }
                if (apply && configs.load(name)) {
                  activeConfig.set(name);
                  friends.parcel(line, Friends.Parcel.WORN);
                } else {
                  friends.parcel(line, Friends.Parcel.KEPT);
                }
              });
        });
  }

  public static FriendsModule module() {
    return module;
  }

  public static void preview(boolean on, String uuid) {
    linked = on;
    keyed = on;
    circled = on;
    linkedGeneration = on ? Social.generation() : -1;
    self = uuid;
    if (on && sealer == null) {
      sealer = Sealer.create();
    }
  }

  private static ToastStack toasts() {
    return Hud.module().toasts.stack;
  }

  public static void toast(Friends.Toast toast) {
    toasts().push(toast);
  }

  private static String address(Minecraft mc) {
    ServerData server = mc.getCurrentServer();
    if (server == null || mc.isLocalServer() || server.isLan() || mc.level == null) {
      return "";
    }
    String ip = server.ip.strip();
    return ip.length() <= Note.SERVER_LIMIT && ip.matches("[A-Za-z0-9._\\-\\[\\]:]+") ? ip : "";
  }

  public static void tick(Minecraft mc) {
    if (module == null) {
      return;
    }
    friends.settle();
    boolean active = module.enabled.get() && account.online();
    if (!active || linkedGeneration != Social.generation()) {
      if (linked) {
        linked = false;
        keyed = false;
        circled = false;
        cursor = 0L;
        friends.clear();
        toasts().clear();
      }
      if (!active) {
        return;
      }
      linkedGeneration = Social.generation();
    }
    long now = System.nanoTime();
    self = mc.getUser().getProfileId().toString().toLowerCase(Locale.ROOT);
    friends.quiet(!module.toasts.get() || module.mode.get() == FriendsModule.Mode.BUSY);
    if (!Social.held()) {
      if (!linked) {
        linked = true;
        link();
      }
      if (keyed && !streaming && now - streamAt > RETRY_NANOS) {
        streamAt = now;
        listen();
      }
      if (keyed && circleDue) {
        circleDue = false;
        circle();
      }
      if (keyed && circled && mailDue && !fetching) {
        mailDue = false;
        mail();
      }
    }
    if (mc.level != null) {
      List<Friends.Friend> list = friends.friends();
      for (int i = 0; i < list.size(); i++) {
        Friends.Friend friend = list.get(i);
        Player entity =
            friend.online ? mc.level.getPlayerByUUID(UUID.fromString(friend.uuid)) : null;
        friend.entityId = entity == null ? Friends.NO_ENTITY : entity.getId();
      }
    }
    friends.here(
        mc.player != null,
        mc.player == null ? 0.0 : mc.player.getX(),
        mc.player == null ? 0.0 : mc.player.getY(),
        mc.player == null ? 0.0 : mc.player.getZ(),
        Markers.dimension(mc),
        address(mc));
    Friends.Toast toast;
    boolean chime = false;
    while (toasts().size() < ToastStack.CAPACITY && (toast = friends.nextToast()) != null) {
      toasts().push(toast);
      chime = true;
    }
    if (chime && module.sound.get()) {
      mc.getSoundManager()
          .play(
              SimpleSoundInstance.forUI(
                  SoundEvents.AMETHYST_BLOCK_CHIME, CHIME_PITCH, CHIME_VOLUME));
    }
    String stored = friends.favorites();
    if (!stored.equals(module.favorites.get())) {
      module.favorites.set(stored);
    }
    String read = friends.read();
    if (!read.equals(module.read.get())) {
      module.read.set(read);
    }
    if (!pendingJoin.isEmpty()) {
      String server = pendingJoin;
      pendingJoin = "";
      join(mc, server);
    }
  }

  public static void overlay(GuiGraphicsExtractor graphics) {
    if (module == null || Hud.live() || toasts().size() == 0) {
      frameNanos = 0L;
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    long now = System.nanoTime();
    float dt = frameNanos == 0L ? 0.0F : Math.min((now - frameNanos) / 1.0e9F, MAX_FRAME_SECONDS);
    frameNanos = now;
    float uiScale = McCanvas.interfaceScale(mc.getWindow().getHeight());
    graphics.nextStratum();
    CANVAS.begin(graphics, uiScale, Math.max(1, mc.getWindow().getGuiScale()));
    float left = (mc.getWindow().getWidth() / uiScale - ToastStack.WIDTH) * 0.5F;
    toasts()
        .draw(
            CANVAS,
            Theme.DARK,
            left,
            FALLBACK_TOP,
            ToastStack.extent(ToastStack.CAPACITY),
            0.5F,
            false,
            dt);
    CANVAS.end();
  }

  private static void join(Minecraft mc, String server) {
    if (!ServerAddress.isValidAddress(server)) {
      friends.notice(Lang.tr("That server address isn't valid."));
      return;
    }
    if (mc.level != null) {
      mc.disconnectFromWorld(Component.translatable("multiplayer.status.quitting"));
    }
    ConnectScreen.startConnecting(
        new TitleScreen(),
        mc,
        ServerAddress.parseString(server),
        new ServerData(server, server, ServerData.Type.OTHER),
        false,
        null);
  }

  private static void link() {
    String base = social.baseUrl();
    String token = Social.token();
    String owner = self;
    int attempt = Social.generation();
    Social.work(
        () -> {
          try {
            Sealer mine = sealer;
            if (mine == null || !owner.equals(sealerOwner)) {
              mine = identity(owner);
              sealerOwner = owner;
              sealer = mine;
            }
            JsonObject key = new JsonObject();
            key.addProperty("key", mine.publicKey());
            Social.cloud().put(base + "/v2/key", token, key.toString());
            Social.later(
                () -> {
                  if (attempt == Social.generation()) {
                    keyed = true;
                    circleDue = true;
                    mailDue = true;
                  }
                });
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.warn("Couldn't set up friends: {}", e.getMessage());
            Social.later(
                () -> {
                  if (attempt == Social.generation()) {
                    linked = false;
                  }
                });
          }
        });
  }

  private static Sealer identity(String owner) throws IOException {
    Path own = identityFile.resolveSibling(IDENTITY_FILE + "-" + owner);
    if (!Files.isRegularFile(own) && Files.isRegularFile(identityFile)) {
      Files.move(identityFile, own, StandardCopyOption.REPLACE_EXISTING);
    }
    if (Files.isRegularFile(own) && Files.size(own) < 4096L) {
      Sealer stored = Sealer.restore(Files.readString(own, StandardCharsets.UTF_8));
      if (stored != null) {
        return stored;
      }
    }
    Sealer fresh = Sealer.create();
    Path tmp = own.resolveSibling(own.getFileName() + ".tmp");
    Files.createDirectories(own.getParent());
    Files.writeString(tmp, fresh.stored(), StandardCharsets.UTF_8);
    Files.move(tmp, own, StandardCopyOption.REPLACE_EXISTING);
    return fresh;
  }

  private static void listen() {
    String base = social.baseUrl();
    String token = Social.token();
    streaming = true;
    Thread thread =
        new Thread(
            () -> {
              try {
                Social.cloud()
                    .listen(
                        base + "/v2/events",
                        token,
                        event -> {
                          if ("message".equals(event)) {
                            mailDue = true;
                          } else if ("friends".equals(event) || "ready".equals(event)) {
                            circleDue = true;
                            mailDue = true;
                          }
                        });
              } catch (IOException | RuntimeException e) {
                CxVisualsClient.LOG.debug("Friends stream closed: {}", e.getMessage());
              } finally {
                streaming = false;
              }
            },
            "CxVisuals Friends");
    thread.setDaemon(true);
    thread.setPriority(Thread.NORM_PRIORITY - 1);
    thread.start();
  }

  private static List<Friends.Person> people(JsonObject reply, String key) {
    List<Friends.Person> out = new ArrayList<>();
    JsonElement rows = reply.get(key);
    if (rows == null || !rows.isJsonArray()) {
      return out;
    }
    for (JsonElement row : rows.getAsJsonArray()) {
      if (!row.isJsonObject()) {
        continue;
      }
      String uuid = SocialProtocol.lower(SocialProtocol.text(row.getAsJsonObject(), "uuid", ""));
      if (SocialProtocol.validUuid(uuid)) {
        out.add(
            new Friends.Person(
                uuid,
                SocialProtocol.clip(
                    SocialProtocol.text(row.getAsJsonObject(), "name", ""),
                    SocialProtocol.NAME_LIMIT)));
      }
    }
    return out;
  }

  private static void circle() {
    String base = social.baseUrl();
    String token = Social.token();
    int attempt = Social.generation();
    Social.work(
        () -> {
          try {
            JsonObject reply = Social.cloud().get(base + "/v2/friends", token);
            List<Friends.Friend> list = new ArrayList<>();
            JsonElement rows = reply.get("friends");
            if (rows != null && rows.isJsonArray()) {
              for (JsonElement row : rows.getAsJsonArray()) {
                if (!row.isJsonObject()) {
                  continue;
                }
                JsonObject json = row.getAsJsonObject();
                String uuid = SocialProtocol.lower(SocialProtocol.text(json, "uuid", ""));
                if (SocialProtocol.validUuid(uuid)) {
                  list.add(
                      Friends.person(
                          uuid,
                          SocialProtocol.clip(
                              SocialProtocol.text(json, "name", ""), SocialProtocol.NAME_LIMIT),
                          SocialProtocol.text(json, "key", "")));
                }
              }
            }
            List<Friends.Person> incoming = people(reply, "incoming");
            List<Friends.Person> outgoing = people(reply, "outgoing");
            Social.later(
                () -> {
                  if (attempt == Social.generation()) {
                    friends.circle(list, incoming, outgoing);
                    circled = true;
                  }
                });
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.warn("Couldn't load friends: {}", e.getMessage());
          }
        });
  }

  private static void presence(JsonElement rows) {
    if (!linked || rows == null || !rows.isJsonArray()) {
      return;
    }
    Set<String> seen = new HashSet<>();
    JsonArray list = rows.getAsJsonArray();
    for (int i = 0; i < list.size(); i++) {
      if (!list.get(i).isJsonObject()) {
        continue;
      }
      JsonObject json = list.get(i).getAsJsonObject();
      String uuid = SocialProtocol.lower(SocialProtocol.text(json, "uuid", ""));
      if (friends.friend(uuid) == null) {
        continue;
      }
      seen.add(uuid);
      String server =
          SocialProtocol.clip(SocialProtocol.text(json, "server", ""), Note.SERVER_LIMIT);
      friends.presence(
          uuid,
          "busy".equals(SocialProtocol.text(json, "mode", "")),
          SocialProtocol.clip(SocialProtocol.text(json, "status", ""), FriendsModule.STATUS_LIMIT),
          ServerAddress.isValidAddress(server) ? server : "",
          SocialProtocol.trackLine(
              SocialProtocol.text(json, "title", ""),
              SocialProtocol.text(json, "artist", ""),
              SocialProtocol.TRACK_LIMIT));
    }
    friends.presenceDone(seen);
    for (int i = 0; i < list.size(); i++) {
      if (!list.get(i).isJsonObject()) {
        continue;
      }
      JsonObject json = list.get(i).getAsJsonObject();
      String uuid = SocialProtocol.lower(SocialProtocol.text(json, "uuid", ""));
      JsonElement spot = json.get("spot");
      if (spot != null && spot.isJsonObject()) {
        JsonObject at = spot.getAsJsonObject();
        friends.spot(
            uuid,
            true,
            SocialProtocol.decimal(at, "x", 0.0),
            SocialProtocol.decimal(at, "y", 0.0),
            SocialProtocol.decimal(at, "z", 0.0),
            SocialProtocol.clip(SocialProtocol.text(at, "d", ""), Note.PLACE_LIMIT));
      } else {
        friends.spot(uuid, false, 0.0, 0.0, 0.0, "");
      }
    }
  }

  private static void mail() {
    fetching = true;
    String base = social.baseUrl();
    String token = Social.token();
    String me = self;
    long after = cursor;
    int attempt = Social.generation();
    Social.work(
        () -> {
          try {
            JsonObject reply = Social.cloud().get(base + "/v2/messages?after=" + after, token);
            JsonElement rows = reply.get("messages");
            JsonArray list =
                rows != null && rows.isJsonArray() ? rows.getAsJsonArray() : new JsonArray();
            boolean more = SocialProtocol.flag(reply, "more", false);
            Social.later(
                () -> {
                  fetching = false;
                  if (attempt != Social.generation()) {
                    return;
                  }
                  boolean first = after == 0L;
                  for (int i = 0; i < list.size(); i++) {
                    if (list.get(i).isJsonObject()) {
                      deliver(list.get(i).getAsJsonObject(), me, !first);
                    }
                  }
                  if (more) {
                    mailDue = true;
                  }
                });
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.warn("Couldn't load messages: {}", e.getMessage());
            Social.later(() -> fetching = false);
          }
        });
  }

  private static void deliver(JsonObject json, String me, boolean announce) {
    long id = SocialProtocol.number(json, "id", 0L);
    if (id <= cursor) {
      return;
    }
    cursor = id;
    String from = SocialProtocol.lower(SocialProtocol.text(json, "from", ""));
    String to = SocialProtocol.lower(SocialProtocol.text(json, "to", ""));
    boolean mine = from.equals(me);
    String other = mine ? to : from;
    Friends.Friend friend = friends.friend(other);
    if (friend == null) {
      return;
    }
    Note note = null;
    Sealer own = sealer;
    try {
      byte[] sealed = Base64.getDecoder().decode(SocialProtocol.text(json, "body", ""));
      note = own == null ? null : Note.decode(own.open(friend.key, me, other, from, sealed));
    } catch (RuntimeException e) {
      note = null;
    }
    if (note == null) {
      List<Friends.Line> before = friends.lines(other);
      if (!before.isEmpty() && before.get(before.size() - 1).note() == Note.UNREADABLE) {
        return;
      }
      note = Note.UNREADABLE;
      announce = false;
    }
    Friends.Line line = new Friends.Line(id, mine, note, SocialProtocol.number(json, "at", 0L));
    if (friends.receive(other, line) && announce) {
      friends.announce(other, line);
    }
  }

  private static void add(String name) {
    String base = social.baseUrl();
    String token = Social.token();
    JsonObject body = new JsonObject();
    body.addProperty("name", name);
    Social.work(
        () -> {
          String notice;
          try {
            JsonObject reply = Social.cloud().post(base + "/v2/friends", token, body.toString());
            notice =
                "friends".equals(SocialProtocol.text(reply, "state", ""))
                    ? Lang.format("You and {0} are now friends.", name)
                    : Lang.format("Request sent to {0}.", name);
          } catch (Cloud.Refused refused) {
            notice =
                switch (refused.code) {
                  case "unknown_player" ->
                      Lang.format("{0} hasn't connected to CxVisuals yet.", name);
                  case "yourself" -> Lang.tr("That's you.");
                  case "list_full" -> Lang.tr("One of your friend lists is full.");
                  case "slow_down" -> Lang.tr("Too many requests. Try again in a minute.");
                  default -> Lang.tr("Couldn't send the request.");
                };
          } catch (IOException | RuntimeException e) {
            notice = Lang.tr("Couldn't reach the cloud.");
          }
          String shown = notice;
          Social.later(() -> friends.notice(shown));
          circleDue = true;
        });
  }

  private static void call(String method, String uuid) {
    String base = social.baseUrl();
    String token = Social.token();
    Social.work(
        () -> {
          try {
            if ("PUT".equals(method)) {
              Social.cloud().put(base + "/v2/friends/" + uuid, token, "{}");
            } else {
              Social.cloud().delete(base + "/v2/friends/" + uuid, token);
            }
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.warn("Friend change failed: {}", e.getMessage());
          }
          circleDue = true;
        });
  }

  private static void send(String uuid, Note note) {
    if (friends.friend(uuid) != null) {
      post(uuid, note, friends.pend(uuid, note));
    }
  }

  private static void post(String uuid, Note note, long pending) {
    Friends.Friend friend = friends.friend(uuid);
    if (friend == null || sealer == null) {
      friends.fail(uuid, pending);
      return;
    }
    if (friend.key.isEmpty()) {
      friends.fail(uuid, pending);
      friends.notice(
          Lang.format(
              "{0} needs to open CxVisuals once before you can message them.", friend.name));
      return;
    }
    String base = social.baseUrl();
    String token = Social.token();
    String me = self;
    String key = friend.key;
    Social.work(
        () -> {
          try {
            byte[] sealed = sealer.seal(key, me, uuid, note.encode());
            JsonObject body = new JsonObject();
            body.addProperty("to", uuid);
            body.addProperty("body", Base64.getEncoder().encodeToString(sealed));
            Social.cloud().post(base + "/v2/messages", token, body.toString());
            mailDue = true;
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.warn("Message not sent: {}", e.getMessage());
            Social.later(() -> friends.fail(uuid, pending));
          }
        });
  }

  private static void shareAvatar(String uuid, String id) {
    AvatarEntry entry = Avatars.library().byId(id);
    if (entry == null || entry.state() != AvatarEntry.State.READY || entry.world() == null) {
      friends.notice(Lang.tr("That avatar is still loading. Try again in a moment."));
      return;
    }
    String name = entry.name();
    BakedAvatar world = entry.world();
    BakedAvatar small = entry.preview();
    String base = social.baseUrl();
    String session = Social.token();
    long token = friends.pend(uuid, Note.model(name, "", "", ""));
    Social.work(
        () -> {
          try {
            byte[] blob = SharedAvatar.toBytes(SharedAvatar.downscale(world));
            if (blob.length > SharedAvatar.MAX_BYTES) {
              Social.later(
                  () -> {
                    friends.fail(uuid, token);
                    friends.notice(Lang.tr("That avatar is too large to share."));
                  });
              return;
            }
            byte[] key = Sealer.newKey();
            byte[] full = SharedAvatar.pack(blob);
            byte[] mini =
                small == null
                    ? null
                    : SharedAvatar.pack(SharedAvatar.toBytes(SharedAvatar.mini(small)));
            String[] glimpse =
                mini == null || mini.length > SharedAvatar.MINI_BYTES
                    ? new String[] {"", ""}
                    : upload(base, session, uuid, key, mini);
            String[] stored = upload(base, session, uuid, key, full);
            Note made =
                Note.model(
                    name,
                    stored[0],
                    Base64.getEncoder().encodeToString(key),
                    stored[1],
                    glimpse[0],
                    glimpse[1],
                    world.triangles,
                    world.clips.length,
                    Math.max(1, full.length >> 10));
            Social.later(() -> post(uuid, made, token));
          } catch (Cloud.Refused refused) {
            Social.later(
                () -> {
                  friends.fail(uuid, token);
                  if ("quota".equals(refused.code)) {
                    friends.notice(
                        Lang.tr("You've shared a lot lately. Older shares free up after 30 days."));
                  }
                });
          } catch (IOException | RuntimeException e) {
            Social.later(() -> friends.fail(uuid, token));
          }
        });
  }

  private static void unpack(Friends.Line line, boolean keep) {
    Note note = line.note();
    if (note.kind() != Note.Kind.MODEL) {
      return;
    }
    if (!keep && Avatars.trying(note.hash())) {
      Avatars.endTrial();
      friends.parcel(line, Friends.Parcel.IDLE);
      return;
    }
    friends.parcel(line, Friends.Parcel.WORKING);
    Social.work(
        () -> {
          byte[] blob = fetch(note, SharedAvatar.MAX_BYTES);
          BakedAvatar avatar = blob == null ? null : SharedAvatar.fromBytes(blob);
          BakedAvatar done = avatar;
          Social.later(
              () -> {
                if (done == null) {
                  friends.parcel(line, Friends.Parcel.FAILED);
                  return;
                }
                Tune tune = Tune.defaults(RawModel.Axis.Y);
                if (keep) {
                  Avatars.endTrial();
                  Avatars.library()
                      .adopt(
                          note.text().isEmpty() ? Lang.tr("Shared avatar") : note.text(),
                          note.hash(),
                          done,
                          tune);
                  friends.parcel(line, Friends.Parcel.KEPT);
                } else {
                  Avatars.tryOn(note.hash(), done, tune);
                  friends.parcel(line, Friends.Parcel.WORN);
                }
              });
        });
  }
}
