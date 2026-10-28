package me.cxiy.cxvisuals.mc.compat;

import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;

public final class Tooltips {
  private Tooltips() {}

  public static void show(
      GuiGraphicsExtractor graphics,
      Font font,
      List<ClientTooltipComponent> lines,
      int x,
      int y,
      ClientTooltipPositioner positioner,
      Identifier style) {
    graphics.tooltip(font, lines, x, y, positioner, style);
  }
}
