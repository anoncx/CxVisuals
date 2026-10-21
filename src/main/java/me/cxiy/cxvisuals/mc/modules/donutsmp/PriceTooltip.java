package me.cxiy.cxvisuals.mc.modules.donutsmp;

import com.mojang.blaze3d.platform.Window;
import java.util.List;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

final class PriceTooltip implements ClientTooltipComponent {
  private static final int GAP = 4;
  private static final int PAD = 5;
  private static final int LINE = 10;
  private static final float RADIUS = 5.0F;
  private static final int BRIGHT = 0x80;
  private static final int WHITE = 0xFFFFFFFF;
  private static final McCanvas CANVAS = new McCanvas();

  private final List<Component> lines;
  private final Theme theme;

  PriceTooltip(List<Component> lines, Theme theme) {
    this.lines = lines;
    this.theme = (theme.paper() >> 8 & 0xFF) > BRIGHT ? Theme.DARK : theme;
  }

  @Override
  public int getWidth(Font font) {
    int widest = 0;
    for (Component line : lines) {
      widest = Math.max(widest, font.width(line));
    }
    return widest + PAD * 2;
  }

  @Override
  public int getHeight(Font font) {
    return GAP + PAD + lines.size() * LINE + PAD - 2;
  }

  @Override
  public void extractImage(
      Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
    Window window = Minecraft.getInstance().getWindow();
    int guiScale = Math.max(1, window.getGuiScale());
    float scale = McCanvas.interfaceScale(window.getHeight());
    float pointer = guiScale / scale;
    float left = x * pointer;
    float top = (y + GAP) * pointer;
    float wide = getWidth(font) * pointer;
    float tall = (getHeight(font) - GAP) * pointer;
    float radius = RADIUS * pointer;
    CANVAS.begin(graphics, scale, guiScale);
    if (theme.glass()) {
      CANVAS.glass(left, top, wide, tall, radius, theme.paper());
    } else {
      CANVAS.roundRect(left, top, wide, tall, radius, theme.paper());
    }
    CANVAS.rim(
        left, top, wide, tall, radius, 1.0F, Math.min(tall, radius * 1.4F), Depth.rimColor(theme));
    CANVAS.end();
    int textY = y + GAP + PAD;
    for (int i = 0; i < lines.size(); i++) {
      graphics.text(font, lines.get(i), x + PAD, textY + i * LINE, WHITE);
    }
  }
}
