package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.platform.Window;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.accounts.SwitchView;
import me.cxiy.cxvisuals.mc.compat.Keys;
import me.cxiy.cxvisuals.mc.menu.MenuStage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class SwitchScreen extends Screen {
  private static final float MAX_FRAME_SECONDS = 0.1F;
  private static final float FROST = 22.0F;
  private static final int OPAQUE = 0xFF000000;

  private final Screen parent;
  private final SwitchView view;
  private final Supplier<Theme> theme;
  private final Runnable proceed;
  private final McCanvas canvas = new McCanvas();
  private long lastNanos;
  private float pointerScale = 1.0F;
  private boolean left;

  public SwitchScreen(Screen parent, SwitchView view, Supplier<Theme> theme, Runnable proceed) {
    super(Component.literal("CxVisuals"));
    ShapePipeline.recheck();
    GlassPipeline.recheck();
    this.parent = parent;
    this.view = view;
    this.theme = theme;
    this.proceed = proceed;
  }

  public SwitchView view() {
    return view;
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    if (this.minecraft.level == null && !MenuStage.backs(this)) {
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
    view.resize(window.getWidth() / interfaceScale, window.getHeight() / interfaceScale);
    McCanvas.glassBlur(FROST);
    canvas.begin(graphics, interfaceScale, guiScale);
    view.draw(canvas, theme.get(), mouseX * pointerScale, mouseY * pointerScale, dt);
    canvas.end();
    McCanvas.glassBlur(0.0F);
  }

  @Override
  public void tick() {
    if (!left && view.closed() && this.minecraft.gui.screen() == this) {
      left = true;
      if (view.result() == SwitchView.Result.PROCEED) {
        proceed.run();
      } else {
        this.minecraft.gui.setScreen(parent);
      }
    }
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
    if (event.button() == 0) {
      view.mouseDown((float) event.x() * pointerScale, (float) event.y() * pointerScale);
    }
    return true;
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent event) {
    if (event.button() == 0) {
      view.mouseUp((float) event.x() * pointerScale, (float) event.y() * pointerScale);
    }
    return true;
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    return view.keyDown(Keys.code(event)) || super.keyPressed(event);
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
