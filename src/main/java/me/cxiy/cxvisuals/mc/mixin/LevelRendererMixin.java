package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import me.cxiy.cxvisuals.mc.modules.animations.BlockPopEffects;
import me.cxiy.cxvisuals.mc.modules.deaths.DeathEffects;
import me.cxiy.cxvisuals.mc.modules.shaders.EntityShaders;
import me.cxiy.cxvisuals.mc.modules.shaders.OutlineShaders;
import me.cxiy.cxvisuals.mc.modules.swings.Swings;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
  @Shadow @Final private EntityRenderDispatcher entityRenderDispatcher;

  @Inject(method = "submitEntities", at = @At("HEAD"))
  private void cxvisuals$hideCorpses(
      PoseStack poseStack,
      LevelRenderState levelRenderState,
      SubmitNodeCollector output,
      CallbackInfo ci) {
    DeathEffects.hide(levelRenderState);
  }

  @Inject(method = "submitEntities", at = @At("HEAD"))
  private void cxvisuals$swingsBegin(
      PoseStack poseStack,
      LevelRenderState levelRenderState,
      SubmitNodeCollector output,
      CallbackInfo ci) {
    Swings.beginWorld(levelRenderState.cameraRenderState.pos);
  }

  @Inject(method = "submitEntities", at = @At("TAIL"))
  private void cxvisuals$swingsEnd(
      PoseStack poseStack,
      LevelRenderState levelRenderState,
      SubmitNodeCollector output,
      CallbackInfo ci) {
    Swings.endWorld();
  }

  @Inject(method = "submitEntities", at = @At("TAIL"))
  private void cxvisuals$entityMask(
      PoseStack poseStack,
      LevelRenderState levelRenderState,
      SubmitNodeCollector output,
      CallbackInfo ci) {
    EntityShaders.capture(levelRenderState, entityRenderDispatcher, poseStack);
    DeathEffects.submit(levelRenderState, entityRenderDispatcher, poseStack, output);
    BlockPopEffects.submit(poseStack, output, levelRenderState);
  }

  @Inject(method = "submitBlockEntities", at = @At("HEAD"))
  private void cxvisuals$poppingEntities(
      PoseStack poseStack,
      LevelRenderState levelRenderState,
      SubmitNodeCollector output,
      CallbackInfo ci) {
    BlockPopEffects.submitEntities(poseStack, output, levelRenderState);
  }

  @WrapWithCondition(
      method = "submitBlockEntities",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/blockentity/BlockEntityRenderDispatcher;submit(Lnet/minecraft/client/renderer/blockentity/state/BlockEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
  private boolean cxvisuals$skipPoppingEntity(
      BlockEntityRenderDispatcher dispatcher,
      BlockEntityRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      CameraRenderState camera) {
    return !BlockPopEffects.ownsEntity(state);
  }

  @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$shadedOutline(
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      LevelRenderState levelRenderState,
      CallbackInfo ci) {
    if (OutlineShaders.replaces()) {
      ci.cancel();
    }
  }

  @Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$popOutline(
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      LevelRenderState levelRenderState,
      CallbackInfo ci) {
    if (BlockPopEffects.popOutline(poseStack, submitNodeCollector, levelRenderState)) {
      ci.cancel();
    }
  }
}
