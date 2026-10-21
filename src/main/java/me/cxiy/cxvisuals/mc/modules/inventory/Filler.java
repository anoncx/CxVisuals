package me.cxiy.cxvisuals.mc.modules.inventory;

import me.cxiy.cxvisuals.modules.inventory.Kind;
import me.cxiy.cxvisuals.modules.inventory.Recipe;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

final class Filler {
  private static final int RESULT = 0;
  private static final int INVENTORY_GRID = 2;
  private static final int TABLE_GRID = 3;
  private static final int INVENTORY_FIRST = 9;
  private static final int TABLE_FIRST = 10;

  private Filler() {}

  static boolean supports(AbstractContainerMenu menu, Recipe recipe) {
    if (recipe.kind() != Kind.CRAFT) {
      return false;
    }
    int grid = gridWidth(menu);
    if (grid == 0 || recipe.columns() > grid || recipe.rows() > grid) {
      return false;
    }
    for (int[] slot : recipe.inputs()) {
      for (int option : slot) {
        if (!Stacks.plain(Stacks.get(option))) {
          return false;
        }
      }
    }
    return true;
  }

  private static int gridWidth(AbstractContainerMenu menu) {
    if (menu instanceof InventoryMenu) {
      return INVENTORY_GRID;
    }
    return menu instanceof CraftingMenu ? TABLE_GRID : 0;
  }

  static void fill(Minecraft mc, AbstractContainerMenu menu, Recipe recipe) {
    int grid = gridWidth(menu);
    if (grid == 0 || mc.gameMode == null || mc.player == null) {
      return;
    }
    int firstInventory = grid == INVENTORY_GRID ? INVENTORY_FIRST : TABLE_FIRST;
    int total = menu.slots.size();
    int[] reserved = new int[total];
    int[] sources = new int[recipe.cells()];
    for (int i = 0; i < sources.length; i++) {
      sources[i] = -1;
      int[] options = recipe.inputs()[i];
      if (options.length == 0) {
        continue;
      }
      for (int s = firstInventory; s < total && sources[i] < 0; s++) {
        ItemStack held = menu.slots.get(s).getItem();
        if (held.isEmpty() || held.getCount() - reserved[s] <= 0 || !Stacks.plain(held)) {
          continue;
        }
        for (int option : options) {
          if (ItemStack.isSameItem(held, Stacks.get(option))) {
            sources[i] = s;
            reserved[s]++;
            break;
          }
        }
      }
      if (sources[i] < 0) {
        mc.player.sendOverlayMessage(Component.literal("Missing ingredients"));
        return;
      }
    }
    int first = RESULT + 1;
    for (int s = first; s < first + grid * grid; s++) {
      if (!menu.slots.get(s).getItem().isEmpty()) {
        mc.gameMode.handleContainerInput(
            menu.containerId, s, 0, ContainerInput.QUICK_MOVE, mc.player);
      }
    }
    for (int i = 0; i < sources.length; i++) {
      if (sources[i] < 0) {
        continue;
      }
      int target = first + (i / recipe.columns()) * grid + (i % recipe.columns());
      mc.gameMode.handleContainerInput(
          menu.containerId, sources[i], 0, ContainerInput.PICKUP, mc.player);
      mc.gameMode.handleContainerInput(
          menu.containerId, target, 1, ContainerInput.PICKUP, mc.player);
      mc.gameMode.handleContainerInput(
          menu.containerId, sources[i], 0, ContainerInput.PICKUP, mc.player);
    }
  }
}
