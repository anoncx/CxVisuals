package me.cxiy.cxvisuals.mc.modules.inventory;

import java.util.ArrayList;
import java.util.List;
import me.cxiy.cxvisuals.modules.inventory.Handles;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class Stacks {
  public static final String SKYBLOCK_PREFIX = "sb:";
  private static final String ID_TAG = "id";

  private static final Handles HANDLES = new Handles();
  private static final List<ItemStack> TABLE = new ArrayList<>(4096);

  private Stacks() {}

  public static Handles handles() {
    return HANDLES;
  }

  public static ItemStack get(int handle) {
    return handle >= 0 && handle < TABLE.size() ? TABLE.get(handle) : ItemStack.EMPTY;
  }

  public static String skyblockId(ItemStack stack) {
    CustomData data = stack.get(DataComponents.CUSTOM_DATA);
    if (data == null || data.isEmpty()) {
      return null;
    }
    CompoundTag tag = data.copyTag();
    return tag.getString(ID_TAG).filter(id -> !id.isEmpty()).orElse(null);
  }

  public static String keyOf(ItemStack stack) {
    String custom = skyblockId(stack);
    if (custom != null) {
      return SKYBLOCK_PREFIX + custom;
    }
    String base = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    return stack.getComponentsPatch().isEmpty()
        ? base
        : base + "@" + Integer.toHexString(ItemStack.hashItemAndComponents(stack));
  }

  public static boolean plain(ItemStack stack) {
    return skyblockId(stack) == null;
  }

  public static int register(String key, ItemStack stack, String space, String tip) {
    int known = HANDLES.find(key);
    if (known != Handles.NONE) {
      return known;
    }
    ItemStack shown = stack.copy();
    shown.setCount(1);
    int handle = HANDLES.intern(key, shown.getHoverName().getString(), space, tip);
    TABLE.add(shown);
    return handle;
  }

  public static int of(ItemStack stack) {
    if (stack.isEmpty()) {
      return Handles.NONE;
    }
    String key = keyOf(stack);
    int handle = HANDLES.find(key);
    if (handle != Handles.NONE) {
      return handle;
    }
    String base = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    handle = HANDLES.find(base);
    if (handle != Handles.NONE && plain(stack)) {
      return handle;
    }
    String space =
        key.startsWith(SKYBLOCK_PREFIX)
            ? "skyblock"
            : BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace();
    return register(key, stack, space, "");
  }

  public static int ofItem(ItemStack stack) {
    String base = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    int handle = HANDLES.find(base);
    return handle != Handles.NONE
        ? handle
        : register(
            base,
            new ItemStack(stack.getItem()),
            BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace(),
            "");
  }
}
