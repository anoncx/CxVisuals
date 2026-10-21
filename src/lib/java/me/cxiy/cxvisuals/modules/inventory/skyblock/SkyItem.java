package me.cxiy.cxvisuals.modules.inventory.skyblock;

import java.util.List;

public record SkyItem(
    String id,
    String itemId,
    int damage,
    String name,
    List<String> lore,
    String nbt,
    List<SkyRecipe> recipes) {}
