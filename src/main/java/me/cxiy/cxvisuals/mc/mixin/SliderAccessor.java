package me.cxiy.cxvisuals.mc.mixin;

import net.minecraft.client.gui.components.AbstractSliderButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractSliderButton.class)
public interface SliderAccessor {
  @Accessor("value")
  double cxvisuals$value();

  @Invoker("setValue")
  void cxvisuals$setValue(double value);
}
