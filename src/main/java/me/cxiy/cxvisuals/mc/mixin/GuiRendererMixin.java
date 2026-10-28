package me.cxiy.cxvisuals.mc.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.mc.gui.Backdrop;
import me.cxiy.cxvisuals.mc.gui.GuiTilt;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.mc.gui.ScreenLayer;
import me.cxiy.cxvisuals.mc.modules.avatar.Avatars;
import me.cxiy.cxvisuals.mc.modules.shaders.CirclePreview;
import me.cxiy.cxvisuals.mc.modules.shaders.TrailPreview;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.Projection;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
  private static final String PROJECT =
      "Lcom/mojang/blaze3d/systems/RenderSystem;setProjectionMatrix(Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;Lcom/mojang/blaze3d/ProjectionType;)V";
  private static final String SCISSOR =
      "Lnet/minecraft/client/gui/render/GuiRenderer;enableScissor(Lnet/minecraft/client/gui/navigation/ScreenRectangle;Lcom/mojang/renderpearl/api/commands/RenderPass;)V";

  @Shadow @Final private List<?> draws;

  @Shadow @Final private Projection guiProjection;

  @Shadow private RenderPipeline previousPipeline;

  @Unique private int meshKind;

  @Unique private boolean boundTilted;

  @Unique private boolean drawTilted;

  @Unique private int cursor;

  @Inject(method = "render", at = @At("HEAD"))
  private void cxvisuals$captureBackdrop(CallbackInfo ci) {
    Backdrop.captureIfRequested();
    Avatars.flushPreviews();
    CirclePreview.flush();
    TrailPreview.flush();
    McCanvas.flushTerrain();
  }

  @Inject(method = "render", at = @At("TAIL"))
  private void cxvisuals$tiltEndFrame(CallbackInfo ci) {
    GuiTilt.endFrame();
  }

  @Inject(method = "addElementsToMeshes", at = @At("HEAD"))
  private void cxvisuals$tiltRange(CallbackInfo ci) {
    meshKind = GuiTilt.FLAT;
  }

  @Inject(method = "addElementToMesh", at = @At("HEAD"))
  private void cxvisuals$tiltBreak(CallbackInfo ci) {
    int kind = GuiTilt.kind();
    if (kind != meshKind) {
      meshKind = kind;
      previousPipeline = null;
    }
  }

  @Inject(
      method = "addElementToMesh",
      at =
          @At(
              value = "INVOKE",
              target = "Ljava/util/List;add(Ljava/lang/Object;)Z",
              shift = At.Shift.AFTER))
  private void cxvisuals$tiltMark(CallbackInfo ci) {
    GuiTilt.mark(draws.size() - 1, meshKind);
  }

  @Inject(method = "draw", at = @At(value = "INVOKE", target = PROJECT, shift = At.Shift.AFTER))
  private void cxvisuals$tiltUpload(CallbackInfo ci) {
    if (GuiTilt.active()) {
      GuiTilt.upload(guiProjection);
    }
  }

  @Inject(method = "executeDrawRange", at = @At("HEAD"), cancellable = true)
  private void cxvisuals$tiltCursor(
      Supplier<String> label,
      RenderTarget main,
      GpuBufferSlice transforms,
      int startIndex,
      int endIndex,
      CallbackInfo ci) {
    cursor = startIndex;
    boundTilted = false;
    if (!GuiTilt.capturing() || !ScreenLayer.usable()) {
      return;
    }
    int first = startIndex;
    while (first < endIndex && !GuiTilt.captured(first)) {
      first++;
    }
    if (first == endIndex) {
      return;
    }
    boolean freshDepth = true;
    int from = startIndex;
    while (from < endIndex) {
      boolean captured = GuiTilt.captured(from);
      int to = from + 1;
      while (to < endIndex && GuiTilt.captured(to) == captured) {
        to++;
      }
      try (RenderPass pass =
          captured
              ? ScreenLayer.pass(label, freshDepth)
              : RenderSystem.getDevice()
                  .createCommandEncoder()
                  .createRenderPass(
                      label,
                      main.getColorTextureView(),
                      Optional.empty(),
                      main.getDepthTextureView(),
                      OptionalDouble.empty())) {
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        boundTilted = false;
        for (int index = from; index < to; index++) {
          ScreenLayer.execute(this, draws.get(index), pass);
        }
      }
      if (captured) {
        freshDepth = false;
      }
      from = to;
    }
    ci.cancel();
  }

  @Inject(method = "executeDraw", at = @At("HEAD"))
  private void cxvisuals$tiltBind(CallbackInfo ci, @Local(argsOnly = true) RenderPass renderPass) {
    drawTilted = GuiTilt.marked(cursor++);
    if (drawTilted != boundTilted) {
      boundTilted = drawTilted;
      renderPass.setUniform(
          "Projection", drawTilted ? GuiTilt.slice() : RenderSystem.getProjectionMatrixBuffer());
    }
  }

  @ModifyArg(method = "executeDraw", at = @At(value = "INVOKE", target = SCISSOR), index = 0)
  private ScreenRectangle cxvisuals$tiltScissor(ScreenRectangle area) {
    return drawTilted ? GuiTilt.scissor(area) : area;
  }
}
