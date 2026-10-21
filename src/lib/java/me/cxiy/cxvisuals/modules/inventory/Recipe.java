package me.cxiy.cxvisuals.modules.inventory;

public record Recipe(
    Kind kind,
    int columns,
    int rows,
    int[][] inputs,
    int[] counts,
    int output,
    int outputCount,
    int station,
    String note) {
  public int cells() {
    return inputs.length;
  }

  public boolean makes(int handle) {
    return output == handle;
  }

  public boolean uses(int handle) {
    for (int[] slot : inputs) {
      for (int option : slot) {
        if (option == handle) {
          return true;
        }
      }
    }
    return station == handle;
  }
}
