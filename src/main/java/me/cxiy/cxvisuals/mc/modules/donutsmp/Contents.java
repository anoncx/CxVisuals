package me.cxiy.cxvisuals.mc.modules.donutsmp;

import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;

final class Contents {
  record Worth(double value, int stacks, int unpriced) {
    static final Worth NONE = new Worth(0.0, 0, 0);

    boolean any() {
      return stacks > 0;
    }

    boolean reliable() {
      return stacks > 0 && unpriced * 4 <= stacks && value > 0.0;
    }
  }

  private static final int DEPTH = 3;

  private Contents() {}

  static boolean filled(ItemStack stack) {
    ItemContainerContents box = stack.get(DataComponents.CONTAINER);
    if (box != null && box.nonEmptyItemCopyStream().findAny().isPresent()) {
      return true;
    }
    BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
    return bundle != null && !bundle.isEmpty();
  }

  static Worth of(ItemStack stack) {
    return of(stack, DEPTH);
  }

  static Worth of(ItemStack stack, int depth) {
    double value = 0.0;
    int stacks = 0;
    int unpriced = 0;
    ItemContainerContents box = stack.get(DataComponents.CONTAINER);
    if (box != null) {
      for (ItemStack inner : (Iterable<ItemStack>) box.nonEmptyItemCopyStream()::iterator) {
        Worth part = price(inner, depth);
        value += part.value;
        stacks += part.stacks;
        unpriced += part.unpriced;
      }
    }
    BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
    if (bundle != null) {
      List<ItemStackTemplate> items = bundle.items();
      for (ItemStackTemplate template : items) {
        Worth part = price(template.create(), depth);
        value += part.value;
        stacks += part.stacks;
        unpriced += part.unpriced;
      }
    }
    return new Worth(value, stacks, unpriced);
  }

  private static Worth price(ItemStack inner, int depth) {
    if (inner.isEmpty()) {
      return Worth.NONE;
    }
    double each = Prices.quote(inner, Math.max(0, depth - 1)).price();
    double own = each > 0.0 ? each * inner.getCount() : 0.0;
    return new Worth(own, 1, each > 0.0 ? 0 : 1);
  }
}
