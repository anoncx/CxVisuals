package me.cxiy.cxvisuals.mc.modules.avatar;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import me.cxiy.cxvisuals.modules.avatar.anim.Animator;
import me.cxiy.cxvisuals.modules.avatar.anim.ClipMap;
import me.cxiy.cxvisuals.modules.avatar.anim.ClipSet;
import me.cxiy.cxvisuals.modules.avatar.anim.Motion;
import me.cxiy.cxvisuals.modules.avatar.bake.BakedAvatar;
import me.cxiy.cxvisuals.modules.avatar.bake.Tune;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

final class WorldRenderer {
  private static final float SAME_FRAME = 0.002F;
  private static final float LONGEST_FRAME = 0.1F;
  private static final int GHOST = 0x26FFFFFF;

  private final class Part implements SubmitNodeCollector.CustomGeometryRenderer {
    final BakedAvatar.Part source;
    final RenderType solid;
    final RenderType ghost;
    final int[] colors;
    final float[] positions;
    final float[] normals;
    int light;
    int overlay;
    int tint;

    Part(BakedAvatar.Part source, AvatarTextures textures) {
      this.source = source;
      Identifier texture = textures.texture(source.texture);
      this.solid =
          source.blend
              ? AvatarTypes.translucent(texture)
              : source.doubleSided ? AvatarTypes.doubleSided(texture) : AvatarTypes.culled(texture);
      this.ghost = AvatarTypes.translucent(texture);
      int count = source.vertexCount();
      this.colors = new int[count];
      for (int v = 0; v < count; v++) {
        int vertex = source.colors == null ? 0xFFFFFFFF : source.colors[v];
        colors[v] = ARGB.multiply(source.color, vertex);
      }
      boolean skinned = source.joints != null;
      this.positions = skinned ? new float[source.positions.length] : source.positions;
      this.normals = skinned ? new float[source.normals.length] : source.normals;
    }

    @Override
    public void render(PoseStack.Pose pose, VertexConsumer consumer) {
      Matrix4f m = pose.pose();
      Matrix3f n = pose.normal();
      float m00 = m.m00();
      float m01 = m.m01();
      float m02 = m.m02();
      float m10 = m.m10();
      float m11 = m.m11();
      float m12 = m.m12();
      float m20 = m.m20();
      float m21 = m.m21();
      float m22 = m.m22();
      float m30 = m.m30();
      float m31 = m.m31();
      float m32 = m.m32();
      float n00 = n.m00();
      float n01 = n.m01();
      float n02 = n.m02();
      float n10 = n.m10();
      float n11 = n.m11();
      float n12 = n.m12();
      float n20 = n.m20();
      float n21 = n.m21();
      float n22 = n.m22();
      float[] p = positions;
      float[] q = normals;
      float[] uv = source.uvs;
      int[] idx = source.indices;
      int lightCoords = light;
      int overlayCoords = overlay;
      boolean tinted = tint != -1;
      for (int i = 0; i < idx.length; i++) {
        int v = idx[i];
        int i3 = v * 3;
        float x = p[i3];
        float y = p[i3 + 1];
        float z = p[i3 + 2];
        float nx = q[i3];
        float ny = q[i3 + 1];
        float nz = q[i3 + 2];
        float tx = n00 * nx + n10 * ny + n20 * nz;
        float ty = n01 * nx + n11 * ny + n21 * nz;
        float tz = n02 * nx + n12 * ny + n22 * nz;
        float length = tx * tx + ty * ty + tz * tz;
        if (length > 1e-12F) {
          float inv = 1.0F / (float) Math.sqrt(length);
          tx *= inv;
          ty *= inv;
          tz *= inv;
        }
        int color = tinted ? ARGB.multiply(colors[v], tint) : colors[v];
        consumer.addVertex(
            m00 * x + m10 * y + m20 * z + m30,
            m01 * x + m11 * y + m21 * z + m31,
            m02 * x + m12 * y + m22 * z + m32,
            color,
            uv[v * 2],
            uv[v * 2 + 1],
            overlayCoords,
            lightCoords,
            tx,
            ty,
            tz);
      }
    }

    void skin(float[] palette) {
      if (source.joints == null) {
        return;
      }
      float[] sp = source.positions;
      float[] sn = source.normals;
      byte[] joints = source.joints;
      float[] weights = source.weights;
      int count = source.vertexCount();
      for (int v = 0; v < count; v++) {
        int i3 = v * 3;
        int i4 = v * 4;
        float x = sp[i3];
        float y = sp[i3 + 1];
        float z = sp[i3 + 2];
        float nx = sn[i3];
        float ny = sn[i3 + 1];
        float nz = sn[i3 + 2];
        float total = 0.0F;
        float ox = 0.0F;
        float oy = 0.0F;
        float oz = 0.0F;
        float px = 0.0F;
        float py = 0.0F;
        float pz = 0.0F;
        for (int k = 0; k < 4; k++) {
          float w = weights[i4 + k];
          if (w <= 0.0F) {
            continue;
          }
          int o = (joints[i4 + k] & 0xFF) * 16;
          total += w;
          ox += w * (palette[o] * x + palette[o + 4] * y + palette[o + 8] * z + palette[o + 12]);
          oy +=
              w * (palette[o + 1] * x + palette[o + 5] * y + palette[o + 9] * z + palette[o + 13]);
          oz +=
              w * (palette[o + 2] * x + palette[o + 6] * y + palette[o + 10] * z + palette[o + 14]);
          px += w * (palette[o] * nx + palette[o + 4] * ny + palette[o + 8] * nz);
          py += w * (palette[o + 1] * nx + palette[o + 5] * ny + palette[o + 9] * nz);
          pz += w * (palette[o + 2] * nx + palette[o + 6] * ny + palette[o + 10] * nz);
        }
        float rest = 1.0F - total;
        if (rest > 0.0F) {
          ox += rest * x;
          oy += rest * y;
          oz += rest * z;
          px += rest * nx;
          py += rest * ny;
          pz += rest * nz;
        }
        positions[i3] = ox;
        positions[i3 + 1] = oy;
        positions[i3 + 2] = oz;
        normals[i3] = px;
        normals[i3 + 1] = py;
        normals[i3 + 2] = pz;
      }
    }
  }

  private final BakedAvatar avatar;
  private final Animator animator;
  private final Part[] parts;
  private final Motion motion = new Motion();
  private final Matrix4f root = new Matrix4f();
  private final Matrix4f fit = new Matrix4f();
  private long lastNanos;

  WorldRenderer(BakedAvatar avatar, AvatarTextures textures) {
    this.avatar = avatar;
    this.animator = new Animator(avatar);
    this.parts = new Part[avatar.parts.length];
    for (int i = 0; i < parts.length; i++) {
      parts[i] = new Part(avatar.parts[i], textures);
    }
    fit.set(avatar.fit);
  }

  void clips(ClipMap map) {
    animator.clips(ClipSet.of(avatar.clips, map));
  }

  void submit(
      AvatarRenderState state,
      Player player,
      PoseStack poseStack,
      SubmitNodeCollector collector,
      Tune tune,
      double motionScale,
      boolean squash) {
    long now = System.nanoTime();
    float dt = lastNanos == 0L ? 0.0F : (now - lastNanos) / 1.0e9F;
    if (dt >= SAME_FRAME || lastNanos == 0L) {
      lastNanos = now;
      fill(state, player);
      animator.update(motion, Math.min(dt, LONGEST_FRAME), motionScale, squash, tune.clips());
      float[] palette = animator.palette();
      if (palette.length > 0) {
        for (Part part : parts) {
          part.skin(palette);
        }
      }
    }

    poseStack.pushPose();
    orient(state, poseStack);
    float scale = state.scale * (float) (tune.size() / 100.0);
    poseStack.translate(0.0F, (float) tune.lift(), 0.0F);
    poseStack.scale(scale, scale, scale);
    root.set(animator.root());
    poseStack.mulPose(root);
    poseStack.mulPose(fit);

    boolean ghost = state.isInvisible;
    int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
    for (Part part : parts) {
      part.light = state.lightCoords;
      part.overlay = overlay;
      part.tint = ghost ? GHOST : -1;
      collector.submitCustomGeometry(poseStack, ghost ? part.ghost : part.solid, part);
    }
    poseStack.popPose();
  }

  private static void orient(AvatarRenderState state, PoseStack poseStack) {
    if (state.hasPose(Pose.SLEEPING)) {
      Direction bed = state.bedOrientation;
      if (bed != null) {
        float offset = state.eyeHeight - 0.1F;
        poseStack.translate(-bed.getStepX() * offset, 0.0F, -bed.getStepZ() * offset);
        float angle =
            switch (bed) {
              case SOUTH -> 90.0F;
              case NORTH -> 270.0F;
              case EAST -> 180.0F;
              default -> 0.0F;
            };
        poseStack.rotate(Axis.YP.rotationDegrees(angle));
      }
      poseStack.rotate(Axis.ZP.rotationDegrees(90.0F));
      poseStack.rotate(Axis.YP.rotationDegrees(90.0F));
      return;
    }
    poseStack.rotate(Axis.YP.rotationDegrees(-state.bodyRot));
    if (state.deathTime > 0.0F) {
      float fall = Math.min(1.0F, (float) Math.sqrt((state.deathTime - 1.0F) / 20.0F * 1.6F));
      poseStack.rotate(Axis.ZP.rotationDegrees(fall * 90.0F));
    }
  }

  private void fill(AvatarRenderState state, Player player) {
    Vec3 movement = player.getDeltaMovement();
    motion.walkPos = state.walkAnimationPos;
    motion.walkSpeed = state.walkAnimationSpeed;
    motion.speed = (float) Math.sqrt(movement.x * movement.x + movement.z * movement.z);
    motion.verticalSpeed = (float) movement.y;
    motion.yawRate = (player.yBodyRot - player.yBodyRotO) * 20.0F;
    motion.pitch = player.getXRot();
    motion.swim = state.swimAmount;
    motion.attack = state.swingAnimation;
    motion.deathTime = state.deathTime;
    motion.hurt = state.hasRedOverlay && state.deathTime <= 0.0F;
    motion.onGround = player.onGround();
    motion.crouching = state.isCrouching;
    motion.flying = state.isFallFlying;
    motion.sleeping = state.hasPose(Pose.SLEEPING);
    motion.passenger = state.isPassenger;
  }
}
