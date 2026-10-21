package me.cxiy.cxvisuals.mc.modules.accounts;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.FriendsService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.mixin.MinecraftAccessor;
import me.cxiy.cxvisuals.modules.accounts.Hub;
import me.cxiy.cxvisuals.modules.accounts.Ids;
import me.cxiy.cxvisuals.modules.accounts.Platform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.gui.screens.social.RemoteFriendListUpdateHandler;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.report.ReportEnvironment;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.util.Util;

final class Sessions implements Platform {
  private static final int LONGEST_WAIT = 400;

  private record Pending(
      Hub.Ready ready,
      ServerData server,
      ServerAddress address,
      int settle,
      Consumer<String> done) {}

  private Pending pending;
  private int waited;
  private int settled;

  @Override
  public String activeUuid() {
    User user = Minecraft.getInstance().getUser();
    return user == null ? "" : Ids.plain(user.getProfileId().toString());
  }

  @Override
  public String activeName() {
    User user = Minecraft.getInstance().getUser();
    return user == null ? "" : user.getName();
  }

  @Override
  public boolean inGame() {
    return Minecraft.getInstance().level != null;
  }

  @Override
  public String server() {
    Minecraft mc = Minecraft.getInstance();
    ServerData data = mc.getCurrentServer();
    return mc.level == null || data == null || data.ip == null ? "" : data.ip;
  }

  @Override
  public void main(Runnable task) {
    Minecraft.getInstance().execute(task);
  }

  @Override
  public void open(String url) {
    Util.getPlatform().openUri(url);
  }

  @Override
  public void switchTo(Hub.Ready ready, Rejoin rejoin, Consumer<String> done) {
    Minecraft mc = Minecraft.getInstance();
    String address = server();
    if (rejoin != Rejoin.YES || address.isEmpty()) {
      done.accept(apply(ready));
      return;
    }
    rejoin(ready, mc.getCurrentServer(), done);
  }

  void rejoin(Hub.Ready ready, ServerData server, Consumer<String> done) {
    if (pending != null) {
      done.accept(Lang.tr("Another switch is still running."));
      return;
    }
    pending =
        new Pending(
            ready,
            server,
            ServerAddress.parseString(server.ip),
            Math.max(1, Math.round(Accounts.settleSeconds() * 20.0F)),
            done);
    waited = 0;
    settled = 0;
    Minecraft.getInstance().disconnect(new ProgressScreen(true), false);
  }

  void tick(Minecraft mc) {
    Pending job = pending;
    if (job == null) {
      return;
    }
    waited++;
    boolean closed =
        mc.level == null
            && mc.player == null
            && mc.getConnection() == null
            && !(mc.gui.screen() instanceof ConnectScreen);
    settled = closed ? settled + 1 : 0;
    if (settled < job.settle && waited < LONGEST_WAIT) {
      return;
    }
    pending = null;
    String problem = apply(job.ready);
    if (problem != null) {
      mc.gui.setScreen(new JoinMultiplayerScreen(new TitleScreen()));
      job.done.accept(problem);
      return;
    }
    job.done.accept(null);
    Accounts.connect(
        new JoinMultiplayerScreen(new TitleScreen()), mc, job.address, job.server, false, null);
  }

  boolean busy() {
    return pending != null;
  }

  static String apply(Hub.Ready ready) {
    UUID id = Ids.parse(ready.uuid());
    if (id == null) {
      return Lang.tr("This account has no valid profile id.");
    }
    try {
      Minecraft mc = Minecraft.getInstance();
      MinecraftAccessor accessor = (MinecraftAccessor) mc;
      User user = new User(ready.name(), id, ready.token(), Optional.empty(), Optional.empty());
      YggdrasilAuthenticationService auth = new YggdrasilAuthenticationService(mc.getProxy());
      boolean online = !ready.token().isEmpty();
      UserApiService api =
          online ? auth.createUserApiService(user.getAccessToken()) : UserApiService.OFFLINE;
      FriendsService friends = auth.createFriendsService(user.getAccessToken());
      accessor.cxvisuals$user(user);
      accessor.cxvisuals$userApiService(api);
      accessor.cxvisuals$playerSocialManager(
          new PlayerSocialManager(
              mc, api, friends, new RemoteFriendListUpdateHandler(friends, mc)));
      accessor.cxvisuals$profileKeyPairManager(
          online
              ? ProfileKeyPairManager.create(api, user, mc.gameDirectory.toPath())
              : ProfileKeyPairManager.EMPTY_KEY_MANAGER);
      accessor.cxvisuals$reportingContext(ReportingContext.create(ReportEnvironment.local(), api));
      accessor.cxvisuals$profileFuture(
          online
              ? CompletableFuture.supplyAsync(
                  () -> mc.services().sessionService().fetchProfile(user.getProfileId(), true),
                  Util.ioPool())
              : CompletableFuture.completedFuture(null));
      return null;
    } catch (RuntimeException e) {
      CxVisualsClient.LOG.error("The account session could not be switched", e);
      return Lang.tr("The session could not be switched, check the log.");
    }
  }
}
