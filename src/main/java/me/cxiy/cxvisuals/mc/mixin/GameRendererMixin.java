package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import me.cxiy.cxvisuals.mc.menu.MenuStage;
import me.cxiy.cxvisuals.mc.modules.animations.Animations;
import me.cxiy.cxvisuals.mc.modules.deaths.DeathEffects;
import me.cxiy.cxvisuals.mc.modules.environment.Environment;
import me.cxiy.cxvisuals.mc.modules.fullbright.Fullbright;
import me.cxiy.cxvisuals.mc.modules.idlecamera.IdleCamera;
import me.cxiy.cxvisuals.mc.modules.motionblur.MotionBlur;
import me.cxiy.cxvisuals.mc.modules.nametags.Nametags;
import me.cxiy.cxvisuals.mc.modules.shaders.EntityShaders;
import me.cxiy.cxvisuals.mc.modules.shaders.EntityTrails;
import me.cxiy.cxvisuals.mc.modules.shaders.JumpCircles;
import me.cxiy.cxvisuals.mc.modules.shaders.OutlineShaders;
import me.cxiy.cxvisuals.mc.modules.shaders.SkyShaders;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
  @Shadow @Final private RenderTarget mainRenderTarget;

  @Inject(method = "nightVisionScale", at = @At("HEAD"), cancellable = true)
  private static void cxvisuals$fullbright(
      LivingEntity entity, float partialTicks, CallbackInfoReturnable<Float> cir) {
    if (!entity.hasEffect(MobEffects.NIGHT_VISION)) {
      float scale = Fullbright.shaderNightVision();
      if (scale > 0.0F) {
        cir.setReturnValue(scale);
      }
    }
  }

  @Inject(method = "update", at = @At("HEAD"))
  private void cxvisuals$animate(DeltaTracker deltaTracker, CallbackInfo ci) {
    Animations.frame();
    MenuStage.frame();
  }

  @Inject(
      method = "render",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/renderer/fog/FogRenderer;endFrame()V"))
  private void cxvisuals$menuBackdrop(
      DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
    MenuStage.backdrop(mainRenderTarget);
  }

  @Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$hideHand(
      CameraRenderState cameraState,
      float deltaPartialTick,
      Matrix4fc modelViewMatrix,
      CallbackInfo ci) {
    if (IdleCamera.weight > 0.0F) {
      ci.cancel();
    }
  }

  @Inject(
      method = "renderLevel",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lcom/mojang/blaze3d/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/ProjectionType;)V",
              ordinal = 0))
  private void cxvisuals$levelLens(
      DeltaTracker deltaTracker, CallbackInfo ci, @Local Matrix4f projectionMatrix) {
    EntityShaders.projection(projectionMatrix);
  }

  @Inject(
      method = "renderLevel",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/client/renderer/Projection;setupPerspective(FFFFF)V"))
  private void cxvisuals$motionBlur(DeltaTracker deltaTracker, CallbackInfo ci) {
    SkyShaders.render((GameRenderer) (Object) this);
    OutlineShaders.render((GameRenderer) (Object) this);
    JumpCircles.render((GameRenderer) (Object) this);
    EntityTrails.render((GameRenderer) (Object) this);
    DeathEffects.render((GameRenderer) (Object) this);
    EntityShaders.world((GameRenderer) (Object) this);
    MotionBlur.apply((GameRenderer) (Object) this);
    Environment.render((GameRenderer) (Object) this);
    Nametags.render((GameRenderer) (Object) this);
  }

  @Inject(
      method = "renderLevel",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submit(ZZFLnet/minecraft/client/renderer/SubmitNodeCollector;Z)V"))
  private void cxvisuals$handShader(DeltaTracker deltaTracker, CallbackInfo ci) {
    EntityShaders.hand((GameRenderer) (Object) this);
  }

  @ModifyArg(
      method = "renderLevel",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/ScreenEffectRenderer;submit(ZZFLnet/minecraft/client/renderer/SubmitNodeCollector;Z)V"),
      index = 0)
  private boolean cxvisuals$noFirstPersonOverlays(boolean firstPerson) {
    return firstPerson && IdleCamera.weight <= 0.0F;
  }
}
