package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import java.util.List;
import me.cxiy.cxvisuals.mc.gui.GuiTilt;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
public abstract class GuiRenderStateMixin implements GuiTilt.Strata {
  private static final String WALK =
      "Lnet/minecraft/client/renderer/state/gui/GuiRenderState;traverse(Lnet/minecraft/client/renderer/state/gui/GuiRenderState$Node;Ljava/util/function/Consumer;)V";

  @Shadow @Final private List<?> strata;

  @Override
  public int cxvisuals$strata() {
    return strata.size();
  }

  @Inject(
      method =
          "traverse(Ljava/util/function/Consumer;Lnet/minecraft/client/renderer/state/gui/GuiRenderState$TraverseRange;)V",
      at = @At(value = "INVOKE", target = WALK))
  private void cxvisuals$stratum(CallbackInfo ci, @Local(ordinal = 2) int index) {
    GuiTilt.stratum(index);
  }
}
