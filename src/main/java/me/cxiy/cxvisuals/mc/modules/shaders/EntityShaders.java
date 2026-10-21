package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.ByteBuffer;
import java.util.List;
import me.cxiy.cxvisuals.core.settings.Palette;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.IrisCompat;
import me.cxiy.cxvisuals.modules.shaders.Effect;
import me.cxiy.cxvisuals.modules.shaders.ScreenBounds;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

public final class EntityShaders {
  private static final int UNIFORM_SIZE =
      new Std140SizeCalculator()
              .putMat4f()
              .putMat4f()
              .putVec4()
              .putVec4()
              .putVec4()
              .putVec4()
              .putVec4()
              .putVec4()
              .putVec4()
              .putVec4()
              .putVec4()
              .putVec4()
              .putVec4()
              .putVec4()
              .putVec4()
              .putVec4()
              .get()
          + 25 * 16;
  private static final int UNIFORM_USAGE = GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST;
  private static final float CLOCK_WRAP = 3600.0F;
  private static final float LONGEST_FRAME = 0.1F;
  private static final float CHANNEL = 1.0F / 255.0F;
  private static final float HAND_DISTANCE = 0.7F;
  private static final float HAND_REACH = 0.4F;
  private static final int MARGIN = 4;
  private static final int CENTRES = 32;

  private static final Silhouettes WORLD = new Silhouettes();
  private static final Silhouettes HAND = new Silhouettes();
  private static final ScreenBounds BOUNDS = new ScreenBounds();
  private static final Matrix4f PROJECTION = new Matrix4f();
  private static final Matrix4f LENS = new Matrix4f();
  private static final Matrix4f UNPROJECT = new Matrix4f();
  private static final Matrix4f ANCHOR = new Matrix4f();
  private static final float[] CENTRE = new float[CENTRES * 4];
  private static final float NEAREST_CENTRE = 0.6F;

  private static GpuBuffer uniforms;
  private static int passes;
  private static float clock;
  private static long lastNanos;
  private static int centres;
  private static boolean broken;

  private EntityShaders() {}

  public static int passes() {
    return passes;
  }

  public static Matrix4f lens() {
    return PROJECTION;
  }

  public static void projection(Matrix4fc matrix) {
    PROJECTION.set(matrix);
    HAND.drop();
  }

  public static void capture(
      LevelRenderState level, EntityRenderDispatcher dispatcher, PoseStack poseStack) {
    if (IrisCompat.isRenderingShadowPass()) {
      return;
    }
    WORLD.drop();
    ShadersModule module = Shaders.module();
    if (broken || module == null || !module.entitiesLive()) {
      return;
    }
    boolean self = module.self.get();
    boolean players = module.players.get();
    boolean others = !module.others.get().isEmpty();
    if (!self && !players && !others) {
      return;
    }
    Minecraft mc = Minecraft.getInstance();
    RenderTarget main = mc.gameRenderer.mainRenderTarget();
    int selfId = mc.player == null ? Integer.MIN_VALUE : mc.player.getId();
    CameraRenderState camera = level.cameraRenderState;
    Vec3 eye = camera.pos;
    LENS.set(PROJECTION).mul(camera.viewRotationMatrix);
    BOUNDS.reset(main.width, main.height);
    centres = 0;
    float far = module.fadeEnd() + 2.0F;
    double farSq = far * far;
    List<EntityRenderState> states = level.entityRenderStates;
    for (int i = 0; i < states.size(); i++) {
      EntityRenderState state = states.get(i);
      if (state.distanceToCameraSq <= farSq && wanted(state, selfId, self, players)) {
        double x = state.x - eye.x;
        double y = state.y - eye.y;
        double z = state.z - eye.z;
        WORLD.submit(dispatcher, state, camera, x, y, z, poseStack);
        BOUNDS.add(
            LENS,
            (float) x,
            (float) y,
            (float) z,
            state.boundingBoxWidth * 0.5F,
            state.boundingBoxHeight);
        float depth =
            LENS.m03() * (float) x
                + LENS.m13() * ((float) y + state.boundingBoxHeight * 0.5F)
                + LENS.m23() * (float) z
                + LENS.m33();
        depth = Math.max(depth, NEAREST_CENTRE);
        int slot = centres < CENTRES ? centres++ : farthest(depth);
        if (slot >= 0) {
          float cx = (float) x;
          float cy = (float) y + state.boundingBoxHeight * 0.5F;
          float cz = (float) z;
          CENTRE[slot * 4] =
              ((LENS.m00() * cx + LENS.m10() * cy + LENS.m20() * cz + LENS.m30()) / depth * 0.5F
                      + 0.5F)
                  * main.width;
          CENTRE[slot * 4 + 1] =
              ((LENS.m01() * cx + LENS.m11() * cy + LENS.m21() * cz + LENS.m31()) / depth * 0.5F
                      + 0.5F)
                  * main.height;
          CENTRE[slot * 4 + 2] = depth;
          CENTRE[slot * 4 + 3] =
              Math.max(state.boundingBoxWidth, state.boundingBoxHeight) * 1.5F + 2.0F;
        }
      }
    }
    if (WORLD.picked() > 0) {
      WORLD.lens();
    }
  }

  private static int farthest(float depth) {
    int slot = -1;
    float far = depth;
    for (int i = 0; i < CENTRES; i++) {
      if (CENTRE[i * 4 + 2] > far) {
        far = CENTRE[i * 4 + 2];
        slot = i;
      }
    }
    return slot;
  }

  private static boolean wanted(
      EntityRenderState state, int selfId, boolean self, boolean players) {
    if (state.isInvisible) {
      return false;
    }
    if (state instanceof AvatarRenderState avatar) {
      return !avatar.isSpectator && (avatar.id == selfId ? self : players);
    }
    return Shaders.picked(state.entityType);
  }

  public static SubmitNodeCollector handCollector(SubmitNodeCollector real) {
    ShadersModule module = Shaders.module();
    if (broken
        || module == null
        || !module.entitiesLive()
        || !module.hand.get()
        || IrisCompat.isRenderingShadowPass()) {
      return real;
    }
    return HAND.tee(real);
  }

  public static void handDrawn() {
    HAND.drawn();
  }

  public static void world(GameRenderer renderer) {
    if (WORLD.picked() == 0) {
      return;
    }
    ShadersModule module = Shaders.module();
    RenderTarget main = renderer.mainRenderTarget();
    float pixelsPerBlock = main.height * PROJECTION.m11() * 0.5F;
    int reach = ScreenBounds.reach(module.reachBlocks(), pixelsPerBlock, BOUNDS.nearest());
    BOUNDS.finish(reach + MARGIN);
    if (BOUNDS.empty()) {
      WORLD.drop();
      return;
    }
    tick(module);
    CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
    UNPROJECT.set(PROJECTION).invert();
    ANCHOR.set(camera.viewRotationMatrix).transpose();
    run(
        renderer,
        module,
        WORLD,
        centres,
        module.reachBlocks(),
        pixelsPerBlock,
        reach,
        !module.throughWalls.get());
  }

  public static void hand(GameRenderer renderer) {
    if (!HAND.ready()) {
      HAND.drop();
      return;
    }
    ShadersModule module = Shaders.module();
    RenderTarget main = renderer.mainRenderTarget();
    float fov = renderer.gameRenderState().levelRenderState.cameraRenderState.hudFov;
    float slope = (float) Math.tan(Math.toRadians(fov) * 0.5);
    float pixelsPerBlock = main.height / (2.0F * slope);
    BOUNDS.reset(main.width, main.height);
    BOUNDS.add(ANCHOR.identity(), 0.0F, 0.0F, 0.0F, 1.0F, 1.0F);
    BOUNDS.finish(0);
    UNPROJECT
        .zero()
        .m00(slope * HAND_DISTANCE * main.width / main.height)
        .m11(slope * HAND_DISTANCE)
        .m32(-HAND_DISTANCE)
        .m33(1.0F);
    float reachBlocks = module.reachBlocks() * HAND_REACH;
    int reach = ScreenBounds.reach(reachBlocks, pixelsPerBlock, HAND_DISTANCE);
    int anchors = 0;
    if (HAND.placed()) {
      Vector3f first = HAND.view().transformPosition(HAND.first());
      float depth = -first.z;
      if (depth > 0.01F) {
        CENTRE[0] =
            (first.x / depth / (slope * main.width / main.height) * 0.5F + 0.5F) * main.width;
        CENTRE[1] = (first.y / depth / slope * 0.5F + 0.5F) * main.height;
        CENTRE[2] = HAND_DISTANCE;
        CENTRE[3] = 100.0F;
        anchors = 1;
      }
    }
    run(renderer, module, HAND, anchors, reachBlocks, pixelsPerBlock, reach, false);
  }

  private static void run(
      GameRenderer renderer,
      ShadersModule module,
      Silhouettes layer,
      int anchors,
      float reachBlocks,
      float pixelsPerBlock,
      int reach,
      boolean occlude) {
    try {
      draw(renderer, module, layer, anchors, reachBlocks, pixelsPerBlock, reach, occlude);
    } catch (RuntimeException e) {
      broken = true;
      CxVisualsClient.LOG.error("Entity shaders failed and do nothing for this session", e);
    }
    layer.drop();
  }

  private static void tick(ShadersModule module) {
    long now = System.nanoTime();
    float frame = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, LONGEST_FRAME);
    lastNanos = now;
    if (!Minecraft.getInstance().isPaused()) {
      clock = (clock + frame * module.speedScale()) % CLOCK_WRAP;
    }
  }

  private static void draw(
      GameRenderer renderer,
      ShadersModule module,
      Silhouettes layer,
      int anchors,
      float reachBlocks,
      float pixelsPerBlock,
      int reach,
      boolean occlude) {
    RenderTarget main = renderer.mainRenderTarget();
    GpuDevice device = RenderSystem.getDevice();
    Effect effect = module.effect.get();
    if (!FieldPasses.prepare(device, main) || !EffectPass.ready(device, effect)) {
      return;
    }
    if (uniforms == null) {
      uniforms = device.createBuffer(() -> "cxvisuals_entity_fx", UNIFORM_USAGE, UNIFORM_SIZE);
    }
    CommandEncoder encoder = device.createCommandEncoder();
    RenderTarget mask = FieldPasses.select(false);
    FieldPasses.wipe(encoder, BOUNDS);
    layer.stencil(renderer, mask);

    boolean zeroToOne = device.getDeviceInfo().isZZeroToOne();
    Palette palette = module.palette.get();
    int first = palette.first();
    int second = palette.second();
    try (MemoryStack memory = MemoryStack.stackPush()) {
      Std140Builder block =
          Std140Builder.onStack(memory, UNIFORM_SIZE)
              .putMat4f(UNPROJECT)
              .putMat4f(ANCHOR)
              .putVec4(main.width, main.height, 1.0F / main.width, 1.0F / main.height)
              .putVec4(zeroToOne ? 1.0F : 2.0F, zeroToOne ? 0.0F : -1.0F, pixelsPerBlock, clock)
              .putVec4(module.fadeStart(), module.fadeEnd(), 0.0F, reachBlocks)
              .putVec4(module.intensityScale(), module.bodyScale(), occlude ? 1.0F : 0.0F, anchors)
              .putVec4(red(first), green(first), blue(first), 1.0F)
              .putVec4(red(second), green(second), blue(second), 1.0F)
              .putVec4(BOUNDS.x(), BOUNDS.y(), BOUNDS.width(), BOUNDS.height());
      for (int i = 0; i < CENTRES; i++) {
        block.putVec4(CENTRE[i * 4], CENTRE[i * 4 + 1], CENTRE[i * 4 + 2], CENTRE[i * 4 + 3]);
      }
      ByteBuffer data = block.get();
      encoder.writeToBuffer(uniforms.slice(), data);
    }

    GpuTextureView field = FieldPasses.run(encoder, uniforms, BOUNDS);
    EffectPass.draw(device, encoder, uniforms, main, BOUNDS, field, effect);
    passes++;
  }

  private static float red(int argb) {
    return (argb >> 16 & 0xFF) * CHANNEL;
  }

  private static float green(int argb) {
    return (argb >> 8 & 0xFF) * CHANNEL;
  }

  private static float blue(int argb) {
    return (argb & 0xFF) * CHANNEL;
  }
}
