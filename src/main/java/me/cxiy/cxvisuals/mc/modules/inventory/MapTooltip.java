package me.cxiy.cxvisuals.mc.modules.inventory;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.state.MapRenderState;
import org.joml.Matrix3x2fStack;

final class MapTooltip implements ClientTooltipComponent {
  private static final int SIZE = 128;
  private static final int GAP = 4;

  private final MapRenderState state;

  MapTooltip(MapRenderState state) {
    this.state = state;
  }

  @Override
  public int getWidth(Font font) {
    return SIZE;
  }

  @Override
  public int getHeight(Font font) {
    return SIZE + GAP;
  }

  @Override
  public void extractImage(
      Font font, int x, int y, int width, int height, GuiGraphicsExtractor graphics) {
    Matrix3x2fStack pose = graphics.pose();
    pose.pushMatrix();
    pose.translate(x, y + GAP);
    graphics.map(state);
    pose.popMatrix();
  }
}
