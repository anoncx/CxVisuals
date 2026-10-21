package me.cxiy.cxvisuals.mc.modules.inventory;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.modules.inventory.Handles;
import me.cxiy.cxvisuals.modules.inventory.Kind;
import me.cxiy.cxvisuals.modules.inventory.Recipe;
import me.cxiy.cxvisuals.modules.inventory.skyblock.SkyItem;
import me.cxiy.cxvisuals.modules.inventory.skyblock.SkyRecipe;
import me.cxiy.cxvisuals.modules.inventory.skyblock.SkyRepo;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.component.ResolvableProfile;

final class SkyBlock {
  private static final String SOURCE =
      "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/refs/heads/master.zip";
  private static final String CACHE_FILE = "skyblock-repo.zip";
  private static final long HOUR_MILLIS = 3_600_000L;
  private static final int SKULL_DAMAGE = 3;

  private static boolean running;

  private SkyBlock() {}

  static void load(Path folder, double refreshHours, BiConsumer<int[], List<Recipe>> done) {
    if (running) {
      return;
    }
    running = true;
    Thread worker =
        new Thread(
            () -> {
              try {
                Path cache = folder.resolve(CACHE_FILE);
                if (!Files.isRegularFile(cache)
                    || System.currentTimeMillis() - Files.getLastModifiedTime(cache).toMillis()
                        > refreshHours * HOUR_MILLIS) {
                  download(cache);
                }
                List<SkyItem> items;
                try (InputStream in = Files.newInputStream(cache)) {
                  items = SkyRepo.read(in);
                }
                Minecraft.getInstance()
                    .execute(
                        () -> {
                          try {
                            convert(items, done);
                          } finally {
                            running = false;
                          }
                        });
              } catch (IOException | InterruptedException | RuntimeException e) {
                CxVisualsClient.LOG.warn("SkyBlock item data unavailable: {}", e.toString());
                running = false;
              }
            },
            "CxVisuals-SkyBlock");
    worker.setDaemon(true);
    worker.start();
  }

  private static void download(Path target) throws IOException, InterruptedException {
    Files.createDirectories(target.getParent());
    HttpClient client =
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
    HttpRequest request =
        HttpRequest.newBuilder(URI.create(SOURCE))
            .timeout(Duration.ofMinutes(3))
            .header("User-Agent", "CxVisuals")
            .build();
    Path partial = target.resolveSibling(CACHE_FILE + ".part");
    HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(partial));
    if (response.statusCode() != 200) {
      Files.deleteIfExists(partial);
      throw new IOException("HTTP " + response.statusCode());
    }
    Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING);
  }

  private static void convert(List<SkyItem> items, BiConsumer<int[], List<Recipe>> done) {
    Handles handles = Stacks.handles();
    int[] entries = new int[items.size()];
    int count = 0;
    for (SkyItem item : items) {
      ItemStack stack = stackOf(item);
      String tip = Legacy.strip(String.join(" ", item.lore()));
      entries[count++] =
          Stacks.register(Stacks.SKYBLOCK_PREFIX + item.id(), stack, "skyblock", tip);
    }
    int[] shown = java.util.Arrays.copyOf(entries, count);
    List<Recipe> recipes = new ArrayList<>(items.size());
    for (SkyItem item : items) {
      for (SkyRecipe source : item.recipes()) {
        Recipe recipe = recipeOf(source, handles);
        if (recipe != null) {
          recipes.add(recipe);
        }
      }
    }
    done.accept(shown, recipes);
  }

  private static Recipe recipeOf(SkyRecipe source, Handles handles) {
    int[] out = resolve(source.output(), handles);
    if (out == null) {
      return null;
    }
    int size = source.inputs().size();
    int[][] inputs = new int[size][];
    int[] counts = new int[size];
    boolean any = false;
    for (int i = 0; i < size; i++) {
      int[] slot = resolve(source.inputs().get(i), handles);
      if (slot == null) {
        inputs[i] = new int[0];
        counts[i] = 0;
      } else {
        inputs[i] = new int[] {slot[0]};
        counts[i] = slot[1];
        any = true;
      }
    }
    if (!any) {
      return null;
    }
    int columns = Math.max(1, source.columns());
    int rows = (size + columns - 1) / columns;
    int station =
        source.kind() == Kind.CRAFT
            ? Stacks.handles().find("minecraft:crafting_table")
            : Handles.NONE;
    return new Recipe(
        source.kind(), columns, rows, inputs, counts, out[0], out[1], station, source.note());
  }

  private static int[] resolve(String spec, Handles handles) {
    if (spec == null || spec.isEmpty()) {
      return null;
    }
    int split = spec.lastIndexOf(':');
    String id = spec;
    int amount = 1;
    if (split > 0) {
      try {
        amount = Math.max(1, (int) Double.parseDouble(spec.substring(split + 1)));
        id = spec.substring(0, split);
      } catch (NumberFormatException e) {
        amount = 1;
        id = spec;
      }
    }
    int handle = handles.find(Stacks.SKYBLOCK_PREFIX + id);
    return handle == Handles.NONE ? null : new int[] {handle, amount};
  }

  private static ItemStack stackOf(SkyItem item) {
    ItemStack stack = new ItemStack(Legacy.item(item.itemId(), item.damage()));
    if (!item.name().isEmpty()) {
      stack.set(DataComponents.CUSTOM_NAME, Legacy.component(item.name()));
    }
    if (!item.lore().isEmpty()) {
      List<net.minecraft.network.chat.Component> lines = new ArrayList<>(item.lore().size());
      for (String line : item.lore()) {
        lines.add(Legacy.component(line));
      }
      stack.set(DataComponents.LORE, new ItemLore(lines));
    }
    CompoundTag tag = new CompoundTag();
    tag.putString("id", item.id());
    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    if (!item.nbt().isEmpty()) {
      decorate(stack, item);
    }
    return stack;
  }

  private static void decorate(ItemStack stack, SkyItem item) {
    try {
      CompoundTag nbt = TagParser.parseCompoundFully(item.nbt());
      if (item.damage() == SKULL_DAMAGE && nbt.contains("SkullOwner")) {
        CompoundTag owner = nbt.getCompoundOrEmpty("SkullOwner");
        ListTag textures = owner.getCompoundOrEmpty("Properties").getListOrEmpty("textures");
        if (!textures.isEmpty()) {
          String value = textures.getCompoundOrEmpty(0).getStringOr("Value", "");
          if (!value.isEmpty()) {
            UUID id =
                UUID.nameUUIDFromBytes(item.id().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            PropertyMap properties =
                new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", value)));
            stack.set(
                DataComponents.PROFILE,
                ResolvableProfile.createResolved(new GameProfile(id, "", properties)));
          }
        }
      }
      if (nbt.contains("ench") || item.nbt().contains("ench:")) {
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
      }
    } catch (CommandSyntaxException | RuntimeException e) {
      CxVisualsClient.LOG.warn(
          "SkyBlock item {} could not be dressed: {}", item.id(), e.toString());
    }
  }
}
