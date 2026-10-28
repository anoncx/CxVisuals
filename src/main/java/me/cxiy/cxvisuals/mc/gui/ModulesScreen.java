package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.platform.Window;
import java.nio.file.Path;
import java.util.List;
import me.cxiy.cxvisuals.gui.ModulesView;
import me.cxiy.cxvisuals.gui.Themes;
import me.cxiy.cxvisuals.mc.CxVisuals;
import me.cxiy.cxvisuals.mc.compat.Keys;
import me.cxiy.cxvisuals.mc.menu.MenuStage;
import me.cxiy.cxvisuals.mc.modules.avatar.Avatars;
import me.cxiy.cxvisuals.modules.avatar.AvatarModule;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import me.cxiy.cxvisuals.mc.compat.Mouse;

public final class ModulesScreen extends Screen {
  private static final float MAX_FRAME_SECONDS = 0.1F;

  private static final float FROST = 22.0F;
  private static final int OPAQUE = 0xFF000000;

  private final Screen parent;
  private ModulesView view;
  private final McCanvas canvas = new McCanvas();
  private final Themes themes = new Themes(CxVisuals.registry().client());
  private long lastNanos;
  private float pointerScale = 1.0F;

  public ModulesScreen(ModulesView view) {
    this(view, null);
  }

  public ModulesScreen(ModulesView view, Screen parent) {
    super(Component.literal("CxVisuals"));
    this.parent = parent;
    ShapePipeline.recheck();
    GlassPipeline.recheck();
    this.view = view;
    view.open();
  }

  public ModulesView view() {
    return view;
  }

  public void swap(ModulesView next) {
    view = next;
  }

  @Override
  public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    if (parent != null && !MenuStage.backs(this)) {
      graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), themes.get().paper() | OPAQUE);
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

    if (parent != null) {
      McCanvas.glassBlur(FROST);
    }
    canvas.begin(graphics, interfaceScale, guiScale);
    view.draw(canvas, mouseX * pointerScale, mouseY * pointerScale, dt);
    canvas.end();
    if (parent != null) {
      McCanvas.glassBlur(0.0F);
    }
  }

  @Override
  public void tick() {
    if (view.closed()) {
      this.minecraft.gui.setScreen(parent);
    }
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
    float x = (float) event.x() * pointerScale;
    float y = (float) event.y() * pointerScale;
    if (Mouse.button(event) == 0 && !view.mouseDown(x, y)) {
      view.close();
    } else if (Mouse.button(event) == 1) {
      view.secondaryDown(x, y);
    }
    return true;
  }

  @Override
  public boolean mouseReleased(MouseButtonEvent event) {
    if (Mouse.button(event) == 0) {
      view.mouseUp((float) event.x() * pointerScale, (float) event.y() * pointerScale);
    }
    return true;
  }

  @Override
  public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
    if (Mouse.button(event) == 0) {
      view.mouseDrag((float) event.x() * pointerScale, (float) event.y() * pointerScale);
    }
    return true;
  }

  @Override
  public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
    view.scroll((float) x * pointerScale, (float) y * pointerScale, (float) scrollY);
    return true;
  }

  @Override
  public boolean keyPressed(KeyEvent event) {
    if (view.keyDown(
        Keys.code(event), Keys.scan(event), event.hasControlDown(), event.hasShiftDown())) {
      return true;
    }
    return super.keyPressed(event);
  }

  @Override
  public boolean charTyped(CharacterEvent event) {
    return view.charTyped(event.codepoint()) || super.charTyped(event);
  }

  @Override
  public void onFilesDrop(List<Path> paths) {
    if (Avatars.library() != null && !paths.isEmpty()) {
      view.showModule(AvatarModule.ID);
      Avatars.importFiles(paths);
    }
  }

  @Override
  public void onClose() {
    view.close();
  }

  @Override
  public boolean isPauseScreen() {
    return false;
  }
}
