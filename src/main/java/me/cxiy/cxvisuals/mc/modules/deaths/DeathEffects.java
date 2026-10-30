package me.cxiy.cxvisuals.mc.modules.deaths;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import me.cxiy.cxvisuals.mc.modules.shaders.SceneDepth;
import me.cxiy.cxvisuals.core.settings.Palette;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.compat.IrisCompat;
import me.cxiy.cxvisuals.mc.modules.shaders.EntityShaders;
import me.cxiy.cxvisuals.mc.modules.shaders.Shaders;
import me.cxiy.cxvisuals.modules.deaths.Corpses;
import me.cxiy.cxvisuals.modules.deaths.DeathsModule;
import me.cxiy.cxvisuals.modules.deaths.Fate;
import me.cxiy.cxvisuals.modules.deaths.Posture;
import me.cxiy.cxvisuals.modules.environment.View;
import me.cxiy.cxvisuals.modules.shaders.ScreenBounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class DeathEffects {
  private static final long CLOCK_WRAP = 3_600_000_000_000L;
  private static final int MARGIN = 6;
  private static final float GLITCH_REACH = 0.4F;
  private static final float DOT_FALL = 0.45F;
  private static final Set<EntityType<?>> PICKED =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private static final Corpses CORPSES = new Corpses();
  private static final CorpseStorage[] STORAGES = new CorpseStorage[Corpses.MOST];
  private static final ScreenBounds[] BOUNDS = new ScreenBounds[Corpses.MOST];
  private static final float[] RISEN = new float[Corpses.MOST * 3];
  private static final float[] TALL = new float[Corpses.MOST];
  private static final boolean[] STAGED = new boolean[Corpses.MOST];
  private static final Set<EntityRenderState> CLAIMED =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private static final Posture POSTURE = new Posture();
  private static final CorpseLayer LAYER = new CorpseLayer();
  private static final CorpseLayer.Look LOOK = new CorpseLayer.Look();
  private static final DeathBatch BATCH = new DeathBatch();
  private static final View VIEW = new View();
  private static final Vector3f AXIS = new Vector3f();
  private static final Matrix4f LENS = new Matrix4f();

  private static DeathsModule module;
  private static int staged;
  private static boolean compiled;
  private static boolean broken;
  private static int passes;

  static {
    for (int i = 0; i < Corpses.MOST; i++) {
      STORAGES[i] = new CorpseStorage();
      BOUNDS[i] = new ScreenBounds();
    }
  }

  private DeathEffects() {}

  public static DeathsModule create(McKeyBinder keys) {
    module = new DeathsModule(keys);
    List<EntityType<?>> types = new ArrayList<>();
    for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
      if (type != EntityTypes.PLAYER) {
        types.add(type);
      }
    }
    types.sort(Comparator.comparing(Shaders::label));
    List<String> ids = new ArrayList<>(types.size());
    List<String> labels = new ArrayList<>(types.size());
    for (EntityType<?> type : types) {
      ids.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
      labels.add(Shaders.label(type));
    }
    module.only.options(ids, labels);
    module.only.onChange(DeathEffects::repick);
    return module;
  }

  private static void repick() {
    PICKED.clear();
    for (String id : module.only.get()) {
      Identifier key = Identifier.tryParse(id);
      if (key != null) {
        BuiltInRegistries.ENTITY_TYPE.getOptional(key).ifPresent(PICKED::add);
      }
    }
  }

  public static DeathsModule module() {
    return module;
  }

  public static int passes() {
    return passes;
  }

  public static int corpses() {
    return CORPSES.count();
  }

  private static boolean live() {
    return !broken && module != null && module.effectsLive();
  }

  public static void tick(Minecraft mc) {
    if (mc.level == null || !live()) {
      CORPSES.clear();
      CLAIMED.clear();
    } else {
      CORPSES.prune(System.nanoTime(), module.seconds());
    }
  }

  public static void extracted(Entity entity, EntityRenderState state) {
    if (!live()
        || !(entity instanceof LivingEntity living)
        || !living.isDeadOrDying()
        || entity instanceof ArmorStand
        || !wanted(entity)) {
      return;
    }
    Corpses.Corpse corpse = CORPSES.begin(entity.getId(), module.fate.get(), System.nanoTime());
    if (corpse == null) {
      return;
    }
    corpse.body = state;
    corpse.x = state.x;
    corpse.y = state.y;
    corpse.z = state.z;
    corpse.width = state.boundingBoxWidth;
    corpse.height = state.boundingBoxHeight;
    if (!IrisCompat.handInScene() || corpse.fate.moves()) {
      CLAIMED.add(state);
    }
  }

  public static boolean quiet(LivingEntity entity) {
    return live() && CORPSES.find(entity.getId()) != null;
  }

  public static void hide(LevelRenderState level) {
    if (!CLAIMED.isEmpty()) {
      level.entityRenderStates.removeIf(CLAIMED::contains);
      CLAIMED.clear();
    }
  }

  private static boolean wanted(Entity entity) {
    Minecraft mc = Minecraft.getInstance();
    if (entity == mc.player) {
      return module.self.get();
    }
    if (entity instanceof Player) {
      return module.players.get();
    }
    return module.mobs.get() && (PICKED.isEmpty() || PICKED.contains(entity.getType()));
  }

  public static void submit(
      LevelRenderState level,
      EntityRenderDispatcher dispatcher,
      PoseStack poseStack,
      SubmitNodeCollector output) {
    staged = 0;
    if (!live() || IrisCompat.isRenderingShadowPass() || CORPSES.empty()) {
      return;
    }
    boolean pack = IrisCompat.handInScene();
    long now = System.nanoTime();
    float seconds = module.seconds();
    CameraRenderState camera = level.cameraRenderState;
    Vec3 eye = camera.pos;
    RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
    LENS.set(EntityShaders.lens()).mul(camera.viewRotationMatrix);
    float far = module.range();
    for (int i = 0; i < CORPSES.count(); i++) {
      Corpses.Corpse corpse = CORPSES.get(i);
      STAGED[i] = false;
      if (!(corpse.body instanceof LivingEntityRenderState body)) {
        continue;
      }
      if (pack && !corpse.fate.moves()) {
        continue;
      }
      float x = (float) (corpse.x - eye.x);
      float y = (float) (corpse.y - eye.y);
      float z = (float) (corpse.z - eye.z);
      float distance = (float) Math.sqrt(x * x + y * y + z * z);
      if (distance > far) {
        continue;
      }
      float t = Math.min(1.0F, corpse.progress(now, seconds));
      corpse.fate.pose(t, corpse.height, corpse.seed, POSTURE);
      body.deathTime = 0.0F;
      body.hasRedOverlay = false;
      body.isInvisible = false;
      float px = x + POSTURE.jitterX;
      float py = y + POSTURE.rise;
      float pz = z + POSTURE.jitterZ;
      float pivot = py + corpse.height * POSTURE.pivot;
      if (pack) {
        poseStack.pushPose();
        if (!POSTURE.still()) {
          poseStack.translate(px, pivot, pz);
          poseStack.scale(POSTURE.scale, POSTURE.scale * POSTURE.squash, POSTURE.scale);
          poseStack.translate(-px, -pivot, -pz);
        }
        dispatcher.submit(body, camera, px, py, pz, poseStack, output);
        poseStack.popPose();
        continue;
      }
      CorpseStorage storage = STORAGES[i];
      storage.wipe();
      poseStack.pushPose();
      if (!POSTURE.still()) {
        poseStack.translate(px, pivot, pz);
        poseStack.scale(POSTURE.scale, POSTURE.scale * POSTURE.squash, POSTURE.scale);
        poseStack.translate(-px, -pivot, -pz);
      }
      dispatcher.submit(body, camera, px, py, pz, poseStack, storage);
      poseStack.popPose();
      float reach =
          Math.max(corpse.width, corpse.height) * 0.5F
              + (corpse.fate == Fate.GLITCH ? GLITCH_REACH : 0.0F);
      ScreenBounds bounds = BOUNDS[i];
      bounds.reset(main.width, main.height);
      float fall = corpse.fate == Fate.MATRIX ? corpse.height * DOT_FALL : 0.0F;
      bounds.add(
          LENS,
          px,
          Math.min(py, y) - fall,
          pz,
          reach,
          corpse.height + Math.abs(POSTURE.rise) + fall);
      bounds.finish(MARGIN);
      if (bounds.empty()) {
        continue;
      }
      RISEN[i * 3] = px;
      RISEN[i * 3 + 1] =
          py + corpse.height * POSTURE.pivot * (1.0F - POSTURE.scale * POSTURE.squash);
      RISEN[i * 3 + 2] = pz;
      TALL[i] = corpse.height * POSTURE.scale * POSTURE.squash;
      STAGED[i] = true;
      staged++;
    }
    if (staged > 0) {
      LAYER.lens();
    }
  }

  public static void render(GameRenderer renderer) {
    if (!live() || CORPSES.empty()) {
      return;
    }
    try {
      draw(renderer);
    } catch (RuntimeException e) {
      broken = true;
      CORPSES.clear();
      LAYER.release();
      BATCH.release();
      CxVisualsClient.LOG.error("Death effects failed to draw, they are off for this session", e);
    }
  }

  private static void draw(GameRenderer renderer) {
    RenderTarget main = renderer.mainRenderTarget();
    CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
    GpuDevice device = RenderSystem.getDevice();
    if (!compiled) {
      compiled = true;
      if (!CorpseLayer.compiles(device) || !DeathBatch.compiles(device)) {
        broken = true;
        CxVisualsClient.LOG.warn("Death effect shaders failed to compile, deaths look vanilla");
        return;
      }
    }
    long now = System.nanoTime();
    float seconds = module.seconds();
    float clock = (now % CLOCK_WRAP) / 1.0e9F;
    Palette palette = module.palette.get();
    CommandEncoder encoder = device.createCommandEncoder();
    Vec3 eye = camera.pos;
    Matrix4f projection = EntityShaders.lens();
    LENS.set(projection).mul(camera.viewRotationMatrix);
    if (staged > 0 && LAYER.prepare(device, main)) {
      LENS.invert(LOOK.unproject);
      LOOK.zeroToOne = device.getDeviceInfo().isZZeroToOne();
      LOOK.first = palette.first();
      LOOK.second = palette.second();
      LOOK.intensity = module.intensityScale();
      LOOK.clock = clock;
      float pixels = Math.abs(projection.m11()) * main.height * 0.5F;
      for (int i = 0; i < CORPSES.count(); i++) {
        if (!STAGED[i]) {
          continue;
        }
        Corpses.Corpse corpse = CORPSES.get(i);
        LOOK.originX = RISEN[i * 3];
        LOOK.originY = RISEN[i * 3 + 1];
        LOOK.originZ = RISEN[i * 3 + 2];
        LOOK.height = TALL[i];
        LOOK.floor = (float) (corpse.y - eye.y);
        LOOK.width = corpse.width;
        LOOK.progress = Math.min(1.0F, corpse.progress(now, seconds));
        LOOK.fate = corpse.fate.ordinal();
        float distance =
            (float)
                Math.sqrt(
                    LOOK.originX * LOOK.originX
                        + LOOK.originY * LOOK.originY
                        + LOOK.originZ * LOOK.originZ);
        LOOK.tall = corpse.height * pixels / Math.max(distance, 0.5F);
        focus(main, LOOK.originX, LOOK.originY + LOOK.height * 0.5F, LOOK.originZ);
        LOOK.horizon =
            Fate.holeRadius(corpse.height, corpse.width)
                * Fate.horizon(LOOK.progress)
                * pixels
                / Math.max(distance, 0.5F);
        LAYER.draw(renderer, encoder, STORAGES[i], BOUNDS[i], LOOK);
        passes++;
      }
    }
    look(camera);
    if (BATCH.build(CORPSES, VIEW, seconds, now) > 0) {
      BATCH.upload(
          encoder, LENS, palette.first(), palette.second(), module.intensityScale(), clock);
      try (RenderPass pass =
          encoder.createRenderPass(
              () -> "CxVisuals death effects",
              main.getColorTextureView(),
              Optional.empty(),
              SceneDepth.attachment(main),
              OptionalDouble.empty())) {
        BATCH.draw(pass);
      }
    }
  }

  private static void focus(RenderTarget main, float x, float y, float z) {
    float w = LENS.m03() * x + LENS.m13() * y + LENS.m23() * z + LENS.m33();
    if (w <= 1.0e-3F) {
      LOOK.focusX = -1.0e4F;
      LOOK.focusY = -1.0e4F;
      return;
    }
    LOOK.focusX =
        ((LENS.m00() * x + LENS.m10() * y + LENS.m20() * z + LENS.m30()) / w * 0.5F + 0.5F)
            * main.width;
    LOOK.focusY =
        ((LENS.m01() * x + LENS.m11() * y + LENS.m21() * z + LENS.m31()) / w * 0.5F + 0.5F)
            * main.height;
  }

  private static void look(CameraRenderState camera) {
    VIEW.x = camera.pos.x;
    VIEW.y = camera.pos.y;
    VIEW.z = camera.pos.z;
    camera.orientation.transform(AXIS.set(0.0F, 0.0F, -1.0F));
    VIEW.fx = AXIS.x;
    VIEW.fy = AXIS.y;
    VIEW.fz = AXIS.z;
    camera.orientation.transform(AXIS.set(1.0F, 0.0F, 0.0F));
    VIEW.rx = AXIS.x;
    VIEW.ry = AXIS.y;
    VIEW.rz = AXIS.z;
    camera.orientation.transform(AXIS.set(0.0F, 1.0F, 0.0F));
    VIEW.ux = AXIS.x;
    VIEW.uy = AXIS.y;
    VIEW.uz = AXIS.z;
  }
}
