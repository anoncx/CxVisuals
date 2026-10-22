package me.cxiy.cxvisuals.mc.modules.animations;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.cxiy.cxvisuals.modules.animations.AnimationsModule;
import me.cxiy.cxvisuals.modules.animations.BlockPops;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public final class BlockPopEffects {
  private static final double RANGE_SQ = 64.0 * 64.0;
  private static final long NO_MINING = Long.MIN_VALUE;
  private static final float MIN_SCALE = 0.02F;

  private static final BlockPops POPS = new BlockPops();
  private static final MovingBlockRenderState[] MOVING =
      new MovingBlockRenderState[BlockPops.CAPACITY];

  private static boolean selfAction;
  private static long miningPos = NO_MINING;

  private BlockPopEffects() {}

  public static boolean hidden(long pos) {
    return POPS.hidden(pos);
  }

  public static int count() {
    return POPS.count();
  }

  public static boolean outlinePose(long key, float[] pose) {
    if (!Animations.on()) {
      return false;
    }
    for (int i = 0; i < POPS.count(); i++) {
      if (POPS.pos(i) == key) {
        float angle = POPS.spin(i) * spinRadians();
        pose[0] = POPS.lift(i);
        pose[1] = (float) Math.cos(angle);
        pose[2] = (float) Math.sin(angle);
        pose[3] = Math.max(POPS.scale(i), MIN_SCALE);
        return true;
      }
    }
    return false;
  }

  public static void selfAction(boolean active) {
    selfAction = active;
  }

  public static void mining(BlockPos pos) {
    miningPos = pos == null ? NO_MINING : pos.asLong();
  }

  public static void miningEnded() {
    miningPos = NO_MINING;
  }

  public static void reset() {
    POPS.clear();
  }

  public static void beforeSetBlock(Level world, BlockPos pos, BlockState next) {
    ClientLevel level = world instanceof ClientLevel client ? client : null;
    if (level == null || level != Minecraft.getInstance().level || !Animations.on()) {
      return;
    }
    BlockState old = level.getBlockState(pos);
    boolean place = old.isAir() && !next.isAir();
    boolean broken = next.isAir() && !old.isAir();
    if (!place && !broken) {
      return;
    }
    AnimationsModule module = Animations.module();
    if (broken && !module.blocksBreaks.get()) {
      return;
    }
    BlockState visual = place ? next : old;
    if (visual.getRenderShape() != RenderShape.MODEL) {
      return;
    }
    LocalPlayer player = Minecraft.getInstance().player;
    if (player == null) {
      return;
    }
    double dx = pos.getX() + 0.5 - player.getX();
    double dy = pos.getY() + 0.5 - player.getY();
    double dz = pos.getZ() + 0.5 - player.getZ();
    if (dx * dx + dy * dy + dz * dz > RANGE_SQ) {
      return;
    }
    long key = pos.asLong();
    boolean self = selfAction || key == miningPos;
    boolean wasHidden = POPS.hidden(key);
    POPS.start(
        key,
        place ? BlockPops.Kind.PLACE : BlockPops.Kind.BREAK,
        visual,
        module.blocksSource.get(),
        self);
    if (wasHidden && !POPS.hidden(key)) {
      remesh(level, key);
    }
    if (broken && key == miningPos) {
      miningPos = NO_MINING;
    }
  }

  public static void frame(float dt) {
    ClientLevel level = Minecraft.getInstance().level;
    if (level == null) {
      POPS.clear();
      return;
    }
    if (!Animations.on()) {
      unhideAll(level);
      return;
    }
    AnimationsModule module = Animations.module();
    POPS.update(dt, module.seconds(module.blocksTime.get().floatValue()));
    for (int k = 0; k < POPS.finishedCount(); k++) {
      if (POPS.finishedKind(k) == BlockPops.Kind.PLACE) {
        remesh(level, POPS.finishedPos(k));
      }
    }
  }

  public static void submit(
      PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState level) {
    if (!Animations.on() || POPS.count() == 0) {
      return;
    }
    CameraRenderState camera = level.cameraRenderState;
    ClientLevel client = Minecraft.getInstance().level;
    if (camera == null || client == null) {
      return;
    }
    AnimationsModule module = Animations.module();
    float radians = spinRadians();
    Vec3 eye = camera.pos;
    int count = POPS.count();
    for (int i = 0; i < count; i++) {
      BlockState state = (BlockState) POPS.state(i);
      if (state.getRenderShape() != RenderShape.MODEL) {
        continue;
      }
      long key = POPS.pos(i);
      BlockPos pos = BlockPos.of(key);
      poseStack.pushPose();
      poseStack.translate(
          pos.getX() - (float) eye.x, pos.getY() - (float) eye.y, pos.getZ() - (float) eye.z);
      transform(poseStack, i, radians);
      MovingBlockRenderState moving = MOVING[i];
      if (moving == null) {
        MOVING[i] = moving = new MovingBlockRenderState();
      }
      moving.blockPos = pos;
      moving.randomSeedPos = pos;
      moving.blockState = state;
      moving.biome = client.getBiome(pos);
      moving.cardinalLighting = client.cardinalLighting();
      moving.lightEngine = client.getLightEngine();
      collector.submitMovingBlock(poseStack, moving, 0);
      poseStack.popPose();
    }
  }

  public static boolean popOutline(
      PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState level) {
    if (!Animations.on()
        || POPS.count() == 0
        || level.blockOutlineRenderState == null
        || level.cameraRenderState == null) {
      return false;
    }
    BlockOutlineRenderState outline = level.blockOutlineRenderState;
    long key = outline.pos().asLong();
    int found = -1;
    for (int i = 0; i < POPS.count(); i++) {
      if (POPS.pos(i) == key) {
        found = i;
        break;
      }
    }
    if (found < 0) {
      return false;
    }
    Vec3 eye = level.cameraRenderState.pos;
    poseStack.pushPose();
    poseStack.translate(
        outline.pos().getX() - eye.x, outline.pos().getY() - eye.y, outline.pos().getZ() - eye.z);
    transform(poseStack, found, spinRadians());
    boolean highContrast = outline.highContrast();
    int color = highContrast ? -11010079 : ARGB.black(102);
    float width =
        Minecraft.getInstance()
            .gameRenderer
            .gameRenderState()
            .windowRenderState
            .appropriateLineWidth;
    if (highContrast) {
      collector.submitShapeOutline(
          poseStack,
          outline.shape(),
          RenderTypes.secondaryBlockOutline(),
          color,
          7.0F,
          outline.isTranslucent());
    }
    collector.submitShapeOutline(
        poseStack, outline.shape(), RenderTypes.lines(), color, width, outline.isTranslucent());
    poseStack.popPose();
    return true;
  }

  private static float spinRadians() {
    return (float) Math.toRadians(Animations.module().blocksSpin.get().floatValue());
  }

  private static void transform(PoseStack poseStack, int index, float radians) {
    poseStack.translate(0.5F, 0.5F + POPS.lift(index), 0.5F);
    poseStack.mulPose(Axis.YP.rotation(POPS.spin(index) * radians));
    float scale = POPS.scale(index);
    poseStack.scale(scale, scale, scale);
    poseStack.translate(-0.5F, -0.5F, -0.5F);
  }

  private static void unhideAll(ClientLevel level) {
    if (POPS.hiddenCount() == 0) {
      POPS.clear();
      return;
    }
    for (int i = 0; i < POPS.count(); i++) {
      if (POPS.kind(i) == BlockPops.Kind.PLACE) {
        remesh(level, POPS.pos(i));
      }
    }
    POPS.clear();
  }

  private static void remesh(ClientLevel level, long key) {
    level.setSectionDirtyWithNeighbors(
        BlockPos.getX(key) >> 4, BlockPos.getY(key) >> 4, BlockPos.getZ(key) >> 4);
  }
}
