package me.cxiy.cxvisuals.mc.modules.donutsmp;

import java.util.Locale;
import me.cxiy.cxvisuals.modules.donutsmp.auction.AuctionText;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

final class Plain {
  private Plain() {}

  static boolean renamed(ItemStack stack) {
    if (stack.get(DataComponents.CUSTOM_NAME) == null) {
      return false;
    }
    return !name(stack.getHoverName().getString())
        .equals(name(new ItemStack(stack.getItem()).getHoverName().getString()));
  }

  private static String name(String raw) {
    return AuctionText.plain(raw).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
  }
}
