package me.cxiy.cxvisuals.modules.inventory.skyblock;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import me.cxiy.cxvisuals.modules.inventory.Kind;

public final class SkyRepo {
  private static final String[] GRID = {"A1", "B1", "C1", "A2", "B2", "C2", "A3", "B3", "C3"};

  private SkyRepo() {}

  public static List<SkyItem> read(InputStream zip) throws IOException {
    List<SkyItem> items = new ArrayList<>(6000);
    ZipInputStream in = new ZipInputStream(zip);
    for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
      String name = entry.getName();
      if (entry.isDirectory() || !name.endsWith(".json") || !name.contains("/items/")) {
        continue;
      }
      byte[] bytes = in.readAllBytes();
      try {
        SkyItem item = parse(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)));
        if (item != null) {
          items.add(item);
        }
      } catch (RuntimeException e) {
        continue;
      }
    }
    return items;
  }

  public static SkyItem parse(JsonElement root) {
    if (!root.isJsonObject()) {
      return null;
    }
    JsonObject json = root.getAsJsonObject();
    String id = text(json, "internalname");
    if (id.isEmpty()) {
      return null;
    }
    List<String> lore = new ArrayList<>();
    if (json.has("lore") && json.get("lore").isJsonArray()) {
      for (JsonElement line : json.getAsJsonArray("lore")) {
        lore.add(line.getAsString());
      }
    }
    List<SkyRecipe> recipes = new ArrayList<>(2);
    if (json.has("recipe") && json.get("recipe").isJsonObject()) {
      SkyRecipe crafting = crafting(json.getAsJsonObject("recipe"), id);
      if (crafting != null) {
        recipes.add(crafting);
      }
    }
    if (json.has("recipes") && json.get("recipes").isJsonArray()) {
      for (JsonElement element : json.getAsJsonArray("recipes")) {
        if (element.isJsonObject()) {
          SkyRecipe parsed = recipe(element.getAsJsonObject(), id);
          if (parsed != null) {
            recipes.add(parsed);
          }
        }
      }
    }
    int damage =
        json.has("damage") && json.get("damage").isJsonPrimitive()
            ? json.get("damage").getAsInt()
            : 0;
    return new SkyItem(
        id,
        text(json, "itemid"),
        damage,
        text(json, "displayname"),
        lore,
        text(json, "nbttag"),
        recipes);
  }

  private static SkyRecipe recipe(JsonObject json, String id) {
    String type = text(json, "type");
    switch (type) {
      case "crafting":
        return crafting(json, id);
      case "forge":
        {
          List<String> inputs = new ArrayList<>();
          JsonArray array =
              json.has("inputs") && json.get("inputs").isJsonArray()
                  ? json.getAsJsonArray("inputs")
                  : new JsonArray();
          for (JsonElement input : array) {
            inputs.add(input.getAsString());
          }
          String output = text(json, "overrideOutputId");
          String count = text(json, "count");
          String result = (output.isEmpty() ? id : output) + ":" + (count.isEmpty() ? "1" : count);
          long seconds =
              json.has("duration") && json.get("duration").isJsonPrimitive()
                  ? json.get("duration").getAsLong()
                  : 0L;
          return inputs.isEmpty()
              ? null
              : new SkyRecipe(
                  Kind.FORGE,
                  Math.min(4, inputs.size()),
                  inputs,
                  result,
                  seconds > 0L ? duration(seconds) : "");
        }
      case "npc_shop":
        {
          List<String> inputs = new ArrayList<>();
          if (json.has("cost") && json.get("cost").isJsonArray()) {
            for (JsonElement cost : json.getAsJsonArray("cost")) {
              inputs.add(cost.getAsString());
            }
          }
          String result = text(json, "result");
          return inputs.isEmpty()
              ? null
              : new SkyRecipe(
                  Kind.SHOP,
                  Math.min(3, inputs.size()),
                  inputs,
                  result.isEmpty() ? id + ":1" : result,
                  "");
        }
      case "trade":
        {
          String cost = text(json, "cost");
          String result = text(json, "result");
          return cost.isEmpty()
              ? null
              : new SkyRecipe(
                  Kind.TRADE, 1, List.of(cost), result.isEmpty() ? id + ":1" : result, "");
        }
      default:
        return null;
    }
  }

  private static SkyRecipe crafting(JsonObject json, String id) {
    List<String> inputs = new ArrayList<>(9);
    boolean any = false;
    for (String slot : GRID) {
      String value = text(json, slot);
      any |= !value.isEmpty();
      inputs.add(value);
    }
    if (!any) {
      return null;
    }
    String count = text(json, "count");
    return new SkyRecipe(Kind.CRAFT, 3, inputs, id + ":" + (count.isEmpty() ? "1" : count), "");
  }

  private static String duration(long seconds) {
    if (seconds >= 3600L) {
      return (seconds / 3600L) + "h " + ((seconds % 3600L) / 60L) + "m";
    }
    return seconds >= 60L ? (seconds / 60L) + "m" : seconds + "s";
  }

  private static String text(JsonObject json, String key) {
    JsonElement element = json.get(key);
    return element != null && element.isJsonPrimitive() ? element.getAsString() : "";
  }
}
