package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cxiy.cxvisuals.mc.modules.hud.Probe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenEntries;
import net.minecraft.client.gui.components.debug.DebugScreenEntryList;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
  @WrapOperation(
      method = "renderFrame",
      at =
          @At(
              value = "INVOKE",
              target =
                  "Lnet/minecraft/client/gui/components/debug/DebugScreenEntryList;isCurrentlyEnabled(Lnet/minecraft/resources/Identifier;)Z"))
  private boolean cxvisuals$measureGpu(
      DebugScreenEntryList entries, Identifier entry, Operation<Boolean> original) {
    return original.call(entries, entry)
        || (entry == DebugScreenEntries.GPU_UTILIZATION && Probe.gpuWanted());
  }
}
