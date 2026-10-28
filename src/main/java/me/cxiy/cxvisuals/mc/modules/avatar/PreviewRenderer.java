package me.cxiy.cxvisuals.mc.modules.avatar;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.IntFunction;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.Gpu;
import me.cxiy.cxvisuals.mc.gui.ModelPreviews;
import me.cxiy.cxvisuals.modules.avatar.anim.Animator;
import me.cxiy.cxvisuals.modules.avatar.bake.BakedAvatar;
import me.cxiy.cxvisuals.modules.avatar.library.AvatarEntry;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;

final class PreviewRenderer implements ModelPreviews {
  private static final String UNIFORMS = "AvatarPreview";
  private static final String ALBEDO = "Albedo";
  private static final int JOINTS = 128;
  private static final int UNIFORM_SIZE;
  private static final int TARGET_USAGE =
      GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
  private static final int UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
  private static final long TARGET_LIFE = 300L;
  private static final long MESH_LIFE = 900L;
  private static final float FOV = (float) Math.toRadians(26.0);
  private static final float ELEVATION = (float) Math.toRadians(10.0);
  private static final Optional<Vector4fc> CLEAR =
      Optional.of(new Vector4f(0.0F, 0.0F, 0.0F, 0.0F));
  private static final OptionalDouble CLEAR_DEPTH = OptionalDouble.of(0.0);
  private static final long EVICT_EVERY = 63L;
  private static final float CUTOUT = 0.1F;
  private static final float FILL = 0.8F;

  static {
    Std140SizeCalculator size =
        new Std140SizeCalculator().putMat4f().putMat4f().putVec4().putVec4().putVec4().putVec4();
    for (int i = 0; i < JOINTS; i++) {
      size.putMat4f();
    }
    UNIFORM_SIZE = size.get();
  }

  private static final RenderPipeline CULLED = pipeline("avatar_preview_cull", true, false);
  private static final RenderPipeline DOUBLE_SIDED = pipeline("avatar_preview", false, false);
  private static final RenderPipeline TRANSLUCENT =
      pipeline("avatar_preview_translucent", false, true);

  private static RenderPipeline pipeline(String name, boolean cull, boolean blend) {
    RenderPipeline.Builder builder =
        RenderPipeline.builder()
            .withBindGroupLayout(
                BindGroupLayout.builder()
                    .withUniform(ALBEDO, UniformType.COMBINED_IMAGE_SAMPLER)
                    .withUniform(UNIFORMS, UniformType.UNIFORM_BUFFER)
                    .build())
            .withLocation(
                Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/" + name))
            .withVertexShader(
                Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/avatar_preview"))
            .withFragmentShader(
                Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/avatar_preview"))
            .withVertexBinding(0, PreviewMesh.FORMAT)
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
            .withCull(cull);
    return builder
        .withColorTargetState(
            blend
                ? new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
                : ColorTargetState.DEFAULT)
        .build();
  }

  private static final class Target {
    final int handle;
    final int width;
    final int height;
    final GpuTexture color;
    final GpuTexture depth;
    final GpuTextureView colorView;
    final GpuTextureView depthView;
    final TextureSetup setup;
    final GpuBuffer uniforms;
    int revision = -1;
    long used;
    float yaw;
    int audition = -1;
    boolean queued;

    Target(GpuDevice device, int handle, int width, int height) {
      this.handle = handle;
      this.width = width;
      this.height = height;
      color =
          device.createTexture(
              () -> "cxvisuals_avatar_preview",
              TARGET_USAGE,
              GpuFormat.RGBA8_UNORM,
              width,
              height,
              1,
              1);
      depth =
          device.createTexture(
              () -> "cxvisuals_avatar_preview_depth",
              GpuTexture.USAGE_RENDER_ATTACHMENT,
              GpuFormat.D32_FLOAT,
              width,
              height,
              1,
              1);
      colorView = device.createTextureView(color);
      depthView = device.createTextureView(depth);
      setup =
          TextureSetup.singleTexture(
              colorView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
      uniforms =
          device.createBuffer(
              () -> "cxvisuals_avatar_preview_uniforms", UNIFORM_USAGE, UNIFORM_SIZE);
    }

    void close() {
      colorView.close();
      depthView.close();
      color.close();
      depth.close();
      uniforms.close();
    }
  }

  private final IntFunction<AvatarEntry> lookup;
  private final Long2ObjectOpenHashMap<Target> targets = new Long2ObjectOpenHashMap<>();
  private final Int2ObjectOpenHashMap<PreviewMesh> meshes = new Int2ObjectOpenHashMap<>();
  private final List<Target> queue = new ArrayList<>();
  private final Matrix4f projection = new Matrix4f();
  private final Matrix4f view = new Matrix4f();
  private final Matrix4f model = new Matrix4f();
  private final Matrix4f scratch = new Matrix4f();
  private final Matrix4f mvp = new Matrix4f();
  private final Matrix4f normal = new Matrix4f();
  private final Matrix4f joint = new Matrix4f();
  private long frame;
  private boolean compiled;
  private boolean broken;

  PreviewRenderer(IntFunction<AvatarEntry> lookup) {
    this.lookup = lookup;
  }

  @Override
  public TextureSetup request(
      int handle, int width, int height, float yaw, boolean live, int audition) {
    if (broken) {
      return null;
    }
    AvatarEntry entry = lookup.apply(handle);
    if (entry == null || entry.state() != AvatarEntry.State.READY || entry.preview() == null) {
      return null;
    }
    try {
      PreviewMesh mesh = meshes.get(handle);
      if (mesh == null || mesh.revision != entry.revision()) {
        if (mesh != null) {
          mesh.close();
        }
        mesh = new PreviewMesh(entry.preview(), entry.revision());
        meshes.put(handle, mesh);
      }
      mesh.used = frame;
      if (mesh.clipRevision != entry.clipRevision()) {
        mesh.clips(entry.clipMap(), entry.clipRevision());
      }
      long key =
          (long) handle << 33 | (audition >= 0 ? 1L << 32 : 0L) | (long) width << 16 | height;
      Target target = targets.get(key);
      if (target == null) {
        target = new Target(RenderSystem.getDevice(), handle, width, height);
        targets.put(key, target);
      }
      target.used = frame;
      if ((live
              || target.revision != entry.revision()
              || target.yaw != yaw
              || target.audition != audition)
          && !target.queued) {
        target.queued = true;
        target.yaw = yaw;
        target.audition = audition;
        target.revision = entry.revision();
        queue.add(target);
      }
      return target.setup;
    } catch (RuntimeException e) {
      fail(e);
      return null;
    }
  }

  void flush() {
    if (broken) {
      queue.clear();
      return;
    }
    try {
      if (!queue.isEmpty() && compile()) {
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        for (Target target : queue) {
          PreviewMesh mesh = meshes.get(target.handle);
          AvatarEntry entry = lookup.apply(target.handle);
          if (mesh != null && entry != null) {
            draw(encoder, target, mesh, entry.tune().clips());
          }
        }
      }
    } catch (RuntimeException e) {
      fail(e);
    } finally {
      for (Target target : queue) {
        target.queued = false;
      }
      queue.clear();
    }
    frame++;
    if ((frame & EVICT_EVERY) == 0L) {
      evict();
    }
  }

  private boolean compile() {
    if (!compiled && Gpu.ready()) {
      compiled = true;
      GpuDevice device = RenderSystem.getDevice();
      if (!Gpu.usable(CULLED) || !Gpu.usable(DOUBLE_SIDED) || !Gpu.usable(TRANSLUCENT)) {
        broken = true;
        CxVisualsClient.LOG.warn(
            "Avatar preview shader failed to compile, the gallery shows no previews");
        return false;
      }
    }
    return true;
  }

  private void draw(CommandEncoder encoder, Target target, PreviewMesh mesh, boolean clips) {
    Animator pose = mesh.animate(frame, clips, target.audition);
    float[] palette = pose.palette();
    BakedAvatar avatar = mesh.avatar;
    float footprint =
        0.5F * (float) Math.sqrt(avatar.width * avatar.width + avatar.depth * avatar.depth);
    float radius = (float) Math.sqrt(footprint * footprint + avatar.height * avatar.height * 0.25F);
    float aspect = (float) target.width / target.height;
    float tanV = (float) Math.tan(FOV * 0.5F);
    float tanH = tanV * aspect;
    float distance =
        Math.max(avatar.height * 0.5F / (FILL * tanV), footprint / (FILL * tanH)) + footprint;
    boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
    projection.setPerspective(
        FOV,
        aspect,
        distance + radius * 2.0F,
        Math.max(0.01F, distance - radius * 1.5F),
        zeroToOne);
    view.translation(0.0F, 0.0F, -distance).rotateX(ELEVATION);
    model.rotationY(target.yaw).translate(0.0F, -avatar.height * 0.5F, 0.0F);
    scratch.set(pose.root());
    model.mul(scratch);
    scratch.set(avatar.fit);
    model.mul(scratch);
    view.mul(model, normal);
    projection.mul(normal, mvp);
    try (MemoryStack stack = MemoryStack.stackPush()) {
      Std140Builder builder =
          Std140Builder.onStack(stack, UNIFORM_SIZE)
              .putMat4f(mvp)
              .putMat4f(normal)
              .putVec4(-0.45F, 0.75F, 0.55F, 0.0F)
              .putVec4(0.62F, 0.6F, 0.57F, 0.62F)
              .putVec4(0.75F, 0.8F, 0.92F, 0.22F)
              .putVec4(mesh.skinned ? 1.0F : 0.0F, CUTOUT, 0.0F, 0.0F);
      int joints = Math.min(JOINTS, palette.length / 16);
      for (int i = 0; i < JOINTS; i++) {
        if (i < joints) {
          joint.set(palette, i * 16);
        } else {
          joint.identity();
        }
        builder.putMat4f(joint);
      }
      ByteBuffer data = builder.get();
      encoder.writeToBuffer(target.uniforms.slice(), data);
    }
    try (RenderPass pass =
        encoder.createRenderPass(
            () -> "CxVisuals avatar preview",
            target.colorView,
            CLEAR,
            target.depthView,
            CLEAR_DEPTH)) {
      pass.setUniform(UNIFORMS, target.uniforms);
      pass.setVertexBuffer(0, mesh.vertices.slice());
      pass.setIndexBuffer(mesh.indices, IndexType.INT);
      drawParts(pass, mesh, false);
      drawParts(pass, mesh, true);
    }
  }

  private static void drawParts(RenderPass pass, PreviewMesh mesh, boolean blend) {
    for (int p = 0; p < mesh.indexCount.length; p++) {
      BakedAvatar.Part part = mesh.avatar.parts[p];
      if (mesh.indexCount[p] == 0 || part.blend != blend) {
        continue;
      }
      Gpu.use(pass, blend ? TRANSLUCENT : part.doubleSided ? DOUBLE_SIDED : CULLED);
      Gpu.bind(pass, ALBEDO, mesh.views[p], mesh.samplers[p]);
      pass.drawIndexed(mesh.indexCount[p], 1, mesh.firstIndex[p], mesh.baseVertex[p], 0);
    }
  }

  private void evict() {
    ObjectIterator<Long2ObjectMap.Entry<Target>> targetIterator =
        targets.long2ObjectEntrySet().fastIterator();
    while (targetIterator.hasNext()) {
      Target target = targetIterator.next().getValue();
      if (frame - target.used > TARGET_LIFE) {
        target.close();
        targetIterator.remove();
      }
    }
    ObjectIterator<Int2ObjectMap.Entry<PreviewMesh>> meshIterator =
        meshes.int2ObjectEntrySet().fastIterator();
    while (meshIterator.hasNext()) {
      PreviewMesh mesh = meshIterator.next().getValue();
      if (frame - mesh.used > MESH_LIFE) {
        mesh.close();
        meshIterator.remove();
      }
    }
  }

  private void fail(RuntimeException e) {
    broken = true;
    CxVisualsClient.LOG.error("Avatar previews failed, the gallery shows no previews", e);
    for (Target target : targets.values()) {
      target.close();
    }
    targets.clear();
    for (PreviewMesh mesh : meshes.values()) {
      mesh.close();
    }
    meshes.clear();
    queue.clear();
  }
}
