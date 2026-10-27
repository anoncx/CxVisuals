package me.cxiy.cxvisuals.mc.mixin;

import me.cxiy.cxvisuals.mc.modules.animations.BlockPopEffects;
import me.cxiy.cxvisuals.mc.modules.map.Maps;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {
  @Inject(
      method =
          "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
      at = @At("HEAD"))
  private void cxvisuals$blockPop(
      BlockPos pos, BlockState state, int flags, int limit, CallbackInfoReturnable<Boolean> cir) {
    BlockPopEffects.beforeSetBlock((Level) (Object) this, pos, state);
    Maps.changed((Level) (Object) this, pos);
  }
}
