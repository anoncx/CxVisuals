package me.cxiy.cxvisuals.mc.mixin.sodium;

import me.cxiy.cxvisuals.mc.modules.animations.BlockPopEffects;
import net.caffeinemc.mods.sodium.client.world.LevelSlice;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelSlice.class)
public abstract class LevelSliceMixin {
  @Inject(
      method =
          "getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
      at = @At("HEAD"),
      cancellable = true)
  private void cxvisuals$hidePopping(BlockPos pos, CallbackInfoReturnable<BlockState> cir) {
    if (BlockPopEffects.hidden(pos.asLong())) {
      cir.setReturnValue(Blocks.AIR.defaultBlockState());
    }
  }

  @Inject(
      method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;",
      at = @At("HEAD"),
      cancellable = true)
  private void cxvisuals$hidePoppingCoords(
      int x, int y, int z, CallbackInfoReturnable<BlockState> cir) {
    if (BlockPopEffects.hidden(BlockPos.asLong(x, y, z))) {
      cir.setReturnValue(Blocks.AIR.defaultBlockState());
    }
  }
}
