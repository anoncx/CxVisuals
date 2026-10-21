package me.cxiy.cxvisuals.modules.inventory;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.TextSetting;

public final class InventoryModule extends Module {
  public enum Side {
    RIGHT,
    LEFT
  }

  public enum Repo {
    AUTO,
    ALWAYS,
    OFF
  }

  public enum Preview {
    OFF,
    HOLD,
    ALWAYS
  }

  public enum MapPlace {
    CURSOR,
    SIDE,
    TOOLTIP,
    TEXTURE
  }

  public enum Place {
    CURSOR,
    SIDE,
    TOOLTIP
  }

  public static final String ID = "inventory";
  public static final String PREVIEW_KEY = "inventory.preview";
  private static final int KEY_LEFT_ALT = 342;
  public static final String RECIPE_KEY = "inventory.recipe";
  public static final String USAGE_KEY = "inventory.usage";
  public static final String FAVORITE_KEY = "inventory.favorite";
  public static final String HIDE_KEY = "inventory.hide";
  public static final String SEARCH_KEY = "inventory.search";
  private static final int KEY_R = 82;
  private static final int KEY_U = 85;
  private static final int KEY_A = 65;
  private static final int KEY_O = 79;
  private static final int KEY_F = 70;

  public final BoolSetting itemList;
  public final EnumSetting<Side> side;
  public final BoolSetting storage;
  public final BoolSetting autoHide;
  public final NumberSetting columns;
  public final NumberSetting rows;
  public final BoolSetting cheat;
  public final BoolSetting fill;
  public final NumberSetting opacity;
  public final BoolSetting hints;
  public final EnumSetting<Repo> skyblock;
  public final NumberSetting refresh;
  public final EnumSetting<Preview> preview;
  public final EnumSetting<Place> place;
  public final EnumSetting<Preview> mapPreview;
  public final EnumSetting<MapPlace> mapPlace;
  public final BoolSetting tint;
  public final BoolSetting compact;
  public final NumberSetting previewScale;
  public final BoolSetting visible;
  public final TextSetting favorites;

  public InventoryModule(KeyBinder keys) {
    super(ID, "Inventory", true, keys);
    Page explorer = section("explorer", "Explorer");

    itemList =
        explorer
            .group("Item list")
            .bool("enabled", "Show item list", true)
            .describe("The searchable list of every item beside your inventory and containers.");

    Group layout = explorer.group("Layout").showWhen(this::listing);
    side =
        layout
            .enumOf("side", "Side", Side.RIGHT, "Right", "Left")
            .describe("Which side of the container the item list sits on.");
    columns =
        layout
            .number("columns", "Columns", 7.0, 3.0, 14.0, 1.0, "")
            .describe("How many items fit across the list.");
    rows =
        layout
            .number("rows", "Rows", 8.0, 3.0, 16.0, 1.0, "")
            .describe("How many items fit down the list.");
    autoHide =
        layout
            .bool("auto_hide", "Auto hide", true)
            .describe("Slides the list off the screen edge until the mouse comes near.");
    storage =
        layout
            .bool("storage", "Storage menus", true)
            .describe("Also shows the list next to chests, shops and server menus.");
    opacity =
        layout
            .number("opacity", "Panel opacity", 100.0, 40.0, 100.0, 5.0, "%")
            .describe("How solid the item list panel is.");
    hints =
        layout
            .bool("hints", "Key hints", true)
            .describe("Shows the lookup keys under the hovered item.");

    Group actions = explorer.group("Actions").showWhen(this::listing);
    cheat =
        actions
            .bool("cheat", "Give items", false)
            .describe("Clicking an item in the list gives it to you when you have permission.");
    fill =
        actions
            .bool("fill", "Fill recipes", true)
            .describe("The plus button on a crafting recipe moves the ingredients into the grid.");

    Group controls = explorer.group("Keys").showWhen(this::listing);
    controls.key("recipe_key", "Show recipes", keys.bind(RECIPE_KEY, "Show recipes", KEY_R));
    controls.key("usage_key", "Show uses", keys.bind(USAGE_KEY, "Show uses", KEY_U));
    controls.key(
        "favorite_key", "Favourite item", keys.bind(FAVORITE_KEY, "Favourite item", KEY_A));
    controls.key("hide_key", "Hide list", keys.bind(HIDE_KEY, "Hide list", KEY_O));
    controls.key("search_key", "Search", keys.bind(SEARCH_KEY, "Search", KEY_F));

    Page contents = section("preview", "Container preview");
    Group peek = contents.group("Preview");
    preview =
        peek.enumOf("mode", "Preview", Preview.ALWAYS, "Off", "Hold key", "Always")
            .describe("Shows what is inside a shulker box, bundle or ender chest you point at.");
    place =
        peek.enumOf("place", "Shown", Place.CURSOR, "At cursor", "On the side", "In tooltip")
            .describe(
                "On the side lists every box in the screen and lets you click one. In tooltip puts the contents inside the item tooltip.");
    tint =
        peek.bool("tint", "Match box colour", true)
            .describe("Tints the preview to the colour of the shulker box.");
    previewScale =
        peek.number("preview_scale", "Preview size", 100.0, 40.0, 200.0, 5.0, "%")
            .describe("How big the preview cards are.");
    compact =
        peek.bool("compact", "Compact", false)
            .describe(
                "Merges identical items and identical boxes into one entry with a combined count.");
    peek.key("preview_key", "Preview key", keys.bind(PREVIEW_KEY, "Preview key", KEY_LEFT_ALT));

    Page mapsPage = section("maps", "Map preview");
    Group mapPeek = mapsPage.group("Preview");
    mapPreview =
        mapPeek
            .enumOf("mode", "Preview", Preview.OFF, "Off", "Hold key", "Always")
            .describe(
                "Shows the art of filled maps without holding them. Hold key uses the preview key.");
    mapPlace =
        mapPeek
            .enumOf(
                "place",
                "Shown",
                MapPlace.CURSOR,
                "At cursor",
                "On the side",
                "In tooltip",
                "Item texture")
            .describe(
                "At cursor shows a card beside the pointer. On the side lists every map in the screen. In tooltip puts the art inside the item tooltip. Item texture swaps the map item itself for its art.");

    Page server = section("skyblock", "SkyBlock");
    Group data = server.group("Item data");
    skyblock =
        data.enumOf("mode", "SkyBlock items", Repo.AUTO, "Auto", "Always", "Off")
            .describe("Adds SkyBlock items and recipes. Auto turns on while connected to Hypixel.");
    refresh =
        data.number("refresh", "Refresh", 24.0, 1.0, 168.0, 1.0, " h")
            .describe("How often the item data is downloaded again.");

    Group saved = server.group("Saved").showWhen(() -> false);
    visible = saved.bool("visible", "List visible", true);
    favorites = saved.text("favorites", "Favourites", "");
  }

  public boolean listing() {
    return itemList.get();
  }

  public boolean wantsSkyBlock(boolean onHypixel) {
    Repo mode = skyblock.get();
    return mode == Repo.ALWAYS || (mode == Repo.AUTO && onHypixel);
  }
}
