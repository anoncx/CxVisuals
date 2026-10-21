package me.cxiy.cxvisuals.mc.modules.inventory;

import me.cxiy.cxvisuals.mc.gui.McCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import org.joml.Matrix3x2fStack;

final class Maps {
  static final int GRID = 3;
  private static final float ICON = 16.0F / 128.0F;

  static final class Card {
    ItemStack source = ItemStack.EMPTY;
    int id = -1;
    String title = "";
    String note = "";
    boolean ready;
    int copies = 1;
    String label = "";
    int labelCopies = 1;
    String labelTitle = "";

    String label() {
      if (copies <= 1) {
        return title;
      }
      if (labelCopies != copies || !labelTitle.equals(title)) {
        label = copies + "\u00d7 " + title;
        labelCopies = copies;
        labelTitle = title;
      }
      return label;
    }
  }

  private static final MapRenderState SCRATCH = new MapRenderState();

  private final Card[] cards = new Card[Previews.CARDS];
  private final MapRenderState[] states = new MapRenderState[Previews.CARDS];

  Maps() {
    for (int i = 0; i < cards.length; i++) {
      cards[i] = new Card();
      states[i] = new MapRenderState();
    }
    McCanvas.maps(states);
  }

  Card card(int index) {
    return cards[index];
  }

  MapRenderState state(int index) {
    return states[index];
  }

  static boolean candidate(ItemStack stack) {
    return stack.is(Items.FILLED_MAP) && stack.has(DataComponents.MAP_ID);
  }

  void reset() {
    for (Card card : cards) {
      card.ready = false;
    }
  }

  boolean resolve(int index, ItemStack stack) {
    if (!candidate(stack)) {
      return false;
    }
    Minecraft mc = Minecraft.getInstance();
    ClientLevel level = mc.level;
    MapId id = stack.get(DataComponents.MAP_ID);
    MapItemSavedData data = level == null ? null : level.getMapData(id);
    if (data == null) {
      return false;
    }
    mc.getMapRenderer().extractRenderState(id, data, states[index]);
    Card card = cards[index];
    if (card.ready
        && card.id == id.id()
        && ItemStack.isSameItemSameComponents(card.source, stack)) {
      return true;
    }
    card.source = stack.copy();
    card.id = id.id();
    card.ready = true;
    card.title = stack.getHoverName().getString();
    card.note = "1:" + (1 << data.scale);
    return true;
  }

  static boolean face(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
    if (!candidate(stack)) {
      return false;
    }
    Minecraft mc = Minecraft.getInstance();
    ClientLevel level = mc.level;
    if (level == null) {
      return false;
    }
    MapId id = stack.get(DataComponents.MAP_ID);
    MapItemSavedData data = level.getMapData(id);
    if (data == null) {
      return false;
    }
    mc.getMapRenderer().extractRenderState(id, data, SCRATCH);
    Matrix3x2fStack pose = graphics.pose();
    pose.pushMatrix();
    pose.translate(x, y);
    pose.scale(ICON, ICON);
    graphics.map(SCRATCH);
    pose.popMatrix();
    return true;
  }
}
