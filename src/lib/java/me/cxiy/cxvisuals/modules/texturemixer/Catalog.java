package me.cxiy.cxvisuals.modules.texturemixer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Catalog {
  private static final String STATES = "blockstates/";
  private static final String ITEMS = "textures/item/";
  private static final String JSON = ".json";
  private static final String PNG = ".png";
  private static final Pattern VARIANT =
      Pattern.compile("(.+)_(pulling_\\d|standby|arrow|firework|cast|broken|open|filled)");
  private static final Pattern FRAME = Pattern.compile("(_pulling_\\d|_\\d\\d)$");
  private static final Set<String> HIDDEN =
      Set.of(
          "air",
          "barrier",
          "light",
          "structure_void",
          "void_air",
          "cave_air",
          "moving_piston",
          "piston_head",
          "jigsaw",
          "structure_block",
          "command_block",
          "chain_command_block",
          "repeating_command_block");
  private static final String[] HIDDEN_PARTS = {
    "potted_", "wall_", "_wall_sign", "_wall_hanging", "attached_", "test_"
  };
  private static final String[] SECONDARY = {
    "_slab",
    "_stairs",
    "_wall",
    "_fence",
    "_gate",
    "_button",
    "_pressure_plate",
    "_carpet",
    "_pane",
    "_trapdoor",
    "_door"
  };

  private final List<Entry> blocks;
  private final List<Entry> items;
  private final List<Entry> entities;

  private Catalog(List<Entry> blocks, List<Entry> items, List<Entry> entities) {
    this.blocks = blocks;
    this.items = items;
    this.entities = entities;
  }

  public List<Entry> blocks() {
    return blocks;
  }

  public List<Entry> items() {
    return items;
  }

  public List<Entry> entities() {
    return entities;
  }

  public List<Entry> of(Entry.Kind kind) {
    return switch (kind) {
      case BLOCK -> blocks;
      case ITEM -> items;
      case ENTITY -> entities;
    };
  }

  public static Catalog build(Assets vanilla) {
    Shapes shapes = new Shapes(vanilla);
    TreeSet<String> names = new TreeSet<>();
    TreeSet<String> sprites = new TreeSet<>();
    for (String path : vanilla.paths()) {
      if (path.startsWith(STATES) && path.endsWith(JSON)) {
        names.add(path.substring(STATES.length(), path.length() - JSON.length()));
      } else if (path.startsWith(ITEMS)
          && path.endsWith(PNG)
          && path.indexOf('/', ITEMS.length()) < 0) {
        sprites.add(path.substring(ITEMS.length(), path.length() - PNG.length()));
      }
    }
    Map<String, Integer> bySignature = new HashMap<>();
    List<Entry> blocks = new ArrayList<>(names.size());
    for (String name : names) {
      if (hidden(name)) {
        continue;
      }
      List<Entry.State> states = shapes.states(name);
      if (states.isEmpty() || states.get(0).shape().empty()) {
        continue;
      }
      String signature = String.join("|", states.get(0).shape().textures());
      Entry made = new Entry(name, Entry.Kind.BLOCK, states, 1.0F);
      Integer slot = bySignature.get(signature);
      if (slot == null) {
        bySignature.put(signature, blocks.size());
        blocks.add(made);
      } else if (better(made, blocks.get(slot))) {
        blocks.set(slot, made);
      }
    }
    Map<String, List<Entry.State>> variants = new TreeMap<>();
    for (String name : sprites) {
      Matcher match = VARIANT.matcher(name);
      if (match.matches() && sprites.contains(match.group(1))) {
        variants
            .computeIfAbsent(match.group(1), key -> new ArrayList<>())
            .add(new Entry.State(Entry.pretty(match.group(2)), Shape.sprite("item/" + name)));
      }
    }
    List<Entry> items = new ArrayList<>(sprites.size());
    for (String name : sprites) {
      Matcher match = VARIANT.matcher(name);
      boolean variant = match.matches() && sprites.contains(match.group(1));
      if (variant || FRAME.matcher(name).find()) {
        continue;
      }
      List<Entry.State> states = new ArrayList<>();
      states.add(new Entry.State("", Shape.sprite("item/" + name)));
      states.addAll(variants.getOrDefault(name, List.of()));
      items.add(new Entry(name, Entry.Kind.ITEM, states, 1.0F));
    }
    return new Catalog(
        List.copyOf(blocks), List.copyOf(items), Entities.build(vanilla));
  }

  private static boolean better(Entry candidate, Entry current) {
    boolean a = candidate.shape().cube();
    boolean b = current.shape().cube();
    if (a != b) {
      return a;
    }
    boolean c = secondary(candidate.id());
    boolean d = secondary(current.id());
    if (c != d) {
      return !c;
    }
    return candidate.id().length() < current.id().length();
  }

  private static boolean secondary(String name) {
    for (String suffix : SECONDARY) {
      if (name.endsWith(suffix)) {
        return true;
      }
    }
    return false;
  }

  private static boolean hidden(String name) {
    if (HIDDEN.contains(name)) {
      return true;
    }
    for (String part : HIDDEN_PARTS) {
      if (name.contains(part)) {
        return true;
      }
    }
    return false;
  }
}
