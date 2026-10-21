package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import me.cxiy.cxvisuals.mc.gui.HudText;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Font.class)
public abstract class FontMixin {
  @WrapMethod(method = "width(Ljava/lang/String;)I")
  private int cxvisuals$plainWidth(String text, Operation<Integer> original) {
    return HudText.active() ? HudText.width((Font) (Object) this, text) : original.call(text);
  }

  @WrapMethod(method = "width(Lnet/minecraft/network/chat/FormattedText;)I")
  private int cxvisuals$formattedWidth(FormattedText text, Operation<Integer> original) {
    return HudText.active() ? HudText.width((Font) (Object) this, text) : original.call(text);
  }

  @WrapMethod(method = "width(Lnet/minecraft/util/FormattedCharSequence;)I")
  private int cxvisuals$sequenceWidth(FormattedCharSequence text, Operation<Integer> original) {
    return HudText.active() ? HudText.width((Font) (Object) this, text) : original.call(text);
  }
}
