package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import me.cxiy.cxvisuals.mc.modules.hands.Hands;
import me.cxiy.cxvisuals.mc.modules.shaders.EntityShaders;
import me.cxiy.cxvisuals.mc.modules.swings.Swings;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
  private static HumanoidArm cxvisuals$arm(AbstractClientPlayer player, InteractionHand hand) {
    return hand == InteractionHand.MAIN_HAND
        ? player.getMainArm()
        : player.getMainArm().getOpposite();
  }

  @WrapMethod(method = "submitArmWithItem")
  private void cxvisuals$handMask(
      AbstractClientPlayer player,
      float frameInterp,
      float xRot,
      InteractionHand hand,
      float attack,
      ItemStack itemStack,
      float inverseArmHeight,
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      int lightCoords,
      Operation<Void> original) {
    original.call(
        player,
        frameInterp,
        xRot,
        hand,
        attack,
        itemStack,
        inverseArmHeight,
        poseStack,
        EntityShaders.handCollector(submitNodeCollector),
        lightCoords);
  }

  @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
  private float cxvisuals$equipDip(float inverseArmHeight) {
    return Hands.equipHeight(inverseArmHeight);
  }

  @Inject(
      method = "submitArmWithItem",
      at =
          @At(
              value = "INVOKE",
              target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V",
              shift = At.Shift.AFTER,
              ordinal = 0))
  private void cxvisuals$handPosition(
      AbstractClientPlayer player,
      float frameInterp,
      float xRot,
      InteractionHand hand,
      float attack,
      ItemStack itemStack,
      float inverseArmHeight,
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      int lightCoords,
      CallbackInfo ci) {
    Hands.position(poseStack, hand, cxvisuals$arm(player, hand));
  }

  @Inject(
      method = "submitArmWithItem",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V"))
  private void cxvisuals$heldItem(
      AbstractClientPlayer player,
      float frameInterp,
      float xRot,
      InteractionHand hand,
      float attack,
      ItemStack itemStack,
      float inverseArmHeight,
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      int lightCoords,
      CallbackInfo ci) {
    Hands.item(poseStack, hand, cxvisuals$arm(player, hand));
  }

  @Inject(method = "submitHandsWithItems", at = @At("HEAD"))
  private void cxvisuals$swingsBegin(
      float frameInterp,
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      LocalPlayer player,
      int lightCoords,
      CallbackInfo ci) {
    Swings.beginHand();
  }

  @Inject(method = "submitHandsWithItems", at = @At("RETURN"))
  private void cxvisuals$swingsEnd(
      float frameInterp,
      PoseStack poseStack,
      SubmitNodeCollector submitNodeCollector,
      LocalPlayer player,
      int lightCoords,
      CallbackInfo ci) {
    Swings.endHand();
  }

  @Inject(method = "swingArm", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$swing(
      float attack, PoseStack poseStack, int invert, HumanoidArm arm, CallbackInfo ci) {
    if (Hands.swing(poseStack, attack, invert)) {
      ci.cancel();
    }
  }
}
