package me.cxiy.cxvisuals.mc.mixin;

import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ProgressScreen.class)
public interface ProgressScreenAccessor {
  @Accessor("header")
  Component cxvisuals$header();

  @Accessor("stage")
  Component cxvisuals$stage();

  @Accessor("progress")
  int cxvisuals$progress();
}
