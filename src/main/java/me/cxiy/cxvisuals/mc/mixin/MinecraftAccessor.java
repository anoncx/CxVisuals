package me.cxiy.cxvisuals.mc.mixin;

import com.mojang.authlib.minecraft.UserApiService;
import com.mojang.authlib.yggdrasil.ProfileResult;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import net.minecraft.client.multiplayer.ProfileKeyPairManager;
import net.minecraft.client.multiplayer.chat.report.ReportingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {
  @Mutable
  @Accessor("user")
  void cxvisuals$user(User user);

  @Mutable
  @Accessor("userApiService")
  void cxvisuals$userApiService(UserApiService service);

  @Mutable
  @Accessor("playerSocialManager")
  void cxvisuals$playerSocialManager(PlayerSocialManager manager);

  @Mutable
  @Accessor("profileKeyPairManager")
  void cxvisuals$profileKeyPairManager(ProfileKeyPairManager manager);

  @Mutable
  @Accessor("reportingContext")
  void cxvisuals$reportingContext(ReportingContext context);

  @Mutable
  @Accessor("profileFuture")
  void cxvisuals$profileFuture(CompletableFuture<ProfileResult> future);
}
