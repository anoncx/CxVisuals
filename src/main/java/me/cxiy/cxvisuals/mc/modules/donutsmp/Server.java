package me.cxiy.cxvisuals.mc.modules.donutsmp;

import java.util.function.Supplier;
import me.cxiy.cxvisuals.modules.donutsmp.auction.Host;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;

final class Server {
  private static volatile Supplier<String> allowed = () -> Host.NAME;

  private Server() {}

  static void allow(Supplier<String> names) {
    allowed = names;
  }

  static String address(Minecraft mc) {
    ServerData server = mc.getCurrentServer();
    return server == null || server.ip == null ? "" : server.ip;
  }

  static boolean donut(Minecraft mc) {
    return Host.matches(address(mc), allowed.get());
  }

  static String names() {
    return allowed.get();
  }
}
