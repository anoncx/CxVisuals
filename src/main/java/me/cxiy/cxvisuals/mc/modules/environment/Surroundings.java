package me.cxiy.cxvisuals.mc.modules.environment;

import me.cxiy.cxvisuals.modules.environment.particles.Climate;
import me.cxiy.cxvisuals.modules.environment.particles.Ground;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

final class Surroundings implements Ground {
  private static final int DEPTH = 40;
  private static final long BIOME_NANOS = 250_000_000L;
  private static final float FULL_NIGHT = 0.4F;

  private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
  private ClientLevel level;
  private boolean ceiling;
  private long biomeAt;

  void read(ClientLevel level, LevelRenderState state, Climate climate, long now) {
    this.level = level;
    ceiling = level.dimensionType().hasCeiling();
    BlockPos pos = state.cameraRenderState.blockPos;
    climate.realm =
        level.dimension() == Level.NETHER
            ? Climate.Realm.NETHER
            : level.dimension() == Level.END ? Climate.Realm.END : Climate.Realm.OVERWORLD;
    if (now - biomeAt > BIOME_NANOS) {
      biomeAt = now;
      Holder<Biome> biome = level.getBiome(pos);
      climate.biome = biome.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
      climate.cold = biome.value().coldEnoughToSnow(pos, level.getSeaLevel());
    }
    climate.rain = level.getRainLevel(1.0F);
    float stars = Math.clamp(state.skyRenderState.starBrightness / FULL_NIGHT, 0.0F, 1.0F);
    climate.night = stars * stars * (3.0F - 2.0F * stars);
    climate.open = level.getBrightness(LightLayer.SKY, pos) / Climate.LEVELS;
    climate.light =
        climate.lit(climate.open, level.getBrightness(LightLayer.BLOCK, pos) / Climate.LEVELS);
    FogData fog = state.cameraRenderState.fogData;
    climate.fogStart = Math.min(fog.environmentalStart, fog.renderDistanceStart);
    climate.fogEnd = Math.min(fog.environmentalEnd, fog.renderDistanceEnd);
    climate.far = fog.renderDistanceEnd;
  }

  @Override
  public float floor(double x, double top, double z) {
    int column = Mth.floor(x);
    int row = Mth.floor(z);
    int from = Mth.floor(top);
    if (!ceiling) {
      int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column, row);
      if (surface <= from + 1) {
        return surface;
      }
    }
    for (int y = from; y > from - DEPTH; y--) {
      BlockState below = level.getBlockState(cursor.set(column, y - 1, row));
      if (!below.getCollisionShape(level, cursor).isEmpty() || !below.getFluidState().isEmpty()) {
        return y;
      }
    }
    return from - DEPTH;
  }

  @Override
  public float top(int x, int y, int z) {
    BlockState state = level.getBlockState(cursor.set(x, y, z));
    FluidState fluid = state.getFluidState();
    float wet = fluid.isEmpty() ? AIR : fluid.getHeight(level, cursor);
    VoxelShape shape = state.getCollisionShape(level, cursor);
    return shape.isEmpty() ? wet : Math.max(wet, (float) shape.max(Direction.Axis.Y));
  }

  @Override
  public int light(int x, int y, int z) {
    cursor.set(x, y, z);
    return level.getBrightness(LightLayer.SKY, cursor) << 4
        | level.getBrightness(LightLayer.BLOCK, cursor);
  }
}
