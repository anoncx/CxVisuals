package me.cxiy.cxvisuals.mc.mixin;

import me.cxiy.cxvisuals.mc.modules.animations.Animations;
import me.cxiy.cxvisuals.mc.modules.hud.PingProbe;
import me.cxiy.cxvisuals.mc.modules.hud.Probe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAwardStatsPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.network.protocol.ping.ClientboundPongResponsePacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
  private static final String APPLY =
      "Lnet/minecraft/client/multiplayer/ClientPacketListener;setValuesFromPositionPacket(Lnet/minecraft/world/entity/PositionMoveRotation;Ljava/util/Set;Lnet/minecraft/world/entity/Entity;Z)Z";

  @Inject(method = "handleMovePlayer", at = @At(value = "INVOKE", target = APPLY))
  private void cxvisuals$teleportFrom(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
    Animations.beforeTeleport(Minecraft.getInstance().player);
  }

  @Inject(
      method = "handleMovePlayer",
      at = @At(value = "INVOKE", target = APPLY, shift = At.Shift.AFTER))
  private void cxvisuals$teleportTo(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
    Animations.afterTeleport(Minecraft.getInstance().player);
  }

  @Inject(method = "handleRespawn", at = @At("TAIL"))
  private void cxvisuals$respawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
    Animations.dimensionChanged();
  }

  @Inject(method = "handleSetTime", at = @At("TAIL"))
  private void cxvisuals$serverTime(ClientboundSetTimePacket packet, CallbackInfo ci) {
    Probe.serverTime(packet.gameTime());
  }

  @Inject(method = "handlePongResponse", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$pong(ClientboundPongResponsePacket packet, CallbackInfo ci) {
    if (PingProbe.pong(packet.time())) {
      ci.cancel();
    }
  }

  @Inject(method = "handleAwardStats", at = @At("TAIL"))
  private void cxvisuals$stats(ClientboundAwardStatsPacket packet, CallbackInfo ci) {
    PingProbe.stats();
  }
}
