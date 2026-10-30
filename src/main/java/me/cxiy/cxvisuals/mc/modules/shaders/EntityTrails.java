package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.modules.environment.View;
import me.cxiy.cxvisuals.modules.shaders.ShadersModule;
import me.cxiy.cxvisuals.modules.shaders.trails.Wakes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class EntityTrails {
  private static final long CLOCK_WRAP = 3_600_000_000_000L;
  private static final float LIFE_SLACK = 0.1F;
  private static final Set<EntityType<?>> PICKED =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private static final Wakes WAKES = new Wakes();
  private static final TrailBatch BATCH = new TrailBatch("entity_trails");
  private static final View VIEW = new View();
  private static final Vector3f AXIS = new Vector3f();
  private static final Matrix4f LENS = new Matrix4f();

  private static boolean compiled;
  private static boolean broken;
  private static int passes;

  private EntityTrails() {}

  public static int passes() {
    return passes;
  }

  public static int alive() {
    return WAKES.count();
  }

  static void repick() {
    PICKED.clear();
    for (String id : Shaders.module().trailOnly.get()) {
      Identifier key = Identifier.tryParse(id);
      if (key != null) {
        BuiltInRegistries.ENTITY_TYPE.getOptional(key).ifPresent(PICKED::add);
      }
    }
  }

  public static void tick(Minecraft mc) {
    ShadersModule module = Shaders.module();
    if (broken || module == null || !module.trailsLive() || mc.level == null) {
      WAKES.clear();
    }
  }

  public static void extracted(Entity entity, EntityRenderState state) {
    ShadersModule module = Shaders.module();
    if (broken
        || module == null
        || !module.trailsLive()
        || state.isInvisible
        || !wanted(entity, module)) {
      return;
    }
    WAKES.step(
        entity.getId(),
        state.x,
        state.y,
        state.z,
        state.boundingBoxWidth,
        state.boundingBoxHeight,
        System.nanoTime(),
        module.trailMinimum());
  }

  private static boolean wanted(Entity entity, ShadersModule module) {
    if (entity.isSpectator() || entity.isPassenger() || entity.isRemoved()) {
      return false;
    }
    if (entity == Minecraft.getInstance().player) {
      return module.trailSelf.get();
    }
    if (entity instanceof Player) {
      return module.trailPlayers.get();
    }
    if (entity instanceof Projectile) {
      return module.trailProjectiles.get();
    }
    return entity instanceof LivingEntity living
        && !living.isDeadOrDying()
        && module.trailMobs.get()
        && (PICKED.isEmpty() || PICKED.contains(entity.getType()));
  }

  public static void render(GameRenderer renderer) {
    ShadersModule module = Shaders.module();
    if (broken || module == null || WAKES.empty()) {
      return;
    }
    long now = System.nanoTime();
    WAKES.prune(now, module.trailSeconds() + LIFE_SLACK);
    if (WAKES.empty()) {
      return;
    }
    try {
      draw(
          renderer.mainRenderTarget(),
          renderer.gameRenderState().levelRenderState.cameraRenderState,
          module,
          now);
    } catch (RuntimeException e) {
      broken = true;
      WAKES.clear();
      BATCH.release();
      CxVisualsClient.LOG.error("Entity trails failed to draw, they are off for this session", e);
    }
  }

  private static void draw(
      RenderTarget main, CameraRenderState camera, ShadersModule module, long now) {
    GpuTextureView color = main.getColorTextureView();
    GpuTextureView depth = SceneDepth.attachment(main);
    if (color == null || depth == null || camera.isPanoramicMode) {
      return;
    }
    GpuDevice device = RenderSystem.getDevice();
    if (!compiled) {
      compiled = true;
      if (!TrailBatch.compiles(device)) {
        broken = true;
        CxVisualsClient.LOG.warn("Entity trail shader failed to compile, trails stay off");
        return;
      }
    }
    look(camera);
    if (BATCH.build(WAKES, VIEW, module, now) == 0) {
      return;
    }
    LENS.set(EntityShaders.lens()).mul(camera.viewRotationMatrix);
    CommandEncoder encoder = device.createCommandEncoder();
    BATCH.upload(encoder);
    BATCH.look(encoder, LENS, module, (now % CLOCK_WRAP) / 1.0e9F, TrailBatch.WHOLE);
    try (RenderPass pass =
        encoder.createRenderPass(
            () -> "CxVisuals entity trails",
            color,
            Optional.empty(),
            depth,
            OptionalDouble.empty())) {
      BATCH.draw(pass);
    }
    passes++;
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
