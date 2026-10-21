package me.cxiy.cxvisuals.mc.modules.donutsmp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import me.cxiy.cxvisuals.modules.donutsmp.auction.AuctionText;
import me.cxiy.cxvisuals.modules.donutsmp.auction.Variant;
import me.cxiy.cxvisuals.modules.donutsmp.auction.VariantKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.trim.ArmorTrim;

final class Variants {
  private static final int MAX_CONTENTS = 32;
  private static final int MAX_CONTENT_COUNT = 2048;

  private Variants() {}

  static Variant of(ItemStack stack) {
    return of(stack, 1);
  }

  static Variant of(ItemStack stack, int depth) {
    if (stack == null || stack.isEmpty()) {
      return Variant.EMPTY;
    }
    List<Variant.Enchant> enchants = enchants(stack);
    PotionContents potionContents = stack.get(DataComponents.POTION_CONTENTS);
    String potion = "";
    List<Variant.Effect> effects = new ArrayList<>();
    if (potionContents != null) {
      potion =
          potionContents
              .potion()
              .flatMap(holder -> holder.unwrapKey())
              .map(key -> key.identifier().toString())
              .orElse("");
      for (MobEffectInstance effect : potionContents.customEffects()) {
        String id =
            effect.getEffect().unwrapKey().map(key -> key.identifier().toString()).orElse("");
        if (!id.isEmpty()) {
          int duration = Math.max(1, Math.min(1_000_000, effect.getDuration()));
          int level = Math.max(1, Math.min(255, effect.getAmplifier() + 1));
          effects.add(new Variant.Effect(id, level, duration));
        }
      }
    }
    String name = name(stack, 64);
    int damage = 0;
    if (stack.isDamaged()) {
      int max = Math.max(1, stack.getMaxDamage());
      damage = Math.max(1, Math.min(99, Math.round(100.0F * stack.getDamageValue() / max)));
    }
    String trim = trim(stack);
    List<Variant.Content> contents = depth > 0 ? contents(stack) : List.of();
    return new Variant(enchants, potion, effects, name, damage, trim, contents);
  }

  private static List<Variant.Enchant> enchants(ItemStack stack) {
    Map<String, Integer> found = new LinkedHashMap<>();
    addEnchants(stack.get(DataComponents.ENCHANTMENTS), found);
    addEnchants(stack.get(DataComponents.STORED_ENCHANTMENTS), found);
    List<Variant.Enchant> out = new ArrayList<>(found.size());
    for (Map.Entry<String, Integer> entry : found.entrySet()) {
      out.add(new Variant.Enchant(entry.getKey(), entry.getValue()));
    }
    return out;
  }

  private static void addEnchants(ItemEnchantments values, Map<String, Integer> found) {
    if (values == null) {
      return;
    }
    for (var entry : values.entrySet()) {
      String id = entry.getKey().unwrapKey().map(key -> key.identifier().toString()).orElse("");
      if (id.isEmpty()) {
        continue;
      }
      found.merge(id, Math.min(255, entry.getIntValue()), Math::max);
    }
  }

  private static String name(ItemStack stack, int limit) {
    if (!Plain.renamed(stack)) {
      return "";
    }
    String raw = AuctionText.strip(stack.getHoverName().getString());
    StringBuilder out = new StringBuilder(raw.length());
    for (int i = 0; i < raw.length(); i++) {
      char ch = raw.charAt(i);
      if (ch >= 0x20 && ch != 0x7F) {
        out.append(ch);
      }
    }
    return shorten(out.toString().trim(), limit);
  }

  private static String shorten(String value, int limit) {
    return value.length() > limit ? value.substring(0, limit) : value;
  }

  private static String trim(ItemStack stack) {
    ArmorTrim trim = stack.get(DataComponents.TRIM);
    if (trim == null) {
      return "";
    }
    String material =
        trim.material().unwrapKey().map(key -> key.identifier().toString()).orElse("");
    String pattern = trim.pattern().unwrapKey().map(key -> key.identifier().toString()).orElse("");
    return material.isEmpty() || pattern.isEmpty() ? "" : material + "|" + pattern;
  }

  private static List<Variant.Content> contents(ItemStack stack) {
    Map<String, Variant.Content> merged = new LinkedHashMap<>();
    ItemContainerContents box = stack.get(DataComponents.CONTAINER);
    if (box != null) {
      box.nonEmptyItemCopyStream().forEach(inner -> addContent(merged, inner));
    }
    BundleContents bundle = stack.get(DataComponents.BUNDLE_CONTENTS);
    if (bundle != null) {
      List<ItemStackTemplate> items = bundle.items();
      for (ItemStackTemplate template : items) {
        addContent(merged, template.create());
      }
    }
    return new ArrayList<>(merged.values());
  }

  private static void addContent(Map<String, Variant.Content> merged, ItemStack inner) {
    if (inner == null || inner.isEmpty()) {
      return;
    }
    Variant variant = of(inner, 0);
    String id = BuiltInRegistries.ITEM.getKey(inner.getItem()).toString();
    String key = VariantKey.of(id, variant);
    Variant.Content previous = merged.get(key);
    if (previous == null) {
      if (merged.size() >= MAX_CONTENTS) {
        return;
      }
      merged.put(
          key,
          new Variant.Content(
              id,
              Math.min(MAX_CONTENT_COUNT, inner.getCount()),
              key,
              variant.enchants(),
              shorten(variant.name(), 48)));
      return;
    }
    int count = Math.min(MAX_CONTENT_COUNT, previous.count() + inner.getCount());
    merged.put(key, new Variant.Content(id, count, key, previous.enchants(), previous.name()));
  }
}
