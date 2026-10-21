package me.cxiy.cxvisuals.modules.donutsmp.auction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class Sanity {
  public static final double BAND = 5.0;
  public static final int AGREEING = 3;
  public static final int REFERENCE_SIGHTINGS = 3;

  private static final long WINDOW = 6L * 3600L;
  private static final double TIGHT = 2.0;
  private static final int MOST_ITEMS = 512;
  private static final int MOST_CHALLENGES = 16;

  private record Challenge(int seller, double unit, long at) {}

  private final Map<String, List<Challenge>> challenges = new HashMap<>();

  public boolean accept(String id, double unit, int seller, double reference, long now) {
    if (reference <= 0.0 || (unit <= reference * BAND && unit >= reference / BAND)) {
      challenges.remove(id);
      return true;
    }
    if (seller == 0) {
      return false;
    }
    if (challenges.size() >= MOST_ITEMS && !challenges.containsKey(id)) {
      challenges.clear();
    }
    List<Challenge> list = challenges.computeIfAbsent(id, key -> new ArrayList<>(AGREEING));
    list.removeIf(c -> now - c.at > WINDOW);
    list.removeIf(c -> c.seller == seller);
    if (list.size() >= MOST_CHALLENGES) {
      list.remove(0);
    }
    list.add(new Challenge(seller, unit, now));
    int agree = 0;
    for (Challenge c : list) {
      if (c.unit <= unit * TIGHT && c.unit >= unit / TIGHT) {
        agree++;
      }
    }
    if (agree >= AGREEING) {
      challenges.remove(id);
      return true;
    }
    return false;
  }

  public void clear() {
    challenges.clear();
  }
}
