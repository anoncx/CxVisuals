package me.cxiy.cxvisuals.mc.gui;

import net.minecraft.client.gui.render.TextureSetup;

public interface StagePreviews {
  Frame request(
      int width, int height, float yaw, float pitch, float clock, float radius, boolean figure);

  record Frame(
      TextureSetup back,
      TextureSetup front,
      float figureX,
      float figureY,
      float blockHeight,
      float turn,
      float stride) {}
}
