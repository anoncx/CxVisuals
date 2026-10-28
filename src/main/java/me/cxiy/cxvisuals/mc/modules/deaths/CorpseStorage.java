package me.cxiy.cxvisuals.mc.modules.deaths;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
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

final class CorpseStorage extends SubmitNodeStorage {
  private static final int NO_OUTLINE = 0;

  private final class Body extends SubmitNodeCollection {
    Body() {
      super(false, seeThrough());
    }

    @Override
    public void submitShadow(
        PoseStack poseStack, float radius, List<EntityRenderState.ShadowPiece> pieces) {}

    @Override
    public void submitNameTag(
        PoseStack poseStack,
        Vec3 nameTagAttachment,
        int offset,
        Component name,
        boolean seeThrough,
        int lightCoords,
        CameraRenderState camera) {}

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
        int outlineColor) {}

    @Override
    public void submitFlame(
        PoseStack poseStack, EntityRenderState renderState, Quaternionf rotation) {}

    @Override
    public void submitLeash(PoseStack poseStack, EntityRenderState.LeashState leashState) {}

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
      RenderType corpse = CorpseTypes.of(renderType);
      if (corpse != null) {
        super.submitModel(
            model,
            state,
            poseStack,
            corpse,
            lightCoords,
            overlayCoords,
            tintedColor,
            uvMapping,
            NO_OUTLINE);
      }
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
        ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {}

    @Override
    public void submitTextBackground(
        PoseStack poseStack,
        float x0,
        float y0,
        float x1,
        float y1,
        int color,
        Font.DisplayMode displayMode,
        int lightCoords) {}

    @Override
    public void submitMovingBlock(
        PoseStack poseStack, MovingBlockRenderState movingBlockRenderState, int outlineColor) {}

    @Override
    public void submitBlockModel(
        PoseStack poseStack,
        RenderType renderType,
        List<BlockStateModelPart> modelParts,
        int[] tintLayers,
        int lightCoords,
        int overlayCoords,
        int outlineColor) {}

    @Override
    public void submitBreakingBlockModel(
        PoseStack poseStack,
        List<BlockStateModelPart> parts,
        int progress,
        boolean isBlockTranslucent) {}

    @Override
    public void submitShapeOutline(
        PoseStack poseStack,
        VoxelShape shape,
        RenderType renderType,
        int color,
        float width,
        boolean afterTerrain) {}

    @Override
    public void submitItem(
        PoseStack poseStack,
        ItemDisplayContext displayContext,
        int lightCoords,
        int overlayCoords,
        int outlineColor,
        int[] tintLayers,
        ItemQuads quads,
        ItemStackRenderState.FoilType foilType) {}

    @Override
    public void submitCustomGeometry(
        PoseStack poseStack,
        RenderType renderType,
        SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
      RenderType corpse = CorpseTypes.of(renderType);
      if (corpse != null) {
        super.submitCustomGeometry(poseStack, corpse, customGeometryRenderer);
      }
    }

    @Override
    public void submitQuadParticleGroup(QuadParticleRenderState particles) {}

    @Override
    public void submitGizmoPrimitives(
        DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {}
  }

  @Override
  public SubmitNodeCollection order(int order) {
    return getSubmitsPerOrder().computeIfAbsent(order, key -> new Body());
  }

  void wipe() {
    getSubmitsPerOrder().clear();
  }
}
