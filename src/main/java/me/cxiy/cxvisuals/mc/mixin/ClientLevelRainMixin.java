package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cxiy.cxvisuals.mc.modules.environment.Environment;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public abstract class ClientLevelRainMixin {
  private static final String SPLASH =
      "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V";

  @WrapOperation(method = "tickWeatherEffects", at = @At(value = "INVOKE", target = SPLASH))
  private void cxvisuals$noSplashDots(
      ClientLevel level,
      ParticleOptions options,
      double x,
      double y,
      double z,
      double dx,
      double dy,
      double dz,
      Operation<Void> original) {
    if (options != ParticleTypes.RAIN || !Environment.replacesRain()) {
      original.call(level, options, x, y, z, dx, dy, dz);
    }
  }
}
