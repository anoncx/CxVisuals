package me.cxiy.cxvisuals.mc.menu.bridge;

import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.gui.StyledText;
import me.cxiy.cxvisuals.menu.model.Item;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;

final class ServerItems extends PlainItems<ServerSelectionList.Entry> {
  private static final int MOTD_LINES = 2;
  private static final long[] REACH = {150L, 300L, 600L, 1000L};
  private static final Component NO_ROUTE =
      Component.translatable("multiplayer.status.cannot_resolve");
  private static final Component NO_ANSWER =
      Component.translatable("multiplayer.status.cannot_connect");
  private static final ExecutorService PINGS =
      Executors.newFixedThreadPool(
          4,
          task -> {
            Thread thread = new Thread(task, "CxVisuals server pinger");
            thread.setDaemon(true);
            return thread;
          });

  private final JoinMultiplayerScreen screen;

  ServerItems(JoinMultiplayerScreen screen, ServerSelectionList list) {
    super(list);
    this.screen = screen;
  }

  @Override
  protected boolean live(ServerSelectionList.Entry entry) {
    return entry instanceof ServerSelectionList.OnlineServerEntry;
  }

  @Override
  protected void fill(ServerSelectionList.Entry entry, Item item) {
    if (entry instanceof ServerSelectionList.LANHeader) {
      item.header = true;
      item.title = Texts.plain(Component.translatable("lanServer.scanning"));
      return;
    }
    if (!(entry instanceof ServerSelectionList.OnlineServerEntry online)) {
      super.fill(entry, item);
      return;
    }
    ServerData data = online.getServerData();
    if (data.state() == ServerData.State.INITIAL) {
      ping(data);
    }
    ServerData.State state = data.state();
    int signal =
        switch (state) {
          case INITIAL, PINGING -> Item.BUSY;
          case UNREACHABLE -> 0;
          case INCOMPATIBLE, SUCCESSFUL -> bars(data.ping);
        };
    if (item.title.equals(data.name)
        && item.signal == signal
        && item.icon == data.getIconBytes()
        && item.stamp == data.motd) {
      return;
    }
    item.title = data.name == null ? "" : data.name;
    item.signal = signal;
    item.icon = data.getIconBytes();
    item.stamp = data.motd;
    item.flagged = state == ServerData.State.INCOMPATIBLE || state == ServerData.State.UNREACHABLE;
    StyledText[] motd = Texts.lines(data.motd, MOTD_LINES);
    item.first =
        motd[0].count == 0 && state == ServerData.State.PINGING ? StyledText.of(data.ip) : motd[0];
    item.second = motd[1];
    item.trailing =
        state == ServerData.State.INCOMPATIBLE
            ? Texts.plain(data.version)
            : Texts.plain(data.status);
  }

  private static int bars(long ping) {
    for (int i = 0; i < REACH.length; i++) {
      if (ping < REACH[i]) {
        return Item.BARS - i;
      }
    }
    return 1;
  }

  private void ping(ServerData data) {
    Minecraft mc = Minecraft.getInstance();
    data.setState(ServerData.State.PINGING);
    data.motd = CommonComponents.EMPTY;
    data.status = CommonComponents.EMPTY;
    PINGS.submit(
        () -> {
          try {
            screen
                .getPinger()
                .pingServer(
                    data,
                    () -> mc.execute(() -> screen.getServers().save()),
                    () ->
                        data.setState(
                            data.protocol == SharedConstants.getCurrentVersion().protocolVersion()
                                ? ServerData.State.SUCCESSFUL
                                : ServerData.State.INCOMPATIBLE),
                    EventLoopGroupHolder.remote(mc.options.useNativeTransport()));
          } catch (UnknownHostException e) {
            data.setState(ServerData.State.UNREACHABLE);
            data.motd = NO_ROUTE;
          } catch (Exception e) {
            data.setState(ServerData.State.UNREACHABLE);
            data.motd = NO_ANSWER;
          }
        });
  }

  @Override
  public void open(int index) {
    list.children().get(index).join();
  }

  @Override
  public String empty() {
    return Lang.tr("No servers saved yet. Add one, or connect directly.");
  }
}
