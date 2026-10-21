package me.cxiy.cxvisuals.mc.gui;

import java.util.List;
import me.cxiy.cxvisuals.modules.hud.ChatLog;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class ChatLines {
  private static final int SLOTS = ChatLog.CAPACITY;

  private final int[] ids = new int[SLOTS];
  private final Component[] messages = new Component[SLOTS];
  private final List<FormattedCharSequence>[] wrapped = newLists();

  @SuppressWarnings("unchecked")
  private static List<FormattedCharSequence>[] newLists() {
    return new List[SLOTS];
  }

  void put(int id, Component message) {
    int slot = Math.floorMod(id, SLOTS);
    ids[slot] = id;
    messages[slot] = message;
    wrapped[slot] = null;
  }

  int split(int id, int units) {
    int slot = Math.floorMod(id, SLOTS);
    if (messages[slot] == null || ids[slot] != id) {
      return -1;
    }
    wrapped[slot] = Minecraft.getInstance().font.split(messages[slot], units);
    return wrapped[slot].size();
  }

  FormattedCharSequence line(int id, int index) {
    int slot = Math.floorMod(id, SLOTS);
    List<FormattedCharSequence> lines = wrapped[slot];
    return lines == null || ids[slot] != id || index >= lines.size() ? null : lines.get(index);
  }
}
