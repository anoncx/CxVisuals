package me.cxiy.cxvisuals.modules.inventory;

public enum Kind {
  CRAFT("Crafting", 3),
  SMELT("Smelting", 1),
  BLAST("Blasting", 1),
  SMOKE("Smoking", 1),
  CAMPFIRE("Campfire", 1),
  STONECUT("Stonecutting", 1),
  SMITH("Smithing", 3),
  FORGE("Forge", 4),
  SHOP("Shop", 3),
  TRADE("Trading", 1);

  public final String title;
  public final int columns;

  Kind(String title, int columns) {
    this.title = title;
    this.columns = columns;
  }

  public boolean fillable() {
    return this == CRAFT;
  }
}
