package me.cxiy.cxvisuals.modules.environment;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.modules.environment.particles.Kind;

public final class EnvironmentModule extends Module {
  public enum Placement {
    WORDS,
    LINE,
    FLOAT
  }

  public enum Arrive {
    CONDENSE,
    DISSOLVE,
    FADE
  }

  public enum Leave {
    EVAPORATE,
    DISSOLVE,
    FADE
  }

  public enum Air {
    AUTO(null),
    FIREFLIES(Kind.FIREFLIES),
    PETALS(Kind.PETALS),
    SNOW(Kind.SNOW),
    EMBERS(Kind.EMBERS),
    MOTES(Kind.MOTES),
    BUTTERFLIES(Kind.BUTTERFLIES),
    BUBBLES(Kind.BUBBLES),
    WISPS(Kind.WISPS);

    public final Kind kind;

    Air(Kind kind) {
      this.kind = kind;
    }
  }

  public enum Wet {
    OFF,
    RAIN,
    ALWAYS
  }

  public static final String ID = "environment";

  public final BoolSetting lyrics;
  public final EnumSetting<Placement> placement;
  public final NumberSetting distance;
  public final NumberSetting size;
  public final NumberSetting opacity;
  public final BoolSetting occlude;
  public final NumberSetting offset;
  public final EnumSetting<Arrive> arrive;
  public final EnumSetting<Leave> leave;
  public final BoolSetting calm;
  public final NumberSetting kept;
  public final NumberSetting hold;
  public final BoolSetting apart;
  public final BoolSetting particles;
  public final EnumSetting<Air> air;
  public final NumberSetting density;
  public final NumberSetting particleSize;
  public final NumberSetting wind;
  public final NumberSetting glow;
  public final BoolSetting meteors;
  public final NumberSetting meteorRate;
  public final BoolSetting lanterns;
  public final NumberSetting lanternCount;
  public final BoolSetting fireworks;
  public final NumberSetting fireworkRate;
  public final BoolSetting rain;
  public final NumberSetting rainDensity;
  public final NumberSetting rainStreak;
  public final EnumSetting<Wet> ripples;
  public final NumberSetting rippleDensity;
  public final NumberSetting rippleSize;
  public final NumberSetting rippleSheen;

  public EnvironmentModule(KeyBinder keys) {
    super(ID, "Environment", false, keys);
    Page page = section("lyrics", "Lyrics");

    Group text = page.group("Lyrics");
    lyrics =
        text.bool("lyrics", "Lyrics in the world", true)
            .describe("Shows the words of the playing song in the air in front of you.");
    placement =
        text.enumOf("placement", "Placement", Placement.WORDS, "Words", "Line", "Float")
            .describe(
                "Words scatters each sung word. Line pins the whole line in the world. Float keeps the line ahead of your view.");
    distance =
        text.number("distance", "Distance", 5.0, 2.0, 12.0, 0.5, "")
            .describe("Blocks between you and the text.");
    size = text.number("size", "Size", 100.0, 40.0, 300.0, 10.0, "%");
    opacity = text.number("opacity", "Opacity", 100.0, 20.0, 100.0, 5.0, "%");
    occlude =
        text.bool("occlude", "Hide behind blocks", true)
            .describe("Off draws the text through walls.");
    offset =
        text.number("offset", "Sync offset", 0.0, -3000.0, 3000.0, 50.0, "ms")
            .describe("Moves the lyrics earlier or later when they do not match the music.");

    Group effects = page.group("Effects");
    arrive = effects.enumOf("arrive", "Arrive", Arrive.CONDENSE, "Condense", "Dissolve", "Fade");
    leave = effects.enumOf("leave", "Leave", Leave.EVAPORATE, "Evaporate", "Dissolve", "Fade");
    calm =
        effects
            .bool("calm", "Calm", false)
            .describe("Plain fades only, no drifting, tilting or pulsing.");

    Group words = page.group("Words");
    kept =
        words
            .number("kept", "Words kept", 3.0, 1.0, 6.0, 1.0, "")
            .describe("How many words stay in the air at once in Words placement.");
    hold =
        words
            .number("hold", "Hold", 1.6, 0.5, 6.0, 0.1, "x")
            .context("#environment.hold")
            .describe("How long a word stays, as a multiple of the time it is sung.");
    apart =
        words
            .bool("apart", "Keep words apart", true)
            .describe(
                "A new word takes a free spot instead of covering the ones still in the air.");

    Page around = section("particles", "Particles");

    Group drifting = around.group("Particles");
    particles =
        drifting
            .bool("particles", "Particles in the air", true)
            .describe(
                "Soft particles drift around you, shaped by the place, the weather and the time of day.");
    air =
        drifting
            .enumOf(
                "kind",
                "Kind",
                Air.AUTO,
                "Auto",
                "Fireflies",
                "Petals",
                "Snow",
                "Embers",
                "Motes",
                "Butterflies",
                "Bubbles",
                "Wisps")
            .describe(
                "Auto picks fireflies at night, butterflies in meadows, petals in cherry groves, bubbles by the sea, wisps in dark forests, snow in the cold and embers in the Nether.");

    Group look = around.group("Look");
    density = look.number("density", "Density", 100.0, 10.0, 300.0, 10.0, "%");
    particleSize = look.number("size", "Size", 100.0, 50.0, 200.0, 10.0, "%");
    wind =
        look.number("wind", "Wind", 100.0, 0.0, 300.0, 10.0, "%")
            .describe("How strongly gusts carry the particles.");
    glow =
        look.number("glow", "Glow", 100.0, 0.0, 200.0, 10.0, "%")
            .describe("Brightness of fireflies, embers, motes and sky lights.");

    Group sky = around.group("Sky");
    meteors =
        sky.bool("meteors", "Shooting stars", true)
            .describe("Meteors streak across clear night skies, now and then in showers.");
    meteorRate = sky.number("meteor_rate", "Shooting star rate", 100.0, 25.0, 400.0, 25.0, "%");
    lanterns =
        sky.bool("lanterns", "Sky lanterns", true)
            .describe("Paper lanterns rise into the night sky in the distance.");
    lanternCount = sky.number("lantern_count", "Lanterns", 12.0, 2.0, 40.0, 1.0, "");
    fireworks =
        sky.bool("fireworks", "Fireworks", false)
            .describe("Now and then a show of fireworks bursts over the horizon at night.");
    fireworkRate = sky.number("firework_rate", "Firework rate", 100.0, 25.0, 400.0, 25.0, "%");

    Group downpour = around.group("Rain");
    rain =
        downpour
            .bool("rain", "Custom rain", true)
            .describe(
                "Replaces the vanilla rain with fine streaks that slant with the wind and stop under roofs. Snow stays as it is.");
    rainDensity = downpour.number("rain_density", "Rain density", 100.0, 10.0, 300.0, 10.0, "%");
    rainStreak =
        downpour
            .number("rain_streak", "Streak length", 100.0, 50.0, 250.0, 10.0, "%")
            .describe("How long each drop is smeared, like a camera shutter.");

    Group wet = around.group("Wet ground");
    ripples =
        wet.enumOf("ripples", "Rain ripples", Wet.RAIN, "Off", "Rain", "Always")
            .describe(
                "Rings spread across the ground and water where raindrops land. Rain shows them only while it rains, Always shows them everywhere.");
    rippleDensity = wet.number("ripple_density", "Ripple density", 100.0, 10.0, 300.0, 10.0, "%");
    rippleSize = wet.number("ripple_size", "Ripple size", 100.0, 50.0, 250.0, 10.0, "%");
    rippleSheen =
        wet.number("ripple_sheen", "Ripple sheen", 100.0, 20.0, 200.0, 10.0, "%")
            .describe("How bright the rings catch the sky.");
  }

  @Override
  public String group() {
    return "World";
  }
}
