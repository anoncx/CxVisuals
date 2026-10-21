package me.cxiy.cxvisuals.modules.inventory.skyblock;

import java.util.List;
import me.cxiy.cxvisuals.modules.inventory.Kind;

public record SkyRecipe(Kind kind, int columns, List<String> inputs, String output, String note) {}
