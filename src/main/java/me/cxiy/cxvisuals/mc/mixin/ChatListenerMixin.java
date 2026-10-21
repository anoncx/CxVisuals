package me.cxiy.cxvisuals.mc.mixin;

import com.mojang.authlib.GameProfile;
import java.time.Instant;
import me.cxiy.cxvisuals.mc.modules.chat.Chat;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChatListener.class)
public abstract class ChatListenerMixin {
  @Inject(method = "showMessageToPlayer", at = @At("HEAD"))
  private void cxvisuals$sender(
      ChatType.Bound bound,
      PlayerChatMessage message,
      Component decorated,
      GameProfile profile,
      boolean onlyShowSecure,
      Instant received,
      CallbackInfoReturnable<Boolean> cir) {
    Chat.sender(profile.id());
  }

  @Inject(method = "showMessageToPlayer", at = @At("RETURN"))
  private void cxvisuals$clear(
      ChatType.Bound bound,
      PlayerChatMessage message,
      Component decorated,
      GameProfile profile,
      boolean onlyShowSecure,
      Instant received,
      CallbackInfoReturnable<Boolean> cir) {
    Chat.sender(null);
  }
}
