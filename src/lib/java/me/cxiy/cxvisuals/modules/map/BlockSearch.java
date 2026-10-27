package me.cxiy.cxvisuals.modules.map;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class BlockSearch {
  public static final int MIN_QUERY = 2;
  public static final int CELL_SHIFT = 4;
  public static final int MAX_CLUSTERS = 6000;
  public static final int MAX_KINDS = 8;
  private static final long CELL_MASK = (1L << 22) - 1L;

  public record Kind(String name, String title, int id, int count, double nearX, double nearZ) {}

  public static final class Result {
    public static final Result NONE =
        new Result(
            "",
            List.of(),
            0,
            new float[0],
            new float[0],
            new float[0],
            new int[0],
            new int[0],
            true);

    public final String query;
    public final List<Kind> kinds;
    public final int total;
    public final float[] x;
    public final float[] y;
    public final float[] z;
    public final int[] count;
    public final int[] kind;
    public final boolean complete;

    Result(
        String query,
        List<Kind> kinds,
        int total,
        float[] x,
        float[] y,
        float[] z,
        int[] count,
        int[] kind,
        boolean complete) {
      this.query = query;
      this.kinds = kinds;
      this.total = total;
      this.x = x;
      this.y = y;
      this.z = z;
      this.count = count;
      this.kind = kind;
      this.complete = complete;
    }

    public int size() {
      return x.length;
    }
  }

  private BlockSearch() {}

  public static String normalize(String query) {
    String plain = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
    if (plain.startsWith("minecraft:")) {
      plain = plain.substring("minecraft:".length());
    }
    return plain.replace('_', ' ');
  }

  public static String name(String key) {
    int bar = key.indexOf('|');
    String top = bar < 0 ? key : key.substring(0, bar);
    int state = top.indexOf('[');
    String bare = state < 0 ? top : top.substring(0, state);
    int colon = bare.indexOf(':');
    return colon < 0 ? bare : bare.substring(colon + 1);
  }

  public static String title(String name) {
    StringBuilder out = new StringBuilder(name.length());
    boolean upper = true;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i) == '_' ? ' ' : name.charAt(i);
      out.append(upper ? Character.toUpperCase(c) : c);
      upper = c == ' ';
    }
    return out.toString();
  }

  public static Accumulator accumulator(
      BlockPalette palette, String query, double nearX, double nearZ) {
    String plain = normalize(query);
    if (plain.length() < MIN_QUERY) {
      return null;
    }
    return new Accumulator(palette, plain, plain.split("\s+"), nearX, nearZ);
  }

  public static final class Accumulator {
    private final BlockPalette palette;
    private final String query;
    private final String[] tokens;
    private final double centreX;
    private final double centreZ;
    private final Map<String, Integer> groups = new HashMap<>();
    private final List<String> names = new ArrayList<>();
    private final Map<Long, int[]> cells = new HashMap<>(1024);
    private boolean[] match = new boolean[0];
    private int[] group = new int[0];
    private int[] byId = new int[0];
    private int[] counts = new int[8];
    private double[] nearest = new double[8];
    private double[] nearX = new double[8];
    private double[] nearZ = new double[8];
    private int synced = 1;
    private long lastKey = Long.MIN_VALUE;
    private int[] last;
    private int total;

    Accumulator(
        BlockPalette palette, String query, String[] tokens, double centreX, double centreZ) {
      this.palette = palette;
      this.query = query;
      this.tokens = tokens;
      this.centreX = centreX;
      this.centreZ = centreZ;
      sync();
    }

    public boolean accepts(String key) {
      String spaced = name(key).replace('_', ' ');
      for (String token : tokens) {
        if (!spaced.contains(token)) {
          return false;
        }
      }
      return true;
    }

    public void sync() {
      int size = palette.size();
      if (size <= synced && match.length >= size) {
        return;
      }
      match = java.util.Arrays.copyOf(match, size);
      group = java.util.Arrays.copyOf(group, size);
      byId = java.util.Arrays.copyOf(byId, size);
      for (int id = synced; id < size; id++) {
        String key = palette.key(id);
        if (key.isEmpty() || !accepts(key)) {
          continue;
        }
        String name = name(key);
        Integer known = groups.get(name);
        if (known == null) {
          known = names.size();
          groups.put(name, known);
          names.add(name);
          if (known >= counts.length) {
            int grown = counts.length * 2;
            counts = java.util.Arrays.copyOf(counts, grown);
            nearest = java.util.Arrays.copyOf(nearest, grown);
            nearX = java.util.Arrays.copyOf(nearX, grown);
            nearZ = java.util.Arrays.copyOf(nearZ, grown);
          }
          counts[known] = 0;
          nearest[known] = Double.MAX_VALUE;
        }
        match[id] = true;
        group[id] = known;
      }
      synced = size;
    }

    public void add(Region region) {
      sync();
      char[] top = region.block;
      char[] under = region.ground;
      int baseX = region.x << Region.SHIFT;
      int baseZ = region.z << Region.SHIFT;
      int limit = match.length;
      for (int i = 0; i < Region.COLUMNS; i++) {
        int id = top[i];
        if (id >= limit || !match[id]) {
          id = under[i];
          if (id >= limit || !match[id]) {
            continue;
          }
        }
        int wx = baseX + (i & Region.MASK);
        int wz = baseZ + (i >> Region.SHIFT);
        int g = group[id];
        byId[id]++;
        counts[g]++;
        total++;
        double dx = wx - centreX;
        double dz = wz - centreZ;
        double distance = dx * dx + dz * dz;
        if (distance < nearest[g]) {
          nearest[g] = distance;
          nearX[g] = wx + 0.5;
          nearZ[g] = wz + 0.5;
        }
        long key =
            (((long) (wx >> CELL_SHIFT) & CELL_MASK) << 42)
                | (((long) (wz >> CELL_SHIFT) & CELL_MASK) << 20)
                | g;
        int[] cell;
        if (key == lastKey) {
          cell = last;
        } else {
          cell = cells.get(key);
          if (cell == null) {
            if (cells.size() >= MAX_CLUSTERS) {
              continue;
            }
            cell = new int[5];
            cell[4] = g;
            cells.put(key, cell);
          }
          lastKey = key;
          last = cell;
        }
        cell[0] += wx;
        cell[1] += region.height[i];
        cell[2] += wz;
        cell[3]++;
      }
    }

    private int best(int g) {
      int best = 0;
      for (int id = 1; id < match.length; id++) {
        if (match[id] && group[id] == g && byId[id] > byId[best]) {
          best = id;
        }
      }
      return best;
    }

    public Result finish() {
      return snapshot(true);
    }

    public Result snapshot(boolean complete) {
      List<Integer> order = new ArrayList<>();
      for (int g = 0; g < names.size(); g++) {
        if (counts[g] > 0) {
          order.add(g);
        }
      }
      order.sort((a, b) -> Integer.compare(counts[b], counts[a]));
      int[] remap = new int[names.size()];
      List<Kind> kinds = new ArrayList<>();
      for (int rank = 0; rank < order.size(); rank++) {
        int g = order.get(rank);
        remap[g] = rank;
        if (rank < MAX_KINDS) {
          String name = names.get(g);
          kinds.add(new Kind(name, title(name), best(g), counts[g], nearX[g], nearZ[g]));
        }
      }
      int n = 0;
      for (int[] cell : cells.values()) {
        if (cell[3] > 0 && remap[cell[4]] < MAX_KINDS) {
          n++;
        }
      }
      float[] x = new float[n];
      float[] y = new float[n];
      float[] z = new float[n];
      int[] count = new int[n];
      int[] kind = new int[n];
      int at = 0;
      for (int[] cell : cells.values()) {
        int rank = remap[cell[4]];
        if (cell[3] == 0 || rank >= MAX_KINDS) {
          continue;
        }
        float inverse = 1.0F / cell[3];
        x[at] = cell[0] * inverse + 0.5F;
        y[at] = cell[1] * inverse + 1.0F;
        z[at] = cell[2] * inverse + 0.5F;
        count[at] = cell[3];
        kind[at] = rank;
        at++;
      }
      return new Result(query, List.copyOf(kinds), total, x, y, z, count, kind, complete);
    }
  }
}
