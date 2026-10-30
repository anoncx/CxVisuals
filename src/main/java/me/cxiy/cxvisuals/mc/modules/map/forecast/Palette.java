package me.cxiy.cxvisuals.mc.modules.map.forecast;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;

final class Palette {
  static final int LAVA = 0xD9591A;

  private static final int SAND = 0xE2D6A0;
  private static final int SNOW = 0xF2F6F8;
  private static final int STONE = 0x8E8E8C;
  private static final int WATER = 0x3A66D6;
  private static final int ICE = 0xA9C9E8;
  private static final int SNOW_LINE = 150;
  private static final int STONE_LINE = 125;
  private static final int STONE_SPAN = 60;

  private enum Kind {
    PLAIN,
    BEACH,
    DESERT,
    BADLANDS,
    SNOWY,
    PEAKS,
    STONY,
    MUSHROOM,
    SWAMP,
    DARK,
    TAIGA,
    JUNGLE,
    SAVANNA,
    CHERRY,
    MEADOW,
    FROZEN_SEA
  }

  private record Entry(Kind kind, String path, int water) {}

  private static final Map<Holder<Biome>, Entry> ENTRIES = new ConcurrentHashMap<>();

  private Palette() {}

  private static Entry entry(Holder<Biome> biome) {
    return ENTRIES.computeIfAbsent(biome, Palette::classify);
  }

  private static Entry classify(Holder<Biome> biome) {
    String path = biome.unwrapKey().map(key -> key.identifier().getPath()).orElse("");
    Kind kind = Kind.PLAIN;
    if (path.contains("frozen_ocean") || path.contains("frozen_river")) {
      kind = Kind.FROZEN_SEA;
    } else if (path.contains("stony_shore")) {
      kind = Kind.STONY;
    } else if (path.contains("beach")) {
      kind = Kind.BEACH;
    } else if (path.contains("desert")) {
      kind = Kind.DESERT;
    } else if (path.contains("badlands")) {
      kind = Kind.BADLANDS;
    } else if (path.contains("stony_peaks")) {
      kind = Kind.STONY;
    } else if (path.contains("peaks") || path.contains("snowy") || path.contains("ice_spikes") || path.contains("grove")) {
      kind = Kind.SNOWY;
    } else if (path.contains("windswept")) {
      kind = Kind.PEAKS;
    } else if (path.contains("mushroom")) {
      kind = Kind.MUSHROOM;
    } else if (path.contains("swamp") || path.contains("mangrove")) {
      kind = Kind.SWAMP;
    } else if (path.contains("dark_forest")) {
      kind = Kind.DARK;
    } else if (path.contains("taiga") || path.contains("old_growth")) {
      kind = Kind.TAIGA;
    } else if (path.contains("jungle") || path.contains("bamboo")) {
      kind = Kind.JUNGLE;
    } else if (path.contains("savanna")) {
      kind = Kind.SAVANNA;
    } else if (path.contains("cherry")) {
      kind = Kind.CHERRY;
    } else if (path.contains("meadow")) {
      kind = Kind.MEADOW;
    }
    return new Entry(kind, path, biome.value().getWaterColor() & 0xFFFFFF);
  }

  static int sea(Holder<Biome> biome, int depth) {
    Entry entry = entry(biome);
    if (entry.kind == Kind.FROZEN_SEA) {
      return mix(ICE, entry.water, Math.min(0.6F, depth * 0.04F));
    }
    int water = entry.water == 0 ? WATER : entry.water;
    return mix(SAND, water, Math.min(0.92F, 0.5F + depth * 0.05F));
  }

  static int land(Holder<Biome> biome, int x, int z, int height) {
    Entry entry = entry(biome);
    int base =
        switch (entry.kind) {
          case BEACH -> SAND;
          case DESERT -> 0xE3D09A;
          case BADLANDS -> 0xC0693B;
          case SNOWY -> SNOW;
          case PEAKS -> mix(STONE, biome.value().getGrassColor(x, z) & 0xFFFFFF, 0.35F);
          case STONY -> STONE;
          case MUSHROOM -> 0x8B7B8E;
          case SWAMP -> 0x4E6B3A;
          case DARK -> 0x3F5E2A;
          case TAIGA -> 0x4A6B3E;
          case JUNGLE -> 0x3E8A2A;
          case SAVANNA -> 0xA09A4A;
          case CHERRY -> 0xE2A6C4;
          case MEADOW -> 0x6FA84A;
          case FROZEN_SEA -> ICE;
          case PLAIN -> biome.value().getGrassColor(x, z) & 0xFFFFFF;
        };
    if (entry.kind != Kind.SNOWY && entry.kind != Kind.DESERT && entry.kind != Kind.BADLANDS && entry.kind != Kind.BEACH) {
      if (height > STONE_LINE) {
        base = mix(base, STONE, Math.min(0.85F, (height - STONE_LINE) / (float) STONE_SPAN));
      }
      if (height > SNOW_LINE && biome.value().getBaseTemperature() < 0.55F) {
        base = mix(base, SNOW, Math.min(1.0F, (height - SNOW_LINE) / 30.0F));
      }
    }
    return base;
  }

  static int nether(Holder<Biome> biome, BlockState state) {
    String path = entry(biome).path;
    if (path.contains("crimson")) {
      return 0xA02E48;
    }
    if (path.contains("warped")) {
      return 0x1E8F8A;
    }
    if (path.contains("soul_sand")) {
      return 0x6B5B4A;
    }
    if (path.contains("basalt")) {
      return 0x4A4A52;
    }
    return 0x7A3434;
  }

  static int end(Holder<Biome> biome, int y) {
    return mix(0xCFCB92, 0xE6E2B0, Math.max(0.0F, Math.min(1.0F, (y - 40) / 40.0F)));
  }

  private static int mix(int from, int to, float amount) {
    int r = Math.round(((from >> 16) & 0xFF) * (1.0F - amount) + ((to >> 16) & 0xFF) * amount);
    int g = Math.round(((from >> 8) & 0xFF) * (1.0F - amount) + ((to >> 8) & 0xFF) * amount);
    int b = Math.round((from & 0xFF) * (1.0F - amount) + (to & 0xFF) * amount);
    return (r << 16) | (g << 8) | b;
  }
}
