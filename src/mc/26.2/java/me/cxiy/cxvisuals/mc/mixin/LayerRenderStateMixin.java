package me.cxiy.cxvisuals.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.mc.modules.swings.Swings;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public abstract class LayerRenderStateMixin {
  @Shadow @Final private List<BakedQuad> quads;
  @Shadow private Supplier<Vector3fc[]> extents;

  @Inject(
      method = "submit",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/item/ItemStackRenderState$LayerRenderState;applyTransform(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;)V",
              shift = At.Shift.AFTER))
  private void cxvisuals$blade(
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      int lightCoords,
      int overlayCoords,
      int outlineColor,
      CallbackInfo ci) {
    Swings.layer(poseStack.last().pose(), quads, extents);
  }
}
