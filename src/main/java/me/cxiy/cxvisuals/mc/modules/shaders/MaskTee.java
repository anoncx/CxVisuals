package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.gizmos.DrawableGizmoPrimitives;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;

final class MaskTee implements SubmitNodeCollector {
  private final Int2ObjectOpenHashMap<MaskTee> orders = new Int2ObjectOpenHashMap<>();
  private SubmitNodeCollector source;
  private OrderedSubmitNodeCollector real;
  private OrderedSubmitNodeCollector mask;
  private MaskStorage storage;

  MaskTee over(SubmitNodeCollector real, MaskStorage storage) {
    if (this.source != real || this.storage != storage) {
      orders.clear();
    }
    this.source = real;
    this.real = real;
    this.mask = storage;
    this.storage = storage;
    return this;
  }

  @Override
  public OrderedSubmitNodeCollector order(int order) {
    MaskTee tee = orders.get(order);
    if (tee == null) {
      tee = new MaskTee();
      tee.source = source;
      tee.storage = storage;
      orders.put(order, tee);
    }
    tee.real = source.order(order);
    tee.mask = storage.order(order);
    return tee;
  }

  @Override
  public void submitShadow(
      PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {
    real.submitShadow(poseStack, radius, pieces);
  }

  @Override
  public void submitNameTag(
      PoseStack poseStack,
      Vec3 nameTagAttachment,
      int offset,
      Component name,
      boolean seeThrough,
      int lightCoords,
      CameraRenderState camera) {
    real.submitNameTag(poseStack, nameTagAttachment, offset, name, seeThrough, lightCoords, camera);
  }

  @Override
  public void submitText(
      PoseStack poseStack,
      float x,
      float y,
      FormattedCharSequence string,
      boolean dropShadow,
      Font.DisplayMode displayMode,
      int lightCoords,
      int color,
      int backgroundColor,
      int outlineColor) {
    real.submitText(
        poseStack,
        x,
        y,
        string,
        dropShadow,
        displayMode,
        lightCoords,
        color,
        backgroundColor,
        outlineColor);
  }

  @Override
  public void submitFlame(
      PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {
    real.submitFlame(poseStack, renderState, rotation);
  }

  @Override
  public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {
    real.submitLeash(poseStack, leashState);
  }

  @Override
  public <S> void submitModel(
      Model<? super S> model,
      S state,
      PoseStack poseStack,
      RenderType renderType,
      int lightCoords,
      int overlayCoords,
      int tintedColor,
      UvMapping uvMapping,
      int outlineColor) {
    real.submitModel(
        model,
        state,
        poseStack,
        renderType,
        lightCoords,
        overlayCoords,
        tintedColor,
        uvMapping,
        outlineColor);
    if (MaskTypes.of(renderType) == null) {
      return;
    }
    BakedShape shape = storage.shape();
    model.setupAnim(state);
    model.renderToBuffer(
        poseStack,
        uvMapping == null ? shape : uvMapping.wrap(shape),
        lightCoords,
        overlayCoords,
        -1);
    mask.submitCustomGeometry(poseStack, renderType, shape);
  }

  @Override
  public <S> void submitCrumblingOverlay(
      Model<? super S> model,
      S state,
      PoseStack poseStack,
      RenderType renderType,
      int lightCoords,
      int overlayCoords,
      int tintedColor,
      ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
    real.submitCrumblingOverlay(
        model,
        state,
        poseStack,
        renderType,
        lightCoords,
        overlayCoords,
        tintedColor,
        crumblingOverlay);
  }

  @Override
  public void submitTextBackground(
      PoseStack poseStack,
      float x0,
      float y0,
      float x1,
      float y1,
      int color,
      Font.DisplayMode displayMode,
      int lightCoords) {
    real.submitTextBackground(poseStack, x0, y0, x1, y1, color, displayMode, lightCoords);
  }

  @Override
  public void submitMovingBlock(
      PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {
    real.submitMovingBlock(poseStack, movingBlockRenderState, outlineColor);
  }

  @Override
  public void submitBlockModel(
      PoseStack poseStack,
      RenderType renderType,
      List<BlockStateModelPart> parts,
      int[] tintLayers,
      int lightCoords,
      int overlayCoords,
      int outlineColor) {
    real.submitBlockModel(
        poseStack, renderType, parts, tintLayers, lightCoords, overlayCoords, outlineColor);
    mask.submitBlockModel(
        poseStack, renderType, parts, tintLayers, lightCoords, overlayCoords, outlineColor);
  }

  @Override
  public void submitBreakingBlockModel(
      PoseStack poseStack,
      List<BlockStateModelPart> parts,
      int progress,
      boolean isBlockTranslucent) {
    real.submitBreakingBlockModel(poseStack, parts, progress, isBlockTranslucent);
  }

  @Override
  public void submitShapeOutline(
      PoseStack poseStack,
      VoxelShape shape,
      RenderType renderType,
      int color,
      float width,
      boolean afterTerrain) {
    real.submitShapeOutline(poseStack, shape, renderType, color, width, afterTerrain);
  }

  @Override
  public void submitItem(
      PoseStack poseStack,
      ItemDisplayContext displayContext,
      int lightCoords,
      int overlayCoords,
      int outlineColor,
      int[] tintLayers,
      ItemQuads quads,
      ItemStackRenderState.FoilType foilType) {
    real.submitItem(
        poseStack,
        displayContext,
        lightCoords,
        overlayCoords,
        outlineColor,
        tintLayers,
        quads,
        foilType);
    mask.submitItem(
        poseStack,
        displayContext,
        lightCoords,
        overlayCoords,
        outlineColor,
        tintLayers,
        quads,
        foilType);
  }

  @Override
  public void submitCustomGeometry(
      PoseStack poseStack,
      RenderType renderType,
      SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
    real.submitCustomGeometry(poseStack, renderType, customGeometryRenderer);
    mask.submitCustomGeometry(poseStack, renderType, customGeometryRenderer);
  }

  @Override
  public void submitQuadParticleGroup(QuadParticleRenderState particles) {
    real.submitQuadParticleGroup(particles);
  }

  @Override
  public void submitGizmoPrimitives(
      DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {
    real.submitGizmoPrimitives(group, camera, onTop);
  }
}
