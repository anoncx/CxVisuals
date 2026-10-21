package me.cxiy.cxvisuals.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cxiy.cxvisuals.mc.modules.animations.BlockPopEffects;
import me.cxiy.cxvisuals.mc.modules.deaths.DeathEffects;
import me.cxiy.cxvisuals.mc.modules.shaders.EntityShaders;
import me.cxiy.cxvisuals.mc.modules.shaders.OutlineShaders;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
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

  @Inject(method = "submitEntities", at = @At("TAIL"))
  private void cxvisuals$entityMask(
      PoseStack poseStack,
      LevelRenderState levelRenderState,
      SubmitNodeCollector output,
      CallbackInfo ci) {
    EntityShaders.capture(levelRenderState, entityRenderDispatcher, poseStack);
    DeathEffects.submit(levelRenderState, entityRenderDispatcher, poseStack);
    BlockPopEffects.submit(poseStack, output, levelRenderState);
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
