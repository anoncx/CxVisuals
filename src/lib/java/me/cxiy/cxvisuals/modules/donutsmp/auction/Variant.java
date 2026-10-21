package me.cxiy.cxvisuals.modules.donutsmp.auction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public record Variant(
    List<Enchant> enchants,
    String potion,
    List<Effect> effects,
    String name,
    int damage,
    String trim,
    List<Content> contents) {

  public record Enchant(String id, int level) {}

  public record Effect(String id, int level, int duration) {}

  public record Content(String id, int count, String key, List<Enchant> enchants, String name) {
    public Content {
      key = key == null ? "" : key;
      enchants = enchants == null ? List.of() : List.copyOf(enchants);
      name = name == null ? "" : name;
    }
  }

  public static final Variant EMPTY = new Variant(List.of(), "", List.of(), "", 0, "", List.of());

  public Variant {
    enchants = enchants == null ? List.of() : List.copyOf(enchants);
    potion = potion == null ? "" : potion;
    effects = effects == null ? List.of() : List.copyOf(effects);
    name = name == null ? "" : name;
    trim = trim == null ? "" : trim;
    contents = contents == null ? List.of() : List.copyOf(contents);
  }

  public boolean plain() {
    return enchants.isEmpty()
        && potion.isEmpty()
        && effects.isEmpty()
        && name.isEmpty()
        && damage <= 0
        && trim.isEmpty()
        && contents.isEmpty();
  }

  public String summary() {
    List<String> parts = new ArrayList<>();
    if (!enchants.isEmpty()) {
      List<String> names = new ArrayList<>();
      for (Enchant enchant : enchants) {
        names.add(pretty(enchant.id()) + " " + roman(enchant.level()));
      }
      parts.add(String.join(", ", names));
    }
    if (!potion.isEmpty()) {
      parts.add("Potion: " + pretty(potion));
    }
    if (!effects.isEmpty()) {
      List<String> names = new ArrayList<>();
      for (Effect effect : effects) {
        names.add(pretty(effect.id()) + " " + roman(effect.level()));
      }
      parts.add(String.join(", ", names));
    }
    if (!contents.isEmpty()) {
      parts.add("Filled (" + contents.size() + " kinds)");
    }
    if (!name.isEmpty()) {
      parts.add("\"" + name + "\"");
    }
    if (damage > 0) {
      parts.add((100 - damage) + "% left");
    }
    if (!trim.isEmpty()) {
      parts.add("Trimmed");
    }
    return String.join(" · ", parts);
  }

  public JsonObject json() {
    JsonObject out = new JsonObject();
    if (!enchants.isEmpty()) {
      JsonArray list = new JsonArray();
      for (Enchant enchant : enchants) {
        JsonObject row = new JsonObject();
        row.addProperty("id", enchant.id());
        row.addProperty("l", enchant.level());
        list.add(row);
      }
      out.add("ench", list);
    }
    if (!potion.isEmpty()) {
      out.addProperty("potion", potion);
    }
    if (!effects.isEmpty()) {
      JsonArray list = new JsonArray();
      for (Effect effect : effects) {
        JsonObject row = new JsonObject();
        row.addProperty("id", effect.id());
        row.addProperty("l", effect.level());
        if (effect.duration() > 0) {
          row.addProperty("d", effect.duration());
        }
        list.add(row);
      }
      out.add("effects", list);
    }
    if (!name.isEmpty()) {
      out.addProperty("name", name);
    }
    if (damage > 0) {
      out.addProperty("damage", damage);
    }
    if (!trim.isEmpty()) {
      out.addProperty("trim", trim);
    }
    if (!contents.isEmpty()) {
      JsonArray list = new JsonArray();
      for (Content content : contents) {
        JsonObject row = new JsonObject();
        row.addProperty("id", content.id());
        row.addProperty("n", Math.max(1, content.count()));
        if (!content.key().isEmpty()) {
          row.addProperty("key", content.key());
        }
        if (!content.enchants().isEmpty()) {
          JsonArray enchants = new JsonArray();
          for (Enchant enchant : content.enchants()) {
            JsonObject value = new JsonObject();
            value.addProperty("id", enchant.id());
            value.addProperty("l", enchant.level());
            enchants.add(value);
          }
          row.add("ench", enchants);
        }
        if (!content.name().isEmpty()) {
          row.addProperty("name", content.name());
        }
        list.add(row);
      }
      out.add("contents", list);
    }
    return out;
  }

  public static Variant fromJson(JsonObject row) {
    if (row == null) {
      return EMPTY;
    }
    List<Enchant> enchants = new ArrayList<>();
    for (JsonElement element : array(row, "ench")) {
      if (!element.isJsonObject()) {
        continue;
      }
      JsonObject value = element.getAsJsonObject();
      String id = string(value, "id");
      int level = (int) number(value, "l");
      if (!id.isEmpty() && level > 0) {
        enchants.add(new Enchant(id, level));
      }
    }
    List<Effect> effects = new ArrayList<>();
    for (JsonElement element : array(row, "effects")) {
      if (!element.isJsonObject()) {
        continue;
      }
      JsonObject value = element.getAsJsonObject();
      String id = string(value, "id");
      int level = (int) number(value, "l");
      if (!id.isEmpty() && level > 0) {
        effects.add(new Effect(id, level, (int) number(value, "d")));
      }
    }
    List<Content> contents = new ArrayList<>();
    for (JsonElement element : array(row, "contents")) {
      if (!element.isJsonObject()) {
        continue;
      }
      JsonObject value = element.getAsJsonObject();
      String id = string(value, "id");
      int count = (int) number(value, "n");
      if (id.isEmpty() || count <= 0) {
        continue;
      }
      List<Enchant> inner = new ArrayList<>();
      for (JsonElement enchant : array(value, "ench")) {
        if (!enchant.isJsonObject()) {
          continue;
        }
        JsonObject entry = enchant.getAsJsonObject();
        String enchantId = string(entry, "id");
        int level = (int) number(entry, "l");
        if (!enchantId.isEmpty() && level > 0) {
          inner.add(new Enchant(enchantId, level));
        }
      }
      contents.add(new Content(id, count, string(value, "key"), inner, string(value, "name")));
    }
    return new Variant(
        enchants,
        string(row, "potion"),
        effects,
        string(row, "name"),
        (int) number(row, "damage"),
        string(row, "trim"),
        contents);
  }

  private static JsonArray array(JsonObject row, String key) {
    JsonElement value = row.get(key);
    return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
  }

  private static String string(JsonObject row, String key) {
    JsonElement value = row.get(key);
    return value != null && value.isJsonPrimitive() ? value.getAsString() : "";
  }

  private static long number(JsonObject row, String key) {
    JsonElement value = row.get(key);
    try {
      return value != null && value.isJsonPrimitive() ? (long) value.getAsDouble() : 0L;
    } catch (RuntimeException e) {
      return 0L;
    }
  }

  private static String pretty(String id) {
    String value = id;
    int colon = value.indexOf(':');
    if (colon >= 0) {
      value = value.substring(colon + 1);
    }
    value = value.replace('_', ' ');
    if (value.isEmpty()) {
      return id;
    }
    return Character.toUpperCase(value.charAt(0)) + value.substring(1);
  }

  private static String roman(int level) {
    return switch (level) {
      case 1 -> "I";
      case 2 -> "II";
      case 3 -> "III";
      case 4 -> "IV";
      case 5 -> "V";
      case 6 -> "VI";
      case 7 -> "VII";
      case 8 -> "VIII";
      case 9 -> "IX";
      case 10 -> "X";
      default -> Integer.toString(level);
    };
  }
}
