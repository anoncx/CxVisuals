package me.cxiy.cxvisuals.mc.mixin;

import me.cxiy.cxvisuals.mc.modules.animations.BlockPopEffects;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
public abstract class MultiPlayerGameModeMixin {
  @Inject(method = "useItemOn", at = @At("HEAD"))
  private void cxvisuals$ownAction(
      LocalPlayer player,
      InteractionHand hand,
      BlockHitResult hit,
      CallbackInfoReturnable<InteractionResult> cir) {
    BlockPopEffects.selfAction(true);
  }

  @Inject(method = "useItemOn", at = @At("RETURN"))
  private void cxvisuals$ownActionEnd(
      LocalPlayer player,
      InteractionHand hand,
      BlockHitResult hit,
      CallbackInfoReturnable<InteractionResult> cir) {
    BlockPopEffects.selfAction(false);
  }

  @Inject(method = "destroyBlock", at = @At("HEAD"))
  private void cxvisuals$ownBreak(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
    BlockPopEffects.selfAction(true);
  }

  @Inject(method = "destroyBlock", at = @At("RETURN"))
  private void cxvisuals$ownBreakEnd(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
    BlockPopEffects.selfAction(false);
  }

  @Inject(method = "startDestroyBlock", at = @At("HEAD"))
  private void cxvisuals$miningStart(
      BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
    BlockPopEffects.mining(pos);
  }

  @Inject(method = "continueDestroyBlock", at = @At("HEAD"))
  private void cxvisuals$miningContinue(
      BlockPos pos, Direction face, CallbackInfoReturnable<Boolean> cir) {
    BlockPopEffects.mining(pos);
  }

  @Inject(method = "stopDestroyBlock", at = @At("HEAD"))
  private void cxvisuals$miningStop(CallbackInfo ci) {
    BlockPopEffects.miningEnded();
  }
}
