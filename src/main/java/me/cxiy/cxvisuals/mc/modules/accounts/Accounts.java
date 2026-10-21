package me.cxiy.cxvisuals.mc.modules.accounts;

import java.nio.file.Path;
import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.gui.Themes;
import me.cxiy.cxvisuals.gui.accounts.AccountsView;
import me.cxiy.cxvisuals.gui.accounts.SwitchView;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.gui.AccountsScreen;
import me.cxiy.cxvisuals.mc.gui.SwitchScreen;
import me.cxiy.cxvisuals.modules.accounts.AccountsModule;
import me.cxiy.cxvisuals.modules.accounts.Entry;
import me.cxiy.cxvisuals.modules.accounts.Hub;
import me.cxiy.cxvisuals.modules.accounts.JavaHttp;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class Accounts {
  private static final String FOLDER = "accounts";

  private static AccountsModule module;
  private static Hub hub;
  private static Sessions sessions;
  private static Themes themes;
  private static ClientSettings client;
  private static KeyMapping openKey;
  private static volatile boolean bypass;

  private Accounts() {}

  public static AccountsModule create(McKeyBinder keys, ClientSettings settings) {
    client = settings;
    themes = new Themes(settings);
    sessions = new Sessions();
    module = new AccountsModule(keys, Accounts::open, Accounts::lock);
    Path directory =
        FabricLoader.getInstance().getConfigDir().resolve(CxVisualsClient.MOD_ID).resolve(FOLDER);
    hub = new Hub(module, directory, new JavaHttp(), sessions);
    openKey = keys.mapping(AccountsModule.OPEN_KEY);
    hub.describeVault();
    hub.startLater();
    Runtime.getRuntime()
        .addShutdownHook(new Thread(() -> hub.cancelMicrosoft(), "CxVisuals-Accounts-Exit"));
    return module;
  }

  public static AccountsModule module() {
    return module;
  }

  public static Hub hub() {
    return hub;
  }

  public static boolean live() {
    return module != null && module.live();
  }

  public static float settleSeconds() {
    return module == null ? 1.5F : module.settleSeconds();
  }

  public static void tick(Minecraft mc) {
    if (module == null) {
      return;
    }
    sessions.tick(mc);
    while (openKey.consumeClick()) {
      if (mc.gui.screen() == null && live()) {
        open();
      }
    }
  }

  public static void open() {
    Minecraft mc = Minecraft.getInstance();
    if (module == null || !module.live() || mc.gui.screen() instanceof AccountsScreen) {
      return;
    }
    mc.gui.setScreen(
        new AccountsScreen(mc.gui.screen(), new AccountsView(hub, client.motion::get), themes));
  }

  public static void rejoin(
      Hub.Ready ready, ServerData server, java.util.function.Consumer<String> done) {
    sessions.rejoin(ready, server, done);
  }

  public static void lock() {
    if (hub != null) {
      hub.lock();
    }
  }

  public static boolean connecting(
      Screen parent,
      Minecraft mc,
      ServerAddress address,
      ServerData data,
      boolean quickPlay,
      TransferState transfer) {
    if (bypass
        || module == null
        || hub == null
        || transfer != null
        || mc.level != null
        || mc.getConnection() != null
        || sessions.busy()) {
      return false;
    }
    String text =
        data != null && data.ip != null && !data.ip.isBlank() ? data.ip : address.toString();
    Entry linked = hub.linked(text);
    if (linked == null || hub.isActive(linked)) {
      return false;
    }
    SwitchView view = new SwitchView(hub, linked, text, client.motion::get);
    Runnable go = () -> connect(parent, mc, address, data, quickPlay, transfer);
    SwitchScreen screen = new SwitchScreen(parent, view, themes, go);
    mc.gui.setScreen(screen);
    hub.resolve(
        linked,
        (ready, error) -> {
          if (mc.gui.screen() != screen || view.result() != SwitchView.Result.WAITING) {
            return;
          }
          if (error != null) {
            view.fail(error);
            return;
          }
          String problem = Sessions.apply(ready);
          if (problem != null) {
            view.fail(problem);
            return;
          }
          hub.book().used(linked.id, System.currentTimeMillis());
          view.close();
          go.run();
        });
    return true;
  }

  static void connect(
      Screen parent,
      Minecraft mc,
      ServerAddress address,
      ServerData data,
      boolean quickPlay,
      TransferState transfer) {
    bypass = true;
    try {
      ConnectScreen.startConnecting(parent, mc, address, data, quickPlay, transfer);
    } finally {
      bypass = false;
    }
  }
}
