package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
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
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.joml.Vector3f;

final class MaskStorage extends SubmitNodeStorage {
  private static final int SOLID = -1;
  private static final QuadInstance FLAT = new QuadInstance();

  private record ItemGeometry(List<BakedQuad> quads, RenderType mask)
      implements SubmitNodeCollector.CustomGeometryRenderer {
    @Override
    public void render(PoseStack.Pose pose, VertexConsumer consumer) {
      for (int i = 0; i < quads.size(); i++) {
        BakedQuad quad = quads.get(i);
        if (MaskTypes.of(quad.materialInfo().itemRenderType()) == mask) {
          consumer.putBakedQuad(pose, quad, FLAT);
        }
      }
    }
  }

  private final class Silhouette extends SubmitNodeCollection {
    Silhouette() {
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
      RenderType mask = MaskTypes.of(renderType);
      if (mask != null) {
        place(poseStack);
        super.submitModel(
            model, state, poseStack, mask, lightCoords, overlayCoords, SOLID, uvMapping, SOLID);
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
        int outlineColor) {
      RenderType mask = MaskTypes.of(renderType);
      if (mask != null) {
        super.submitBlockModel(
            poseStack, mask, modelParts, tintLayers, lightCoords, overlayCoords, SOLID);
      }
    }

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
        ItemQuads items,
        ItemStackRenderState.FoilType foilType) {
      List<BakedQuad> quads = items.all();
      RenderType previous = null;
      for (int i = 0; i < quads.size(); i++) {
        RenderType mask = MaskTypes.of(quads.get(i).materialInfo().itemRenderType());
        if (mask == null || mask == previous) {
          continue;
        }
        previous = mask;
        if (firstUse(quads, i, mask)) {
          place(poseStack);
          super.submitCustomGeometry(poseStack, mask, new ItemGeometry(quads, mask));
        }
      }
    }

    @Override
    public void submitCustomGeometry(
        PoseStack poseStack,
        RenderType renderType,
        SubmitNodeCollector.CustomGeometryRenderer customGeometryRenderer) {
      RenderType mask = MaskTypes.of(renderType);
      if (mask != null) {
        place(poseStack);
        super.submitCustomGeometry(poseStack, mask, customGeometryRenderer);
      }
    }

    @Override
    public void submitQuadParticleGroup(QuadParticleRenderState particles) {}

    @Override
    public void submitGizmoPrimitives(
        DrawableGizmoPrimitives.Group group, CameraRenderState camera, boolean onTop) {}

    private boolean firstUse(List<BakedQuad> quads, int index, RenderType mask) {
      for (int i = 0; i < index; i++) {
        if (MaskTypes.of(quads.get(i).materialInfo().itemRenderType()) == mask) {
          return false;
        }
      }
      return true;
    }
  }

  @Override
  public SubmitNodeCollection order(int order) {
    return getSubmitsPerOrder().computeIfAbsent(order, key -> new Silhouette());
  }

  final Vector3f first = new Vector3f();
  boolean placed;
  private final List<BakedShape> shapes = new ArrayList<>();
  private int used;

  BakedShape shape() {
    if (used == shapes.size()) {
      shapes.add(new BakedShape());
    }
    BakedShape shape = shapes.get(used++);
    shape.reset();
    return shape;
  }

  void rewind() {
    used = 0;
    placed = false;
  }

  private void place(PoseStack poseStack) {
    if (!placed) {
      placed = true;
      poseStack.last().pose().getTranslation(first);
    }
  }
}
