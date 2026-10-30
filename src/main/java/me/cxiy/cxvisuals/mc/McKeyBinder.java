package me.cxiy.cxvisuals.mc;

import java.util.HashMap;
import java.util.Map;
import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.settings.KeyHandle;
import me.cxiy.cxvisuals.mc.compat.Keys;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

public final class McKeyBinder implements KeyBinder {
  private static final String PREFIX = "key." + CxVisualsClient.MOD_ID + ".";

  private final KeyMapping.Category category =
      KeyMapping.Category.register(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "main"));
  private final Map<String, KeyMapping> mappings = new HashMap<>();

  @Override
  public KeyHandle bind(String id, String title, int defaultKey) {
    KeyMapping mapping =
        KeyMappingHelper.registerKeyMapping(
            new KeyMapping(PREFIX + id, Keys.vanilla(defaultKey), category));
    mappings.put(id, mapping);
    return new McKeyHandle(mapping);
  }

  public void repair() {
    boolean changed = false;
    for (KeyMapping mapping : mappings.values()) {
      if (mapping.saveString().endsWith(".-1")) {
        mapping.setKey(InputConstants.UNKNOWN);
        changed = true;
      }
    }
    if (changed) {
      KeyMapping.resetMapping();
      Minecraft.getInstance().options.save();
    }
  }

  public KeyMapping mapping(String id) {
    return mappings.get(id);
  }
}
