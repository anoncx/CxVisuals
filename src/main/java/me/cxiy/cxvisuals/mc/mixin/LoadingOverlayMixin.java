package me.cxiy.cxvisuals.mc.mixin;

import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.gui.Splash;
import me.cxiy.cxvisuals.mc.modules.texturemixer.TextureMixer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.server.packs.resources.ReloadInstance;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
  private static final long UNSET = -1L;
  private static final float INTRO_MILLIS = 500.0F;
  private static final float MILLIS = 1000.0F;

  @Shadow @Final private Minecraft minecraft;

  @Shadow @Final private ReloadInstance reload;

  @Shadow @Final private boolean fadeIn;

  @Shadow private long fadeOutStart;

  @Shadow private long fadeInStart;

  @Inject(method = "<init>", at = @At("RETURN"))
  private void cxvisuals$reset(CallbackInfo ci) {
    Splash.reset();
  }

  @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$splash(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
    long now = Util.getMillis();
    if (fadeIn && fadeInStart == UNSET) {
      fadeInStart = now;
    }
    float outro = fadeOutStart > UNSET ? (now - fadeOutStart) / MILLIS : -1.0F;
    float intro = fadeInStart > UNSET ? (now - fadeInStart) / INTRO_MILLIS : -1.0F;
    int state =
        Splash.draw(
            graphics,
            minecraft,
            reload.getActualProgress(),
            fadeIn,
            intro,
            outro,
            CxVisualsClient.version(),
            TextureMixer.quiet());
    if (state == Splash.UNAVAILABLE) {
      return;
    }
    if (state == Splash.FINISHED) {
      minecraft.gui.setOverlay(null);
    }
    ci.cancel();
  }
}
