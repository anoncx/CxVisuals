package me.cxiy.cxvisuals.modules.shaders;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.PaletteSetting;
import me.cxiy.cxvisuals.core.settings.PanelSetting;
import me.cxiy.cxvisuals.core.settings.PickSetting;
import me.cxiy.cxvisuals.modules.shaders.circles.Contour;
import me.cxiy.cxvisuals.modules.shaders.circles.ContourSetting;
import me.cxiy.cxvisuals.modules.shaders.circles.Ring;
import me.cxiy.cxvisuals.modules.shaders.circles.Spread;
import me.cxiy.cxvisuals.modules.shaders.trails.Style;

public final class ShadersModule extends Module {
  public static final String ID = "shaders";

  private static final float REACH_BLOCKS = 0.30F;
  private static final float FADE_FROM = 0.5F;
  private static final float FULL_NIGHT = 0.4F;
  private static final float LINE_BLOCKS = 0.02F;
  private static final float RADIANS = (float) (Math.PI / 180.0);
  private static final float RING_LINE_BLOCKS = 0.05F;
  private static final float ECHO_DELAY = 0.14F;
  private static final float WALL_BLOCKS = 0.9F;

  public final BoolSetting entities;
  public final EnumSetting<Effect> effect;
  public final PaletteSetting palette;
  public final NumberSetting intensity;
  public final NumberSetting size;
  public final NumberSetting speed;
  public final NumberSetting body;
  public final BoolSetting self;
  public final BoolSetting players;
  public final BoolSetting hand;
  public final PickSetting others;
  public final BoolSetting throughWalls;
  public final NumberSetting range;
  public final BoolSetting sky;
  public final EnumSetting<Sky> scene;
  public final PaletteSetting skyPalette;
  public final NumberSetting skyBrightness;
  public final NumberSetting skySpeed;
  public final NumberSetting skyDrift;
  public final EnumSetting<Sky.Hours> skyHours;
  public final BoolSetting outline;
  public final EnumSetting<Outline> outlineStyle;
  public final PaletteSetting outlinePalette;
  public final NumberSetting outlineWidth;
  public final NumberSetting outlineGlow;
  public final NumberSetting outlineFill;
  public final NumberSetting outlineSpeed;
  public final BoolSetting circles;
  public final PanelSetting circlePreview;
  public final PanelSetting circleShape;
  public final ContourSetting contour;
  public final EnumSetting<Ring> ring;
  public final PaletteSetting ringPalette;
  public final NumberSetting ringSize;
  public final NumberSetting ringWidth;
  public final NumberSetting ringGlow;
  public final NumberSetting ringIntensity;
  public final EnumSetting<Spread> spread;
  public final NumberSetting ringDuration;
  public final NumberSetting ringSpin;
  public final NumberSetting echoes;
  public final BoolSetting sparks;
  public final BoolSetting wall;
  public final NumberSetting wallHeight;
  public final NumberSetting floorGlow;
  public final BoolSetting onJump;
  public final BoolSetting onLand;
  public final BoolSetting ringSelf;
  public final BoolSetting ringPlayers;
  public final BoolSetting ringThroughWalls;
  public final BoolSetting trails;
  public final PanelSetting trailPreview;
  public final EnumSetting<Style> trailStyle;
  public final PaletteSetting trailPalette;
  public final NumberSetting trailLength;
  public final NumberSetting trailSize;
  public final NumberSetting trailGlow;
  public final NumberSetting trailIntensity;
  public final NumberSetting trailSpeed;
  public final NumberSetting trailMinSpeed;
  public final BoolSetting trailSelf;
  public final BoolSetting trailPlayers;
  public final BoolSetting trailMobs;
  public final PickSetting trailOnly;
  public final BoolSetting trailProjectiles;
  public final NumberSetting trailRange;

  public ShadersModule(KeyBinder keys) {
    super(ID, "Shaders", false, keys);
    Page page = section("entities", "Entities");

    Group shading = page.group("Entity shaders");
    entities =
        shading
            .bool("enabled", "Shade entities", true)
            .describe("Animated effects worn by players, avatars, mobs and your hand.");

    Group look = page.group("Look");
    effect =
        look.enumOf(
            "effect",
            "Effect",
            Effect.PLASMA,
            "Plasma",
            "Aura",
            "Frost",
            "Outline",
            "Hologram",
            "Glitch",
            "Signal");
    palette = look.palette("colour", "Colour", 0);
    intensity = look.number("intensity", "Intensity", 100.0, 0.0, 200.0, 5.0, "%");
    size =
        look.number("size", "Size", 100.0, 25.0, 300.0, 5.0, "%")
            .describe("How far the effect reaches out from the body.");
    speed = look.number("speed", "Speed", 100.0, 0.0, 300.0, 10.0, "%");
    body =
        look.number("body", "Body", 100.0, 0.0, 200.0, 5.0, "%")
            .describe("How much the effect covers the body itself.");

    Group targets = page.group("Targets");
    self =
        targets.bool("self", "Yourself", true).describe("In third person and in the idle camera.");
    players = targets.bool("players", "Other players", true);
    hand = targets.bool("hand", "First-person hand", false);
    others = targets.picks("others", "Other entities");

    Group visibility = page.group("Visibility");
    range =
        visibility
            .number("range", "Range", 24.0, 8.0, 96.0, 4.0, "")
            .describe("Blocks. The effect fades out gradually over the far half of this distance.");
    throughWalls =
        visibility
            .bool("through_walls", "Show through walls", false)
            .describe(
                "Draws the effect through blocks. Many servers treat seeing players through walls as cheating.");

    Page above = section("sky", "Sky");

    Group skies = above.group("Sky shaders");
    sky =
        skies
            .bool("enabled", "Shade sky", false)
            .describe("Replaces the sky with an animated scene that stays fixed in space.");

    Group skyLook = above.group("Look");
    scene =
        skyLook.enumOf(
            "scene", "Scene", Sky.GALAXY, "Galaxy", "Planet", "Aurora", "Prism", "Storm", "Sunset");
    skyPalette = skyLook.palette("colour", "Colour", 1);
    skyBrightness = skyLook.number("brightness", "Brightness", 100.0, 25.0, 200.0, 5.0, "%");
    skySpeed = skyLook.number("speed", "Speed", 100.0, 0.0, 300.0, 10.0, "%");
    skyDrift =
        skyLook
            .number("drift", "Drift", 0.5, 0.0, 5.0, 0.25, "°/s")
            .describe("How fast the sky turns overhead.");

    Group skyVisibility = above.group("Visibility");
    skyHours = skyVisibility.enumOf("hours", "Show", Sky.Hours.ALWAYS, "Always", "At night");

    Page target = section("outline", "Block outline");

    Group outlines = target.group("Outline shaders");
    outline =
        outlines
            .bool("enabled", "Shade outline", false)
            .describe("Replaces the box around the block you look at with an animated outline.");

    Group outlineLook = target.group("Look");
    outlineStyle = outlineLook.enumOf("style", "Style", Outline.EDGES, "Edges", "Corners", "Scan");
    outlinePalette = outlineLook.palette("colour", "Colour", 0);
    outlineWidth = outlineLook.number("width", "Width", 100.0, 25.0, 300.0, 5.0, "%");
    outlineGlow = outlineLook.number("glow", "Glow", 100.0, 0.0, 200.0, 5.0, "%");
    outlineFill =
        outlineLook
            .number("fill", "Fill", 10.0, 0.0, 60.0, 5.0, "%")
            .describe("How strongly the faces of the block are tinted.");
    outlineSpeed = outlineLook.number("speed", "Speed", 100.0, 0.0, 300.0, 10.0, "%");

    Page jumps = section("circles", "Jump circles");

    Group rings = jumps.group("Jump circles");
    circles =
        rings
            .bool("enabled", "Draw jump circles", false)
            .describe("A shaped ring of light spreads from your feet when you jump.");

    circlePreview = jumps.group("Preview").panel("preview");
    circleShape = jumps.group("Shape").panel("shape");
    contour =
        settings
            .hidden("circles", "Jump circles")
            .group("Shape")
            .add(new ContourSetting("contour", "Shape"));

    Group ringLook = jumps.group("Look");
    ring = ringLook.enumOf("style", "Style", Ring.HALO, Ring.LABELS);
    ringPalette = ringLook.palette("colour", "Colour", 0);
    ringSize =
        ringLook
            .number("size", "Size", 1.2, 0.4, 4.0, 0.1, "")
            .describe("Radius in blocks when the ring is fully open.");
    ringWidth = ringLook.number("width", "Line width", 100.0, 25.0, 300.0, 5.0, "%");
    ringGlow = ringLook.number("glow", "Glow", 100.0, 0.0, 200.0, 5.0, "%");
    ringIntensity = ringLook.number("intensity", "Intensity", 100.0, 10.0, 200.0, 5.0, "%");

    Group ringMotion = jumps.group("Motion");
    spread = ringMotion.enumOf("motion", "Motion", Spread.EXPAND, "Expand", "Pop", "Implode");
    ringDuration = ringMotion.number("duration", "Duration", 0.9, 0.3, 3.0, 0.1, "s");
    ringSpin = ringMotion.number("spin", "Spin", 30.0, -360.0, 360.0, 15.0, "°/s");
    echoes =
        ringMotion
            .number("echoes", "Echoes", 0.0, 0.0, 3.0, 1.0, "")
            .describe("Fainter rings that follow the first one.");

    Group ringEffects = jumps.group("Effects");
    sparks =
        ringEffects.bool("sparks", "Sparks", true).describe("Bright motes thrown off the rim.");
    wall =
        ringEffects
            .bool("wall", "Light wall", false)
            .describe("A glowing curtain rises from the ring and fades.");
    wallHeight = ringEffects.number("wall_height", "Wall height", 100.0, 25.0, 300.0, 5.0, "%");
    floorGlow =
        ringEffects
            .number("floor", "Floor glow", 30.0, 0.0, 100.0, 5.0, "%")
            .describe("Lights the ground inside the ring.");

    Group ringTriggers = jumps.group("Triggers");
    onJump = ringTriggers.bool("jump", "When jumping", true);
    onLand =
        ringTriggers
            .bool("land", "When landing", false)
            .describe("Higher falls make bigger circles.");
    ringSelf = ringTriggers.bool("self", "Yourself", true);
    ringPlayers = ringTriggers.bool("players", "Other players", true);

    Group ringVisibility = jumps.group("Visibility");
    ringThroughWalls =
        ringVisibility
            .bool("through_walls", "Show through walls", false)
            .describe(
                "Draws circles through blocks. Many servers treat seeing players through walls as cheating.");

    Page wakes = section("trails", "Entity trails");

    Group wake = wakes.group("Entity trails");
    trails =
        wake.bool("enabled", "Draw entity trails", false)
            .describe(
                "Players, mobs and projectiles leave a trail of light behind them while they move.");

    trailPreview = wakes.group("Preview").panel("preview");

    Group wakeLook = wakes.group("Look");
    trailStyle = wakeLook.enumOf("style", "Style", Style.STREAK, Style.LABELS);
    trailPalette = wakeLook.palette("colour", "Colour", 0);
    trailLength =
        wakeLook
            .number("length", "Length", 1.2, 0.4, 3.0, 0.1, "s")
            .describe("How long a trail takes to fade away.");
    trailSize = wakeLook.number("size", "Size", 100.0, 25.0, 250.0, 5.0, "%");
    trailGlow = wakeLook.number("glow", "Glow", 100.0, 0.0, 200.0, 5.0, "%");
    trailIntensity = wakeLook.number("intensity", "Intensity", 100.0, 10.0, 200.0, 5.0, "%");

    Group wakeMotion = wakes.group("Motion");
    trailSpeed =
        wakeMotion
            .number("speed", "Speed", 100.0, 0.0, 300.0, 10.0, "%")
            .describe("How fast the effect animates.");
    trailMinSpeed =
        wakeMotion
            .number("min_speed", "Minimum speed", 3.0, 0.0, 10.0, 0.5, "")
            .describe(
                "Blocks per second an entity has to move before it leaves a trail. Walking is about 4, sprinting about 6.");

    Group wakeTargets = wakes.group("Targets");
    trailSelf =
        wakeTargets
            .bool("self", "Yourself", true)
            .describe("In third person and in the idle camera.");
    trailPlayers = wakeTargets.bool("players", "Other players", true);
    trailMobs = wakeTargets.bool("mobs", "Mobs and animals", false);
    trailOnly =
        wakeTargets
            .picks("only", "Only these mobs")
            .describe("Pick mobs to limit the trails to them. Leave empty to include every mob.");
    trailProjectiles =
        wakeTargets
            .bool("projectiles", "Projectiles", false)
            .describe("Arrows, tridents, snowballs and other flying things.");

    Group wakeVisibility = wakes.group("Visibility");
    trailRange =
        wakeVisibility
            .number("range", "Range", 32.0, 8.0, 96.0, 4.0, "")
            .describe("Blocks. Trails fade out gradually over the far half of this distance.");
  }

  public boolean entitiesLive() {
    return enabled.get() && entities.get();
  }

  public float reachBlocks() {
    return (float) (size.get() / 100.0) * REACH_BLOCKS;
  }

  public float fadeEnd() {
    return range.get().floatValue();
  }

  public float fadeStart() {
    return fadeEnd() * FADE_FROM;
  }

  public float intensityScale() {
    return (float) (intensity.get() / 100.0);
  }

  public float speedScale() {
    return (float) (speed.get() / 100.0);
  }

  public float bodyScale() {
    return (float) (body.get() / 100.0);
  }

  public boolean skyLive() {
    return enabled.get() && sky.get();
  }

  public float skyBrightnessScale() {
    return (float) (skyBrightness.get() / 100.0);
  }

  public float skySpeedScale() {
    return (float) (skySpeed.get() / 100.0);
  }

  public float skyDriftRate() {
    return skyDrift.get().floatValue() * RADIANS;
  }

  public float skyShown(float starBrightness) {
    if (skyHours.get() == Sky.Hours.ALWAYS) {
      return 1.0F;
    }
    float night = Math.clamp(starBrightness / FULL_NIGHT, 0.0F, 1.0F);
    return night * night * (3.0F - 2.0F * night);
  }

  public boolean outlineLive() {
    return enabled.get() && outline.get();
  }

  public float outlineWidthBlocks() {
    return (float) (outlineWidth.get() / 100.0) * LINE_BLOCKS;
  }

  public float outlineGlowScale() {
    return (float) (outlineGlow.get() / 100.0);
  }

  public float outlineFillShare() {
    return (float) (outlineFill.get() / 100.0);
  }

  public float outlineSpeedScale() {
    return (float) (outlineSpeed.get() / 100.0);
  }

  public boolean circlesLive() {
    return enabled.get()
        && circles.get()
        && (onJump.get() || onLand.get())
        && (ringSelf.get() || ringPlayers.get());
  }

  public float ringRadius() {
    return ringSize.get().floatValue();
  }

  public float ringLineBlocks() {
    return (float) (ringWidth.get() / 100.0) * RING_LINE_BLOCKS;
  }

  public float ringGlowScale() {
    return (float) (ringGlow.get() / 100.0);
  }

  public float ringIntensityScale() {
    return (float) (ringIntensity.get() / 100.0);
  }

  public float ringSeconds() {
    return ringDuration.get().floatValue();
  }

  public float ringSpinRate() {
    return ringSpin.get().floatValue() * RADIANS;
  }

  public int echoCount() {
    return echoes.get().intValue();
  }

  public float echoDelay() {
    return ringSeconds() * ECHO_DELAY;
  }

  public float ringLife() {
    return ringSeconds() + echoCount() * echoDelay();
  }

  public float wallBlocks() {
    return wall.get() ? (float) (wallHeight.get() / 100.0) * WALL_BLOCKS : 0.0F;
  }

  public float floorGlowShare() {
    return (float) (floorGlow.get() / 100.0);
  }

  public boolean trailsLive() {
    return enabled.get()
        && trails.get()
        && (trailSelf.get() || trailPlayers.get() || trailMobs.get() || trailProjectiles.get());
  }

  public float trailSeconds() {
    return trailLength.get().floatValue();
  }

  public float trailSizeScale() {
    return (float) (trailSize.get() / 100.0);
  }

  public float trailGlowScale() {
    return (float) (trailGlow.get() / 100.0);
  }

  public float trailIntensityScale() {
    return (float) (trailIntensity.get() / 100.0);
  }

  public float trailSpeedScale() {
    return (float) (trailSpeed.get() / 100.0);
  }

  public float trailMinimum() {
    return trailMinSpeed.get().floatValue();
  }

  public float trailReach() {
    return trailRange.get().floatValue();
  }

  public Contour ringContour() {
    return contour.get();
  }

  @Override
  public String group() {
    return "World";
  }
}
