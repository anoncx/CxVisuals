package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.cxiy.cxvisuals.mc.modules.animations.Animations;
import me.cxiy.cxvisuals.mc.modules.chat.Chat;
import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
  private static final String RENDER =
      "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V";

  @Shadow @Final private Minecraft minecraft;

  @Shadow
  private int getWidth() {
    throw new AssertionError();
  }

  @Inject(method = RENDER, at = @At("HEAD"), cancellable = true)
  private void cxvisuals$begin(
      GuiGraphicsExtractor graphics,
      Font font,
      int ticks,
      int mouseX,
      int mouseY,
      ChatComponent.DisplayMode mode,
      boolean cursor,
      CallbackInfo ci) {
    if (Hud.feedActive()) {
      ci.cancel();
      return;
    }
    float height = ChatComponent.getHeight(minecraft.options.chatHeightFocused().get());
    Hud.chat.natural(0.0F, graphics.guiHeight() - 40.0F - height, getWidth() + 12.0F, height);
    if (Hud.begin(Hud.chat, graphics)) {
      ci.cancel();
    }
  }

  @Inject(method = RENDER, at = @At("RETURN"))
  private void cxvisuals$end(
      GuiGraphicsExtractor graphics,
      Font font,
      int ticks,
      int mouseX,
      int mouseY,
      ChatComponent.DisplayMode mode,
      boolean cursor,
      CallbackInfo ci) {
    Hud.end(Hud.chat, graphics);
  }

  @Inject(method = "addMessage", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$feed(
      Component message,
      MessageSignature signature,
      GuiMessageSource source,
      GuiMessageTag tag,
      CallbackInfo ci) {
    if (Chat.hides(message, source)) {
      ci.cancel();
      return;
    }
    Hud.feed(message, source != GuiMessageSource.PLAYER);
  }

  @Inject(method = "captureClickableText", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$clicks(
      ActiveTextCollector collector,
      int screenHeight,
      int ticks,
      ChatComponent.DisplayMode mode,
      CallbackInfo ci) {
    if (Hud.clicks(collector)) {
      ci.cancel();
      return;
    }
    Hud.remap(Hud.chat, collector);
  }

  @Inject(method = "addMessageToDisplayQueue", at = @At("TAIL"))
  private void cxvisuals$slideIn(GuiMessage message, CallbackInfo ci) {
    if (!Hud.feedActive()) {
      Animations.chatLine();
    }
  }

  @WrapMethod(method = RENDER)
  private void cxvisuals$rise(
      GuiGraphicsExtractor graphics,
      Font font,
      int ticks,
      int mouseX,
      int mouseY,
      ChatComponent.DisplayMode mode,
      boolean cursor,
      Operation<Void> original) {
    float rise = Animations.chatRise(font.lineHeight);
    if (rise == 0.0F) {
      original.call(graphics, font, ticks, mouseX, mouseY, mode, cursor);
      return;
    }
    graphics.pose().pushMatrix();
    graphics.pose().translate(0.0F, rise);
    original.call(graphics, font, ticks, mouseX, mouseY, mode, cursor);
    graphics.pose().popMatrix();
  }
}
