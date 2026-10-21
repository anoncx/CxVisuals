package me.cxiy.cxvisuals.modules.environment.particles;

public final class Moods {
  private static final Scene CRIMSON = new Scene(Kind.EMBERS, Tint.CRIMSON);
  private static final Scene WARPED = new Scene(Kind.MOTES, Tint.WARPED);
  private static final Scene ASH = new Scene(Kind.SNOW, Tint.ASH);
  private static final Scene VOID = new Scene(Kind.MOTES, Tint.VOID);
  private static final Scene SPORES = new Scene(Kind.MOTES, Tint.SPORE);
  private static final Scene PALE = new Scene(Kind.PETALS, Tint.PALE);
  private static final Scene SOUL_WISPS = new Scene(Kind.WISPS, Tint.SOUL);
  private static final Scene GHOSTS = new Scene(Kind.WISPS, Tint.GHOST);
  private static final Scene SCULK = new Scene(Kind.WISPS, Tint.SCULK);
  private static final float UNDERGROUND = 0.25F;
  private static final float WET = 0.3F;
  private static final float DARK = 0.5F;

  private Moods() {}

  public static Scene pick(Climate climate) {
    return switch (climate.realm) {
      case NETHER -> nether(climate.biome);
      case END -> VOID;
      case OVERWORLD -> overworld(climate);
    };
  }

  private static Scene nether(String biome) {
    return switch (biome) {
      case "crimson_forest" -> CRIMSON;
      case "warped_forest" -> WARPED;
      case "soul_sand_valley" -> SOUL_WISPS;
      case "basalt_deltas" -> ASH;
      default -> Scene.of(Kind.EMBERS);
    };
  }

  private static Scene overworld(Climate climate) {
    if ("lush_caves".equals(climate.biome)) {
      return SPORES;
    }
    if ("deep_dark".equals(climate.biome)) {
      return SCULK;
    }
    if (climate.open < UNDERGROUND) {
      return null;
    }
    if (climate.cold) {
      return Scene.of(Kind.SNOW);
    }
    if (climate.rain > WET) {
      return null;
    }
    boolean night = climate.night > DARK;
    return switch (climate.biome) {
      case "cherry_grove" -> Scene.of(Kind.PETALS);
      case "pale_garden" -> night ? GHOSTS : PALE;
      case "dark_forest" -> night ? GHOSTS : Scene.of(Kind.MOTES);
      case "meadow", "flower_forest", "sunflower_plains", "plains" ->
          night ? Scene.of(Kind.FIREFLIES) : Scene.of(Kind.BUTTERFLIES);
      case "beach", "mushroom_fields" -> night ? Scene.of(Kind.FIREFLIES) : Scene.of(Kind.BUBBLES);
      default -> night ? Scene.of(Kind.FIREFLIES) : Scene.of(Kind.MOTES);
    };
  }
}
