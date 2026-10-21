package me.cxiy.cxvisuals.mc.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EditBox.class)
public interface EditBoxAccessor {
  @Accessor("hint")
  Component cxvisuals$hint();

  @Accessor("maxLength")
  int cxvisuals$maxLength();
}
