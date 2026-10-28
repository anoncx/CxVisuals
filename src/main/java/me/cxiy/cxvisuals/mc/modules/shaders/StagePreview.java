package me.cxiy.cxvisuals.mc.modules.shaders;

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
import java.util.Optional;
import java.util.OptionalDouble;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.Gpu;
import me.cxiy.cxvisuals.mc.gui.StagePreviews;
import me.cxiy.cxvisuals.modules.environment.View;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.resources.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.joml.Vector4fc;
import org.lwjgl.system.MemoryStack;

abstract class StagePreview implements StagePreviews {
  private static final String BLOCK = "Stage";
  private static final RenderPipeline STAGE =
      RenderPipeline.builder()
          .withBindGroupLayout(
              BindGroupLayout.builder().withUniform(BLOCK, UniformType.UNIFORM_BUFFER).build())
          .withLocation(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "pipeline/jump_stage"))
          .withVertexShader(Identifier.withDefaultNamespace("core/screenquad"))
          .withFragmentShader(
              Identifier.fromNamespaceAndPath(CxVisualsClient.MOD_ID, "core/jump_stage"))
          .withDepthStencilState(new DepthStencilState(CompareOp.ALWAYS_PASS, true))
          .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
          .withColorTargetState(ColorTargetState.DEFAULT)
          .build();
  private static final int UNIFORM_SIZE =
      new Std140SizeCalculator()
          .putMat4f()
          .putMat4f()
          .putVec4()
          .putVec4()
          .putVec4()
          .putVec4()
          .get();
  private static final int TARGET_USAGE =
      GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
  private static final Optional<Vector4fc> CLEAR = Optional.of(new Vector4f(0.0F));
  private static final OptionalDouble CLEAR_DEPTH = OptionalDouble.of(0.0);
  private static final float FOV = (float) Math.toRadians(34.0);
  private static final float NEAR = 0.05F;
  private static final float FAR = 120.0F;
  private static final float AIM = 0.75F;
  private static final float MIDDLE = 0.9F;
  private static final float CHANNEL = 1.0F / 255.0F;
  private static final long IDLE_FRAMES = 240L;
  private static final Vector4fc WHOLE = new Vector4f(0.0F, 0.0F, 0.0F, -1.0F);

  protected final View view = new View();
  protected final Vector3f eye = new Vector3f();
  protected final Matrix4f lens = new Matrix4f();
  protected float clock;

  private final String name;
  private final Matrix4f projection = new Matrix4f();
  private final Matrix4f rotation = new Matrix4f();
  private final Matrix4f unproject = new Matrix4f();
  private final Vector3f axis = new Vector3f();
  private final Vector4f point = new Vector4f();
  private final Vector4f behind = new Vector4f();
  private final Vector4f ahead = new Vector4f();
  private GpuTexture back;
  private GpuTexture front;
  private GpuTexture depth;
  private GpuTextureView backView;
  private GpuTextureView frontView;
  private GpuTextureView depthView;
  private TextureSetup backSetup;
  private TextureSetup frontSetup;
  private GpuBuffer uniforms;
  private int width;
  private int height;
  private float corner;
  private boolean figure;
  private long frame;
  private long requested = -IDLE_FRAMES;
  private boolean queued;
  private boolean compiled;
  private boolean broken;

  StagePreview(String name) {
    this.name = name;
  }

  abstract boolean ready();

  abstract float distance();

  abstract float lift();

  abstract float figureX();

  abstract float figureZ();

  abstract float turn(float yaw);

  abstract float stride();

  abstract int accent();

  abstract float glow();

  abstract boolean compiles(GpuDevice device);

  abstract int build();

  abstract void light(CommandEncoder encoder, Vector4fc cut);

  abstract void upload(CommandEncoder encoder);

  abstract void drawBatch(RenderPass pass);

  abstract void releaseBatch();

  final void present() {
    render();
  }

  @Override
  public final Frame request(
      int targetWidth,
      int targetHeight,
      float yaw,
      float pitch,
      float time,
      float radius,
      boolean player) {
    if (broken || !ready()) {
      return null;
    }
    try {
      if (backSetup == null || width != targetWidth || height != targetHeight) {
        release();
        allocate(targetWidth, targetHeight);
      }
    } catch (RuntimeException e) {
      fail(e);
      return null;
    }
    clock = time;
    corner = radius;
    figure = !player;
    requested = frame;
    queued = true;
    aim(yaw, pitch);
    float lift = lift();
    float centreX = figureX() - eye.x;
    float centreZ = figureZ() - eye.z;
    project(point.set(centreX, lift + MIDDLE - eye.y, centreZ, 1.0F));
    float middleX = point.x;
    float middleY = point.y;
    project(point.set(centreX, lift + MIDDLE + 0.5F - eye.y, centreZ, 1.0F));
    float upper = point.y;
    project(point.set(centreX, lift + MIDDLE - 0.5F - eye.y, centreZ, 1.0F));
    return new Frame(
        backSetup,
        player ? frontSetup : null,
        middleX,
        middleY,
        point.y - upper,
        turn(yaw),
        stride());
  }

  private void project(Vector4f at) {
    lens.transform(at);
    at.set(at.x / at.w * 0.5F + 0.5F, 0.5F - at.y / at.w * 0.5F, 0.0F, 1.0F);
  }

  private void aim(float yaw, float pitch) {
    boolean zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
    float away = distance();
    float level = (float) Math.cos(pitch);
    eye.set(
        (float) Math.sin(yaw) * level * away,
        AIM + (float) Math.sin(pitch) * away,
        -(float) Math.cos(yaw) * level * away);
    rotation
        .setLookAt(eye.x, eye.y, eye.z, 0.0F, AIM, 0.0F, 0.0F, 1.0F, 0.0F)
        .setTranslation(0.0F, 0.0F, 0.0F);
    projection.setPerspective(FOV, (float) width / height, FAR, NEAR, zeroToOne);
    lens.set(projection).mul(rotation);
    lens.invert(unproject);
    view.x = eye.x;
    view.y = eye.y;
    view.z = eye.z;
    rotation.positiveZ(axis);
    view.fx = -axis.x;
    view.fy = -axis.y;
    view.fz = -axis.z;
    rotation.positiveX(axis);
    view.rx = axis.x;
    view.ry = axis.y;
    view.rz = axis.z;
    rotation.positiveY(axis);
    view.ux = axis.x;
    view.uy = axis.y;
    view.uz = axis.z;
  }

  private void render() {
    frame++;
    if (!queued) {
      if (backSetup != null && frame - requested > IDLE_FRAMES) {
        release();
        releaseBatch();
      }
      return;
    }
    queued = false;
    if (broken) {
      return;
    }
    try {
      GpuDevice device = RenderSystem.getDevice();
      if (!compiled && Gpu.ready()) {
        compiled = true;
        if (!Gpu.usable(STAGE) || !compiles(device)) {
          broken = true;
          CxVisualsClient.LOG.warn("{} preview failed to compile, the studio shows no stage", name);
          return;
        }
      }
      draw(device);
    } catch (RuntimeException e) {
      fail(e);
    }
  }

  private void draw(GpuDevice device) {
    boolean zeroToOne = device.getDeviceInfo().isZZeroToOne();
    int quads = build();
    int first = accent();
    CommandEncoder encoder = device.createCommandEncoder();
    try (MemoryStack memory = MemoryStack.stackPush()) {
      encoder.writeToBuffer(
          uniforms.slice(),
          Std140Builder.onStack(memory, UNIFORM_SIZE)
              .putMat4f(lens)
              .putMat4f(unproject)
              .putVec4(eye.x, eye.y, eye.z, lift())
              .putVec4(zeroToOne ? 1.0F : 2.0F, zeroToOne ? 0.0F : -1.0F, width, height)
              .putVec4(
                  (first >> 16 & 0xFF) * CHANNEL,
                  (first >> 8 & 0xFF) * CHANNEL,
                  (first & 0xFF) * CHANNEL,
                  glow())
              .putVec4(corner, figure ? 1.0F : 0.0F, figureX(), figureZ())
              .get());
    }
    float level = (float) Math.sqrt(view.fx * view.fx + view.fz * view.fz);
    float towardX = view.fx / level;
    float towardZ = view.fz / level;
    float split = (figureX() - eye.x) * towardX + (figureZ() - eye.z) * towardZ;
    if (quads > 0) {
      upload(encoder);
      light(encoder, figure ? WHOLE : behind.set(towardX, 0.0F, towardZ, split));
    }
    try (RenderPass pass =
        encoder.createRenderPass(
            () -> "CxVisuals " + name + " stage", backView, CLEAR, depthView, CLEAR_DEPTH)) {
      Gpu.use(pass, STAGE);
      pass.setUniform(BLOCK, uniforms);
      pass.draw(3, 1, 0, 0);
      if (quads > 0) {
        drawBatch(pass);
      }
    }
    if (figure) {
      return;
    }
    if (quads > 0) {
      light(encoder, ahead.set(-towardX, 0.0F, -towardZ, -split));
    }
    try (RenderPass pass =
        encoder.createRenderPass(
            () -> "CxVisuals " + name + " front", frontView, CLEAR, depthView, CLEAR_DEPTH)) {
      if (quads > 0) {
        drawBatch(pass);
      }
    }
  }

  private void allocate(int targetWidth, int targetHeight) {
    GpuDevice device = RenderSystem.getDevice();
    width = targetWidth;
    height = targetHeight;
    back =
        device.createTexture(
            () -> "cxvisuals_" + name + "_stage",
            TARGET_USAGE,
            GpuFormat.RGBA8_UNORM,
            width,
            height,
            1,
            1);
    front =
        device.createTexture(
            () -> "cxvisuals_" + name + "_stage_front",
            TARGET_USAGE,
            GpuFormat.RGBA8_UNORM,
            width,
            height,
            1,
            1);
    depth =
        device.createTexture(
            () -> "cxvisuals_" + name + "_stage_depth",
            GpuTexture.USAGE_RENDER_ATTACHMENT,
            GpuFormat.D32_FLOAT,
            width,
            height,
            1,
            1);
    backView = device.createTextureView(back);
    frontView = device.createTextureView(front);
    depthView = device.createTextureView(depth);
    backSetup =
        TextureSetup.singleTexture(
            backView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    frontSetup =
        TextureSetup.singleTexture(
            frontView, RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    if (uniforms == null) {
      uniforms =
          device.createBuffer(
              () -> "cxvisuals_" + name + "_stage_uniforms",
              GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
              UNIFORM_SIZE);
    }
  }

  private void release() {
    if (backSetup != null) {
      backView.close();
      frontView.close();
      depthView.close();
      back.close();
      front.close();
      depth.close();
      backSetup = null;
      frontSetup = null;
    }
  }

  private void fail(RuntimeException e) {
    broken = true;
    queued = false;
    release();
    releaseBatch();
    CxVisualsClient.LOG.error("{} preview failed, the studio shows no stage", name, e);
  }
}
