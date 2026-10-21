package me.cxiy.cxvisuals.modules.animations;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;

public final class AnimationsModule extends Module {
  public enum HotbarStyle {
    GLIDE,
    SNAP
  }

  public enum TeleportStyle {
    GLIDE,
    FADE,
    OFF
  }

  public enum OpenStyle {
    POP,
    SLIDE,
    OFF
  }

  public enum Source {
    SELF,
    EVERYONE
  }

  public static final String ID = "animations";

  public final NumberSetting speed;
  public final EnumSetting<HotbarStyle> selector;
  public final BoolSetting selectBounce;
  public final BoolSetting pickup;
  public final EnumSetting<TeleportStyle> teleport;
  public final NumberSetting teleportTime;
  public final NumberSetting teleportMin;
  public final NumberSetting teleportMax;
  public final BoolSetting moves;
  public final BoolSetting cursor;
  public final EnumSetting<OpenStyle> screenOpen;
  public final BoolSetting bars;
  public final BoolSetting ghost;
  public final BoolSetting numbers;
  public final BoolSetting chat;
  public final BoolSetting tooltips;
  public final BoolSetting lists;
  public final EnumSetting<Source> blocksSource;
  public final NumberSetting blocksTime;
  public final NumberSetting blocksSpin;
  public final BoolSetting blocksBreaks;

  public AnimationsModule(KeyBinder keys) {
    super(ID, "Animations", false, keys);
    Page hotbar = section("hotbar", "Hotbar");
    Group general = hotbar.group("General");
    speed =
        general
            .number("speed", "Speed", 1.0, 0.5, 2.0, 0.05, "×")
            .describe("Scales every animation.");
    Group bar = hotbar.group("Hotbar");
    selector = bar.enumOf("selector", "Selector", HotbarStyle.GLIDE, "Glide", "Snap");
    selectBounce =
        bar.bool("select_bounce", "Select bounce", true)
            .describe("The item you switch to gives a small bounce.");
    pickup =
        bar.bool("pickup", "Pickup bounce", true).describe("A slot bounces when its stack grows.");

    Page camera = section("camera", "Camera");
    Group jump = camera.group("Teleport");
    teleport =
        jump.enumOf("teleport", "Teleport", TeleportStyle.GLIDE, "Glide", "Fade", "Off")
            .describe("Glide flies the camera to where you were sent.");
    teleportTime = jump.number("teleport_time", "Duration", 0.4, 0.2, 1.0, 0.05, "s");
    teleportMin =
        jump.number("teleport_min", "Minimum distance", 3.0, 1.0, 16.0, 0.5, "m")
            .describe("Shorter corrections stay instant.");
    teleportMax =
        jump.number("teleport_max", "Maximum distance", 64.0, 16.0, 256.0, 8.0, "m")
            .describe("Longer jumps fade instead of flying.");

    Page inventory = section("inventory", "Inventory");
    Group items = inventory.group("Items");
    moves =
        items
            .bool("moves", "Sliding items", true)
            .describe("A stack travels to its new slot instead of jumping.");
    cursor =
        items
            .bool("cursor", "Soft cursor item", true)
            .describe("The held stack trails the cursor slightly.");
    Group screens = inventory.group("Screens");
    screenOpen = screens.enumOf("screen_open", "Opening", OpenStyle.POP, "Pop", "Slide", "Off");

    Page hud = section("hud", "HUD");
    Group status = hud.group("Status");
    bars =
        status
            .bool("bars", "Smooth bars", true)
            .describe("Health, hunger, armor and XP ease to their value.");
    ghost =
        status.bool("ghost", "Damage ghost", true).describe("Lost health lingers for a moment.");
    numbers = status.bool("numbers", "Number pop", true);
    Group text = hud.group("Text");
    chat = text.bool("chat", "Chat slide", true);
    tooltips = text.bool("tooltips", "Tooltip pop", true);
    lists = text.bool("lists", "List pop", true).describe("Player list and scoreboard.");

    Page blocks = section("blocks", "Blocks");
    Group world = blocks.group("World");
    blocksSource =
        world
            .enumOf("blocks_source", "Source", Source.EVERYONE, "Only you", "Everyone")
            .describe("Blocks pop in for everyone or only your own changes.");
    blocksTime =
        world
            .number("blocks_time", "Duration", 0.35, 0.1, 1.0, 0.05, "s")
            .describe("How long a placed or broken block animates.");
    blocksSpin =
        world
            .number("blocks_spin", "Spin", 90.0, 0.0, 360.0, 15.0, "°")
            .describe("Degrees the block rotates through while it settles.");
    blocksBreaks =
        world
            .bool("blocks_breaks", "Breaking", true)
            .describe("Broken blocks spin away instead of vanishing.");
  }

  public float seconds(float base) {
    return base / speed.get().floatValue();
  }

  @Override
  public String group() {
    return "Interface";
  }
}
