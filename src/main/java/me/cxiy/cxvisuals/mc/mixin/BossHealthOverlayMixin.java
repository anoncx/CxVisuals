package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.Map;
import java.util.UUID;
import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.BossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {
  private static final String BAR =
      "Lnet/minecraft/client/gui/components/BossHealthOverlay;extractBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/world/BossEvent;)V";
  private static final int FIRST = 12;
  private static final int STEP = 19;

  @Shadow @Final private Map<UUID, LerpingBossEvent> events;

  @Shadow @Final private Minecraft minecraft;

  @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$begin(GuiGraphicsExtractor graphics, CallbackInfo ci) {
    int total = events.size();
    if (total == 0) {
      return;
    }
    int limit = graphics.guiHeight() / 3;
    int bars = 0;
    for (int y = FIRST; bars < total; ) {
      bars++;
      y += STEP;
      if (y >= limit) {
        break;
      }
    }
    int widest = 182;
    int counted = 0;
    for (LerpingBossEvent event : events.values()) {
      if (counted++ == bars) {
        break;
      }
      widest = Math.max(widest, minecraft.font.width(event.getName()));
    }
    float pad = Hud.CARD_PAD;
    Hud.bossBars.natural(
        graphics.guiWidth() * 0.5F - widest * 0.5F - pad,
        0.0F,
        widest + pad * 2.0F,
        (bars - 1) * STEP + 17.0F + pad);
    if (Hud.begin(Hud.bossBars, graphics)) {
      ci.cancel();
      return;
    }
    Hud.bossCard(graphics);
  }

  @Inject(method = "extractRenderState", at = @At("RETURN"))
  private void cxvisuals$end(GuiGraphicsExtractor graphics, CallbackInfo ci) {
    Hud.end(Hud.bossBars, graphics);
  }

  @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = BAR))
  private void cxvisuals$bar(
      BossHealthOverlay overlay,
      GuiGraphicsExtractor graphics,
      int x,
      int y,
      BossEvent event,
      Operation<Void> original) {
    if (!Hud.bossBar(graphics, x, y, event)) {
      original.call(overlay, graphics, x, y, event);
    }
  }
}
