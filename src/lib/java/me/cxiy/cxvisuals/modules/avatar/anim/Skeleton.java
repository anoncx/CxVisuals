package me.cxiy.cxvisuals.modules.avatar.anim;

public final class Skeleton {
  public static final int STRIDE = 10;
  public static final Skeleton EMPTY =
      new Skeleton(
          new String[0],
          new int[0],
          new float[0],
          new float[0],
          new float[0],
          new int[0],
          new float[0]);

  private final String[] names;
  private final int[] parents;
  private final float[] restT;
  private final float[] restR;
  private final float[] restS;
  private final int[] paletteNodes;
  private final float[] inverseBind;

  public Skeleton(
      String[] names,
      int[] parents,
      float[] restT,
      float[] restR,
      float[] restS,
      int[] paletteNodes,
      float[] inverseBind) {
    this.names = names;
    this.parents = parents;
    this.restT = restT;
    this.restR = restR;
    this.restS = restS;
    this.paletteNodes = paletteNodes;
    this.inverseBind = inverseBind;
  }

  public int count() {
    return parents.length;
  }

  public int paletteSize() {
    return paletteNodes.length;
  }

  public String name(int node) {
    return names[node];
  }

  public int parent(int node) {
    return parents[node];
  }

  public int paletteNode(int entry) {
    return paletteNodes[entry];
  }

  public float[] restT() {
    return restT;
  }

  public float[] restR() {
    return restR;
  }

  public float[] restS() {
    return restS;
  }

  public float[] inverseBind() {
    return inverseBind;
  }

  public void rest(float[] local) {
    for (int i = 0; i < parents.length; i++) {
      int o = i * STRIDE;
      local[o] = restT[i * 3];
      local[o + 1] = restT[i * 3 + 1];
      local[o + 2] = restT[i * 3 + 2];
      local[o + 3] = restR[i * 4];
      local[o + 4] = restR[i * 4 + 1];
      local[o + 5] = restR[i * 4 + 2];
      local[o + 6] = restR[i * 4 + 3];
      local[o + 7] = restS[i * 3];
      local[o + 8] = restS[i * 3 + 1];
      local[o + 9] = restS[i * 3 + 2];
    }
  }

  public void pose(float[] local, float[] globals, float[] palette) {
    for (int i = 0; i < parents.length; i++) {
      int o = i * STRIDE;
      int g = i * 16;
      Affine.compose(
          local[o],
          local[o + 1],
          local[o + 2],
          local[o + 3],
          local[o + 4],
          local[o + 5],
          local[o + 6],
          local[o + 7],
          local[o + 8],
          local[o + 9],
          globals,
          g);
      int parent = parents[i];
      if (parent >= 0) {
        Affine.multiply(globals, parent * 16, globals, g, globals, g);
      }
    }
    for (int e = 0; e < paletteNodes.length; e++) {
      Affine.multiply(globals, paletteNodes[e] * 16, inverseBind, e * 16, palette, e * 16);
    }
  }
}
