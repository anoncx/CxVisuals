package me.cxiy.cxvisuals.modules.waypoints;

import java.util.Locale;

public final class Icons {
  public static final String NONE = "";
  public static final String HOME = "home";
  public static final String BASE = "base";
  public static final String MINE = "mine";
  public static final String FARM = "farm";
  public static final String VILLAGE = "village";
  public static final String PORTAL = "portal";
  public static final String CHEST = "chest";
  public static final String SWORD = "sword";
  public static final String TREE = "tree";
  public static final String BOAT = "boat";
  public static final String STAR = "star";
  public static final String SKULL = "skull";

  public static final String[] ALL = {
    HOME, BASE, MINE, FARM, VILLAGE, PORTAL, CHEST, SWORD, TREE, BOAT, STAR, SKULL
  };

  private static final String[][] WORDS = {
    {SKULL, "death", "died", "grave", "tod", "смерт"},
    {HOME, "home", "house", "bed", "haus", "heim", "дом", "кроват"},
    {BASE, "base", "fort", "castle", "bunker", "burg", "basis", "база", "замок"},
    {MINE, "mine", "ore", "cave", "diamond", "quarry", "stollen", "höhle", "шахт", "руд", "пещер"},
    {FARM, "farm", "crop", "wheat", "field", "garden", "feld", "acker", "ферм", "огород", "поле"},
    {VILLAGE, "village", "town", "city", "trade", "dorf", "stadt", "деревн", "город", "торг"},
    {PORTAL, "portal", "nether", "stronghold", "end", "портал", "незер"},
    {CHEST, "chest", "storage", "loot", "vault", "stash", "kiste", "lager", "сундук", "склад"},
    {SWORD, "pvp", "arena", "fight", "raid", "boss", "dungeon", "spawner", "kampf", "бой", "арена"},
    {TREE, "tree", "forest", "wood", "jungle", "baum", "wald", "дерев", "лес"},
    {BOAT, "boat", "ship", "dock", "harbor", "harbour", "port", "ocean", "sea", "hafen", "meer", "лодк", "порт", "море"},
    {STAR, "spawn", "fav", "important", "wichtig", "важн"}
  };

  private Icons() {}

  public static boolean known(String icon) {
    for (String id : ALL) {
      if (id.equals(icon)) {
        return true;
      }
    }
    return false;
  }

  public static String suggest(String name) {
    if (name == null || name.isEmpty()) {
      return NONE;
    }
    String lower = name.toLowerCase(Locale.ROOT);
    for (String[] row : WORDS) {
      for (int i = 1; i < row.length; i++) {
        if (lower.contains(row[i])) {
          return row[0];
        }
      }
    }
    return NONE;
  }
}
