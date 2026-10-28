package me.cxiy.cxvisuals.mc;

import com.mojang.blaze3d.platform.InputConstants;
import me.cxiy.cxvisuals.core.settings.KeyHandle;
import me.cxiy.cxvisuals.mc.compat.Keys;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

final class McKeyHandle implements KeyHandle {
  private final KeyMapping mapping;

  McKeyHandle(KeyMapping mapping) {
    this.mapping = mapping;
  }

  @Override
  public String label() {
    return mapping.getTranslatedKeyMessage().getString();
  }

  @Override
  public void rebind(int keyCode, int scanCode) {
    mapping.setKey(InputConstants.getKey(Keys.event(keyCode, scanCode, 0)));
    KeyMapping.resetMapping();
    Minecraft.getInstance().options.save();
  }
}
