package me.cxiy.cxvisuals.mc.modules.animations;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cxiy.cxvisuals.modules.animations.AnimationsModule;
import me.cxiy.cxvisuals.modules.animations.AnimationsModule.BlockBreak;
import me.cxiy.cxvisuals.modules.animations.BlockPops;
import me.cxiy.cxvisuals.modules.animations.BlockPose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

public final class BlockPopEffects {
  private static final double RANGE_SQ = 64.0 * 64.0;
  private static final long NO_MINING = Long.MIN_VALUE;
  private static final AABB WHOLE = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

  private static final BlockPops POPS = new BlockPops();
  private static final BlockPose POSE = new BlockPose();
  private static final Matrix4f MATRIX = new Matrix4f();
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

  public static boolean outlinePose(long key, Matrix4f out) {
    int index = Animations.on() ? POPS.indexOf(key) : -1;
    if (index < 0) {
      return false;
    }
    out.set(pose(index));
    return true;
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
    if (broken && module.blocksBreak.get() == BlockBreak.OFF) {
      return;
    }
    BlockState visual = place ? next : old;
    if (visual.getRenderShape() != RenderShape.MODEL && !visual.hasBlockEntity()) {
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
    boolean started =
        POPS.start(
            key,
            place ? BlockPops.Kind.PLACE : BlockPops.Kind.BREAK,
            visual,
            module.blocksSource.get(),
            self,
            box(level, pos, visual));
    if (started && broken) {
      POPS.attach(key, extract(level.getBlockEntity(pos)));
    }
    if (wasHidden && !POPS.hidden(key)) {
      remesh(level, key);
    }
    if (broken && key == miningPos) {
      miningPos = NO_MINING;
    }
  }

  private static BlockPops.Box box(ClientLevel level, BlockPos pos, BlockState state) {
    VoxelShape shape = state.getShape(level, pos);
    AABB bounds = shape.isEmpty() ? WHOLE : shape.bounds();
    Direction partner = partner(state);
    if (partner != null) {
      bounds = bounds.minmax(bounds.move(partner.getStepX(), partner.getStepY(), partner.getStepZ()));
    }
    return new BlockPops.Box(
        (float) bounds.minX,
        (float) bounds.minY,
        (float) bounds.minZ,
        (float) bounds.maxX,
        (float) bounds.maxY,
        (float) bounds.maxZ);
  }

  private static Direction partner(BlockState state) {
    if (state.getBlock() instanceof BedBlock) {
      Direction facing = state.getValue(BedBlock.FACING);
      return state.getValue(BedBlock.PART) == BedPart.FOOT ? facing : facing.getOpposite();
    }
    if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
      return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
          ? Direction.UP
          : Direction.DOWN;
    }
    if (state.getBlock() instanceof ChestBlock
        && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
      return ChestBlock.getConnectedDirection(state);
    }
    return null;
  }

  private static BlockEntityRenderState extract(BlockEntity entity) {
    if (entity == null) {
      return null;
    }
    Minecraft mc = Minecraft.getInstance();
    float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
    return mc.levelRenderer
        .blockEntityRenderDispatcher()
        .tryExtractRenderState(entity, partial, null, false);
  }

  public static void submitEntities(
      PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState level) {
    ClientLevel client = Minecraft.getInstance().level;
    if (POPS.count() == 0 || !Animations.on() || client == null) {
      return;
    }
    BlockEntityRenderDispatcher dispatcher =
        Minecraft.getInstance().levelRenderer.blockEntityRenderDispatcher();
    Vec3 eye = level.cameraRenderState.pos;
    for (int i = 0; i < POPS.count(); i++) {
      long key = POPS.pos(i);
      BlockEntityRenderState state =
          POPS.kind(i) == BlockPops.Kind.BREAK
              ? (BlockEntityRenderState) POPS.attachment(i)
              : ((BlockState) POPS.state(i)).hasBlockEntity()
                  ? extract(client.getBlockEntity(BlockPos.of(key)))
                  : null;
      if (state == null) {
        continue;
      }
      poseStack.pushPose();
      place(poseStack, key, eye, i);
      dispatcher.submit(state, poseStack, collector, level.cameraRenderState);
      poseStack.popPose();
    }
  }

  public static boolean ownsEntity(BlockEntityRenderState state) {
    return POPS.count() > 0 && Animations.on() && POPS.indexOf(state.blockPos.asLong()) >= 0;
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
    POPS.styles(module.blocksPlace.get(), module.blocksBreak.get());
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
    Vec3 eye = camera.pos;
    for (int i = 0; i < POPS.count(); i++) {
      BlockState state = (BlockState) POPS.state(i);
      if (state.getRenderShape() != RenderShape.MODEL) {
        continue;
      }
      long key = POPS.pos(i);
      BlockPos pos = BlockPos.of(key);
      poseStack.pushPose();
      place(poseStack, key, eye, i);
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
    int found = POPS.indexOf(key);
    if (found < 0) {
      return false;
    }
    poseStack.pushPose();
    place(poseStack, key, level.cameraRenderState.pos, found);
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

  private static void place(PoseStack poseStack, long key, Vec3 eye, int index) {
    poseStack.translate(
        (float) (BlockPos.getX(key) - eye.x),
        (float) (BlockPos.getY(key) - eye.y),
        (float) (BlockPos.getZ(key) - eye.z));
    poseStack.mulPose(pose(index));
  }

  private static Matrix4f pose(int index) {
    float spin = (float) Math.toRadians(Animations.module().blocksSpin.get().floatValue());
    POPS.pose(index, spin, POSE);
    return MATRIX
        .translation(POSE.pivotX + POSE.moveX, POSE.pivotY + POSE.moveY, POSE.pivotZ + POSE.moveZ)
        .rotateY(POSE.yaw)
        .rotateX(POSE.pitch)
        .rotateZ(POSE.roll)
        .scale(POSE.scaleX, POSE.scaleY, POSE.scaleZ)
        .translate(-POSE.pivotX, -POSE.pivotY, -POSE.pivotZ);
  }

  private static void unhideAll(ClientLevel level) {
    if (POPS.hiddenCount() == 0) {
      POPS.clear();
      return;
    }
    for (int i = 0; i < POPS.count(); i++) {
      if (POPS.kind(i) == BlockPops.Kind.PLACE && !POPS.settling(i)) {
        remesh(level, POPS.pos(i));
      }
    }
    POPS.clear();
  }

  private static void remesh(ClientLevel level, long key) {
    BlockPos pos = BlockPos.of(key);
    BlockState state = level.getBlockState(pos);
    level.sendBlockUpdated(pos, state, state, Block.UPDATE_IMMEDIATE);
  }
}
