package me.cxiy.cxvisuals.modules.idlecamera;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.DoubleSupplier;
import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.ActionSetting;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.modules.idlecamera.camera.DirectorConfig;
import me.cxiy.cxvisuals.modules.idlecamera.camera.MotionParams;
import me.cxiy.cxvisuals.modules.idlecamera.camera.ShotRegistry;
import me.cxiy.cxvisuals.modules.idlecamera.camera.ShotType;
import me.cxiy.cxvisuals.modules.idlecamera.camera.Transition;
import me.cxiy.cxvisuals.modules.idlecamera.idle.CinematicController;

public final class IdleCameraModule extends Module
    implements DirectorConfig, CinematicController.Config {
  public record Hooks(Runnable preview, DoubleSupplier playerFov) {}

  public enum LetterboxRatio {
    R185(1.85),
    R200(2.0),
    R239(2.39);

    public final double value;

    LetterboxRatio(double value) {
      this.value = value;
    }
  }

  public static final String ID = "idle_camera";
  public static final String START_KEY = ID + ".start";
  public static final int START_KEY_DEFAULT = 297;

  public final NumberSetting idleTime;
  public final BoolSetting exitOnDamage;
  public final BoolSetting playInMenu;
  public final NumberSetting speed;
  public final NumberSetting roll;
  public final NumberSetting shotLength;
  public final BoolSetting avoidBlocks;
  public final EnumSetting<Transition> transition;
  public final NumberSetting blendTime;
  public final BoolSetting hideHud;
  public final BoolSetting letterbox;
  public final EnumSetting<LetterboxRatio> letterboxRatio;
  public final BoolSetting fovChanges;
  public final ActionSetting preview;

  private final Map<ShotType, BoolSetting> shotEnabled = new IdentityHashMap<>();
  private final Map<ShotType, NumberSetting> shotWeight = new IdentityHashMap<>();
  private final DoubleSupplier playerFov;
  private MotionParams motion;

  public IdleCameraModule(KeyBinder keys, Hooks hooks) {
    super(ID, "Idle Camera", true, keys);
    this.playerFov = hooks.playerFov();
    preview = action("preview", "Preview", ActionSetting.Kind.QUIET, hooks.preview());

    Page camera = section("camera", "Camera");
    Group trigger = camera.group("Trigger");
    idleTime = trigger.number("idle_time", "Idle time", 60, 10, 600, 5, "s");
    trigger.key(
        "start_key", "Start cinematic", keys.bind(START_KEY, "Start cinematic", START_KEY_DEFAULT));
    exitOnDamage =
        trigger
            .bool("exit_on_damage", "Exit on damage", true)
            .describe("Hand the camera back the moment you get hurt.");
    playInMenu =
        trigger
            .bool("in_menu", "Play in pause menu", false)
            .describe("The menu stays open and is hidden while it plays.");

    Group movement = camera.group("Motion");
    speed = movement.number("speed", "Speed", 1.0, 0.25, 2.0, 0.05, "×");
    roll = movement.number("roll", "Roll", 4.0, 0.0, 12.0, 0.5, "°");
    shotLength = movement.number("shot_length", "Shot length", 10, 5, 30, 1, "s");
    avoidBlocks =
        movement
            .bool("avoid_blocks", "Stay out of blocks", true)
            .describe("Goes around walls instead of through them.");

    Group cuts = camera.group("Transition");
    transition = cuts.enumOf("mode", "Mode", Transition.BLEND, "Blend", "Cut", "Mix");
    blendTime = cuts.number("blend_time", "Blend time", 2.0, 0.5, 5.0, 0.1, "s");

    Page shots = section("shots", "Shots");
    for (ShotType type : ShotRegistry.ALL) {
      Group group = shots.group(type.name());
      shotEnabled.put(type, group.bool(type.id() + "_enabled", "Enabled", true));
      shotWeight.put(type, group.number(type.id() + "_weight", "Weight", 1.0, 0.1, 3.0, 0.1, "×"));
    }

    Page look = section("look", "Look");
    Group presentation = look.group("Presentation");
    hideHud = presentation.bool("hide_hud", "Hide HUD", true);
    letterbox = presentation.bool("letterbox", "Letterbox", true);
    letterboxRatio =
        presentation.enumOf(
            "letterbox_ratio", "Letterbox ratio", LetterboxRatio.R239, "1.85", "2.00", "2.39");
    fovChanges =
        presentation
            .bool("fov_changes", "FOV changes", true)
            .describe("Let each shot pick its own field of view.");

    settings.onAnyChange(() -> motion = null);
  }

  @Override
  public MotionParams motion() {
    double fov = playerFov.getAsDouble();
    MotionParams cached = motion;
    if (cached == null || cached.baseFov() != fov) {
      cached = new MotionParams(speed.get(), roll.get(), fovChanges.get(), fov);
      motion = cached;
    }
    return cached;
  }

  @Override
  public double shotLength() {
    return shotLength.get();
  }

  @Override
  public Transition transition() {
    return transition.get();
  }

  @Override
  public double blendTime() {
    return blendTime.get();
  }

  @Override
  public boolean avoidBlocks() {
    return avoidBlocks.get();
  }

  @Override
  public boolean shotEnabled(ShotType type) {
    BoolSetting setting = shotEnabled.get(type);
    return setting != null && setting.get();
  }

  @Override
  public double shotWeight(ShotType type) {
    NumberSetting setting = shotWeight.get(type);
    return setting == null ? 0.0 : setting.get();
  }

  @Override
  public boolean enabled() {
    return enabled.get();
  }

  @Override
  public double idleSeconds() {
    return idleTime.get();
  }

  @Override
  public boolean exitOnDamage() {
    return exitOnDamage.get();
  }

  @Override
  public boolean playInMenu() {
    return playInMenu.get();
  }

  @Override
  public String group() {
    return "Camera";
  }
}
