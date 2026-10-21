package me.cxiy.cxvisuals.mc.mixin;

import me.cxiy.cxvisuals.mc.modules.deaths.DeathEffects;
import me.cxiy.cxvisuals.mc.modules.hands.Hands;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
  @Inject(method = "makePoofParticles", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$quietDeath(CallbackInfo ci) {
    if (DeathEffects.quiet((LivingEntity) (Object) this)) {
      ci.cancel();
    }
  }

  @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
  private void cxvisuals$swingSpeed(CallbackInfoReturnable<Integer> cir) {
    if ((Object) this == Minecraft.getInstance().player) {
      int vanilla = cir.getReturnValueI();
      int adjusted = Hands.swingDuration(vanilla);
      if (adjusted != vanilla) {
        cir.setReturnValue(adjusted);
      }
    }
  }
}
