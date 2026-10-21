package me.cxiy.cxvisuals.mc.modules.inventory;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

final class Legacy {
  private static final char SECTION = '\u00a7';
  private static final String[] COLORS = {
    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
    "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black"
  };
  private static final String[] DYES = {
    "ink_sac", "red_dye", "green_dye", "cocoa_beans", "lapis_lazuli", "purple_dye", "cyan_dye",
        "light_gray_dye",
    "gray_dye", "pink_dye", "lime_dye", "yellow_dye", "light_blue_dye", "magenta_dye", "orange_dye",
        "bone_meal"
  };
  private static final String[] WOODS = {"oak", "spruce", "birch", "jungle", "acacia", "dark_oak"};
  private static final String[] STONES = {
    "stone",
    "granite",
    "polished_granite",
    "diorite",
    "polished_diorite",
    "andesite",
    "polished_andesite"
  };
  private static final String[] FLOWERS = {
    "poppy",
    "blue_orchid",
    "allium",
    "azure_bluet",
    "red_tulip",
    "orange_tulip",
    "white_tulip",
    "pink_tulip",
    "oxeye_daisy"
  };
  private static final Map<String, String> RENAMES = new HashMap<>();

  static {
    String[][] pairs = {
      {"skull", "player_head"},
      {"melon", "melon_slice"},
      {"speckled_melon", "glistering_melon_slice"},
      {"reeds", "sugar_cane"},
      {"netherbrick", "nether_brick"},
      {"fireworks", "firework_rocket"},
      {"firework_charge", "firework_star"},
      {"web", "cobweb"},
      {"deadbush", "dead_bush"},
      {"waterlily", "lily_pad"},
      {"brick_block", "bricks"},
      {"hardened_clay", "terracotta"},
      {"stonebrick", "stone_bricks"},
      {"snow", "snow_block"},
      {"snow_layer", "snow"},
      {"wooden_door", "oak_door"},
      {"wooden_button", "oak_button"},
      {"wooden_pressure_plate", "oak_pressure_plate"},
      {"fence", "oak_fence"},
      {"fence_gate", "oak_fence_gate"},
      {"trapdoor", "oak_trapdoor"},
      {"wooden_slab", "oak_slab"},
      {"noteblock", "note_block"},
      {"golden_rail", "powered_rail"},
      {"lit_pumpkin", "jack_o_lantern"},
      {"boat", "oak_boat"},
      {"empty_map", "map"},
      {"record_13", "music_disc_13"},
      {"bed", "red_bed"},
      {"tallgrass", "short_grass"},
      {"double_plant", "sunflower"},
      {"yellow_flower", "dandelion"},
      {"mob_spawner", "spawner"},
      {"cooked_fish", "cooked_cod"},
      {"fish", "cod"},
      {"carpet", "white_carpet"},
      {"quartz_ore", "nether_quartz_ore"},
      {"piston_head", "piston"},
      {"lit_redstone_lamp", "redstone_lamp"},
      {"log", "oak_log"},
      {"log2", "acacia_log"},
      {"planks", "oak_planks"},
      {"leaves", "oak_leaves"},
      {"leaves2", "acacia_leaves"},
      {"sapling", "oak_sapling"},
      {"wool", "white_wool"},
      {"stained_glass", "white_stained_glass"},
      {"stained_glass_pane", "white_stained_glass_pane"},
      {"stained_hardened_clay", "white_terracotta"},
      {"red_flower", "poppy"},
      {"dye", "ink_sac"},
      {"spawn_egg", "allay_spawn_egg"},
    };
    for (String[] pair : pairs) {
      RENAMES.put(pair[0], pair[1]);
    }
  }

  private Legacy() {}

  static Item item(String id, int damage) {
    String name = id.startsWith("minecraft:") ? id.substring(10) : id;
    String modern = modernName(name, damage);
    Item found =
        BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(modern)).orElse(null);
    if (found != null && found != Items.AIR) {
      return found;
    }
    found = BuiltInRegistries.ITEM.getOptional(Identifier.withDefaultNamespace(name)).orElse(null);
    return found != null && found != Items.AIR ? found : Items.PAPER;
  }

  private static String modernName(String name, int damage) {
    int d = Math.max(0, damage);
    switch (name) {
      case "wool", "stained_glass", "stained_glass_pane", "carpet":
        return COLORS[d % 16]
            + "_"
            + (name.equals("wool") ? "wool" : name.equals("carpet") ? "carpet" : name);
      case "stained_hardened_clay":
        return COLORS[d % 16] + "_terracotta";
      case "dye":
        return DYES[d % 16];
      case "log":
        return WOODS[d % 4] + "_log";
      case "log2":
        return WOODS[4 + d % 2] + "_log";
      case "planks":
        return WOODS[d % 6] + "_planks";
      case "leaves":
        return WOODS[d % 4] + "_leaves";
      case "leaves2":
        return WOODS[4 + d % 2] + "_leaves";
      case "sapling":
        return WOODS[d % 6] + "_sapling";
      case "stone":
        return STONES[d % 7];
      case "red_flower":
        return FLOWERS[d % 9];
      case "sand":
        return d == 1 ? "red_sand" : "sand";
      case "coal":
        return d == 1 ? "charcoal" : "coal";
      case "golden_apple":
        return d == 1 ? "enchanted_golden_apple" : "golden_apple";
      case "skull":
        return d == 3
            ? "player_head"
            : d == 0
                ? "skeleton_skull"
                : d == 1 ? "wither_skeleton_skull" : d == 2 ? "zombie_head" : "creeper_head";
      case "fish":
        return d == 1 ? "salmon" : d == 2 ? "tropical_fish" : d == 3 ? "pufferfish" : "cod";
      case "cooked_fish":
        return d == 1 ? "cooked_salmon" : "cooked_cod";
      case "double_plant":
        return d == 0
            ? "sunflower"
            : d == 1
                ? "lilac"
                : d == 2 ? "tall_grass" : d == 3 ? "large_fern" : d == 4 ? "rose_bush" : "peony";
      case "tallgrass":
        return d == 2 ? "fern" : "short_grass";
      default:
        return RENAMES.getOrDefault(name, name);
    }
  }

  static MutableComponent component(String legacy) {
    MutableComponent root = Component.empty().withStyle(Style.EMPTY.withItalic(false));
    StringBuilder run = new StringBuilder();
    Style style = Style.EMPTY;
    for (int i = 0; i < legacy.length(); i++) {
      char c = legacy.charAt(i);
      if ((c == '§' || c == '&' && false) && i + 1 < legacy.length()) {
        flush(root, run, style);
        ChatFormatting format =
            ChatFormatting.getByCode(Character.toLowerCase(legacy.charAt(i + 1)));
        i++;
        if (format == null) {
          continue;
        }
        if (format == ChatFormatting.RESET) {
          style = Style.EMPTY;
        } else if (Character.digit(format.toString().charAt(1), 16) >= 0) {
          style = Style.EMPTY.withColor(format);
        } else {
          style = style.applyFormat(format);
        }
      } else {
        run.append(c);
      }
    }
    flush(root, run, style);
    return root;
  }

  private static void flush(MutableComponent root, StringBuilder run, Style style) {
    if (!run.isEmpty()) {
      root.append(Component.literal(run.toString()).withStyle(style));
      run.setLength(0);
    }
  }

  static String strip(String legacy) {
    StringBuilder out = new StringBuilder(legacy.length());
    for (int i = 0; i < legacy.length(); i++) {
      char c = legacy.charAt(i);
      if (c == '§' && i + 1 < legacy.length()) {
        i++;
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}
