package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import me.cxiy.cxvisuals.mc.mixin.ModelPartAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class MobFaces {
  private static final int LIMIT = 256;
  private static final long FRESH_NANOS = 1_000_000_000L;
  private static final String HEAD = "head";
  private static final int DEPTH = 4;

  record Face(TextureSetup setup, float u0, float v0, float u1, float v1) {}

  private record Known(Face face, long at) {}

  private final Map<Integer, Known> byEntity = new HashMap<>(64);
  private final Map<ModelPart, float[]> heads = new HashMap<>(64);

  @SuppressWarnings({"unchecked", "rawtypes"})
  Face face(int entityId) {
    long now = System.nanoTime();
    Known known = byEntity.get(entityId);
    if (known != null && now - known.at < FRESH_NANOS) {
      return known.face;
    }
    Minecraft mc = Minecraft.getInstance();
    Entity entity = mc.level == null ? null : mc.level.getEntity(entityId);
    Face face = null;
    if (entity instanceof LivingEntity living) {
      EntityRenderer renderer = mc.getEntityRenderDispatcher().getRenderer(living);
      if (renderer instanceof LivingEntityRenderer model) {
        try {
          LivingEntityRenderState state =
              (LivingEntityRenderState) model.createRenderState(living, 1.0F);
          Identifier texture = model.getTextureLocation(state);
          ModelPart root = model.getModel().root();
          float[] uv = heads.computeIfAbsent(root, MobFaces::head);
          if (texture != null && uv != null) {
            face =
                new Face(
                    TextureSetup.singleTexture(
                        mc.getTextureManager().getTexture(texture).getTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)),
                    uv[0],
                    uv[1],
                    uv[2],
                    uv[3]);
          }
        } catch (RuntimeException ignored) {
          face = null;
        }
      }
    }
    if (byEntity.size() > LIMIT) {
      byEntity.clear();
    }
    byEntity.put(entityId, new Known(face, now));
    return face;
  }

  private static float[] head(ModelPart root) {
    ModelPart head = find(root, 0);
    if (head == null) {
      head = root;
    }
    ModelPart.Cube best = largest(head, 0);
    if (best == null) {
      return null;
    }
    for (ModelPart.Polygon polygon : best.polygons) {
      if (polygon.normal().z() < -0.5F) {
        float u0 = Float.MAX_VALUE;
        float v0 = Float.MAX_VALUE;
        float u1 = -Float.MAX_VALUE;
        float v1 = -Float.MAX_VALUE;
        for (ModelPart.Vertex vertex : polygon.vertices()) {
          u0 = Math.min(u0, vertex.u());
          v0 = Math.min(v0, vertex.v());
          u1 = Math.max(u1, vertex.u());
          v1 = Math.max(v1, vertex.v());
        }
        return u1 > u0 && v1 > v0 ? new float[] {u0, v0, u1, v1} : null;
      }
    }
    return null;
  }

  private static ModelPart find(ModelPart part, int depth) {
    Map<String, ModelPart> children = ((ModelPartAccessor) (Object) part).cxvisuals$children();
    ModelPart direct = children.get(HEAD);
    if (direct != null) {
      return direct;
    }
    if (depth >= DEPTH) {
      return null;
    }
    for (ModelPart child : children.values()) {
      ModelPart found = find(child, depth + 1);
      if (found != null) {
        return found;
      }
    }
    return null;
  }

  private static ModelPart.Cube largest(ModelPart part, int depth) {
    ModelPartAccessor access = (ModelPartAccessor) (Object) part;
    List<ModelPart.Cube> cubes = access.cxvisuals$cubes();
    ModelPart.Cube best = null;
    float volume = 0.0F;
    for (ModelPart.Cube cube : cubes) {
      float size = (cube.maxX - cube.minX) * (cube.maxY - cube.minY) * (cube.maxZ - cube.minZ);
      if (size > volume) {
        volume = size;
        best = cube;
      }
    }
    if (best != null || depth >= DEPTH) {
      return best;
    }
    for (ModelPart child : access.cxvisuals$children().values()) {
      ModelPart.Cube found = largest(child, depth + 1);
      if (found != null) {
        return found;
      }
    }
    return null;
  }
}
