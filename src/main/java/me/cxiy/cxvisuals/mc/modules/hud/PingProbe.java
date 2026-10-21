package me.cxiy.cxvisuals.mc.modules.hud;

import me.cxiy.cxvisuals.modules.hud.HudData;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.ping.ServerboundPingRequestPacket;

public final class PingProbe {
  private static final long NANOS_PER_MILLI = 1_000_000L;
  private static final long TIMEOUT = 3_000L * NANOS_PER_MILLI;
  private static final long FRESH = 8_000L * NANOS_PER_MILLI;
  private static final long RETRY = 60_000L * NANOS_PER_MILLI;
  private static final int MISSES = 3;
  private static final int WINDOW = 3;
  private static final int PONG = 0;
  private static final int STATS = 1;
  private static final int GAVE_UP = 2;

  private static final float[] RECENT = new float[WINDOW];
  private static final float[] SORTED = new float[WINDOW];

  private static ClientPacketListener bound;
  private static volatile long pending;
  private static volatile int method = PONG;
  private static volatile long answeredAt;
  private static int filled;
  private static int cursor;
  private static int misses;
  private static long sentAt;
  private static long gaveUpAt;

  private PingProbe() {}

  public static float measure(ClientPacketListener connection) {
    long now = System.nanoTime();
    if (connection != bound) {
      bound = connection;
      pending = 0L;
      method = PONG;
      answeredAt = 0L;
      filled = 0;
      cursor = 0;
      misses = 0;
    }
    if (connection == null) {
      return HudData.UNKNOWN;
    }
    if (method == GAVE_UP && now - gaveUpAt >= RETRY) {
      method = PONG;
      misses = 0;
    }
    if (pending != 0L && now - sentAt >= TIMEOUT) {
      pending = 0L;
      if (++misses >= MISSES) {
        misses = 0;
        if (method == PONG) {
          method = STATS;
        } else {
          method = GAVE_UP;
          gaveUpAt = now;
        }
      }
    }
    if (pending == 0L && method != GAVE_UP) {
      sentAt = now;
      pending = now;
      connection.send(
          method == PONG
              ? new ServerboundPingRequestPacket(now)
              : new ServerboundClientCommandPacket(
                  ServerboundClientCommandPacket.Action.REQUEST_STATS));
    }
    if (filled == 0 || now - answeredAt > FRESH) {
      return HudData.UNKNOWN;
    }
    System.arraycopy(RECENT, 0, SORTED, 0, filled);
    java.util.Arrays.sort(SORTED, 0, filled);
    return SORTED[filled / 2];
  }

  public static boolean pong(long echoed) {
    long mine = pending;
    if (mine == 0L || echoed != mine || method != PONG) {
      return false;
    }
    arrived(mine);
    return true;
  }

  public static void stats() {
    long mine = pending;
    if (mine != 0L && method == STATS) {
      arrived(mine);
    }
  }

  private static void arrived(long sent) {
    long now = System.nanoTime();
    pending = 0L;
    misses = 0;
    answeredAt = now;
    RECENT[cursor] = (now - sent) / (float) NANOS_PER_MILLI;
    cursor = (cursor + 1) % WINDOW;
    if (filled < WINDOW) {
      filled++;
    }
  }
}
