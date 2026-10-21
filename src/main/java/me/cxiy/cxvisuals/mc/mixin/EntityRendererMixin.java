package me.cxiy.cxvisuals.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cxiy.cxvisuals.mc.modules.deaths.DeathEffects;
import me.cxiy.cxvisuals.mc.modules.nametags.Nametags;
import me.cxiy.cxvisuals.mc.modules.shaders.EntityTrails;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
  private static final String DISPLAY =
      "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
          + "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V";

  @Inject(
      method =
          "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;",
      at = @At("RETURN"))
  private void cxvisuals$corpse(
      Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> cir) {
    DeathEffects.extracted(entity, cir.getReturnValue());
    EntityTrails.extracted(entity, cir.getReturnValue());
  }

  @Inject(method = DISPLAY, at = @At("HEAD"))
  private void cxvisuals$nameSubject(
      EntityRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      CameraRenderState camera,
      int offset,
      CallbackInfo ci) {
    Nametags.subject(state instanceof AvatarRenderState);
  }

  @Inject(method = DISPLAY, at = @At("RETURN"))
  private void cxvisuals$nameSubjectDone(
      EntityRenderState state,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      CameraRenderState camera,
      int offset,
      CallbackInfo ci) {
    Nametags.subject(false);
  }
}
