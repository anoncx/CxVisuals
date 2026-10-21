package me.cxiy.cxvisuals.mc.modules.inventory;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

final class ContainerTooltip implements ClientTooltipComponent {
  private static final int CELL = 18;
  private static final int GAP = 4;
  private static final int WELL = 0x55000000;
  private static final int TINT_ALPHA = 0x48;

  private final Previews previews;
  private final int slots;
  private final int columns;
  private final int tint;

  ContainerTooltip(Previews previews, int slots, int columns, int tint) {
    this.columns = columns;
    this.previews = previews;
    this.slots = slots;
    this.tint = tint;
  }

  @Override
  public int getWidth(Font font) {
    return columns * CELL;
  }

  @Override
  public int getHeight(Font font) {
    return (slots + columns - 1) / columns * CELL + GAP;
  }

  @Override
  public void extractImage(
      Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
    int wash = (TINT_ALPHA << 24) | (tint & 0x00FFFFFF);
    for (int i = 0; i < slots; i++) {
      int sx = x + (i % columns) * CELL;
      int sy = y + GAP + (i / columns) * CELL;
      graphics.fill(sx + 1, sy + 1, sx + CELL - 1, sy + CELL - 1, WELL);
      if (tint != 0) {
        graphics.fill(sx + 1, sy + 1, sx + CELL - 1, sy + CELL - 1, wash);
      }
      ItemStack stack = previews.at(0, i);
      if (!stack.isEmpty()) {
        graphics.item(stack, sx + 1, sy + 1);
        graphics.itemDecorations(font, stack, sx + 1, sy + 1);
      }
    }
  }
}
