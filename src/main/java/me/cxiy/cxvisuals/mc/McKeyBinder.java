package me.cxiy.cxvisuals.mc;

import java.util.HashMap;
import java.util.Map;
import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.settings.KeyHandle;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class McKeyBinder implements KeyBinder {
  private static final String PREFIX = "key." + CxVisualsClient.MOD_ID + ".";

  private final KeyMapping.Category category =
      KeyMapping.Category.register(Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "main"));
  private final Map<String, KeyMapping> mappings = new HashMap<>();

  @Override
  public KeyHandle bind(String id, String title, int defaultKey) {
    KeyMapping mapping =
        KeyMappingHelper.registerKeyMapping(new KeyMapping(PREFIX + id, defaultKey, category));
    mappings.put(id, mapping);
    return new McKeyHandle(mapping);
  }

  public KeyMapping mapping(String id) {
    return mappings.get(id);
  }
}
