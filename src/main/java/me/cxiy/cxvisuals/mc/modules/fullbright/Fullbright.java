package me.cxiy.cxvisuals.mc.modules.fullbright;

import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.compat.IrisCompat;
import me.cxiy.cxvisuals.modules.fullbright.FullbrightModule;
import net.minecraft.client.renderer.state.LightmapRenderState;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class Fullbright {
  private static final Vector3f AMBIENT = new Vector3f();
  private static final Vector3f SKY = new Vector3f();
  private static final Vector3f BLOCK = new Vector3f();
  private static final Vector3f NIGHT = new Vector3f();

  private static FullbrightModule module;

  private Fullbright() {}

  public static FullbrightModule create(McKeyBinder keys) {
    module = new FullbrightModule(keys);
    return module;
  }

  public static FullbrightModule module() {
    return module;
  }

  public static float shaderNightVision() {
    if (module == null || !module.live() || !IrisCompat.handInScene()) {
      return 0.0F;
    }
    return switch (module.mode.get()) {
      case LUMINANCE -> module.floor();
      case GAMMA -> 1.0F;
      case NIGHT_VISION -> module.nightVision();
    };
  }

  public static void apply(LightmapRenderState state) {
    if (module == null || !module.live() || !state.needsUpdate) {
      return;
    }
    float red = module.tintRed();
    float green = module.tintGreen();
    float blue = module.tintBlue();
    boolean tinted = module.tinted();
    if (tinted) {
      state.blockLightTint = scaled(BLOCK, state.blockLightTint, red, green, blue);
      state.skyLightColor = scaled(SKY, state.skyLightColor, red, green, blue);
      state.ambientColor = scaled(AMBIENT, state.ambientColor, red, green, blue);
      state.nightVisionColor = scaled(NIGHT, state.nightVisionColor, red, green, blue);
    }
    switch (module.mode.get()) {
      case LUMINANCE -> {
        float floor = module.floor();
        state.ambientColor =
            floored(AMBIENT, state.ambientColor, floor * red, floor * green, floor * blue);
      }
      case GAMMA -> {
        state.ambientColor =
            floored(
                AMBIENT,
                state.ambientColor,
                FullbrightModule.DARKEST * red,
                FullbrightModule.DARKEST * green,
                FullbrightModule.DARKEST * blue);
        state.brightness = Math.max(state.brightness, module.gammaBoost());
      }
      case NIGHT_VISION ->
          state.nightVisionEffectIntensity =
              Math.max(state.nightVisionEffectIntensity, module.nightVision());
    }
  }

  private static Vector3fc scaled(
      Vector3f out, Vector3fc from, float red, float green, float blue) {
    return out.set(from.x() * red, from.y() * green, from.z() * blue);
  }

  private static Vector3fc floored(
      Vector3f out, Vector3fc from, float red, float green, float blue) {
    return out.set(Math.max(from.x(), red), Math.max(from.y(), green), Math.max(from.z(), blue));
  }
}
