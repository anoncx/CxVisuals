package me.cxiy.cxvisuals.mc.modules.donutsmp;

import com.mojang.blaze3d.platform.Window;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.Slot;

final class SlotPaint {
  private static final float SIZE = 18.0F;
  private static final float RADIUS = 4.0F;
  private static final McCanvas CANVAS = new McCanvas();

  private SlotPaint() {}

  static void draw(
      GuiGraphicsExtractor graphics, Slot slot, int color, float fill, float rim, float rimWidth) {
    Window window = Minecraft.getInstance().getWindow();
    int guiScale = Math.max(1, window.getGuiScale());
    float scale = McCanvas.interfaceScale(window.getHeight());
    float pointer = guiScale / scale;
    float size = SIZE * pointer;
    float x = (slot.x - 1.0F) * pointer;
    float y = (slot.y - 1.0F) * pointer;
    CANVAS.begin(graphics, scale, guiScale);
    CANVAS.roundRect(x, y, size, size, RADIUS * pointer, Theme.withAlpha(color, fill));
    CANVAS.roundRectBorder(
        x, y, size, size, RADIUS * pointer, rimWidth * pointer, Theme.withAlpha(color, rim));
    CANVAS.end();
  }
}
