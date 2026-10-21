package me.cxiy.cxvisuals.mc.modules.shaders;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;

public final class Shaders {
  private static final Set<EntityType<?>> PICKED =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private static ShadersModule module;

  private Shaders() {}

  public static ShadersModule create(McKeyBinder keys) {
    module = new ShadersModule(keys);
    List<EntityType<?>> types = new ArrayList<>();
    for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
      if (type != EntityTypes.PLAYER) {
        types.add(type);
      }
    }
    types.sort(Comparator.comparing(Shaders::label));
    List<String> ids = new ArrayList<>(types.size());
    List<String> labels = new ArrayList<>(types.size());
    for (EntityType<?> type : types) {
      ids.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
      labels.add(label(type));
    }
    module.others.options(ids, labels);
    module.others.onChange(Shaders::repick);
    module.trailOnly.options(ids, labels);
    module.trailOnly.onChange(EntityTrails::repick);
    CirclePreview.install();
    TrailPreview.install();
    return module;
  }

  static boolean picked(EntityType<?> type) {
    return PICKED.contains(type);
  }

  private static void repick() {
    PICKED.clear();
    for (String id : module.others.get()) {
      Identifier key = Identifier.tryParse(id);
      if (key != null) {
        BuiltInRegistries.ENTITY_TYPE.getOptional(key).ifPresent(PICKED::add);
      }
    }
  }

  public static String label(EntityType<?> type) {
    String path = BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
    StringBuilder out = new StringBuilder(path.length());
    boolean start = true;
    for (int i = 0; i < path.length(); i++) {
      char at = path.charAt(i);
      if (at == '_') {
        out.append(' ');
        start = true;
      } else {
        out.append(start ? Character.toUpperCase(at) : at);
        start = false;
      }
    }
    return out.toString();
  }

  public static ShadersModule module() {
    return module;
  }
}
