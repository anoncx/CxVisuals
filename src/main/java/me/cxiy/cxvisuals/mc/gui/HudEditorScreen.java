package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.platform.Window;
import me.cxiy.cxvisuals.gui.hud.HudEditor;
import me.cxiy.cxvisuals.mc.compat.Keys;
import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import me.cxiy.cxvisuals.mc.compat.Mouse;

public final class HudEditorScreen extends Screen {
  private static final float MAX_FRAME_SECONDS = 0.1F;

  private final HudEditor editor;
  private final McCanvas canvas = new McCanvas();
  private long lastNanos;
  private float pointerScale = 1.0F;

  public HudEditorScreen(HudEditor editor) {
    super(Component.literal("CxVisuals HUD"));
    ShapePipeline.recheck();
    GlassPipeline.recheck();
    this.editor = editor;
    editor.open();
  }

  public HudEditor editor() {
    return editor;
  }

  public float pointerScale() {
    return pointerScale;
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {}

  @Override
  public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    long now = System.nanoTime();
    float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, MAX_FRAME_SECONDS);
    lastNanos = now;

    Window window = this.minecraft.getWindow();
    int guiScale = Math.max(1, window.getGuiScale());
    float interfaceScale = McCanvas.interfaceScale(window.getHeight());
    pointerScale = guiScale / interfaceScale;
    editor.resize(window.getWidth() / interfaceScale, window.getHeight() / interfaceScale);

    canvas.begin(graphics, interfaceScale, guiScale);
    editor.draw(canvas, mouseX * pointerScale, mouseY * pointerScale, dt);
    canvas.end();
  }

  @Override
  public void tick() {
    if (editor.closed()) {
      this.minecraft.gui.setScreen(null);
    }
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
    float x = (float) event.x() * pointerScale;
    float y = (float) event.y() * pointerScale;
    if (Mouse.button(event) == 0) {
      boolean modified = event.hasShiftDown() || event.hasControlDown();
      if (!modified && Hud.click(event.x(), event.y())) {
        return true;
      }
      editor.mouseDown(x, y, modified);
    } else if (Mouse.button(event) == 1) {
      editor.secondaryDown(x, y);
    }
    return true;
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent event) {
    if (Mouse.button(event) == 0) {
      editor.mouseUp((float) event.x() * pointerScale, (float) event.y() * pointerScale);
    }
    return true;
  }

  @Override
  public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
    if (Mouse.button(event) == 0) {
      editor.mouseDrag((float) event.x() * pointerScale, (float) event.y() * pointerScale);
    }
    return true;
  }

  @Override
  public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
    editor.scroll((float) x * pointerScale, (float) y * pointerScale, (float) scrollY);
    return true;
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    if (editor.keyDown(Keys.code(event), event.hasShiftDown())) {
      return true;
    }
    return super.keyPressed(event);
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
