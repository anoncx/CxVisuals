package me.cxiy.cxvisuals.mc.mixin;

import me.cxiy.cxvisuals.mc.modules.animations.Animations;
import me.cxiy.cxvisuals.mc.modules.idlecamera.CameraHook;
import me.cxiy.cxvisuals.mc.modules.zoom.Zoom;
import me.cxiy.cxvisuals.modules.animations.Teleports;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
  @Shadow private Vec3 position;
  @Shadow private float xRot;
  @Shadow private float yRot;
  @Shadow private boolean detached;
  @Shadow private int matrixPropertiesDirty;
  @Shadow private @Nullable Entity entity;
  @Shadow @Final private Quaternionf rotation;
  @Shadow @Final private Vector3f up;
  @Shadow @Final private Vector3f left;

  @Shadow
  protected abstract void setRotation(float yRot, float xRot);

  @Shadow
  protected abstract void setPosition(double x, double y, double z);

  @Inject(method = "alignWithEntity", at = @At("TAIL"))
  private void cxvisuals$applyCinematic(float partialTicks, CallbackInfo ci) {
    if (!CameraHook.frame(this.entity, partialTicks, this.position, this.yRot, this.xRot)) {
      Teleports teleport = Animations.teleport();
      if (teleport.gliding() && !this.detached) {
        this.setPosition(
            this.position.x + teleport.offsetX(),
            this.position.y + teleport.offsetY(),
            this.position.z + teleport.offsetZ());
        this.setRotation(this.yRot + teleport.yawOffset(), this.xRot + teleport.pitchOffset());
      }
      return;
    }
    this.setPosition(CameraHook.x, CameraHook.y, CameraHook.z);
    this.setRotation(CameraHook.yaw, CameraHook.pitch);
    if (CameraHook.roll != 0.0F) {
      this.rotation.rotateZ(CameraHook.roll * ((float) Math.PI / 180.0F));
      this.up.set(0.0F, 1.0F, 0.0F).rotate(this.rotation);
      this.left.set(-1.0F, 0.0F, 0.0F).rotate(this.rotation);
      this.matrixPropertiesDirty |= 3;
    }
    if (CameraHook.detach) {
      this.detached = true;
    }
  }

  @Inject(method = "calculateFov", at = @At("RETURN"), cancellable = true)
  private void cxvisuals$fov(float partialTicks, CallbackInfoReturnable<Float> cir) {
    float vanilla = cir.getReturnValueF();
    float blended = Zoom.fov(CameraHook.fov(vanilla));
    if (blended != vanilla) {
      cir.setReturnValue(blended);
    }
  }
}
