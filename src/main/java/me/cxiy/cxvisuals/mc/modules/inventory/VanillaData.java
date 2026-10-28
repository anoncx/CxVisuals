package me.cxiy.cxvisuals.mc.modules.inventory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import me.cxiy.cxvisuals.mc.compat.Recipes;
import me.cxiy.cxvisuals.modules.inventory.Handles;
import me.cxiy.cxvisuals.modules.inventory.Kind;
import me.cxiy.cxvisuals.modules.inventory.Recipe;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;
import net.minecraft.world.item.crafting.display.SmithingRecipeDisplay;
import net.minecraft.world.item.crafting.display.StonecutterRecipeDisplay;

final class VanillaData {
  private static final int OPTION_LIMIT = 32;
  private static final int[] NO_OPTIONS = new int[0];
  private static final int TICKS_PER_SECOND = 20;

  private VanillaData() {}

  static int[] items(ClientLevel level) {
    CreativeModeTabs.tryRebuildTabContents(level.enabledFeatures(), true, level.registryAccess());
    Set<Integer> order = new LinkedHashSet<>(2048);
    for (ItemStack stack : CreativeModeTabs.searchTab().getSearchTabDisplayItems()) {
      if (!stack.isEmpty()) {
        String key = Stacks.keyOf(stack);
        String space = BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
        order.add(Stacks.register(key, stack, space, ""));
      }
    }
    int[] out = new int[order.size()];
    int index = 0;
    for (int handle : order) {
      out[index++] = handle;
    }
    return out;
  }

  static List<Recipe> recipes(ClientLevel level) {
    List<Recipe> out = new ArrayList<>(2048);
    ContextMap context = SlotDisplayContext.fromLevel(level);
    for (RecipeHolder<?> holder : Recipes.vanilla(level)) {
      Kind kind = kindOf(holder.value().getType());
      for (RecipeDisplay display : holder.value().display()) {
        Recipe recipe = convert(display, kind, context);
        if (recipe != null) {
          out.add(recipe);
        }
      }
    }
    return out;
  }

  private static Kind kindOf(RecipeType<?> type) {
    if (type == RecipeType.SMELTING) {
      return Kind.SMELT;
    }
    if (type == RecipeType.BLASTING) {
      return Kind.BLAST;
    }
    if (type == RecipeType.SMOKING) {
      return Kind.SMOKE;
    }
    if (type == RecipeType.CAMPFIRE_COOKING) {
      return Kind.CAMPFIRE;
    }
    if (type == RecipeType.STONECUTTING) {
      return Kind.STONECUT;
    }
    if (type == RecipeType.SMITHING) {
      return Kind.SMITH;
    }
    return Kind.CRAFT;
  }

  private static Recipe convert(RecipeDisplay display, Kind kind, ContextMap context) {
    int[] output = first(display.result(), context);
    if (output == null) {
      return null;
    }
    int station = stationOf(display.craftingStation(), context);
    int resultHandle = output[0];
    int resultCount = output[1];
    if (display instanceof ShapedCraftingRecipeDisplay shaped) {
      return grid(
          Kind.CRAFT,
          shaped.width(),
          shaped.height(),
          shaped.ingredients(),
          resultHandle,
          resultCount,
          station,
          "",
          context);
    }
    if (display instanceof ShapelessCraftingRecipeDisplay shapeless) {
      int size = shapeless.ingredients().size();
      int columns = size <= 1 ? 1 : size <= 4 ? 2 : 3;
      int rows = (size + columns - 1) / columns;
      return grid(
          Kind.CRAFT,
          columns,
          rows,
          shapeless.ingredients(),
          resultHandle,
          resultCount,
          station,
          "Shapeless",
          context);
    }
    if (display instanceof FurnaceRecipeDisplay furnace) {
      String note = furnace.duration() / TICKS_PER_SECOND + "s";
      if (furnace.experience() > 0.0F) {
        note += "  " + trim(furnace.experience()) + " xp";
      }
      return grid(
          kind == Kind.CRAFT ? Kind.SMELT : kind,
          1,
          1,
          List.of(furnace.ingredient()),
          resultHandle,
          resultCount,
          station,
          note,
          context);
    }
    if (display instanceof StonecutterRecipeDisplay cutter) {
      return grid(
          Kind.STONECUT,
          1,
          1,
          List.of(cutter.input()),
          resultHandle,
          resultCount,
          station,
          "",
          context);
    }
    if (display instanceof SmithingRecipeDisplay smithing) {
      return grid(
          Kind.SMITH,
          3,
          1,
          List.of(smithing.template(), smithing.base(), smithing.addition()),
          resultHandle,
          resultCount,
          station,
          "",
          context);
    }
    return null;
  }

  private static Recipe grid(
      Kind kind,
      int columns,
      int rows,
      List<SlotDisplay> slots,
      int output,
      int outputCount,
      int station,
      String note,
      ContextMap context) {
    int[][] inputs = new int[slots.size()][];
    int[] counts = new int[slots.size()];
    for (int i = 0; i < inputs.length; i++) {
      inputs[i] = options(slots.get(i), context);
      counts[i] = 1;
    }
    return new Recipe(kind, columns, rows, inputs, counts, output, outputCount, station, note);
  }

  private static int[] options(SlotDisplay slot, ContextMap context) {
    List<ItemStack> stacks = slot.resolveForStacks(context);
    if (stacks.isEmpty()) {
      return NO_OPTIONS;
    }
    Set<Integer> unique = new LinkedHashSet<>(stacks.size());
    for (ItemStack stack : stacks) {
      if (!stack.isEmpty() && unique.size() < OPTION_LIMIT) {
        unique.add(Stacks.ofItem(stack));
      }
    }
    int[] out = new int[unique.size()];
    int index = 0;
    for (int handle : unique) {
      out[index++] = handle;
    }
    return out;
  }

  private static int[] first(SlotDisplay slot, ContextMap context) {
    ItemStack stack = slot.resolveForFirstStack(context);
    if (stack.isEmpty()) {
      return null;
    }
    return new int[] {Stacks.of(stack), stack.getCount()};
  }

  private static int stationOf(SlotDisplay slot, ContextMap context) {
    ItemStack stack = slot.resolveForFirstStack(context);
    return stack.isEmpty() ? Handles.NONE : Stacks.ofItem(stack);
  }

  private static String trim(float value) {
    return value == (int) value
        ? String.valueOf((int) value)
        : String.format(java.util.Locale.ROOT, "%.1f", value);
  }
}
