package me.cxiy.cxvisuals.mc.modules.inventory;

import java.util.Arrays;
import java.util.List;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

final class Previews {
  static final int CARDS = 96;
  static final int CAPACITY = 64;
  private static final int ROW = 9;
  private static final int SHULKER_SLOTS = 27;
  private static final int PLAIN_SHULKER = 0x946794;
  private static final int ENDER = 0x1F5C55;

  static final class Card {
    final int[] counts = new int[CAPACITY];
    ItemStack source = ItemStack.EMPTY;
    int revision = -1;
    String title = "";
    String note = "";
    int slots;
    int columns = ROW;
    int tint;
    boolean ready;
    boolean compact;
    int copies = 1;
    String label = "";
    int labelCopies = 1;
    String labelTitle = "";
  }

  private final ItemStack[] held = new ItemStack[CARDS * CAPACITY];
  private final Card[] cards = new Card[CARDS];
  private ItemStack[] ender = new ItemStack[0];
  private int enderRevision;

  Previews() {
    Arrays.fill(held, ItemStack.EMPTY);
    for (int i = 0; i < CARDS; i++) {
      cards[i] = new Card();
    }
    McCanvas.held(held);
  }

  Card card(int index) {
    return cards[index];
  }

  ItemStack at(int card, int index) {
    return index >= 0 && index < CAPACITY ? held[card * CAPACITY + index] : ItemStack.EMPTY;
  }

  static int base(int card) {
    return card * CAPACITY;
  }

  static boolean candidate(ItemStack stack) {
    if (stack.isEmpty()) {
      return false;
    }
    if (stack.is(Items.ENDER_CHEST) || Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock) {
      return true;
    }
    return stack.has(DataComponents.BUNDLE_CONTENTS) || stack.has(DataComponents.CONTAINER);
  }

  void remember(AbstractContainerMenu menu, int rows) {
    int size = Math.min(CAPACITY, rows * ROW);
    boolean changed = ender.length != size;
    ItemStack[] next = changed ? new ItemStack[size] : ender;
    for (int i = 0; i < size; i++) {
      ItemStack now = menu.slots.get(i).getItem();
      if (changed || !ItemStack.matches(next[i], now)) {
        changed = true;
        next[i] = now.copy();
      }
    }
    if (changed) {
      ender = next;
      enderRevision++;
    }
  }

  boolean resolve(int index, ItemStack stack, boolean compact) {
    if (!candidate(stack)) {
      return false;
    }
    Card card = cards[index];
    boolean isEnder = stack.is(Items.ENDER_CHEST);
    int revision = isEnder ? enderRevision : 0;
    if (card.ready
        && card.compact == compact
        && card.revision == revision
        && ItemStack.isSameItemSameComponents(card.source, stack)) {
      return card.slots > 0;
    }
    card.source = stack.copy();
    card.revision = revision;
    card.compact = compact;
    card.ready = true;
    card.slots = 0;
    List<ItemStack> list = contents(card, stack, isEnder);
    if (list == null) {
      return false;
    }
    int base = base(index);
    Arrays.fill(held, base, base + CAPACITY, ItemStack.EMPTY);
    Arrays.fill(card.counts, 0);
    int filled = 0;
    int limit = Math.min(CAPACITY, list.size());
    int original = card.slots;
    int distinct = 0;
    for (int i = 0; i < limit; i++) {
      ItemStack each = list.get(i);
      if (each.isEmpty()) {
        continue;
      }
      filled++;
      if (!compact) {
        held[base + i] = each;
        card.counts[i] = each.getCount();
        continue;
      }
      int found = -1;
      for (int j = 0; j < distinct && found < 0; j++) {
        if (ItemStack.isSameItemSameComponents(held[base + j], each)) {
          found = j;
        }
      }
      if (found >= 0) {
        card.counts[found] += each.getCount();
        held[base + found].setCount(card.counts[found]);
      } else {
        held[base + distinct] = each.copy();
        card.counts[distinct] = each.getCount();
        distinct++;
      }
    }
    card.columns = ROW;
    if (compact) {
      card.slots = Math.max(1, distinct);
      card.columns = Math.min(ROW, card.slots);
    }
    card.title = stack.getHoverName().getString();
    card.note = filled + "/" + original;
    return true;
  }

  private List<ItemStack> contents(Card card, ItemStack stack, boolean isEnder) {
    Block block = Block.byItem(stack.getItem());
    if (block instanceof ShulkerBoxBlock shulker) {
      DyeColor color = shulker.getColor();
      card.tint = color == null ? PLAIN_SHULKER : color.getTextureDiffuseColor();
      card.slots = SHULKER_SLOTS;
      ItemContainerContents data = stack.get(DataComponents.CONTAINER);
      return data == null ? List.of() : data.allItemsCopyStream().toList();
    }
    if (isEnder) {
      card.tint = ENDER;
      card.slots = ender.length == 0 ? SHULKER_SLOTS : ender.length;
      return Arrays.asList(ender);
    }
    BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
    if (bundle != null && bundle.size() > 0) {
      List<ItemStack> list = bundle.itemCopyStream().toList();
      card.tint = 0;
      card.slots = rounded(list.size());
      return list;
    }
    ItemContainerContents data = stack.get(DataComponents.CONTAINER);
    if (data != null && data != ItemContainerContents.EMPTY) {
      List<ItemStack> list = data.allItemsCopyStream().toList();
      if (list.stream().anyMatch(each -> !each.isEmpty())) {
        card.tint = 0;
        card.slots = rounded(list.size());
        return list;
      }
    }
    return null;
  }

  private static int rounded(int count) {
    return Math.min(CAPACITY, Math.max(ROW, (count + ROW - 1) / ROW * ROW));
  }
}
