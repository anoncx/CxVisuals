package me.cxiy.cxvisuals.modules.inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RecipeBook {
  public static final RecipeBook EMPTY = new RecipeBook(new ArrayList<>());

  private final Map<Integer, List<Recipe>> made = new HashMap<>(2048);
  private final Map<Integer, List<Recipe>> used = new HashMap<>(2048);
  private final int count;

  public RecipeBook(List<Recipe> recipes) {
    for (Recipe recipe : recipes) {
      made.computeIfAbsent(recipe.output(), key -> new ArrayList<>(2)).add(recipe);
      java.util.Set<Integer> seen = new java.util.HashSet<>();
      for (int[] slot : recipe.inputs()) {
        for (int option : slot) {
          if (seen.add(option)) {
            used.computeIfAbsent(option, key -> new ArrayList<>(4)).add(recipe);
          }
        }
      }
      if (recipe.station() >= 0) {
        if (seen.add(recipe.station())) {
          used.computeIfAbsent(recipe.station(), key -> new ArrayList<>(8)).add(recipe);
        }
      }
    }
    count = recipes.size();
  }

  public int size() {
    return count;
  }

  public List<Recipe> making(int handle) {
    return made.getOrDefault(handle, Collections.emptyList());
  }

  public List<Recipe> using(int handle) {
    return used.getOrDefault(handle, Collections.emptyList());
  }

  public boolean has(int handle) {
    return made.containsKey(handle) || used.containsKey(handle);
  }

  public static Map<Kind, List<Recipe>> byKind(List<Recipe> recipes) {
    Map<Kind, List<Recipe>> out = new EnumMap<>(Kind.class);
    for (Recipe recipe : recipes) {
      out.computeIfAbsent(recipe.kind(), key -> new ArrayList<>()).add(recipe);
    }
    return out;
  }
}
