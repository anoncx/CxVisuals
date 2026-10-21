package me.cxiy.cxvisuals.mc.gui;

import net.minecraft.client.gui.render.TextureSetup;

public interface ModelPreviews {
  TextureSetup request(int handle, int width, int height, float yaw, boolean live, int audition);
}
