package me.cxiy.cxvisuals.mc.modules.environment;

import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import me.cxiy.cxvisuals.mc.modules.hud.Media;
import me.cxiy.cxvisuals.modules.environment.EnvironmentModule;
import me.cxiy.cxvisuals.modules.environment.View;
import me.cxiy.cxvisuals.modules.environment.lyrics.Glyphs;
import me.cxiy.cxvisuals.modules.environment.lyrics.Reach;
import me.cxiy.cxvisuals.modules.environment.lyrics.WorldLyrics;
import me.cxiy.cxvisuals.modules.environment.particles.Atmosphere;
import me.cxiy.cxvisuals.modules.environment.particles.Climate;
import me.cxiy.cxvisuals.modules.hud.media.MediaHub;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class Environment {
  private static final long PROBE_NANOS = 100_000_000L;
  private static final float LONGEST_FRAME = 0.1F;
  private static final long SEED = 0x5EED_A1E5L;
  private static final Vector3f AXIS = new Vector3f();
  private static final View VIEW = new View();
  private static final Climate CLIMATE = new Climate();
  private static final Surroundings SURROUNDINGS = new Surroundings();
  private static final ParticleRenderer PARTICLES = new ParticleRenderer();
  private static final Glyphs GLYPHS =
      text -> McCanvas.engine().face(WorldTextRenderer.STYLE).width(text);
  private static final Reach REACH = Environment::clear;

  private static EnvironmentModule module;
  private static WorldLyrics world;
  private static Atmosphere atmosphere;
  private static int user = -1;
  private static boolean wanting;
  private static boolean broken;
  private static boolean airBroken;
  private static int draws;
  private static int airDraws;
  private static float lastYaw;
  private static long lastNanos;
  private static long probedAt;
  private static double probed;
  private static double probedFor;
  private static float probedX;
  private static float probedY;
  private static float probedZ;
  private static float faceX;
  private static float faceY;
  private static float faceZ;

  private Environment() {}

  public static EnvironmentModule create(McKeyBinder keys) {
    module = new EnvironmentModule(keys);
    world = new WorldLyrics(module);
    atmosphere = new Atmosphere(module, SEED);
    return module;
  }

  public static EnvironmentModule module() {
    return module;
  }

  public static WorldLyrics world() {
    return world;
  }

  public static Atmosphere atmosphere() {
    return atmosphere;
  }

  public static Climate climate() {
    return CLIMATE;
  }

  public static int draws() {
    return draws;
  }

  public static int airDraws() {
    return airDraws;
  }

  public static boolean replacesRain() {
    return module != null
        && module.enabled.get()
        && module.rain.get()
        && !airBroken
        && ParticlePipeline.usable();
  }

  public static boolean broken() {
    return broken;
  }

  public static boolean airBroken() {
    return airBroken;
  }

  public static void tick(Minecraft mc) {
    if (mc.level == null) {
      idle();
      still();
    }
  }

  private static void still() {
    if (atmosphere != null) {
      atmosphere.clear();
    }
  }

  private static void idle() {
    if (world != null) {
      world.clear();
    }
    if (wanting) {
      wanting = false;
      MediaHub hub = Media.hub();
      hub.want(user, false, false, false);
      hub.poll();
    }
  }

  public static void render(GameRenderer renderer) {
    Minecraft mc = Minecraft.getInstance();
    if (module == null || !module.enabled.get() || mc.level == null) {
      idle();
      still();
      return;
    }
    CameraRenderState camera = renderer.gameRenderState().levelRenderState.cameraRenderState;
    long now = System.nanoTime();
    float frame = lastNanos == 0L ? 0.0F : Math.min(LONGEST_FRAME, (now - lastNanos) / 1.0e9F);
    lastNanos = now;
    look(camera, frame);
    air(renderer, mc, frame, now);
    lyrics(renderer, mc, camera, now);
  }

  private static void look(CameraRenderState camera, float frame) {
    Vec3 position = camera.pos;
    VIEW.x = position.x;
    VIEW.y = position.y;
    VIEW.z = position.z;
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
    VIEW.yawRate =
        frame <= 0.0F
            ? 0.0F
            : (float) Math.toRadians(Mth.wrapDegrees(camera.yRot - lastYaw)) / frame;
    lastYaw = camera.yRot;
  }

  private static void air(GameRenderer renderer, Minecraft mc, float frame, long now) {
    LevelRenderState level = renderer.gameRenderState().levelRenderState;
    CameraRenderState camera = level.cameraRenderState;
    if (airBroken
        || camera.fogType != FogType.NONE
        || camera.isPanoramicMode
        || !ParticlePipeline.usable()) {
      return;
    }
    try {
      SURROUNDINGS.read(mc.level, level, CLIMATE, now);
      atmosphere.update(VIEW, CLIMATE, SURROUNDINGS, mc.isPaused() ? 0.0F : frame);
      if (!atmosphere.empty()
          && PARTICLES.draw(
                  renderer.mainRenderTarget(), camera.viewRotationMatrix, atmosphere, VIEW, CLIMATE)
              > 0) {
        airDraws++;
      }
    } catch (RuntimeException e) {
      airBroken = true;
      PARTICLES.release();
      atmosphere.clear();
      CxVisualsClient.LOG.error(
          "Environment particles failed to draw, they are off for this session", e);
    }
  }

  private static void lyrics(
      GameRenderer renderer, Minecraft mc, CameraRenderState camera, long now) {
    MediaHub hub = Media.hub();
    if (hub == null
        || broken
        || !module.lyrics.get()
        || mc.gui.hud.isHidden()
        || !WorldTextPipeline.usable()) {
      idle();
      return;
    }
    if (user < 0) {
      user = hub.user();
    }
    wanting = true;
    try {
      hub.want(user, true, false, true);
      hub.poll(Hud.module().online.get());
      world.update(VIEW, hub.snapshot(), hub.lyrics(), now, GLYPHS, REACH);
      if (world.alive() > 0
          && WorldTextRenderer.draw(
                  renderer.mainRenderTarget(),
                  camera.viewRotationMatrix,
                  camera.pos,
                  world,
                  module,
                  now)
              > 0) {
        draws++;
      }
    } catch (RuntimeException e) {
      broken = true;
      WorldTextRenderer.release();
      CxVisualsClient.LOG.error(
          "World text failed to draw, lyrics in the world are off for this session", e);
    }
  }

  private static double clear(
      View view, float dx, float dy, float dz, double distance, float[] normal) {
    long now = System.nanoTime();
    if (now - probedAt < PROBE_NANOS
        && distance == probedFor
        && dx == probedX
        && dy == probedY
        && dz == probedZ) {
      normal[0] = faceX;
      normal[1] = faceY;
      normal[2] = faceZ;
      return probed;
    }
    faceX = 0.0F;
    faceY = 0.0F;
    faceZ = 0.0F;
    probedAt = now;
    probedFor = distance;
    probedX = dx;
    probedY = dy;
    probedZ = dz;
    Minecraft mc = Minecraft.getInstance();
    if (mc.level == null || mc.getCameraEntity() == null) {
      probed = distance;
      return probed;
    }
    Vec3 start = new Vec3(view.x, view.y, view.z);
    Vec3 end = new Vec3(view.x + dx * distance, view.y + dy * distance, view.z + dz * distance);
    HitResult hit =
        mc.level.clip(
            new ClipContext(
                start,
                end,
                ClipContext.Block.VISUAL,
                ClipContext.Fluid.NONE,
                mc.getCameraEntity()));
    probed =
        hit.getType() == HitResult.Type.MISS
            ? distance
            : Math.sqrt(hit.getLocation().distanceToSqr(start));
    if (hit instanceof BlockHitResult block && hit.getType() != HitResult.Type.MISS) {
      faceX = block.getDirection().getStepX();
      faceY = block.getDirection().getStepY();
      faceZ = block.getDirection().getStepZ();
    }
    normal[0] = faceX;
    normal[1] = faceY;
    normal[2] = faceZ;
    return probed;
  }
}
