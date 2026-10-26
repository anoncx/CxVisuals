package me.cxiy.cxvisuals.mc.modules.swings;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import me.cxiy.cxvisuals.core.settings.Palette;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.compat.IrisCompat;
import me.cxiy.cxvisuals.mc.modules.shaders.EntityShaders;
import me.cxiy.cxvisuals.modules.swings.Blade;
import me.cxiy.cxvisuals.modules.swings.Ribbon;
import me.cxiy.cxvisuals.modules.swings.Ribbons;
import me.cxiy.cxvisuals.modules.swings.SwingMesh;
import me.cxiy.cxvisuals.modules.swings.SwingsModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.Projection;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

public final class Swings {
  private static final long CLOCK_WRAP = 3_600_000_000_000L;
  private static final long FLOOR_SPACING = 4_000_000L;
  private static final float LIFE_SLACK = 0.1F;
  private static final float FADE_FROM = 0.5F;
  private static final int HAND_OWNER = 0;

  private static final Ribbons WORLD = new Ribbons();
  private static final Ribbons HAND = new Ribbons();
  private static final SwingMesh MESH = new SwingMesh();
  private static final SwingBatch WORLD_BATCH = new SwingBatch("swing_trails");
  private static final SwingBatch HAND_BATCH = new SwingBatch("swing_hand");
  private static final Vector3f BASE = new Vector3f();
  private static final Vector3f TIP = new Vector3f();
  private static final Matrix4f LENS = new Matrix4f();
  private static final Matrix4f HAND_LENS = new Matrix4f();
  private static final Matrix4f HAND_BASIS = new Matrix4f();

  private static SwingsModule module;
  private static boolean inWorld;
  private static boolean inHand;
  private static LivingEntity swinger;
  private static double cameraX;
  private static double cameraY;
  private static double cameraZ;
  private static boolean compiled;
  private static boolean broken;
  private static int passes;

  private Swings() {}

  public static SwingsModule create(McKeyBinder keys) {
    module = new SwingsModule(keys);
    return module;
  }

  public static SwingsModule module() {
    return module;
  }

  public static int passes() {
    return passes;
  }

  public static int alive() {
    return WORLD.count() + HAND.count();
  }

  private static boolean live() {
    return !broken && module != null && module.trailsLive();
  }

  public static void tick(Minecraft mc) {
    if (mc.level == null || !live()) {
      WORLD.clear();
      HAND.clear();
      swinger = null;
    }
  }

  public static void tag(
      ItemStackRenderState state, ItemStack stack, ItemDisplayContext context, ItemOwner owner) {
    if (!(owner instanceof LivingEntity living)
        || !living.swinging
        || living.swingingArm != InteractionHand.MAIN_HAND
        || stack.isEmpty()
        || !live()) {
      return;
    }
    boolean first = context.firstPerson();
    if (!first
        && context != ItemDisplayContext.THIRD_PERSON_LEFT_HAND
        && context != ItemDisplayContext.THIRD_PERSON_RIGHT_HAND) {
      return;
    }
    HumanoidArm arm = context.leftHand() ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
    if (arm != living.getMainArm() || living.isInvisible() || !eligible(stack)) {
      return;
    }
    if (first ? !module.firstPerson.get() : !wanted(living)) {
      return;
    }
    ((SwingTagged) state).cxvisuals$assign(living);
  }

  private static boolean wanted(LivingEntity living) {
    Minecraft mc = Minecraft.getInstance();
    if (living == mc.player) {
      return module.self.get();
    }
    if (mc.player != null) {
      float far = module.reach();
      if (living.distanceToSqr(mc.player) > far * far) {
        return false;
      }
    }
    return living instanceof Player ? module.players.get() : module.mobs.get();
  }

  private static boolean eligible(ItemStack stack) {
    if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.SPEARS)) {
      return module.swords.get();
    }
    if (stack.is(ItemTags.AXES)) {
      return module.axes.get();
    }
    if (stack.is(ItemTags.PICKAXES) || stack.is(ItemTags.SHOVELS) || stack.is(ItemTags.HOES)) {
      return module.tools.get();
    }
    return module.others.get() && stack.has(DataComponents.WEAPON);
  }

  public static void beginWorld(Vec3 camera) {
    inWorld = true;
    cameraX = camera.x;
    cameraY = camera.y;
    cameraZ = camera.z;
  }

  public static void endWorld() {
    inWorld = false;
    swinger = null;
  }

  public static void beginHand() {
    CameraRenderState camera =
        Minecraft.getInstance().gameRenderer.gameRenderState().levelRenderState.cameraRenderState;
    camera.viewRotationMatrix.invert(HAND_BASIS).mul(RenderSystem.getModelViewStack());
    inHand = true;
    cameraX = camera.pos.x;
    cameraY = camera.pos.y;
    cameraZ = camera.pos.z;
  }

  public static void endHand() {
    inHand = false;
    swinger = null;
  }

  public static void begin(SwingTagged state) {
    LivingEntity owner = state.cxvisuals$swinger();
    boolean capturing = owner != null && (inWorld || inHand) && !IrisCompat.isRenderingShadowPass();
    swinger = capturing ? owner : null;
  }

  public static void end() {
    swinger = null;
  }

  public static void layer(
      Matrix4fc pose, List<BakedQuad> quads, Supplier<Vector3fc[]> extents) {
    LivingEntity owner = swinger;
    if (owner == null) {
      return;
    }
    swinger = null;
    Blade blade = Blades.of(quads, extents);
    pose.transformPosition(blade.baseX, blade.baseY, blade.baseZ, BASE);
    pose.transformPosition(blade.tipX, blade.tipY, blade.tipZ, TIP);
    if (inHand) {
      HAND_BASIS.transformPosition(BASE);
      HAND_BASIS.transformPosition(TIP);
    }
    Ribbon ribbon = (inHand ? HAND : WORLD).of(inHand ? HAND_OWNER : owner.getId());
    if (ribbon == null) {
      return;
    }
    long spacing =
        Math.max(FLOOR_SPACING, (long) (module.seconds() * 1.0e9F / (Ribbon.CAPACITY - 8)));
    float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
    ribbon.add(
        BASE.x + cameraX,
        BASE.y + cameraY,
        BASE.z + cameraZ,
        TIP.x + cameraX,
        TIP.y + cameraY,
        TIP.z + cameraZ,
        owner.getAttackAnim(partial),
        System.nanoTime(),
        spacing);
  }

  public static void render(GameRenderer renderer) {
    if (!live() || WORLD.empty()) {
      return;
    }
    CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
    LENS.set(EntityShaders.lens()).mul(camera.viewRotationMatrix);
    run(renderer, camera, WORLD, WORLD_BATCH, LENS);
  }

  public static void hand(GameRenderer renderer, Projection projection) {
    if (!live() || HAND.empty()) {
      return;
    }
    CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
    projection.getMatrix(HAND_LENS).mul(camera.viewRotationMatrix);
    run(renderer, camera, HAND, HAND_BATCH, HAND_LENS);
  }

  private static void run(
      GameRenderer renderer,
      CameraRenderState camera,
      Ribbons ribbons,
      SwingBatch batch,
      Matrix4fc viewProjection) {
    long now = System.nanoTime();
    ribbons.prune(now, module.seconds() + LIFE_SLACK);
    if (ribbons.empty()) {
      return;
    }
    try {
      draw(renderer.mainRenderTarget(), camera, ribbons, batch, viewProjection, now);
    } catch (RuntimeException e) {
      broken = true;
      WORLD.clear();
      HAND.clear();
      WORLD_BATCH.release();
      HAND_BATCH.release();
      CxVisualsClient.LOG.error("Swing trails failed to draw, they are off for this session", e);
    }
  }

  private static void draw(
      RenderTarget main,
      CameraRenderState camera,
      Ribbons ribbons,
      SwingBatch batch,
      Matrix4fc viewProjection,
      long now) {
    GpuTextureView color = main.getColorTextureView();
    GpuTextureView depth = main.getDepthTextureView();
    if (color == null || depth == null || camera.isPanoramicMode) {
      return;
    }
    GpuDevice device = RenderSystem.getDevice();
    if (!compiled) {
      compiled = true;
      if (!SwingBatch.compiles(device)) {
        broken = true;
        CxVisualsClient.LOG.warn("Swing trail shader failed to compile, trails stay off");
        return;
      }
    }
    float seconds = module.seconds();
    float widthScale = module.widthScale();
    float far = module.reach();
    batch.begin();
    for (int i = 0; i < ribbons.count(); i++) {
      Ribbon ribbon = ribbons.get(i);
      if (ribbon.empty()) {
        continue;
      }
      batch.added(
          MESH.emit(
              ribbon,
              camera.pos.x,
              camera.pos.y,
              camera.pos.z,
              widthScale,
              seconds,
              fade(ribbon, camera.pos, far),
              i * 977 + 1,
              now,
              SwingMesh.QUADS - batch.quads(),
              batch));
    }
    if (batch.quads() == 0) {
      return;
    }
    Palette palette = module.palette.get();
    CommandEncoder encoder = device.createCommandEncoder();
    batch.upload(
        encoder,
        viewProjection,
        palette.first(),
        palette.second(),
        module.intensityScale(),
        (now % CLOCK_WRAP) / 1.0e9F,
        module.look.get().ordinal(),
        module.glowScale(),
        MESH.halo(widthScale),
        widthScale);
    try (RenderPass pass =
        encoder.createRenderPass(
            () -> "CxVisuals swing trails", color, Optional.empty(), depth, OptionalDouble.empty())) {
      batch.draw(pass);
    }
    passes++;
  }

  private static float fade(Ribbon ribbon, Vec3 eye, float far) {
    double dx = ribbon.at(0, 3) - eye.x;
    double dy = ribbon.at(0, 4) - eye.y;
    double dz = ribbon.at(0, 5) - eye.z;
    float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    float start = far * FADE_FROM;
    float t = Math.clamp((distance - start) / (far - start), 0.0F, 1.0F);
    return 1.0F - t * t * (3.0F - 2.0F * t);
  }
}
