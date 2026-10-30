package me.cxiy.cxvisuals.mc.modules.waypoints;

import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.modules.waypoints.Icons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

final class Surroundings {
  record Guess(String name, String icon) {}

  private static final int SPREAD = 6;
  private static final int DOWN = 2;
  private static final int UP = 3;
  private static final int VILLAGE_RADIUS = 24;
  private static final int VILLAGERS = 2;
  private static final int WORKSITE = 2;
  private static final int CROPS = 4;
  private static final int STORAGE = 2;
  private static final int UNDERGROUND = 50;

  private Surroundings() {}

  static Guess guess(Minecraft mc) {
    LocalPlayer player = mc.player;
    Level level = mc.level;
    if (player == null || level == null) {
      return null;
    }
    BlockPos origin = player.blockPosition();
    BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
    int worksite = 0;
    int crops = 0;
    int storage = 0;
    boolean portal = false;
    boolean spawner = false;
    for (int dx = -SPREAD; dx <= SPREAD; dx++) {
      for (int dz = -SPREAD; dz <= SPREAD; dz++) {
        for (int dy = -DOWN; dy <= UP; dy++) {
          at.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
          BlockState state = level.getBlockState(at);
          if (state.isAir()) {
            continue;
          }
          Block block = state.getBlock();
          if (block == Blocks.NETHER_PORTAL
              || block == Blocks.END_PORTAL
              || block == Blocks.END_PORTAL_FRAME) {
            portal = true;
          } else if (block == Blocks.SPAWNER) {
            spawner = true;
          } else if (block == Blocks.FARMLAND) {
            crops++;
          } else if (block == Blocks.CHEST
              || block == Blocks.BARREL
              || block == Blocks.TRAPPED_CHEST
              || state.is(BlockTags.SHULKER_BOXES)) {
            storage++;
          } else if (state.is(BlockTags.BEDS)
              || block == Blocks.CRAFTING_TABLE
              || block == Blocks.FURNACE
              || block == Blocks.BLAST_FURNACE) {
            worksite++;
          }
        }
      }
    }
    if (portal) {
      return new Guess(Lang.tr("Portal"), Icons.PORTAL);
    }
    if (spawner) {
      return new Guess(Lang.tr("Dungeon"), Icons.SWORD);
    }
    int villagers =
        level
            .getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(VILLAGE_RADIUS))
            .size();
    if (villagers >= VILLAGERS) {
      return new Guess(Lang.tr("Village"), Icons.VILLAGE);
    }
    if (crops >= CROPS) {
      return new Guess(Lang.tr("Farm"), Icons.FARM);
    }
    if (worksite >= WORKSITE) {
      return new Guess(Lang.tr("Home"), Icons.HOME);
    }
    if (storage >= STORAGE) {
      return new Guess(Lang.tr("Storage"), Icons.CHEST);
    }
    if (player.isInWater()) {
      return new Guess(Lang.tr("Ocean"), Icons.BOAT);
    }
    if (origin.getY() < UNDERGROUND && !level.canSeeSky(origin)) {
      return new Guess(Lang.tr("Mine"), Icons.MINE);
    }
    return null;
  }
}
