package me.cxiy.cxvisuals.mc.mixin;

import me.cxiy.cxvisuals.mc.modules.environment.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import net.minecraft.client.renderer.state.level.WeatherRenderState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
  @Inject(method = "extractRenderState", at = @At("RETURN"))
  private void cxvisuals$customRain(
      ClientLevel level,
      float partialTick,
      Vec3 cameraPos,
      WeatherRenderState state,
      CallbackInfo ci) {
    if (Environment.replacesRain()) {
      state.rainColumns.clear();
    }
  }
}
