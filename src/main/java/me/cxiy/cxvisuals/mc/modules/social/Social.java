package me.cxiy.cxvisuals.mc.modules.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.authlib.exceptions.AuthenticationException;
import com.mojang.authlib.exceptions.AuthenticationUnavailableException;
import com.mojang.authlib.exceptions.InvalidCredentialsException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.modules.avatar.Avatars;
import me.cxiy.cxvisuals.mc.modules.hud.Media;
import me.cxiy.cxvisuals.modules.avatar.AvatarModule;
import me.cxiy.cxvisuals.modules.avatar.anim.ClipMap;
import me.cxiy.cxvisuals.modules.avatar.bake.BakedAvatar;
import me.cxiy.cxvisuals.modules.avatar.bake.Tune;
import me.cxiy.cxvisuals.modules.avatar.format.RawModel;
import me.cxiy.cxvisuals.modules.avatar.library.AvatarEntry;
import me.cxiy.cxvisuals.modules.hud.media.MediaSnapshot;
import me.cxiy.cxvisuals.modules.social.Account;
import me.cxiy.cxvisuals.modules.social.SharedAvatar;
import me.cxiy.cxvisuals.modules.social.SharedPresence;
import me.cxiy.cxvisuals.modules.social.SocialModule;
import me.cxiy.cxvisuals.modules.social.SocialProtocol;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

public final class Social {
  private static final long SYNC_NANOS = 5_000_000_000L;
  private static final long IDLE_NANOS = 20_000_000_000L;
  private static final long STALE_NANOS = 60_000_000_000L;
  private static final int MAX_NEARBY = 48;
  private static final int MAX_FETCH = 2;
  private static final String SESSION_FILE = "session";

  private static final Cloud cloud = new Cloud();
  private static final ConcurrentHashMap<String, SharedPresence> presence =
      new ConcurrentHashMap<>();
  private static final ConcurrentLinkedQueue<Runnable> ready = new ConcurrentLinkedQueue<>();
  private static final Set<String> inflight = ConcurrentHashMap.newKeySet();
  private static final Set<String> unreadable = ConcurrentHashMap.newKeySet();
  private static final int MAX_UNREADABLE = 256;
  private static SocialModule module;
  private static Account account;
  private static ExecutorService worker;
  private static ExecutorService downloads;
  private static Path shared;
  private static Path sessionFile;
  private static volatile String token = "";
  private static volatile int generation;
  private static boolean started;
  private static User seen;
  private static boolean held;
  private static Companion companion;

  interface Companion {
    String mode();

    String status();

    String server(Minecraft mc);

    JsonObject spot(Minecraft mc);

    boolean brisk(Minecraft mc);

    void friends(JsonElement rows);
  }

  static void companion(Companion next) {
    companion = next;
  }

  static boolean held() {
    return held;
  }

  public static boolean connected() {
    return module != null && module.enabled.get() && !token.isEmpty() && account.online();
  }

  public static JsonObject read(String path) throws IOException {
    return cloud.get(module.baseUrl() + path, token);
  }

  public static void write(String path, String json) throws IOException {
    cloud.put(module.baseUrl() + path, token, json);
  }

  static Cloud cloud() {
    return cloud;
  }

  static String token() {
    return token;
  }

  static int generation() {
    return generation;
  }

  static void work(Runnable task) {
    worker.execute(task);
  }

  static void later(Runnable task) {
    ready.add(task);
  }

  private static boolean syncing;
  private static long syncedAt;
  private static long seenAt;
  private static int mediaUser = -1;
  private static String sharedKey = "";
  private static String sharedHash = "";
  private static byte[] sharedBlob;

  private Social() {}

  public static SocialModule create(McKeyBinder keys) {
    module = new SocialModule(keys);
    account = module.account;
    account.actions(
        new Account.Actions() {
          @Override
          public void connect() {
            Social.connect();
          }

          @Override
          public void disconnect() {
            Social.disconnect(false);
          }

          @Override
          public void erase() {
            Social.disconnect(true);
          }

          @Override
          public void block(String uuid, String name) {
            Social.block(uuid, name);
          }

          @Override
          public void unblock(String uuid) {
            Social.unblock(uuid);
          }
        });
    worker = Executors.newSingleThreadExecutor(task -> daemon(task, "CxVisuals Cloud"));
    downloads = Executors.newFixedThreadPool(MAX_FETCH, task -> daemon(task, "CxVisuals Shared"));
    Path home = FabricLoader.getInstance().getConfigDir().resolve(CxVisualsClient.MOD_ID);
    shared = home.resolve("shared");
    sessionFile = home.resolve(SESSION_FILE);
    try {
      Files.createDirectories(shared);
    } catch (IOException e) {
      CxVisualsClient.LOG.warn("Couldn't create shared cache: {}", e.getMessage());
    }
    return module;
  }

  private static Thread daemon(Runnable task, String name) {
    Thread thread = new Thread(task, name);
    thread.setDaemon(true);
    thread.setPriority(Thread.NORM_PRIORITY - 1);
    return thread;
  }

  public static SocialModule module() {
    return module;
  }

  public static void hold(boolean hold) {
    held = hold;
  }

  public static void preview(SharedPresence row, BakedAvatar avatar, int entityId) {
    presence.put(row.uuid(), row);
    Avatars.putRemote(
        row.uuid(), row.avatarHash(), avatar, remoteTune(row), row.clipMap(), row.clipKey());
    account.nearby(
        List.of(
            new Account.Player(
                row.uuid(), row.name(), entityId, row.trackLine(SocialProtocol.TRACK_LIMIT))));
  }

  public static String trackLine(UUID uuid) {
    if (uuid == null || module == null || !module.seeMusic.get() || !account.online()) {
      return "";
    }
    SharedPresence row = presence.get(key(uuid));
    return row == null ? "" : row.trackLine(SocialProtocol.ROW_LIMIT);
  }

  public static void tick(Minecraft mc) {
    if (module == null) {
      return;
    }
    Runnable next;
    while ((next = ready.poll()) != null) {
      next.run();
    }
    long now = System.nanoTime();
    boolean active = module.enabled.get();
    account.identity(
        mc.getUser().getName(), mc.player == null ? Account.NO_ENTITY : mc.player.getId());
    account.clock(now, !active);
    User user = mc.getUser();
    if (user != seen) {
      boolean other = seen != null && !user.getProfileId().equals(seen.getProfileId());
      boolean renewed = seen != null && !other;
      seen = user;
      if (other) {
        takeover();
      } else if (renewed) {
        account.renewed();
      }
    }
    if (held) {
      return;
    }
    if (!started) {
      started = true;
      restore();
    }
    if (account.due()) {
      String base = module.baseUrl();
      if (token.isEmpty()) {
        signIn(base, mc.getUser(), generation);
      } else {
        sync(mc, base, now);
      }
    }
    boolean music =
        active && account.online() && (module.shareMusic.get() || module.seeMusic.get());
    if (Media.hub() != null && (music || mediaUser >= 0)) {
      if (mediaUser < 0) {
        mediaUser = Media.hub().user();
      }
      Media.hub().want(mediaUser, music, false, false);
      Media.hub().poll(false);
    }
    if (!active || !account.online() || !module.seeAvatars.get()) {
      Avatars.clearRemotes();
    }
    if (!active || !account.online()) {
      return;
    }
    boolean inWorld = mc.player != null && mc.level != null;
    if (!inWorld && !presence.isEmpty()) {
      forget();
    }
    boolean brisk =
        inWorld && (mc.level.players().size() > 1 || (companion != null && companion.brisk(mc)));
    long pace = brisk ? SYNC_NANOS : IDLE_NANOS;
    if (!syncing && now - syncedAt >= pace) {
      sync(mc, module.baseUrl(), now);
    }
    if (!presence.isEmpty() && now - seenAt > STALE_NANOS) {
      forget();
    }
    Avatars.pruneRemotes(now);
  }

  private static String owner() {
    return Minecraft.getInstance().getUser().getProfileId().toString().toLowerCase(Locale.ROOT);
  }

  private static void restore() {
    if (!module.connected.get()) {
      return;
    }
    String base = module.baseUrl();
    try {
      if (Files.isRegularFile(sessionFile) && Files.size(sessionFile) < 1024L) {
        List<String> lines = Files.readAllLines(sessionFile, StandardCharsets.UTF_8);
        if (lines.size() == 3
            && lines.get(0).equals(base)
            && lines.get(1).equals(owner())
            && lines.get(2).startsWith("cxs_")) {
          token = lines.get(2);
        }
      }
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.warn("Couldn't read the cloud session: {}", e.getMessage());
    }
    account.begin();
  }

  private static void remember(String base, String value) {
    token = value;
    try {
      if (value.isEmpty()) {
        Files.deleteIfExists(sessionFile);
        return;
      }
      Path tmp = sessionFile.resolveSibling(SESSION_FILE + ".tmp");
      Files.writeString(tmp, base + "\n" + owner() + "\n" + value, StandardCharsets.UTF_8);
      Files.move(tmp, sessionFile, StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      CxVisualsClient.LOG.warn("Couldn't store the cloud session: {}", e.getMessage());
    }
  }

  private static void connect() {
    module.connected.set(true);
    generation++;
    account.begin();
  }

  private static void takeover() {
    generation++;
    String base = module.baseUrl();
    String old = token;
    remember(base, "");
    forget();
    Avatars.clearRemotes();
    sharedKey = "";
    sharedHash = "";
    sharedBlob = null;
    account.takeover();
    if (old.isEmpty() || base.isEmpty()) {
      return;
    }
    worker.execute(
        () -> {
          try {
            cloud.delete(base + "/v2/session", old);
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.warn(
                "Couldn't end the previous account's session: {}", e.getMessage());
          }
        });
  }

  private static void disconnect(boolean erase) {
    module.connected.set(false);
    generation++;
    String base = module.baseUrl();
    String old = token;
    remember(base, "");
    account.signOut();
    forget();
    Avatars.clearRemotes();
    sharedKey = "";
    sharedHash = "";
    sharedBlob = null;
    if (old.isEmpty() || base.isEmpty()) {
      return;
    }
    worker.execute(
        () -> {
          try {
            cloud.delete(base + (erase ? "/v2/account" : "/v2/session"), old);
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.warn(
                "Couldn't {} on the cloud: {}",
                erase ? "erase the account" : "end the session",
                e.getMessage());
          }
        });
  }

  private static void block(String uuid, String name) {
    account.block(new Account.Player(uuid, name, Account.NO_ENTITY, ""));
    presence.remove(uuid);
    Avatars.dropRemote(uuid);
    String base = module.baseUrl();
    String session = token;
    JsonObject body = new JsonObject();
    body.addProperty("name", name);
    worker.execute(
        () -> {
          try {
            cloud.put(base + "/v2/blocks/" + uuid, session, body.toString());
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.warn("Couldn't block on the cloud: {}", e.getMessage());
            ready.add(() -> account.unblock(uuid));
          }
        });
  }

  private static void unblock(String uuid) {
    account.unblock(uuid);
    String base = module.baseUrl();
    String session = token;
    worker.execute(
        () -> {
          try {
            cloud.delete(base + "/v2/blocks/" + uuid, session);
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.warn("Couldn't unblock on the cloud: {}", e.getMessage());
          }
        });
  }

  private static void forget() {
    presence.clear();
    account.nearby(List.of());
  }

  private static String key(UUID uuid) {
    return uuid.toString().toLowerCase(Locale.ROOT);
  }

  private static void fail(int attempt, Account.Failure reason) {
    ready.add(
        () -> {
          if (attempt == generation) {
            account.failed(reason);
          }
        });
  }

  private static Account.Failure reason(IOException error) {
    if (error instanceof Cloud.Refused refused) {
      return switch (refused.code) {
        case "not_verified", "expired", "bad_name" -> Account.Failure.NOT_VERIFIED;
        case "mojang_down" -> Account.Failure.MOJANG_DOWN;
        case "slow_down", "try_again" -> Account.Failure.BUSY;
        default -> Account.Failure.UNREACHABLE;
      };
    }
    return Account.Failure.UNREACHABLE;
  }

  private static void signIn(String base, User user, int attempt) {
    if (base.isEmpty()) {
      account.failed(Account.Failure.UNREACHABLE);
      return;
    }
    String name = user.getName();
    UUID profile = user.getProfileId();
    String access = user.getAccessToken();
    worker.execute(
        () -> {
          try {
            JsonObject who = new JsonObject();
            who.addProperty("name", name);
            String nonce =
                SocialProtocol.text(
                    cloud.post(base + "/v2/session/start", "", who.toString()), "nonce", "");
            if (nonce.isEmpty()) {
              throw new IOException("no nonce");
            }
            Minecraft.getInstance().services().sessionService().joinServer(profile, access, nonce);
            JsonObject proof = new JsonObject();
            proof.addProperty("nonce", nonce);
            JsonObject reply = cloud.post(base + "/v2/session/finish", "", proof.toString());
            String session = SocialProtocol.text(reply, "token", "");
            if (!session.startsWith("cxs_")) {
              throw new IOException("no session");
            }
            List<Account.Player> blocks = blocks(reply);
            ready.add(
                () -> {
                  if (attempt != generation) {
                    worker.execute(
                        () -> {
                          try {
                            cloud.delete(base + "/v2/session", session);
                          } catch (IOException | RuntimeException e) {
                            CxVisualsClient.LOG.warn(
                                "Couldn't end a stale session: {}", e.getMessage());
                          }
                        });
                    return;
                  }
                  remember(base, session);
                  account.blocked(blocks);
                  account.connected();
                  syncedAt = 0L;
                });
          } catch (InvalidCredentialsException e) {
            fail(attempt, Account.Failure.NO_ACCOUNT);
          } catch (AuthenticationUnavailableException e) {
            fail(attempt, Account.Failure.MOJANG_DOWN);
          } catch (AuthenticationException e) {
            fail(attempt, Account.Failure.NOT_VERIFIED);
          } catch (IOException e) {
            fail(attempt, reason(e));
          } catch (RuntimeException e) {
            CxVisualsClient.LOG.warn("Cloud sign-in failed: {}", e.toString());
            fail(attempt, Account.Failure.UNREACHABLE);
          }
        });
  }

  private static List<Account.Player> blocks(JsonObject reply) {
    List<Account.Player> out = new ArrayList<>();
    JsonElement rows = reply.get("blocks");
    if (rows == null || !rows.isJsonArray()) {
      return out;
    }
    for (JsonElement element : rows.getAsJsonArray()) {
      if (!element.isJsonObject()) {
        continue;
      }
      String uuid =
          SocialProtocol.lower(SocialProtocol.text(element.getAsJsonObject(), "uuid", ""));
      if (SocialProtocol.validUuid(uuid)) {
        String name =
            SocialProtocol.clip(
                SocialProtocol.text(element.getAsJsonObject(), "name", ""),
                SocialProtocol.NAME_LIMIT);
        out.add(new Account.Player(uuid, name, Account.NO_ENTITY, ""));
      }
    }
    return out;
  }

  private static void sync(Minecraft mc, String base, long now) {
    syncing = true;
    syncedAt = now;
    int attempt = generation;
    String session = token;
    boolean active = module.enabled.get();
    boolean shareAvatar = active && module.shareAvatar.get();
    boolean shareMusic = active && module.shareMusic.get();
    boolean seeAvatars = active && module.seeAvatars.get();
    String mode = companion == null ? "online" : companion.mode();
    String status = companion == null ? "" : companion.status();
    String server = companion == null ? "" : companion.server(mc);
    JsonObject spot = companion == null ? null : companion.spot(mc);
    List<String> nearby = new ArrayList<>();
    if (mc.level != null && mc.player != null && active) {
      for (AbstractClientPlayer other : mc.level.players()) {
        if (other == mc.player || other.isSpectator()) {
          continue;
        }
        String uuid = key(other.getUUID());
        if (!account.blocks(uuid)) {
          nearby.add(uuid);
        }
        if (nearby.size() >= MAX_NEARBY) {
          break;
        }
      }
    }
    worker.execute(
        () -> {
          try {
            AvatarModule avatars = Avatars.module();
            AvatarEntry entry = Avatars.selectedEntry();
            Tune tune = avatars.tune();
            String hash = "";
            if (shareAvatar
                && avatars.enabled.get()
                && entry != null
                && entry.state() == AvatarEntry.State.READY
                && entry.world() != null) {
              hash = shareHash(entry, tune);
            }
            MediaSnapshot track =
                shareMusic && Media.hub() != null ? Media.hub().snapshot() : MediaSnapshot.EMPTY;
            boolean playing = track.present();
            String body =
                SocialProtocol.syncBody(
                    hash,
                    tune,
                    entry == null ? ClipMap.AUTO : entry.clipMap(),
                    playing ? track.title() : "",
                    playing ? track.artist() : "",
                    playing && track.playing(),
                    nearby,
                    mode,
                    status,
                    server,
                    spot);
            JsonObject reply = cloud.post(base + "/v2/sync", session, body);
            if (SocialProtocol.flag(reply, "upload", false) && !hash.isEmpty()) {
              byte[] blob =
                  sharedBlob != null
                      ? sharedBlob
                      : SharedAvatar.toBytes(SharedAvatar.downscale(entry.world()));
              if (SocialProtocol.sha256Hex(blob).equals(hash)) {
                try {
                  cloud.upload(base + "/v2/avatars/" + hash, session, blob);
                  CxVisualsClient.LOG.info("Uploaded shared avatar {}", hash.substring(0, 8));
                } catch (Cloud.Refused refused) {
                  if (refused.signedOut()) {
                    throw refused;
                  }
                  CxVisualsClient.LOG.warn(
                      "The cloud refused shared avatar {}: {}", hash.substring(0, 8), refused.code);
                  sharedHash = "";
                  if (refused.status == 429 || refused.status >= 500) {
                    sharedKey = "";
                  }
                }
              } else {
                sharedKey = "";
              }
            }
            sharedBlob = null;
            List<SharedPresence> rows = new ArrayList<>();
            JsonElement players = reply.get("players");
            if (players != null && players.isJsonArray()) {
              JsonArray list = players.getAsJsonArray();
              for (int i = 0; i < list.size() && i < MAX_NEARBY; i++) {
                SharedPresence row =
                    list.get(i).isJsonObject()
                        ? SharedPresence.parse(list.get(i).getAsJsonObject())
                        : null;
                if (row != null) {
                  rows.add(row);
                }
              }
            }
            ready.add(
                () -> {
                  syncing = false;
                  if (attempt == generation) {
                    account.connected();
                    apply(base, session, rows, seeAvatars);
                    if (companion != null) {
                      companion.friends(reply.get("friends"));
                    }
                  }
                });
          } catch (Cloud.Refused refused) {
            ready.add(
                () -> {
                  syncing = false;
                  if (attempt != generation) {
                    return;
                  }
                  if (refused.signedOut()) {
                    remember(base, "");
                    account.begin();
                  } else {
                    account.failed(reason(refused));
                  }
                });
          } catch (IOException | RuntimeException e) {
            ready.add(
                () -> {
                  syncing = false;
                  if (attempt == generation) {
                    account.failed(Account.Failure.UNREACHABLE);
                  }
                });
          }
        });
  }

  private static String shareHash(AvatarEntry entry, Tune tune) throws IOException {
    String revision = entry.id() + "/" + entry.revision() + "/" + tune.up() + tune.turn();
    if (revision.equals(sharedKey)) {
      return sharedHash;
    }
    BakedAvatar small = SharedAvatar.downscale(entry.world());
    sharedKey = revision;
    sharedBlob = null;
    sharedHash = "";
    String problem = SharedAvatar.problem(small);
    if (!problem.isEmpty()) {
      CxVisualsClient.LOG.warn(
          "Avatar {} can't be shared, others keep seeing your skin: {}", entry.id(), problem);
      return sharedHash;
    }
    byte[] blob = SharedAvatar.toBytes(small);
    if (blob.length > SharedAvatar.MAX_BYTES) {
      CxVisualsClient.LOG.warn(
          "Avatar {} can't be shared, others keep seeing your skin: {} MB, the limit is {} MB",
          entry.id(),
          blob.length >> 20,
          SharedAvatar.MAX_BYTES >> 20);
      return sharedHash;
    }
    sharedHash = SocialProtocol.sha256Hex(blob);
    sharedBlob = blob;
    CxVisualsClient.LOG.info("Sharing avatar {} as {}", entry.id(), sharedHash.substring(0, 8));
    return sharedHash;
  }

  private static void apply(
      String base, String session, List<SharedPresence> rows, boolean seeAvatars) {
    Minecraft mc = Minecraft.getInstance();
    seenAt = System.nanoTime();
    Set<String> live = new HashSet<>();
    List<Account.Player> listed = new ArrayList<>(rows.size());
    for (SharedPresence row : rows) {
      if (account.blocks(row.uuid())) {
        continue;
      }
      live.add(row.uuid());
      presence.put(row.uuid(), row);
      Player entity =
          mc.level == null ? null : mc.level.getPlayerByUUID(UUID.fromString(row.uuid()));
      String detail =
          row.hasTrack()
              ? row.trackLine(SocialProtocol.TRACK_LIMIT)
              : row.hasAvatar() ? Lang.tr("Sharing an avatar") : "";
      listed.add(
          new Account.Player(
              row.uuid(), row.name(), entity == null ? Account.NO_ENTITY : entity.getId(), detail));
      if (seeAvatars && row.hasAvatar()) {
        ensureAvatar(base, session, row);
      } else {
        Avatars.dropRemote(row.uuid());
      }
    }
    for (String uuid : presence.keySet()) {
      if (!live.contains(uuid)) {
        presence.remove(uuid);
        Avatars.dropRemote(uuid);
      }
    }
    account.nearby(listed);
  }

  private static void ensureAvatar(String base, String session, SharedPresence row) {
    if (Avatars.hasRemote(row.uuid(), row.avatarHash())) {
      Avatars.retuneRemote(row.uuid(), remoteTune(row), row.clipMap(), row.clipKey());
      return;
    }
    if (unreadable.contains(row.avatarHash())) {
      Avatars.dropRemote(row.uuid());
      return;
    }
    String flight = row.uuid() + "/" + row.avatarHash();
    if (!inflight.add(flight)) {
      return;
    }
    downloads.execute(
        () -> {
          try {
            byte[] blob = cached(row.avatarHash());
            if (blob == null) {
              blob =
                  cloud.download(
                      base + "/v2/avatars/" + row.avatarHash(), session, SharedAvatar.MAX_BYTES);
              if (!SocialProtocol.sha256Hex(blob).equals(row.avatarHash())) {
                throw new IOException("hash mismatch");
              }
              store(row.avatarHash(), blob);
            }
            BakedAvatar avatar = SharedAvatar.fromBytes(blob);
            if (avatar == null) {
              if (unreadable.size() >= MAX_UNREADABLE) {
                unreadable.clear();
              }
              unreadable.add(row.avatarHash());
              CxVisualsClient.LOG.warn(
                  "{}'s shared avatar {} couldn't be read, showing their normal skin",
                  row.name(),
                  row.avatarHash().substring(0, 8));
              ready.add(
                  () -> {
                    SharedPresence current = presence.get(row.uuid());
                    if (current != null && current.avatarHash().equals(row.avatarHash())) {
                      Avatars.dropRemote(row.uuid());
                    }
                  });
            } else {
              ready.add(
                  () -> {
                    SharedPresence current = presence.get(row.uuid());
                    if (current != null && current.avatarHash().equals(row.avatarHash())) {
                      CxVisualsClient.LOG.info(
                          "Showing {}'s shared avatar {}",
                          row.name(),
                          row.avatarHash().substring(0, 8));
                      Avatars.putRemote(
                          row.uuid(),
                          row.avatarHash(),
                          avatar,
                          remoteTune(row),
                          row.clipMap(),
                          row.clipKey());
                    }
                  });
            }
          } catch (IOException | RuntimeException e) {
            CxVisualsClient.LOG.warn("Couldn't fetch a shared avatar: {}", e.getMessage());
          } finally {
            inflight.remove(flight);
          }
        });
  }

  private static Tune remoteTune(SharedPresence row) {
    RawModel.Axis up = "Z".equals(row.tuneUp()) ? RawModel.Axis.Z : RawModel.Axis.Y;
    return new Tune(up, row.tuneTurn(), row.tuneSize(), row.tuneLift(), row.tuneClips());
  }

  private static byte[] cached(String hash) {
    Path file = shared.resolve(hash + ".bin");
    try {
      if (Files.isRegularFile(file) && Files.size(file) <= SharedAvatar.MAX_BYTES) {
        byte[] blob = Files.readAllBytes(file);
        if (SocialProtocol.sha256Hex(blob).equals(hash)) {
          return blob;
        }
      }
    } catch (IOException e) {
      CxVisualsClient.LOG.warn("Couldn't read the shared cache: {}", e.getMessage());
    }
    return null;
  }

  private static void store(String hash, byte[] blob) {
    try {
      Path tmp = shared.resolve(hash + ".tmp");
      Files.write(tmp, blob);
      Files.move(tmp, shared.resolve(hash + ".bin"), StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      CxVisualsClient.LOG.warn("Couldn't store a shared avatar: {}", e.getMessage());
    }
  }
}
