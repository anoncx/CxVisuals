package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.Collection;
import me.cxiy.cxvisuals.mc.modules.animations.Animations;
import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import me.cxiy.cxvisuals.mc.modules.idlecamera.IdleCamera;
import me.cxiy.cxvisuals.mc.modules.map.Maps;
import me.cxiy.cxvisuals.mc.modules.social.FriendsLink;
import me.cxiy.cxvisuals.mc.modules.waypoints.Markers;
import me.cxiy.cxvisuals.mc.modules.zoom.Zoom;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.gui.Hud.class)
public abstract class HudMixin {
  private static final int BAR_COLOR = 0xFF000000;
  private static final String FILL = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V";
  private static final String SLOT =
      "Lnet/minecraft/client/gui/Hud;extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IILnet/minecraft/client/DeltaTracker;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;I)V";
  private static final String HEARTS =
      "Lnet/minecraft/client/gui/Hud;extractHearts(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/entity/player/Player;IIIIFIIIZ)V";
  private static final String LEVEL =
      "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V";
  private static final String SPRITE =
      "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V";

  @Shadow @Final private Minecraft minecraft;

  @Shadow private Component overlayMessageString;

  @Shadow private int overlayMessageTime;

  @Shadow private int titleTime;

  @Shadow private Component title;

  @Shadow private Component subtitle;

  @Shadow private int toolHighlightTimer;

  @Shadow private ItemStack lastToolHighlight;

  @Unique private boolean nameDeferred;

  @Shadow
  public abstract Font getFont();

  @Shadow
  private void extractSelectedItemName(GuiGraphicsExtractor graphics) {
    throw new AssertionError();
  }

  @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$cinematicHud(
      GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
    float amount = IdleCamera.letterboxAmount();
    if (amount > 0.0F) {
      int width = graphics.guiWidth();
      int height = graphics.guiHeight();
      float full = (height - width / IdleCamera.letterboxRatio()) * 0.5F;
      int bar = Math.round(Math.max(0.0F, full) * amount);
      if (bar > 0) {
        graphics.fill(0, 0, width, bar, BAR_COLOR);
        graphics.fill(0, height - bar, width, height, BAR_COLOR);
      }
    }
    int veil = Animations.veil();
    if (veil != 0) {
      graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), veil);
    }
    if (IdleCamera.hudHidden()) {
      ci.cancel();
      return;
    }
    Markers.frame();
    Maps.frame();
    Hud.frame(graphics);
  }

  @Inject(method = "extractRenderState", at = @At("TAIL"))
  private void cxvisuals$hudOverlay(
      GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
    Hud.settle(graphics, null);
    Hud.overlay(graphics);
    FriendsLink.overlay(graphics);
    Markers.overlay(graphics);
    Zoom.overlay(graphics);
  }

  @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$scoreboardBegin(
      GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
    Hud.scoreboardStart();
    if (Hud.begin(Hud.scoreboard, graphics)) {
      ci.cancel();
    }
  }

  @Inject(method = "displayScoreboardSidebar", at = @At("RETURN"))
  private void cxvisuals$scoreboardEnd(
      GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
    Hud.end(Hud.scoreboard, graphics);
  }

  @WrapOperation(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = FILL))
  private void cxvisuals$scoreboardFill(
      GuiGraphicsExtractor graphics,
      int x0,
      int y0,
      int x1,
      int y1,
      int color,
      Operation<Void> original) {
    if (!Hud.scoreboardFill(graphics, x0, y0, x1, y1)) {
      original.call(graphics, x0, y0, x1, y1, color);
    }
  }

  @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$effectsBegin(
      GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
    if (Hud.module() == null || !Hud.module().enabled.get() || minecraft.player == null) {
      return;
    }
    Collection<MobEffectInstance> active = minecraft.player.getActiveEffects();
    if (active.isEmpty()) {
      return;
    }
    int helpful = 0;
    int harmful = 0;
    for (MobEffectInstance instance : active) {
      if (!instance.showIcon()) {
        continue;
      }
      if (instance.getEffect().value().isBeneficial()) {
        helpful++;
      } else {
        harmful++;
      }
    }
    int columns = Math.max(helpful, harmful);
    if (columns == 0) {
      return;
    }
    Hud.effects.natural(
        graphics.guiWidth() - 25.0F * columns,
        1.0F,
        25.0F * columns - 1.0F,
        harmful > 0 ? 50.0F : 24.0F);
    if (Hud.begin(Hud.effects, graphics)) {
      ci.cancel();
    }
  }

  @Inject(method = "extractEffects", at = @At("RETURN"))
  private void cxvisuals$effectsEnd(
      GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
    Hud.end(Hud.effects, graphics);
  }

  @WrapOperation(method = "extractEffects", at = @At(value = "INVOKE", target = SPRITE))
  private void cxvisuals$effectTile(
      GuiGraphicsExtractor graphics,
      RenderPipeline pipeline,
      Identifier sprite,
      int x,
      int y,
      int width,
      int height,
      Operation<Void> original) {
    if (!Hud.effectTile(graphics, x, y, width, height)) {
      original.call(graphics, pipeline, sprite, x, y, width, height);
    }
  }

  @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$actionBarBegin(
      GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
    if (overlayMessageString == null || overlayMessageTime <= 0) {
      return;
    }
    int width = getFont().width(overlayMessageString);
    Hud.actionBar.natural(
        graphics.guiWidth() * 0.5F - width * 0.5F - 2.0F,
        graphics.guiHeight() - 74.0F,
        width + 4.0F,
        13.0F);
    if (Hud.begin(Hud.actionBar, graphics)) {
      ci.cancel();
    }
  }

  @Inject(method = "extractOverlayMessage", at = @At("RETURN"))
  private void cxvisuals$actionBarEnd(
      GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
    Hud.end(Hud.actionBar, graphics);
  }

  @Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$titleBegin(
      GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
    if (title == null || titleTime <= 0) {
      return;
    }
    Font font = getFont();
    float width = font.width(title) * 4.0F;
    if (subtitle != null) {
      width = Math.max(width, font.width(subtitle) * 2.0F);
    }
    float centreX = graphics.guiWidth() * 0.5F;
    float centreY = graphics.guiHeight() * 0.5F;
    Hud.title.natural(
        centreX - Hud.TITLE_WIDTH * 0.5F, centreY - 40.0F, Hud.TITLE_WIDTH, Hud.TITLE_HEIGHT);
    float fit = width <= Hud.TITLE_WIDTH ? 1.0F : Hud.TITLE_WIDTH / width;
    if (Hud.begin(Hud.title, graphics, fit, centreX, centreY)) {
      ci.cancel();
    }
  }

  @Inject(method = "extractTitle", at = @At("RETURN"))
  private void cxvisuals$titleEnd(
      GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
    Hud.end(Hud.title, graphics);
  }

  @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$hotbarBegin(
      GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
    nameDeferred = false;
    Hud.hotbar.natural(
        graphics.guiWidth() * 0.5F - 91.0F, graphics.guiHeight() - 49.0F, 182.0F, 49.0F);
    if (Hud.begin(Hud.hotbar, graphics)) {
      ci.cancel();
      if (minecraft.gameMode != null && minecraft.gameMode.getPlayerMode() != GameType.SPECTATOR) {
        extractSelectedItemName(graphics);
      }
    }
  }

  @Inject(method = "extractHotbarAndDecorations", at = @At("RETURN"))
  private void cxvisuals$hotbarEnd(
      GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
    Hud.end(Hud.hotbar, graphics);
    if (nameDeferred) {
      nameDeferred = false;
      extractSelectedItemName(graphics);
    }
  }

  @WrapOperation(
      method = "extractHotbarAndDecorations",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/Hud;extractSelectedItemName(Lnet/minecraft/client/gui/GuiGraphicsExtractor;)V"))
  private void cxvisuals$deferItemName(
      net.minecraft.client.gui.Hud hud, GuiGraphicsExtractor graphics, Operation<Void> original) {
    nameDeferred = true;
  }

  @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$itemNameBegin(GuiGraphicsExtractor graphics, CallbackInfo ci) {
    if (toolHighlightTimer <= 0 || lastToolHighlight.isEmpty()) {
      return;
    }
    int width = getFont().width(lastToolHighlight.getHoverName());
    float top = graphics.guiHeight() - 59.0F + (minecraft.gameMode.canHurtPlayer() ? 0.0F : 14.0F);
    Hud.itemName.natural(
        graphics.guiWidth() * 0.5F - width * 0.5F - 2.0F, top - 2.0F, width + 4.0F, 13.0F);
    if (Hud.begin(Hud.itemName, graphics)) {
      ci.cancel();
    }
  }

  @Inject(method = "extractSelectedItemName", at = @At("RETURN"))
  private void cxvisuals$itemNameEnd(GuiGraphicsExtractor graphics, CallbackInfo ci) {
    Hud.end(Hud.itemName, graphics);
  }

  @WrapOperation(
      method = "extractItemHotbar",
      at = @At(value = "INVOKE", target = SPRITE, ordinal = 1))
  private void cxvisuals$selectorGlide(
      GuiGraphicsExtractor graphics,
      RenderPipeline pipeline,
      Identifier sprite,
      int x,
      int y,
      int width,
      int height,
      Operation<Void> original) {
    if (!Animations.on() || minecraft.player == null) {
      original.call(graphics, pipeline, sprite, x, y, width, height);
      return;
    }
    graphics.pose().pushMatrix();
    graphics
        .pose()
        .translate(
            Animations.selectorShift(minecraft.player.getInventory().getSelectedSlot()), 0.0F);
    original.call(graphics, pipeline, sprite, x, y, width, height);
    graphics.pose().popMatrix();
  }

  @WrapOperation(
      method = "extractItemHotbar",
      at = @At(value = "INVOKE", target = SLOT, ordinal = 0))
  private void cxvisuals$slotBounce(
      net.minecraft.client.gui.Hud hud,
      GuiGraphicsExtractor graphics,
      int x,
      int y,
      DeltaTracker deltaTracker,
      Player player,
      ItemStack stack,
      int seed,
      Operation<Void> original) {
    float scale = Animations.on() ? Animations.slotScale(seed - 1) : 1.0F;
    if (scale == 1.0F) {
      original.call(hud, graphics, x, y, deltaTracker, player, stack, seed);
      return;
    }
    graphics.pose().pushMatrix();
    graphics.pose().translate(x + 8.0F, y + 8.0F);
    graphics.pose().scale(scale, scale);
    graphics.pose().translate(-(x + 8.0F), -(y + 8.0F));
    original.call(hud, graphics, x, y, deltaTracker, player, stack, seed);
    graphics.pose().popMatrix();
  }

  @ModifyArg(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = HEARTS), index = 7)
  private int cxvisuals$easedHealth(int current) {
    return Animations.health(current);
  }

  @ModifyArg(method = "extractPlayerHealth", at = @At(value = "INVOKE", target = HEARTS), index = 8)
  private int cxvisuals$ghostHealth(int old) {
    return Animations.healthGhost(old);
  }

  @ModifyExpressionValue(
      method = "extractFood",
      at = @At(value = "INVOKE", target = "Lnet/minecraft/world/food/FoodData;getFoodLevel()I"))
  private int cxvisuals$easedFood(int level) {
    return Animations.food(level);
  }

  @ModifyExpressionValue(
      method = "extractArmor",
      at =
          @At(
              value = "INVOKE",
              target = "Lnet/minecraft/world/entity/player/Player;getArmorValue()I"))
  private static int cxvisuals$easedArmor(int armor) {
    return Animations.armor(armor);
  }

  @WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE", target = LEVEL))
  private void cxvisuals$levelPop(
      GuiGraphicsExtractor graphics, Font font, int level, Operation<Void> original) {
    float scale = Animations.levelScale(level);
    if (scale == 1.0F) {
      original.call(graphics, font, level);
      return;
    }
    float cx = graphics.guiWidth() * 0.5F;
    float cy = graphics.guiHeight() - 31.0F;
    graphics.pose().pushMatrix();
    graphics.pose().translate(cx, cy);
    graphics.pose().scale(scale, scale);
    graphics.pose().translate(-cx, -cy);
    original.call(graphics, font, level);
    graphics.pose().popMatrix();
  }

  @WrapMethod(method = "displayScoreboardSidebar")
  private void cxvisuals$boardPop(
      GuiGraphicsExtractor graphics, Objective objective, Operation<Void> original) {
    float scale = Animations.boardScale();
    if (scale == 1.0F) {
      original.call(graphics, objective);
      return;
    }
    float cx = graphics.guiWidth();
    float cy = graphics.guiHeight() * 0.5F;
    graphics.pose().pushMatrix();
    graphics.pose().translate(cx, cy);
    graphics.pose().scale(scale, scale);
    graphics.pose().translate(-cx, -cy);
    original.call(graphics, objective);
    graphics.pose().popMatrix();
  }
}
