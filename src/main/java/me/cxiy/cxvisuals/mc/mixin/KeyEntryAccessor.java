package me.cxiy.cxvisuals.mc.mixin;

import net.minecraft.client.gui.screens.options.controls.KeyBindsList;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBindsList.KeyEntry.class)
public interface KeyEntryAccessor {
  @Accessor("name")
  Component cxvisuals$name();
}
