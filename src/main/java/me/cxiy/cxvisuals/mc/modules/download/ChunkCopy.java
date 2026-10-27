package me.cxiy.cxvisuals.mc.modules.download;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainerFactory;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.AABB;

record ChunkCopy(
    ResourceKey<Level> dimension,
    ChunkPos pos,
    int minSectionY,
    LevelChunkSection[] sections,
    List<CompoundTag> blockEntities,
    List<CompoundTag> entities,
    PalettedContainerFactory factory,
    RegistryAccess registries) {
  private static final String FULL = "minecraft:full";

  static ChunkCopy take(
      ClientLevel level, LevelChunk chunk, Map<BlockPos, ListTag> items, boolean withEntities) {
    LevelChunkSection[] source = chunk.getSections();
    LevelChunkSection[] sections = new LevelChunkSection[source.length];
    for (int i = 0; i < source.length; i++) {
      sections[i] = source[i].copy();
    }
    RegistryAccess registries = level.registryAccess();
    List<CompoundTag> blockEntities = new ArrayList<>(chunk.getBlockEntities().size());
    for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
      try {
        CompoundTag tag = blockEntity.saveWithFullMetadata(registries);
        ListTag seen = items.get(blockEntity.getBlockPos());
        if (seen != null) {
          tag.put("Items", seen.copy());
        }
        blockEntities.add(tag);
      } catch (RuntimeException ignored) {
        continue;
      }
    }
    List<CompoundTag> entities = new ArrayList<>();
    if (withEntities) {
      ChunkPos pos = chunk.getPos();
      AABB box =
          new AABB(
              pos.getMinBlockX(),
              level.getMinY(),
              pos.getMinBlockZ(),
              pos.getMaxBlockX() + 1,
              level.getMaxY() + 1,
              pos.getMaxBlockZ() + 1);
      for (Entity entity : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) {
        try {
          TagValueOutput output =
              TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
          if (entity.saveAsPassenger(output)) {
            entities.add(output.buildResult());
          }
        } catch (RuntimeException ignored) {
          continue;
        }
      }
    }
    return new ChunkCopy(
        level.dimension(),
        chunk.getPos(),
        chunk.getMinSectionY(),
        sections,
        blockEntities,
        entities,
        level.palettedContainerFactory(),
        registries);
  }

  CompoundTag chunkTag() {
    RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
    CompoundTag tag = NbtUtils.addCurrentDataVersion(new CompoundTag());
    tag.putInt("xPos", pos.x());
    tag.putInt("yPos", minSectionY);
    tag.putInt("zPos", pos.z());
    tag.putLong("LastUpdate", 0L);
    tag.putLong("InhabitedTime", 0L);
    tag.putString("Status", FULL);
    ListTag list = new ListTag();
    for (int i = 0; i < sections.length; i++) {
      CompoundTag section = new CompoundTag();
      section.put(
          "block_states",
          factory
              .blockStatesContainerCodec()
              .encodeStart(ops, sections[i].getStates())
              .getOrThrow());
      section.put(
          "biomes",
          factory.biomeContainerCodec().encodeStart(ops, sections[i].getBiomes()).getOrThrow());
      section.putByte("Y", (byte) (minSectionY + i));
      list.add(section);
    }
    tag.put("sections", list);
    ListTag blocks = new ListTag();
    blocks.addAll(blockEntities);
    tag.put("block_entities", blocks);
    return tag;
  }

  CompoundTag entityTag() {
    CompoundTag tag = NbtUtils.addCurrentDataVersion(new CompoundTag());
    tag.putIntArray("Position", new int[] {pos.x(), pos.z()});
    ListTag list = new ListTag();
    list.addAll(entities);
    tag.put("Entities", list);
    return tag;
  }
}
