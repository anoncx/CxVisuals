package me.cxiy.cxvisuals.modules.inventory;

public interface InventoryHost {
  void recipes(int handle);

  void uses(int handle);

  void give(int handle, boolean full);

  boolean canGive();

  boolean canFill(Recipe recipe);

  void fill(Recipe recipe);

  void hover(int handle);
}
