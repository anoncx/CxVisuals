package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.platform.Window;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.accounts.AccountsView;
import me.cxiy.cxvisuals.mc.compat.Keys;
import me.cxiy.cxvisuals.mc.menu.MenuStage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import me.cxiy.cxvisuals.mc.compat.Mouse;

public final class AccountsScreen extends Screen {
  private static final float MAX_FRAME_SECONDS = 0.1F;
  private static final float FROST = 22.0F;
  private static final int OPAQUE = 0xFF000000;

  private final Screen parent;
  private final AccountsView view;
  private final Supplier<Theme> theme;
  private final McCanvas canvas = new McCanvas();
  private long lastNanos;
  private float pointerScale = 1.0F;

  public AccountsScreen(Screen parent, AccountsView view, Supplier<Theme> theme) {
    super(Component.literal("CxVisuals"));
    ShapePipeline.recheck();
    GlassPipeline.recheck();
    this.parent = parent;
    this.view = view;
    this.theme = theme;
    view.open();
  }

  public AccountsView view() {
    return view;
  }

  public float pointerScale() {
    return pointerScale;
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
    if (view.closed()) {
      this.minecraft.gui.setScreen(parent);
    }
  }

  @Override
  public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
    float x = (float) event.x() * pointerScale;
    float y = (float) event.y() * pointerScale;
    if (Mouse.button(event) == 0 && !view.mouseDown(x, y) && view.outside(x, y)) {
      view.close();
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
      view.mouseDrag((float) event.x() * pointerScale);
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
    return view.keyDown(Keys.code(event), event.hasControlDown(), event.hasShiftDown())
        || super.keyPressed(event);
  }

  @Override
  public boolean charTyped(CharacterEvent event) {
    return view.charTyped(event.codepoint()) || super.charTyped(event);
  }

  @Override
  public boolean shouldCloseOnEsc() {
    return false;
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
