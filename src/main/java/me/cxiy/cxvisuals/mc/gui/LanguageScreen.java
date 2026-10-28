package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.platform.Window;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.gui.LanguagePicker;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.mc.compat.Keys;
import me.cxiy.cxvisuals.mc.menu.MenuStage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import me.cxiy.cxvisuals.mc.compat.Mouse;

public final class LanguageScreen extends Screen {
  private static final float MAX_FRAME_SECONDS = 0.1F;
  private static final float FROST = 22.0F;
  private static final int OPAQUE = 0xFF000000;

  private final Screen parent;
  private final LanguagePicker picker;
  private final Supplier<Theme> theme;
  private final McCanvas canvas = new McCanvas();
  private long lastNanos;
  private float pointerScale = 1.0F;

  public LanguageScreen(Screen parent, LanguagePicker picker, Supplier<Theme> theme) {
    super(Component.literal("CxVisuals"));
    ShapePipeline.recheck();
    GlassPipeline.recheck();
    this.parent = parent;
    this.picker = picker;
    this.theme = theme;
  }

  public LanguagePicker picker() {
    return picker;
  }

  public float pointerScale() {
    return pointerScale;
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    if (!MenuStage.backs(this)) {
      graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), theme.get().paper() | OPAQUE);
    }
  }

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    long now = System.nanoTime();
    float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, MAX_FRAME_SECONDS);
    lastNanos = now;

    Window window = this.minecraft.getWindow();
    int guiScale = Math.max(1, window.getGuiScale());
    float interfaceScale = McCanvas.interfaceScale(window.getHeight());
    pointerScale = guiScale / interfaceScale;
    picker.resize(window.getWidth() / interfaceScale, window.getHeight() / interfaceScale);

    McCanvas.glassBlur(FROST);
    canvas.begin(graphics, interfaceScale, guiScale);
    picker.draw(canvas, theme.get(), mouseX * pointerScale, mouseY * pointerScale, dt);
    canvas.end();
    McCanvas.glassBlur(0.0F);
  }

  @Override
  public void tick() {
    if (picker.closed() && this.minecraft.gui.screen() == this) {
      this.minecraft.gui.setScreen(parent);
    }
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
    if (Mouse.button(event) == 0) {
      picker.mouseDown((float) event.x() * pointerScale, (float) event.y() * pointerScale);
    }
    return true;
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent event) {
    if (Mouse.button(event) == 0) {
      picker.mouseUp((float) event.x() * pointerScale, (float) event.y() * pointerScale);
    }
    return true;
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    return picker.keyDown(Keys.code(event), event.hasShiftDown());
  }

  @Override
  public boolean shouldCloseOnEsc() {
    return false;
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
