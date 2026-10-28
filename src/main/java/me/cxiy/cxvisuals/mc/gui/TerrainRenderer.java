package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Optional;
import java.util.OptionalDouble;
import me.cxiy.cxvisuals.gui.map.Camera3D;
import me.cxiy.cxvisuals.gui.map.TerrainFrame;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.Gpu;
import me.cxiy.cxvisuals.modules.map.MapAtlas;
import me.cxiy.cxvisuals.modules.map.TileKey;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

final class TerrainRenderer {
  private static final String FRAME = "MapTerrain";
  private static final String TILE = "MapTile";
  private static final int TARGET_USAGE =
      GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
  private static final int UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
  private static final int FRAME_SIZE =
      new Std140SizeCalculator()
          .putMat4f()
          .putVec4()
          .putVec4()
          .putVec4()
          .putVec4()
          .putMat4f()
          .putVec4()
          .putVec4()
          .putVec4()
          .get();
  private static final double CLOUD_CELL = 48.0;
  private static final double CLOUD_SPAN = CLOUD_CELL * 256.0;
  private static final double CLOUD_DRIFT = 2.5;
  private static final float CLOUD_DROP = 18.0F;
  private static final int TILE_SIZE =
      new Std140SizeCalculator().putVec4().putIVec4().putIVec4().putIVec4().get();
  private static final int VERTICES = 36;
  private static final OptionalDouble CLEAR_DEPTH = OptionalDouble.of(0.0);
  private static final Optional<Vector4fc> CLEAR =
      Optional.of(new Vector4f(0.0F, 0.0F, 0.0F, 0.0F));
  private static final float FOG_START = 0.55F;
  private static final VertexFormat FORMAT =
      VertexFormat.builder(0)
          .addAttribute("Position", GpuFormat.RGB32_FLOAT)
          .addAttribute("UV0", GpuFormat.RG32_FLOAT)
          .build();
  private static final RenderPipeline CLOUDS =
      RenderPipeline.builder()
          .withBindGroupLayout(
              BindGroupLayout.builder().withUniform(FRAME, UniformType.UNIFORM_BUFFER).build())
          .withLocation(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/map_clouds"))
          .withVertexShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/map_clouds"))
          .withFragmentShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/map_clouds"))
          .withVertexBinding(0, FORMAT)
          .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
          .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
          .withCull(false)
          .withColorTargetState(ColorTargetState.DEFAULT)
          .build();
  private static final RenderPipeline PIPELINE =
      RenderPipeline.builder()
          .withBindGroupLayout(
              BindGroupLayout.builder()
                  .withUniform("Sampler0", UniformType.COMBINED_IMAGE_SAMPLER)
                  .withUniform("Sampler1", UniformType.COMBINED_IMAGE_SAMPLER)
                  .withUniform("Sampler2", UniformType.COMBINED_IMAGE_SAMPLER)
                  .withUniform("Sampler3", UniformType.COMBINED_IMAGE_SAMPLER)
                  .withUniform(FRAME, UniformType.UNIFORM_BUFFER)
                  .withUniform(TILE, UniformType.UNIFORM_BUFFER)
                  .build())
          .withLocation(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/map_terrain"))
          .withVertexShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/map_terrain"))
          .withFragmentShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/map_terrain"))
          .withVertexBinding(0, FORMAT)
          .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
          .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, true))
          .withCull(false)
          .withColorTargetState(ColorTargetState.DEFAULT)
          .build();

  private final MapTextures textures;
  private final Matrix4f viewProjection = new Matrix4f();
  private final Matrix4f inverse = new Matrix4f();
  private final long born = System.nanoTime();
  private double cloudX;
  private double cloudZ;
  private int cloudBase;
  private int cloudTop;
  private boolean zeroToOne;
  private final int[] slot = new int[TerrainFrame.MAX_TILES];
  private final float[] originX = new float[TerrainFrame.MAX_TILES];
  private final float[] originZ = new float[TerrainFrame.MAX_TILES];
  private final int[] level = new int[TerrainFrame.MAX_TILES];
  private final int[] stride = new int[TerrainFrame.MAX_TILES];
  private final int[] near = new int[TerrainFrame.MAX_TILES * 4];
  private final int[] worldX = new int[TerrainFrame.MAX_TILES];
  private final int[] worldZ = new int[TerrainFrame.MAX_TILES];
  private GpuTexture color;
  private GpuTexture depth;
  private GpuTextureView colorView;
  private GpuTextureView depthView;
  private TextureSetup setup;
  private GpuBuffer frameUniforms;
  private GpuBuffer tileUniforms;
  private GpuBuffer template;
  private int tileStride;
  private int targetW;
  private int targetH;
  private boolean queued;
  private boolean compiled;
  private boolean broken;
  private MapAtlas atlas;
  private int count;
  private float eyeX;
  private float eyeY;
  private float eyeZ;
  private float fogEnd;
  private int fog;
  private float terrain;
  private float sideZ;
  private float sideX;
  private float targetY;

  TerrainRenderer(MapTextures textures) {
    this.textures = textures;
  }

  TextureSetup request(TerrainFrame frame, int width, int height) {
    if (broken || frame.count == 0 || width <= 0 || height <= 0) {
      return null;
    }
    try {
      GpuDevice device = RenderSystem.getDevice();
      if (!compile(device)) {
        return null;
      }
      if (color == null || targetW != width || targetH != height) {
        release();
        targetW = width;
        targetH = height;
        color =
            device.createTexture(
                () -> "cxvisuals_map_terrain",
                TARGET_USAGE,
                GpuFormat.RGBA8_UNORM,
                width,
                height,
                1,
                1);
        depth =
            device.createTexture(
                () -> "cxvisuals_map_terrain_depth",
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
      }
      Camera3D camera = frame.camera;
      zeroToOne = device.getDeviceInfo().isZZeroToOne();
      camera.matrix(zeroToOne, viewProjection);
      viewProjection.invert(inverse);
      double drift = (System.nanoTime() - born) / 1.0e9 * CLOUD_DRIFT;
      cloudX = mod(camera.targetX + drift, CLOUD_SPAN);
      cloudZ = mod(camera.targetZ + drift * 0.6, CLOUD_SPAN);
      cloudBase = frame.cloudBase;
      cloudTop = frame.cloudTop;
      atlas = frame.atlas;
      count = frame.count;
      eyeX = camera.eyeX();
      eyeY = camera.eyeY();
      eyeZ = camera.eyeZ();
      fogEnd = camera.distance * 2.2F + 900.0F;
      fog = frame.fog;
      terrain = frame.terrain;
      sideZ = eyeZ >= 0.0F ? 1.0F : -1.0F;
      sideX = eyeX >= 0.0F ? 1.0F : -1.0F;
      targetY = (float) camera.targetY;
      for (int i = 0; i < count; i++) {
        slot[i] = frame.slot[i];
        originX[i] = frame.originX[i];
        originZ[i] = frame.originZ[i];
        level[i] = frame.level[i];
        stride[i] = frame.stride[i];
        near[i * 4] = frame.north[i];
        near[i * 4 + 1] = frame.south[i];
        near[i * 4 + 2] = frame.west[i];
        near[i * 4 + 3] = frame.east[i];
        worldX[i] = (int) Math.round(camera.targetX + frame.originX[i]);
        worldZ[i] = (int) Math.round(camera.targetZ + frame.originZ[i]);
      }
      queued = true;
      return setup;
    } catch (RuntimeException e) {
      fail(e);
      return null;
    }
  }

  void flush() {
    if (!queued || broken) {
      return;
    }
    queued = false;
    try {
      GpuDevice device = RenderSystem.getDevice();
      int alignment = Math.max(16, device.getDeviceInfo().limits().minUniformOffsetAlignment());
      tileStride = (TILE_SIZE + alignment - 1) / alignment * alignment;
      long need = (long) tileStride * TerrainFrame.MAX_TILES;
      if (tileUniforms == null || tileUniforms.size() < need) {
        if (tileUniforms != null) {
          tileUniforms.close();
        }
        tileUniforms = device.createBuffer(() -> "cxvisuals_map_tiles", UNIFORM_USAGE, need);
      }
      CommandEncoder encoder = device.createCommandEncoder();
      try (MemoryStack stack = MemoryStack.stackPush()) {
        ByteBuffer data =
            Std140Builder.onStack(stack, FRAME_SIZE)
                .putMat4f(viewProjection)
                .putVec4(eyeX, eyeY, eyeZ, fogEnd)
                .putVec4(
                    ((fog >> 16) & 0xFF) / 255.0F,
                    ((fog >> 8) & 0xFF) / 255.0F,
                    (fog & 0xFF) / 255.0F,
                    fogEnd * FOG_START)
                .putVec4(terrain, sideZ, sideX, 0.0F)
                .putVec4(targetY, zeroToOne ? 1.0F : 0.0F, 0.0F, 0.0F)
                .putMat4f(inverse)
                .putVec4((float) cloudX, (float) cloudZ, -CLOUD_DROP, (float) CLOUD_CELL)
                .putVec4(red(cloudBase), green(cloudBase), blue(cloudBase), 1.0F)
                .putVec4(red(cloudTop), green(cloudTop), blue(cloudTop), 1.0F)
                .get();
        encoder.writeToBuffer(frameUniforms.slice(), data);
      }
      ByteBuffer tiles = MemoryUtil.memCalloc((int) need).order(ByteOrder.nativeOrder());
      try {
        for (int i = 0; i < count; i++) {
          int base = i * tileStride;
          tiles.putFloat(base, originX[i]);
          tiles.putFloat(base + 4, originZ[i]);
          tiles.putFloat(base + 8, 1 << level[i]);
          tiles.putFloat(base + 12, 0.0F);
          tiles.putInt(base + 16, slot[i]);
          tiles.putInt(base + 20, stride[i]);
          tiles.putInt(base + 32, near[i * 4]);
          tiles.putInt(base + 36, near[i * 4 + 1]);
          tiles.putInt(base + 40, near[i * 4 + 2]);
          tiles.putInt(base + 44, near[i * 4 + 3]);
          tiles.putInt(base + 48, worldX[i]);
          tiles.putInt(base + 52, worldZ[i]);
        }
        tiles.limit((int) need);
        encoder.writeToBuffer(tileUniforms.slice(), tiles);
      } finally {
        MemoryUtil.memFree(tiles);
      }
      TextureSetup maps = textures.setup(atlas);
      try (RenderPass pass =
          encoder.createRenderPass(
              () -> "CxVisuals map terrain", colorView, CLEAR, depthView, CLEAR_DEPTH)) {
        Gpu.use(pass, PIPELINE);
        pass.setUniform(FRAME, frameUniforms);
        Gpu.bind(pass, "Sampler0", maps.texure0(), maps.sampler0());
        Gpu.bind(pass, "Sampler1", maps.texure1(), maps.sampler1());
        Gpu.bind(pass, "Sampler2", maps.texure2(), maps.sampler2());
        Gpu.bind(pass, "Sampler3", textures.grounds(atlas), maps.sampler1());
        pass.setVertexBuffer(0, template.slice());
        for (int i = 0; i < count; i++) {
          int per = TileKey.EDGE / stride[i];
          pass.setUniform(TILE, tileUniforms.slice((long) i * tileStride, TILE_SIZE));
          pass.draw(VERTICES, per * per, 0, 0);
        }
        Gpu.use(pass, CLOUDS);
        pass.setUniform(FRAME, frameUniforms);
        pass.setVertexBuffer(0, template.slice());
        pass.draw(3, 1, 0, 0);
      }
    } catch (RuntimeException e) {
      fail(e);
    }
  }

  private boolean compile(GpuDevice device) {
    if (!compiled && Gpu.ready()) {
      compiled = true;
      if (!Gpu.usable(PIPELINE) || !Gpu.usable(CLOUDS)) {
        broken = true;
        CxVisualsClient.LOG.warn("Map terrain shader failed to compile, 3D view is off");
        return false;
      }
      frameUniforms = device.createBuffer(() -> "cxvisuals_map_frame", UNIFORM_USAGE, FRAME_SIZE);
      ByteBuffer data = columns();
      try {
        template = device.createBuffer(() -> "cxvisuals_map_column", GpuBuffer.USAGE_VERTEX, data);
      } finally {
        MemoryUtil.memFree(data);
      }
    }
    return !broken;
  }

  private static ByteBuffer columns() {
    float[][] top = {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {0, 1, 0}, {1, 1, 1}, {1, 1, 0}};
    float[][] zSide = {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {0, 0, 0}, {1, 1, 0}, {1, 0, 0}};
    float[][] xSide = {{0, 0, 0}, {0, 1, 0}, {0, 1, 1}, {0, 0, 0}, {0, 1, 1}, {0, 0, 1}};
    ByteBuffer out = MemoryUtil.memAlloc(VERTICES * 20).order(ByteOrder.nativeOrder());
    float[][][] faces = {top, zSide, xSide, top, zSide, xSide};
    for (int f = 0; f < faces.length; f++) {
      for (float[] corner : faces[f]) {
        out.putFloat(corner[0]).putFloat(corner[1]).putFloat(corner[2]);
        out.putFloat(f).putFloat(0.0F);
      }
    }
    out.flip();
    return out;
  }

  private static double mod(double value, double span) {
    double out = value % span;
    return out < 0.0 ? out + span : out;
  }

  private static float red(int argb) {
    return ((argb >> 16) & 0xFF) / 255.0F;
  }

  private static float green(int argb) {
    return ((argb >> 8) & 0xFF) / 255.0F;
  }

  private static float blue(int argb) {
    return (argb & 0xFF) / 255.0F;
  }

  private void release() {
    if (colorView != null) {
      colorView.close();
      depthView.close();
      color.close();
      depth.close();
      colorView = null;
      color = null;
    }
  }

  private void fail(RuntimeException e) {
    broken = true;
    queued = false;
    CxVisualsClient.LOG.error("Map terrain failed, the 3D view falls back to 2D", e);
    release();
  }

  boolean broken() {
    return broken;
  }
}
