package me.cxiy.cxvisuals.mc.mixin;

import java.util.function.Function;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CycleButton.class)
public interface CycleButtonAccessor<T> {
  @Accessor("name")
  Component cxvisuals$name();

  @Accessor("valueStringifier")
  Function<T, Component> cxvisuals$stringifier();
}
