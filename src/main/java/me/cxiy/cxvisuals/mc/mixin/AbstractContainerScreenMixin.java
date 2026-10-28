package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import me.cxiy.cxvisuals.mc.modules.animations.Animations;
import me.cxiy.cxvisuals.mc.modules.donutsmp.DonutSMP;
import me.cxiy.cxvisuals.mc.modules.inventory.Inventory;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin<T extends AbstractContainerMenu> {
  private static final String FLOAT =
      "Lnet/minecraft/client/gui/screens/inventory/AbstractContainerScreen;extractFloatingItem(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V";

  @Shadow @Final protected T menu;

  @Shadow protected int leftPos;

  @Shadow protected int topPos;

  @Shadow @Final protected int imageWidth;

  @Shadow @Final protected int imageHeight;

  @Shadow protected Slot hoveredSlot;

  @Inject(method = "init", at = @At("TAIL"))
  private void cxvisuals$opened(CallbackInfo ci) {
    Animations.containerReset();
    Inventory.opened(cxvisuals$self());
  }

  @Inject(method = "onClose", at = @At("HEAD"))
  private void cxvisuals$closed(CallbackInfo ci) {
    Inventory.closed();
  }

  @Inject(method = "extractContents", at = @At("TAIL"))
  private void cxvisuals$inventory(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
    Inventory.render(
        cxvisuals$self(),
        graphics,
        mouseX,
        mouseY,
        leftPos,
        topPos,
        imageWidth,
        imageHeight,
        hoveredSlot);
    DonutSMP.render(
        cxvisuals$self(),
        graphics,
        mouseX,
        mouseY,
        leftPos,
        topPos,
        imageWidth,
        imageHeight,
        hoveredSlot);
  }

  @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$inventoryClick(
      MouseButtonEvent event, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
    if (Inventory.mouseClicked(cxvisuals$self(), event)) {
      cir.setReturnValue(true);
    }
  }

  @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$inventoryRelease(
      MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
    if (Inventory.mouseReleased(cxvisuals$self(), event)) {
      cir.setReturnValue(true);
    }
  }

  @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$inventoryDrag(
      MouseButtonEvent event, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
    if (Inventory.mouseDragged(cxvisuals$self(), event)) {
      cir.setReturnValue(true);
    }
  }

  @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$inventoryScroll(
      double x, double y, double scrollX, double scrollY, CallbackInfoReturnable<Boolean> cir) {
    if (Inventory.mouseScrolled(cxvisuals$self(), x, y, scrollY)) {
      cir.setReturnValue(true);
    }
  }

  @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$inventoryKey(KeyEvent event, CallbackInfoReturnable<Boolean> cir) {
    if (Inventory.keyPressed(cxvisuals$self(), event, hoveredSlot)
        || DonutSMP.keyPressed(event, hoveredSlot)) {
      cir.setReturnValue(true);
    }
  }

  @Inject(
      method = {"extractSlotHighlightBack", "extractSlotHighlightFront"},
      at = @At("HEAD"),
      cancellable = true)
  private void cxvisuals$covered(CallbackInfo ci) {
    if (Inventory.covers(cxvisuals$self())) {
      ci.cancel();
    }
  }

  @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$tooltipCovered(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
    if (Inventory.hidesTooltip(cxvisuals$self())
        || Inventory.previewTooltip(cxvisuals$self(), graphics, mouseX, mouseY, hoveredSlot)
        || DonutSMP.tooltip(cxvisuals$self(), graphics, mouseX, mouseY, hoveredSlot)) {
      ci.cancel();
    }
  }

  @Inject(
      method =
          "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
      at = @At("HEAD"),
      cancellable = true)
  private void cxvisuals$guard(
      Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
    if (DonutSMP.click(slot, input)) {
      ci.cancel();
    }
  }

  @Unique
  private AbstractContainerScreen<?> cxvisuals$self() {
    return (AbstractContainerScreen<?>) (Object) this;
  }

  @Inject(method = "extractSlots", at = @At("HEAD"))
  private void cxvisuals$observe(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
    DonutSMP.observe(cxvisuals$self());
    if (Animations.moving()) {
      Animations.observe(menu.slots);
    }
  }

  @Inject(method = "extractSlot", at = @At("HEAD"))
  private void cxvisuals$worth(
      GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
    DonutSMP.slot(cxvisuals$self(), graphics, slot);
  }

  @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$hideArriving(
      GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
    if (Animations.moving() && Animations.hides(slot.index)) {
      ci.cancel();
    }
  }

  @Inject(method = "extractSlots", at = @At("TAIL"))
  private void cxvisuals$flights(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
    if (Animations.moving()) {
      Animations.flights(graphics, menu.slots);
    }
  }

  @Inject(method = "extractCarriedItem", at = @At("HEAD"))
  private void cxvisuals$cursorIdle(
      GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
    if (menu.getCarried().isEmpty()) {
      Animations.cursorIdle();
    }
  }

  @WrapOperation(method = "extractCarriedItem", at = @At(value = "INVOKE", target = FLOAT))
  private void cxvisuals$softCursor(
      AbstractContainerScreen<?> screen,
      GuiGraphicsExtractor graphics,
      ItemStack carried,
      int x,
      int y,
      String count,
      Operation<Void> original) {
    if (!Animations.softCursor()) {
      original.call(screen, graphics, carried, x, y, count);
      return;
    }
    graphics.pose().pushMatrix();
    graphics.pose().translate(Animations.cursorX(x), Animations.cursorY(y));
    original.call(screen, graphics, carried, x, y, count);
    graphics.pose().popMatrix();
  }
}
