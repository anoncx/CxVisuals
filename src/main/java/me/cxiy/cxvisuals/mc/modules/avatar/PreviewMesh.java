package me.cxiy.cxvisuals.mc.modules.avatar;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.modules.avatar.anim.Animator;
import me.cxiy.cxvisuals.modules.avatar.anim.ClipMap;
import me.cxiy.cxvisuals.modules.avatar.anim.ClipSet;
import me.cxiy.cxvisuals.modules.avatar.anim.Motion;
import me.cxiy.cxvisuals.modules.avatar.bake.BakedAvatar;
import me.cxiy.cxvisuals.modules.avatar.bake.Texture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ARGB;
import org.lwjgl.system.MemoryUtil;

final class PreviewMesh implements AutoCloseable {
  static final VertexFormat FORMAT =
      VertexFormat.builder(0)
          .addAttribute("Position", GpuFormat.RGB32_FLOAT)
          .addAttribute("Normal", GpuFormat.RGBA8_SNORM)
          .addAttribute("UV0", GpuFormat.RG32_FLOAT)
          .addAttribute("Color", GpuFormat.RGBA8_UNORM)
          .addAttribute("Joints", GpuFormat.RGBA8_UINT)
          .addAttribute("Weights", GpuFormat.RGBA8_UNORM)
          .build();

  private static final int PIXEL_ART = 128;
  private static final float LONGEST_FRAME = 0.1F;

  final int revision;
  final BakedAvatar avatar;
  final GpuBuffer vertices;
  final GpuBuffer indices;
  final int[] firstIndex;
  final int[] indexCount;
  final int[] baseVertex;
  final GpuTextureView[] views;
  final GpuSampler[] samplers;
  final boolean skinned;
  private final DynamicTexture[] textures;
  private final DynamicTexture white;
  private final Animator animator;
  private final Motion idle = new Motion();
  private long animatedFrame = -1L;
  private long lastNanos;
  int clipRevision = -1;
  long used;
  private ClipSet set;
  private Animator audition;
  private long auditionFrame = -1L;
  private long auditionNanos;

  PreviewMesh(BakedAvatar avatar, int revision) {
    this.revision = revision;
    this.avatar = avatar;
    this.skinned = avatar.skinned();
    this.animator = new Animator(avatar);
    idle.onGround = true;
    GpuDevice device = RenderSystem.getDevice();
    int parts = avatar.parts.length;
    firstIndex = new int[parts];
    indexCount = new int[parts];
    baseVertex = new int[parts];
    int vertexTotal = 0;
    int indexTotal = 0;
    for (int p = 0; p < parts; p++) {
      BakedAvatar.Part part = avatar.parts[p];
      baseVertex[p] = vertexTotal;
      firstIndex[p] = indexTotal;
      indexCount[p] = part.indices.length;
      vertexTotal += part.vertexCount();
      indexTotal += part.indices.length;
    }
    int stride = FORMAT.getVertexSize();
    ByteBuffer vertexData =
        MemoryUtil.memAlloc(Math.max(stride, vertexTotal * stride)).order(ByteOrder.nativeOrder());
    ByteBuffer indexData =
        MemoryUtil.memAlloc(Math.max(4, indexTotal * 4)).order(ByteOrder.nativeOrder());
    try {
      for (BakedAvatar.Part part : avatar.parts) {
        pack(part, vertexData);
        for (int index : part.indices) {
          indexData.putInt(index);
        }
      }
      vertexData.flip();
      indexData.flip();
      vertices =
          device.createBuffer(
              () -> "cxvisuals_avatar_preview_vertices", GpuBuffer.USAGE_VERTEX, vertexData);
      indices =
          device.createBuffer(
              () -> "cxvisuals_avatar_preview_indices", GpuBuffer.USAGE_INDEX, indexData);
    } finally {
      MemoryUtil.memFree(vertexData);
      MemoryUtil.memFree(indexData);
    }
    white = upload("white", new Texture(1, 1, new int[] {0xFFFFFFFF}));
    textures = new DynamicTexture[avatar.textures.length];
    GpuSampler[] textureSamplers = new GpuSampler[avatar.textures.length];
    for (int i = 0; i < textures.length; i++) {
      Texture texture = avatar.textures[i];
      textures[i] = upload(Integer.toString(i), texture);
      boolean pixelArt = Math.max(texture.width(), texture.height()) <= PIXEL_ART;
      textureSamplers[i] =
          RenderSystem.getSamplerCache()
              .getRepeat(pixelArt ? FilterMode.NEAREST : FilterMode.LINEAR);
    }
    views = new GpuTextureView[parts];
    samplers = new GpuSampler[parts];
    for (int p = 0; p < parts; p++) {
      int texture = avatar.parts[p].texture;
      boolean textured = texture >= 0 && texture < textures.length;
      views[p] = textured ? textures[texture].getTextureView() : white.getTextureView();
      samplers[p] =
          textured
              ? textureSamplers[texture]
              : RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);
    }
  }

  void clips(ClipMap map, int revision) {
    clipRevision = revision;
    set = ClipSet.of(avatar.clips, map);
    animator.clips(set);
    if (audition != null) {
      audition.clips(set);
    }
  }

  Animator animate(long frame, boolean clips, int request) {
    if (request < 0) {
      if (frame != animatedFrame) {
        animatedFrame = frame;
        long now = System.nanoTime();
        float dt =
            lastNanos == 0L ? 1.0F / 60.0F : Math.min(LONGEST_FRAME, (now - lastNanos) / 1.0e9F);
        lastNanos = now;
        animator.update(idle, dt, 1.0, false, clips);
      }
      return animator;
    }
    if (audition == null) {
      audition = new Animator(avatar);
      if (set != null) {
        audition.clips(set);
      }
    }
    audition.audition(
        request < Canvas.AUDITION_CLIP ? request : -1,
        request < Canvas.AUDITION_CLIP ? -1 : request - Canvas.AUDITION_CLIP);
    if (frame != auditionFrame) {
      auditionFrame = frame;
      long now = System.nanoTime();
      float dt =
          auditionNanos == 0L
              ? 1.0F / 60.0F
              : Math.min(LONGEST_FRAME, (now - auditionNanos) / 1.0e9F);
      auditionNanos = now;
      audition.update(idle, dt, 1.0, false, true);
    }
    return audition;
  }

  private static DynamicTexture upload(String label, Texture texture) {
    NativeImage image =
        new NativeImage(NativeImage.Format.RGBA, texture.width(), texture.height(), false);
    int[] argb = texture.argb();
    for (int y = 0; y < texture.height(); y++) {
      int row = y * texture.width();
      for (int x = 0; x < texture.width(); x++) {
        image.setPixel(x, y, argb[row + x]);
      }
    }
    DynamicTexture dynamic = new DynamicTexture(() -> "cxvisuals_avatar_preview_" + label, image);
    dynamic.upload();
    return dynamic;
  }

  private static void pack(BakedAvatar.Part part, ByteBuffer out) {
    int count = part.vertexCount();
    for (int v = 0; v < count; v++) {
      out.putFloat(part.positions[v * 3]);
      out.putFloat(part.positions[v * 3 + 1]);
      out.putFloat(part.positions[v * 3 + 2]);
      out.put(snorm(part.normals[v * 3]));
      out.put(snorm(part.normals[v * 3 + 1]));
      out.put(snorm(part.normals[v * 3 + 2]));
      out.put((byte) 0);
      out.putFloat(part.uvs[v * 2]);
      out.putFloat(part.uvs[v * 2 + 1]);
      int color = ARGB.multiply(part.color, part.colors == null ? 0xFFFFFFFF : part.colors[v]);
      out.put((byte) ARGB.red(color));
      out.put((byte) ARGB.green(color));
      out.put((byte) ARGB.blue(color));
      out.put((byte) ARGB.alpha(color));
      for (int k = 0; k < 4; k++) {
        out.put(part.joints == null ? 0 : part.joints[v * 4 + k]);
      }
      for (int k = 0; k < 4; k++) {
        float weight = part.weights == null ? 0.0F : part.weights[v * 4 + k];
        out.put((byte) Math.round(Math.max(0.0F, Math.min(1.0F, weight)) * 255.0F));
      }
    }
  }

  private static byte snorm(float value) {
    return (byte) Math.round(Math.max(-1.0F, Math.min(1.0F, value)) * 127.0F);
  }

  @Override
  public void close() {
    vertices.close();
    indices.close();
    white.close();
    for (DynamicTexture texture : textures) {
      texture.close();
    }
  }
}
