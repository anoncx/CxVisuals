package me.cxiy.cxvisuals.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cxiy.cxvisuals.mc.modules.nametags.Nametags;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SubmitNodeCollection.class)
public abstract class NameTagMixin {
  @Inject(method = "submitNameTag", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$nametag(
      PoseStack poseStack,
      Vec3 attachment,
      int offset,
      Component name,
      boolean seeThrough,
      int lightCoords,
      CameraRenderState camera,
      CallbackInfo ci) {
    if (attachment != null
        && Nametags.capture(poseStack, attachment, offset, name, seeThrough, camera)) {
      ci.cancel();
    }
  }
}
