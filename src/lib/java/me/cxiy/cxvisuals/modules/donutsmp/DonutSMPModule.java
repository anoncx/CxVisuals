package me.cxiy.cxvisuals.modules.donutsmp;

import me.cxiy.cxvisuals.core.module.KeyBinder;
import me.cxiy.cxvisuals.core.module.Module;
import me.cxiy.cxvisuals.core.settings.ActionSetting;
import me.cxiy.cxvisuals.core.settings.BoolSetting;
import me.cxiy.cxvisuals.core.settings.EnumSetting;
import me.cxiy.cxvisuals.core.settings.Group;
import me.cxiy.cxvisuals.core.settings.InfoSetting;
import me.cxiy.cxvisuals.core.settings.NumberSetting;
import me.cxiy.cxvisuals.core.settings.Page;
import me.cxiy.cxvisuals.core.settings.TextSetting;
import me.cxiy.cxvisuals.modules.donutsmp.auction.Host;

public final class DonutSMPModule extends Module {
  public enum Show {
    OFF,
    HOLD,
    ALWAYS
  }

  public enum Side {
    TOP,
    BOTTOM
  }

  public enum Slots {
    OFF,
    CONTAINER,
    ALL
  }

  public enum Window {
    HOUR(3600L),
    SIX_HOURS(6L * 3600L),
    DAY(24L * 3600L),
    WEEK(7L * 24L * 3600L);

    public final long seconds;

    Window(long seconds) {
      this.seconds = seconds;
    }
  }

  public static final String ID = "donutsmp";
  public static final String PREVIEW_KEY = "donutsmp.preview";
  public static final String WATCH_KEY = "donutsmp.watch";
  public static final String TRENDS_KEY = "donutsmp.trends";

  public final BoolSetting community;
  public final TextSetting host;
  public final InfoSetting pageStatus;
  public final InfoSetting syncStatus;
  public final BoolSetting history;
  public final EnumSetting<Window> window;
  public final BoolSetting tooltips;
  public final BoolSetting arrows;
  public final EnumSetting<Show> container;
  public final EnumSetting<Side> side;
  public final NumberSetting previewScale;
  public final NumberSetting minimum;
  public final BoolSetting inventory;
  public final EnumSetting<Slots> slots;
  public final NumberSetting slotFloor;
  public final BoolSetting follow;
  public final BoolSetting alerts;
  public final NumberSetting alertAt;
  public final Watchlist watchlist;
  public final BoolSetting guard;
  public final NumberSetting guardPercent;
  public final NumberSetting guardLimit;
  public final BoolSetting deals;
  public final NumberSetting dealPercent;
  public final BoolSetting unitPrices;
  public final BoolSetting sniper;
  public final NumberSetting snipePercent;
  public final NumberSetting snipeProfit;
  public final BoolSetting snipeSound;
  public final BoolSetting dealList;
  public final BoolSetting learn;

  private volatile boolean present = true;

  public DonutSMPModule(
      KeyBinder keys,
      Runnable openTrends,
      Runnable resetSession,
      Runnable clearHistory,
      Runnable clearMarket) {
    super(ID, "DonutSMP", true, keys);

    Page auction = section("auction", "Auction house");
    Group source = auction.group("Source");
    community =
        source
            .bool("community_prices", "Community prices", true)
            .formerly("donutsmp.prices.community_prices")
            .describe(
                "Shares the prices you see with other CxVisuals players and uses theirs, so items you have not looked up still have a price. Needs a CxVisuals account.");
    host =
        source
            .text("server_address", "Server address", Host.NAME)
            .formerly("donutsmp.prices.server_address")
            .describe(
                "Prices are only recorded and shared while you are connected to this address. Add more names with commas.");
    syncStatus = source.info("sync_status", "Community", "Waiting");

    Group learning = auction.group("Market");
    learn =
        learning
            .bool("learn_enabled", "Learn from browsing", true)
            .describe(
                "Remembers the listings you scroll past so prices come from the real market.");
    pageStatus =
        learning.info(
            "page_status", "Last auction page", "Open the auction house to record prices");
    learning.action(
        "clear_market", "Learned prices", "Clear", ActionSetting.Kind.DANGER, clearMarket);

    Group protect = auction.group("Guard");
    guard =
        protect
            .bool("guard_enabled", "Block overpriced listings", true)
            .describe(
                "Stops a click on a listing priced far above the market. Hold Shift to buy it anyway.");
    guardPercent =
        protect
            .number("guard_percent", "Block above market", 60.0, 10.0, 500.0, 10.0, "%")
            .describe("How far over the market price a listing may be before it is blocked.");
    guardLimit =
        protect
            .number("guard_limit", "Block above price", 0.0, 0.0, 10000000.0, 50000.0, "$")
            .describe("Blocks any listing that costs more than this in total. Zero turns it off.");

    Group bargains = auction.group("Deals");
    deals =
        bargains
            .bool("deals_enabled", "Highlight deals", true)
            .describe("Tints listings green when they are cheap and red when they are overpriced.");
    dealPercent =
        bargains
            .number("deal_percent", "Deal from", 15.0, 5.0, 90.0, 5.0, "%")
            .describe("How far under the market price a listing must be to count as a deal.");
    unitPrices =
        bargains
            .bool("unit_prices", "Price per item", true)
            .describe("Adds the price of one item and the gap to the market to listing tooltips.");

    Group snipe = auction.group("Sniper");
    sniper =
        snipe
            .bool("sniper_enabled", "Deal alerts", true)
            .describe(
                "Pings you and marks the best deal the moment one shows up on the page you are viewing.");
    snipePercent =
        snipe
            .number("snipe_percent", "Alert from", 30.0, 10.0, 95.0, 5.0, "%")
            .describe("How far under the market price a listing must be to raise an alert.");
    snipeProfit =
        snipe
            .number("snipe_profit", "Least profit", 5000.0, 0.0, 1000000.0, 1000.0, "$")
            .describe("Ignores deals that would earn less than this.");
    snipeSound = snipe.bool("snipe_sound", "Sound", true);
    dealList =
        snipe
            .bool("deal_list", "Deal list", true)
            .describe("Lists the best deals on the page beside the auction house.");

    Page prices = section("prices", "Prices");
    Group tips = prices.group("Tooltips");
    tooltips =
        tips.bool("tooltip_enabled", "Item prices", true)
            .describe("Adds the price of an item to its tooltip.");
    arrows =
        tips.bool("tooltip_trend", "Trend arrows", true)
            .describe("Shows how each price moved over the trend window.");
    Group tracking = prices.group("History");
    history =
        tracking
            .bool("history_enabled", "Record history", true)
            .formerly("donutsmp.trends.history_enabled")
            .describe(
                "Keeps a week of price changes on disk so charts and trend arrows have something to show.");
    window =
        tracking
            .enumOf(
                "trend_window",
                "Trend window",
                Window.DAY,
                "1 hour",
                "6 hours",
                "24 hours",
                "7 days")
            .formerly("donutsmp.trends.trend_window")
            .describe("The span trend arrows and the first chart use.");
    tracking.action(
        "clear_history", "Stored prices", "Clear", ActionSetting.Kind.DANGER, clearHistory);
    Group watching = prices.group("Watchlist");
    watchlist =
        new Watchlist(
            settings.hidden("watchlist", "Watchlist").group("Items").text("items", "Items", ""));
    watching
        .key(
            "watch_key", "Watch key", keys.bind(WATCH_KEY, "Watch hovered item", KeyBinder.UNBOUND))
        .formerly("donutsmp.trends.watch_key");
    watching.action(
        "clear_watchlist", "Watched items", "Clear", ActionSetting.Kind.DANGER, watchlist::clear);
    Group alert = prices.group("Alerts");
    alerts =
        alert
            .bool("alerts_enabled", "Price alerts", true)
            .formerly("donutsmp.trends.alerts_enabled")
            .describe(
                "Pops a notification when an item you carry or watch moves sharply within the hour.");
    alertAt =
        alert
            .number("alert_at", "Alert at", 15.0, 5.0, 100.0, 5.0, "%")
            .formerly("donutsmp.trends.alert_at")
            .describe("The change that counts as a spike.");
    Group screen = prices.group("Screen");
    screen
        .key(
            "trends_key", "Open key", keys.bind(TRENDS_KEY, "Open price trends", KeyBinder.UNBOUND))
        .formerly("donutsmp.trends.trends_key");
    screen.action("open_trends", "Price trends", "Open", ActionSetting.Kind.QUIET, openTrends);

    Page value = section("container", "Containers");
    Group card = value.group("Preview");
    container =
        card.enumOf("mode", "Preview", Show.ALWAYS, "Off", "Hold key", "Always")
            .describe("Shows what the open container is worth. Hold key uses the preview key.");
    side = card.enumOf("side", "Position", Side.TOP, "Top", "Bottom");
    previewScale =
        card.number("preview_scale", "Preview size", 100.0, 40.0, 200.0, 5.0, "%")
            .describe("How big the preview card is.");
    follow =
        card.bool("follow_hover", "Follow hovered stack", true)
            .describe("Marks the hovered stack in the card and shows what it is worth.");
    minimum =
        card.number("minimum_value", "Hide items under", 0.0, 0.0, 100000.0, 500.0, "$")
            .describe("Leaves out stacks worth less than this so only what matters is listed.");
    inventory =
        card.bool("include_inventory", "Count my inventory", false)
            .describe("Adds the items in your own inventory to the container value.");
    card.key(
        "preview_key",
        "Preview key",
        keys.bind(PREVIEW_KEY, "Container value key", KeyBinder.UNBOUND));

    Group tint = value.group("Slots");
    slots =
        tint.enumOf("slot_mode", "Highlight", Slots.CONTAINER, "Off", "Container", "All")
            .describe(
                "Tints slots by what their stack is worth, green for modest and pink for rich.");
    slotFloor =
        tint.number("slot_floor", "Highlight from", 1000.0, 100.0, 100000.0, 100.0, "$")
            .describe("The stack value where the tint starts.");

    Group collected = value.group("Collected");
    collected.action(
        "reset_session", "Collected value", "Reset", ActionSetting.Kind.DANGER, resetSession);
    listedWhen(() -> present);
  }

  public boolean present() {
    return present;
  }

  public void present(boolean onServer) {
    present = onServer;
  }

  @Override
  public boolean live() {
    return present && super.live();
  }

  @Override
  public String group() {
    return "Inventory";
  }
}
