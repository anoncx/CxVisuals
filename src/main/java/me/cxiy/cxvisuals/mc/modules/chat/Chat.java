package me.cxiy.cxvisuals.mc.modules.chat;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.modules.chat.ChatModule;
import me.cxiy.cxvisuals.modules.chat.Speakers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;

public final class Chat {
  private static final int NAME_MIN = 3;
  private static final int NAME_MAX = 16;
  private static final float PING_PITCH = 1.5F;

  private static final long ROSTER_NANOS = 2_000_000_000L;
  private static final Map<String, UUID> ROSTER = new HashMap<>();

  private static ChatModule module;
  private static ClientPacketListener rosterOwner;
  private static long rosterAt;
  private static int rosterSize = -1;
  private static UUID sender;

  private Chat() {}

  public static ChatModule create(McKeyBinder keys) {
    module = new ChatModule(keys);
    return module;
  }

  public static ChatModule module() {
    return module;
  }

  public static void sender(UUID id) {
    sender = id;
  }

  public static boolean hides(Component message, GuiMessageSource source) {
    if (module == null || !module.live() || source == GuiMessageSource.SYSTEM_CLIENT) {
      return false;
    }
    Minecraft minecraft = Minecraft.getInstance();
    Player self = minecraft.player;
    ClientPacketListener connection = minecraft.getConnection();
    if (self == null || connection == null) {
      return false;
    }
    String text = Speakers.plain(message.getString());
    UUID from = sender != null ? sender : scan(connection, text);
    int origin = ChatModule.SERVER;
    double distance = Double.POSITIVE_INFINITY;
    if (from == null) {
      String speaker = Speakers.speaker(text);
      if (speaker != null) {
        origin = ChatModule.PLAYER;
        from = roster(connection).get(speaker.toLowerCase(Locale.ROOT));
      } else if (source == GuiMessageSource.PLAYER) {
        origin = ChatModule.PLAYER;
      }
    }
    if (from != null) {
      if (from.equals(self.getUUID())) {
        origin = ChatModule.OWN;
      } else {
        origin = ChatModule.PLAYER;
        Player other = minecraft.level == null ? null : minecraft.level.getPlayerByUUID(from);
        if (other != null) {
          distance = self.distanceTo(other);
        }
      }
    }
    ChatModule.Verdict verdict =
        module.judge(
            text, self.getGameProfile().name(), origin, distance, System.currentTimeMillis());
    if (verdict == ChatModule.Verdict.MATCH && module.ping.get()) {
      minecraft
          .getSoundManager()
          .play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), PING_PITCH));
    }
    return verdict == ChatModule.Verdict.HIDE;
  }

  private static UUID scan(ClientPacketListener connection, String text) {
    int length = text.length();
    int start = -1;
    for (int i = 0; i <= length; i++) {
      boolean word =
          i < length
              && (Character.isLetterOrDigit(text.charAt(i)) && text.charAt(i) < 128
                  || text.charAt(i) == '_');
      if (word) {
        if (start < 0) {
          start = i;
        }
        continue;
      }
      if (start >= 0) {
        int size = i - start;
        if (size >= NAME_MIN && size <= NAME_MAX) {
          UUID found = roster(connection).get(text.substring(start, i).toLowerCase(Locale.ROOT));
          if (found != null) {
            return found;
          }
        }
        start = -1;
      }
    }
    return null;
  }

  private static Map<String, UUID> roster(ClientPacketListener connection) {
    long now = System.nanoTime();
    int size = connection.getOnlinePlayers().size();
    if (connection != rosterOwner || size != rosterSize || now - rosterAt > ROSTER_NANOS) {
      rosterOwner = connection;
      rosterSize = size;
      rosterAt = now;
      ROSTER.clear();
      for (PlayerInfo info : connection.getOnlinePlayers()) {
        UUID id = info.getProfile().id();
        String name = info.getProfile().name().toLowerCase(Locale.ROOT);
        ROSTER.put(name, id);
        if (name.startsWith(".")) {
          ROSTER.putIfAbsent(name.substring(1), id);
        }
        Component display = info.getTabListDisplayName();
        if (display != null) {
          for (String word : display.getString().split("[^A-Za-z0-9_]+")) {
            if (word.length() >= NAME_MIN) {
              ROSTER.putIfAbsent(word.toLowerCase(Locale.ROOT), id);
            }
          }
        }
      }
    }
    return ROSTER;
  }
}
