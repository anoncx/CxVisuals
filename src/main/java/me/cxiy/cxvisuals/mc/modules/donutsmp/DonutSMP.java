package me.cxiy.cxvisuals.mc.modules.donutsmp;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.gui.PreviewPanel;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.Themes;
import me.cxiy.cxvisuals.gui.TrendChart;
import me.cxiy.cxvisuals.gui.TrendsView;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.mc.gui.TrendsScreen;
import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import me.cxiy.cxvisuals.mc.modules.inventory.Inventory;
import me.cxiy.cxvisuals.mc.modules.inventory.Stacks;
import me.cxiy.cxvisuals.modules.donutsmp.DonutSMPModule;
import me.cxiy.cxvisuals.modules.donutsmp.DonutSMPModule.Show;
import me.cxiy.cxvisuals.modules.donutsmp.DonutSMPModule.Side;
import me.cxiy.cxvisuals.modules.donutsmp.DonutSMPModule.Slots;
import me.cxiy.cxvisuals.modules.donutsmp.Money;
import me.cxiy.cxvisuals.modules.donutsmp.PriceHistory;
import me.cxiy.cxvisuals.modules.donutsmp.Trail;
import me.cxiy.cxvisuals.modules.donutsmp.TrendsHost;
import me.cxiy.cxvisuals.modules.donutsmp.auction.PriceBlend;
import me.cxiy.cxvisuals.modules.donutsmp.auction.PriceBlend.Quote;
import me.cxiy.cxvisuals.modules.friends.Friends;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class DonutSMP {
  public static final String NONE = "--";

  private static final String HISTORY_FILE = "donutsmp-history.bin.gz";
  private static final String MARKET_FILE = "donutsmp-market.bin.gz";
  private static final float CELL = 18.0F;
  private static final float EDGE = 12.0F;
  private static final float MAX_FRAME_SECONDS = 0.1F;
  private static final int CAPACITY = 54;
  private static final int INVENTORY_TICKS = 10;
  private static final long HASH = 31L;
  private static final String TITLE = "Container value";
  private static final float SLOT_FILL = 0.3F;
  private static final float SLOT_RIM = 0.8F;
  private static final double TIER_SPAN = 3.0;
  private static final int TIER_LOW = 0xFF3DDC5A;
  private static final int TIER_MID = 0xFFE9D53A;
  private static final int TIER_HIGH = 0xFFEC4FB8;
  private static final float CONTAINER_WEIGHT = 4.0F;
  private static final float PREFERENCE = 40.0F;
  private static final int OBSTACLES = 16;
  private static final long ALERT_WINDOW = 3600L;
  private static final long ALERT_COOLDOWN = 1800L;
  private static final long STALE_AFTER = 6L * 3600L;
  private static final long OLD_NOTE = 3600L;
  private static final String TILDE = "~";
  private static final int TRAIL = 60;
  private static final int TRAIL_EVERY = 20;
  private static final Trail NO_TRAIL = new Trail(1);
  private static final float OBSTACLE_MARGIN = 6.0F;

  private static DonutSMP self;

  private final DonutSMPModule module;
  private final KeyMapping previewKey;
  private final KeyMapping trendsKey;
  private final KeyMapping watchKey;
  private final ClientSettings client;
  private final Themes themes;
  private final PreviewPanel panel;
  private final McCanvas canvas = new McCanvas();
  private final PriceHistory history = new PriceHistory();
  private final Path historyFile;
  private final Names names = new Names();
  private final Auctions auctions;
  private final Crowd crowd;
  private final double[] cardAuction = new double[CAPACITY];
  private final Map<String, Long> alerted = new HashMap<>();
  private final Trail inventoryTrail = new Trail(TRAIL);
  private final Trail collectedTrail = new Trail(TRAIL);
  private int trailTick;
  private final float[] obstacles = new float[OBSTACLES * 4];
  private final int[] cardCounts = new int[CAPACITY];
  private final int[] cardHandles = new int[CAPACITY];
  private final Map<Item, Integer> carried = new HashMap<>();
  private final Map<Item, Integer> counted = new HashMap<>();

  private int alertSeen = -1;
  private int watchSignature = Integer.MIN_VALUE;
  private int watchedRevision;
  private List<Watched> watched = List.of();
  private long lastFrame;
  private int ticks;
  private long cardSignature;
  private boolean cardReady;
  private int cardShown;
  private String cardNote = "";
  private int inventoryRevision;
  private String inventoryText = NONE;
  private String inventoryNote = "";
  private Player tracked;
  private boolean primed;
  private double collected;
  private int sessionRevision;
  private String sessionText = Money.format(0.0);

  private DonutSMP(
      DonutSMPModule module,
      KeyMapping previewKey,
      KeyMapping trendsKey,
      KeyMapping watchKey,
      ClientSettings client,
      Path historyFile) {
    this.module = module;
    this.previewKey = previewKey;
    this.trendsKey = trendsKey;
    this.watchKey = watchKey;
    this.themes = new Themes(client);
    this.panel = new PreviewPanel(client.motion::get);
    this.historyFile = historyFile;
    this.client = client;
    this.auctions = new Auctions(module, history, names, historyFile.resolveSibling(MARKET_FILE));
    this.crowd = new Crowd(Prices.session(), history);
  }

  public static DonutSMPModule create(McKeyBinder keys, ClientSettings settings) {
    DonutSMPModule module =
        new DonutSMPModule(
            keys,
            DonutSMP::openTrends,
            DonutSMP::resetSession,
            DonutSMP::clearHistory,
            DonutSMP::clearMarket);
    Path file =
        FabricLoader.getInstance()
            .getConfigDir()
            .resolve(CxVisualsClient.MOD_ID)
            .resolve(HISTORY_FILE);
    self =
        new DonutSMP(
            module,
            keys.mapping(DonutSMPModule.PREVIEW_KEY),
            keys.mapping(DonutSMPModule.TRENDS_KEY),
            keys.mapping(DonutSMPModule.WATCH_KEY),
            settings,
            file);
    self.restore();
    Server.allow(module.host::get);
    module.present(false);
    Runtime.getRuntime().addShutdownHook(new Thread(self::persist, "CxVisuals-DonutSMP-History"));
    return module;
  }

  public static void tick(Minecraft mc) {
    if (self == null) {
      return;
    }
    self.module.present(Server.donut(mc));
    if (self.module.live()) {
      self.update(mc);
    }
  }

  public static boolean onServer() {
    return self != null && self.module.present();
  }

  public static void render(
      AbstractContainerScreen<?> screen,
      GuiGraphicsExtractor graphics,
      int mouseX,
      int mouseY,
      int left,
      int top,
      int width,
      int height,
      Slot hovered) {
    if (self != null && self.module.live()) {
      self.draw(screen, graphics, mouseX, mouseY, left, top, width, height, hovered);
    }
  }

  public static boolean keyPressed(KeyEvent event, Slot hovered) {
    if (self == null
        || !self.module.live()
        || hovered == null
        || !hovered.hasItem()
        || !self.watchKey.matches(event)) {
      return false;
    }
    self.names.watch(BuiltInRegistries.ITEM.getKey(hovered.getItem().getItem()).toString());
    return true;
  }

  public static void observe(AbstractContainerScreen<?> screen) {
    if (self != null && self.module.live()) {
      self.auctions.observe(screen);
    }
  }

  public static boolean click(Slot slot, ContainerInput input) {
    return self != null
        && self.module.live()
        && self.auctions.blocks(slot, input == ContainerInput.QUICK_MOVE);
  }

  public static void inject(String id, double unit) {
    if (self != null) {
      self.auctions.demo(id, unit);
    }
  }

  public static void clearMarket() {
    if (self != null) {
      self.auctions.clear();
    }
  }

  public static void slot(
      AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, Slot slot) {
    if (self == null
        || !self.module.live()
        || !slot.hasItem()
        || self.auctions.tint(graphics, slot)) {
      return;
    }
    if (self.module.slots.get() != Slots.OFF) {
      self.tint(screen, graphics, slot);
    }
  }

  public static boolean tooltip(
      AbstractContainerScreen<?> screen,
      GuiGraphicsExtractor graphics,
      int mouseX,
      int mouseY,
      Slot hovered) {
    return self != null
        && self.module.live()
        && hovered != null
        && hovered.hasItem()
        && screen.getMenu().getCarried().isEmpty()
        && self.describe(graphics, hovered.getItem(), mouseX, mouseY);
  }

  private boolean describe(GuiGraphicsExtractor graphics, ItemStack stack, int mouseX, int mouseY) {
    List<Component> lines = new ArrayList<>(6);
    auctions.describe(stack, lines::add);
    if (module.tooltips.get()) {
      addPrices(stack, lines::add);
    }
    if (lines.isEmpty()) {
      return false;
    }
    Minecraft mc = Minecraft.getInstance();
    List<ClientTooltipComponent> parts = new ArrayList<>();
    for (Component line : Screen.getTooltipFromItem(mc, stack)) {
      parts.add(ClientTooltipComponent.create(line.getVisualOrderText()));
    }
    stack
        .getTooltipImage()
        .ifPresent(
            image -> parts.add(Math.min(1, parts.size()), ClientTooltipComponent.create(image)));
    parts.add(new PriceTooltip(lines, themes.get()));
    graphics.tooltip(
        mc.font,
        parts,
        mouseX,
        mouseY,
        DefaultTooltipPositioner.INSTANCE,
        stack.get(DataComponents.TOOLTIP_STYLE));
    return true;
  }

  public static void openTrends() {
    Minecraft mc = Minecraft.getInstance();
    if (self != null) {
      mc.gui.setScreen(
          new TrendsScreen(
              mc.gui.screen(),
              new TrendsView(self.history, self.names, self.module, self.client.motion::get),
              self.themes));
    }
  }

  public static void clearHistory() {
    if (self != null) {
      self.history.reset();
      self.persist();
    }
  }

  public static DonutSMPModule module() {
    return self == null ? null : self.module;
  }

  public static PriceHistory history() {
    return self == null ? null : self.history;
  }

  public static void resetSession() {
    if (self != null) {
      self.primed = false;
      self.collected = 0.0;
      self.publishSession();
    }
  }

  public static int inventoryRevision() {
    return self == null ? 0 : self.inventoryRevision;
  }

  public static String inventoryText() {
    return self == null ? NONE : self.inventoryText;
  }

  public static String inventoryNote() {
    return self == null ? "" : self.inventoryNote;
  }

  public static List<Watched> watched() {
    return self == null ? List.of() : self.watched;
  }

  public static int watchedRevision() {
    return self == null ? 0 : self.watchedRevision;
  }

  public static int kind() {
    return PriceHistory.AUCTION;
  }

  public static long windowSeconds() {
    return self == null ? PriceHistory.RETAIN : self.module.window.get().seconds;
  }

  public static boolean stale() {
    return self != null && self.outdated();
  }

  public static Trail inventoryTrail() {
    return self == null ? NO_TRAIL : self.inventoryTrail;
  }

  public static Trail collectedTrail() {
    return self == null ? NO_TRAIL : self.collectedTrail;
  }

  public static int sessionRevision() {
    return self == null ? 0 : self.sessionRevision;
  }

  public static String sessionText() {
    return self == null ? NONE : self.sessionText;
  }

  private boolean outdated() {
    long updated = Prices.updated();
    return updated != 0L && PriceHistory.now() - updated > STALE_AFTER;
  }

  private long staleAfter() {
    return STALE_AFTER;
  }

  private void restore() {
    try {
      history.load(historyFile);
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.warn("DonutSMP price history unreadable: {}", e.toString());
    }
  }

  private void persist() {
    auctions.persist();
    if (!history.dirty()) {
      return;
    }
    try {
      history.save(historyFile);
    } catch (IOException e) {
      CxVisualsClient.LOG.warn("DonutSMP price history not saved: {}", e.toString());
    }
  }

  private void update(Minecraft mc) {
    boolean onDonut = Server.donut(mc);
    crowd.tick(module.community.get(), module.history.get(), onDonut);
    module.syncStatus.show(crowd.status(module.community.get(), onDonut));
    while (trendsKey.consumeClick()) {
      if (mc.gui.screen() == null) {
        openTrends();
      }
    }
    if (mc.player != null && ++ticks >= INVENTORY_TICKS) {
      ticks = 0;
      refreshInventory(mc);
      refreshWatched();
    }
    if (mc.player != null && Prices.revision() != alertSeen) {
      alertSeen = Prices.revision();
      if (module.alerts.get() && module.history.get()) {
        scanAlerts();
      }
    }
  }

  private void refreshWatched() {
    int signature =
        (module.watchlist.revision() * 31 + Prices.revision()) * 31 + (outdated() ? 1 : 0);
    if (signature == watchSignature) {
      return;
    }
    watchSignature = signature;
    int kind = PriceHistory.AUCTION;
    long window = module.window.get().seconds;
    long now = PriceHistory.now();
    List<Watched> rows = new ArrayList<>(module.watchlist.size());
    for (String id : module.watchlist.ids()) {
      Item item = Names.item(id);
      if (item == null) {
        continue;
      }
      double price = Prices.auction(item);
      rows.add(
          new Watched(
              id,
              names.handle(id),
              names.title(id),
              price > 0.0 ? "$" + Money.format(price) : NONE,
              price > 0.0 ? history.change(id, kind, window, now) : Float.NaN));
    }
    watched = List.copyOf(rows);
    watchedRevision++;
  }

  private void scanAlerts() {
    Set<String> ids = new LinkedHashSet<>(module.watchlist.ids());
    for (Item item : carried.keySet()) {
      ids.add(BuiltInRegistries.ITEM.getKey(item).toString());
    }
    int kind = PriceHistory.AUCTION;
    float threshold = module.alertAt.get().floatValue() / 100.0F;
    long now = PriceHistory.now();
    for (String id : ids) {
      float change = history.change(id, kind, ALERT_WINDOW, now);
      Long last = alerted.get(id);
      if (Float.isNaN(change)
          || Math.abs(change) < threshold
          || last != null && now - last < ALERT_COOLDOWN) {
        continue;
      }
      if (Hud.module() == null || !Hud.module().toasts.stack.push(spike(id, kind, change))) {
        return;
      }
      alerted.put(id, now);
    }
  }

  private Friends.Toast spike(String id, int kind, float change) {
    String body = "Auction $" + Money.format(history.latest(id, kind)) + " · 1h";
    return new Friends.Toast(
        "",
        names.title(id),
        body,
        Friends.NO_ENTITY,
        String.format(Locale.ROOT, "%+.1f%%", change * 100.0F),
        names.handle(id));
  }

  private void refreshInventory(Minecraft mc) {
    Player player = mc.player;
    if (player != tracked) {
      tracked = player;
      primed = false;
      collected = 0.0;
      inventoryTrail.clear();
      collectedTrail.clear();
    }
    double auction = 0.0;
    counted.clear();
    Container inventory = player.getInventory();
    for (int i = 0; i < inventory.getContainerSize(); i++) {
      ItemStack stack = inventory.getItem(i);
      if (!stack.isEmpty()) {
        auction += worth(Prices.auction(stack), stack.getCount());
        counted.merge(stack.getItem(), stack.getCount(), Integer::sum);
      }
    }
    boolean browsing = player.containerMenu != player.inventoryMenu;
    if (primed && !browsing) {
      for (Map.Entry<Item, Integer> each : counted.entrySet()) {
        int gained = each.getValue() - carried.getOrDefault(each.getKey(), 0);
        if (gained > 0) {
          collected += worth(Prices.auction(each.getKey()), gained);
        }
      }
    }
    carried.clear();
    carried.putAll(counted);
    primed = true;
    publishSession();
    if (inventoryTrail.size() == 0 || ++trailTick >= TRAIL_EVERY) {
      trailTick = 0;
      inventoryTrail.add((float) auction);
      collectedTrail.add((float) collected);
    }

    String text = summary(auction, "");
    if (outdated() && !NONE.equals(text)) {
      text = TILDE + text;
    }
    String note = "Auction";
    if (!text.equals(inventoryText) || !note.equals(inventoryNote)) {
      inventoryText = text;
      inventoryNote = note;
      inventoryRevision++;
    }
  }

  private void publishSession() {
    String text = "$" + Money.format(collected);
    if (!text.equals(sessionText)) {
      sessionText = text;
      sessionRevision++;
    }
  }

  private static double worth(double each, int count) {
    return each > 0.0 ? each * count : 0.0;
  }

  private static String summary(double auction, String prefix) {
    return auction <= 0.0 ? NONE : prefix + Money.format(auction);
  }

  private boolean wanted(Minecraft mc) {
    Show show = module.container.get();
    if (show != Show.HOLD) {
      return show == Show.ALWAYS;
    }
    int code = InputConstants.getKey(previewKey.saveString()).getValue();
    return code != InputConstants.UNKNOWN.getValue()
        && InputConstants.isKeyDown(mc.getWindow(), code);
  }

  private static boolean browsable(AbstractContainerScreen<?> screen) {
    return !(screen.getMenu() instanceof InventoryMenu)
        && !(screen instanceof CreativeModeInventoryScreen);
  }

  private void draw(
      AbstractContainerScreen<?> screen,
      GuiGraphicsExtractor graphics,
      int mouseX,
      int mouseY,
      int left,
      int top,
      int width,
      int height,
      Slot hovered) {
    Minecraft mc = Minecraft.getInstance();
    long now = System.nanoTime();
    float dt = lastFrame == 0L ? 0.0F : Math.min((now - lastFrame) / 1.0e9F, MAX_FRAME_SECONDS);
    lastFrame = now;

    Window window = mc.getWindow();
    int guiScale = Math.max(1, window.getGuiScale());
    float scale = McCanvas.interfaceScale(window.getHeight());
    float pointer = guiScale / scale;
    if (auctions.active()) {
      canvas.begin(graphics, scale, guiScale);
      auctions.panel(
          canvas,
          themes.get().flat(),
          window.getWidth() / scale,
          left * pointer,
          top * pointer,
          width * pointer);
      canvas.end();
    }
    boolean show =
        !auctions.active() && browsable(screen) && wanted(mc) && collect(mc, screen.getMenu());
    if (!show && !panel.visible()) {
      return;
    }

    float zoom = module.previewScale.get().floatValue() / 100.0F;
    float cell = CELL * pointer * zoom;
    float screenWidth = window.getWidth() / scale;
    float screenHeight = window.getHeight() / scale;

    String title = TITLE;
    String note = cardNote;
    int focus = module.follow.get() && show ? focusOf(hovered) : -1;
    if (focus >= 0) {
      title = names.title(BuiltInRegistries.ITEM.getKey(hovered.getItem().getItem()).toString());
      note = summary(cardAuction[focus], "$");
    }
    panel.content(
        title, note, cardShown, Math.min(PreviewPanel.COLUMNS, cardShown), 0, cardCounts, 0, zoom);
    panel.stacks(cardHandles);
    panel.focus(focus);
    float cardWidth = panel.width(canvas, cell);
    float cardHeight = panel.height(cell);
    place(
        screen,
        cardWidth,
        cardHeight,
        cell,
        screenWidth,
        screenHeight,
        left * pointer,
        top * pointer,
        width * pointer,
        height * pointer);

    Theme theme = themes.get().flat();
    canvas.begin(graphics, scale, guiScale);
    panel.draw(canvas, theme, show, false, mouseX * pointer, mouseY * pointer, dt);
    canvas.end();
  }

  private int focusOf(Slot hovered) {
    if (hovered == null || !hovered.hasItem()) {
      return -1;
    }
    int handle =
        names.handle(BuiltInRegistries.ITEM.getKey(hovered.getItem().getItem()).toString());
    for (int i = 0; i < cardShown; i++) {
      if (cardHandles[i] == handle) {
        return i;
      }
    }
    return -1;
  }

  private void place(
      AbstractContainerScreen<?> screen,
      float cardWidth,
      float cardHeight,
      float cell,
      float screenWidth,
      float screenHeight,
      float boxLeft,
      float boxTop,
      float boxWidth,
      float boxHeight) {
    int blocked = Inventory.occupied(screen, obstacles);
    for (int i = 0; i < blocked * 4; i++) {
      obstacles[i] += (i & 2) == 0 ? -OBSTACLE_MARGIN : OBSTACLE_MARGIN;
    }
    boolean above = module.side.get() == Side.TOP;
    float boxRight = boxLeft + boxWidth;
    float boxBottom = boxTop + boxHeight;
    float centred = boxLeft + (boxWidth - cardWidth) * 0.5F;
    float high = boxTop - cardHeight - EDGE;
    float low = boxBottom + EDGE;
    float[] xs = {
      centred, boxLeft, boxRight - cardWidth, boxRight + EDGE, boxLeft - EDGE - cardWidth
    };
    float[] ys = {
      above ? high : low, above ? low : high, EDGE, screenHeight - cardHeight - EDGE, boxTop
    };
    float bestX = centred;
    float bestY = above ? EDGE : screenHeight - cardHeight - EDGE;
    float bestScore = Float.MAX_VALUE;
    for (int a = 0; a < xs.length; a++) {
      float x = Math.max(EDGE, Math.min(screenWidth - cardWidth - EDGE, xs[a]));
      for (int b = 0; b < ys.length; b++) {
        float y = Math.max(EDGE, Math.min(screenHeight - cardHeight - EDGE, ys[b]));
        float score =
            overlap(x, y, cardWidth, cardHeight, boxLeft, boxTop, boxRight, boxBottom)
                    * CONTAINER_WEIGHT
                + (a + b) * PREFERENCE;
        for (int i = 0; i < blocked; i++) {
          score +=
              overlap(
                  x,
                  y,
                  cardWidth,
                  cardHeight,
                  obstacles[i * 4],
                  obstacles[i * 4 + 1],
                  obstacles[i * 4 + 2],
                  obstacles[i * 4 + 3]);
        }
        if (score < bestScore) {
          bestScore = score;
          bestX = x;
          bestY = y;
        }
      }
    }
    panel.place(bestX, bestY, cell, cardWidth);
  }

  private static float overlap(
      float x, float y, float w, float h, float left, float top, float right, float bottom) {
    float across = Math.min(x + w, right) - Math.max(x, left);
    float down = Math.min(y + h, bottom) - Math.max(y, top);
    return across > 0.0F && down > 0.0F ? across * down : 0.0F;
  }

  private boolean collect(Minecraft mc, AbstractContainerMenu menu) {
    double floor = module.minimum.get();
    Container own = module.inventory.get() ? null : mc.player.getInventory();
    long signature =
        ((Prices.revision() * HASH + Double.hashCode(floor)) * HASH + (own == null ? 1 : 0)) * HASH
            + (outdated() ? 1 : 0);
    for (Slot slot : menu.slots) {
      if (slot.container != own && slot.hasItem()) {
        ItemStack stack = slot.getItem();
        signature =
            (signature * HASH + BuiltInRegistries.ITEM.getId(stack.getItem())) * HASH
                + stack.getCount();
      }
    }
    if (cardReady && signature == cardSignature) {
      return cardShown > 0;
    }
    cardReady = true;
    cardSignature = signature;

    Map<Item, Integer> totals = new LinkedHashMap<>();
    for (Slot slot : menu.slots) {
      if (slot.container != own && slot.hasItem()) {
        totals.merge(slot.getItem().getItem(), slot.getItem().getCount(), Integer::sum);
      }
    }
    List<Row> rows = new ArrayList<>(totals.size());
    double auction = 0.0;
    for (Map.Entry<Item, Integer> each : totals.entrySet()) {
      Row row = new Row(each.getKey(), each.getValue());
      auction += row.auction;
      if (row.auction >= floor) {
        rows.add(row);
      }
    }
    rows.sort(Comparator.comparingDouble((Row row) -> row.auction).reversed());
    cardShown = Math.min(CAPACITY, rows.size());
    for (int i = 0; i < cardShown; i++) {
      Row row = rows.get(i);
      cardCounts[i] = row.count;
      cardAuction[i] = row.auction;
      cardHandles[i] = Stacks.ofItem(new ItemStack(row.item));
    }
    cardNote = summary(auction, outdated() ? "~$" : "$");
    return cardShown > 0;
  }

  private void tint(AbstractContainerScreen<?> screen, GuiGraphicsExtractor graphics, Slot slot) {
    Minecraft mc = Minecraft.getInstance();
    if (screen instanceof CreativeModeInventoryScreen || mc.player == null) {
      return;
    }
    if (module.slots.get() == Slots.CONTAINER && slot.container == mc.player.getInventory()) {
      return;
    }
    ItemStack stack = slot.getItem();
    double value = worth(Prices.auction(stack), stack.getCount());
    double floor = module.slotFloor.get();
    if (value < floor) {
      return;
    }
    float tier = (float) Math.min(1.0, Math.log10(value / floor) / TIER_SPAN);
    int color =
        tier < 0.5F
            ? Theme.mix(TIER_LOW, TIER_MID, tier * 2.0F)
            : Theme.mix(TIER_MID, TIER_HIGH, tier * 2.0F - 1.0F);

    SlotPaint.draw(graphics, slot, color, SLOT_FILL, SLOT_RIM, 1.0F);
  }

  private void addPrices(ItemStack stack, Consumer<Component> out) {
    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    Quote quote = Prices.quote(stack);
    if (!quote.known()) {
      return;
    }
    long window = module.window.get().seconds;
    float change =
        module.arrows.get()
            ? history.change(id, PriceHistory.AUCTION, window, PriceHistory.now())
            : Float.NaN;
    out.accept(line(quote, stack.getCount(), change));
  }

  private static String percent(double fraction) {
    return String.format(Locale.ROOT, "%.1f%%", fraction * 100.0);
  }

  private static Component line(Quote quote, int count, float change) {
    double each = quote.price();
    MutableComponent text =
        Component.literal("Auction: ")
            .withStyle(ChatFormatting.GRAY)
            .append(Component.literal("$" + Money.format(each)).withStyle(ChatFormatting.WHITE));
    if (count > 1) {
      text.append(
          Component.literal(" ($" + Money.format(each * count) + ")")
              .withStyle(ChatFormatting.DARK_GRAY));
    }
    if (quote.source() == PriceBlend.Source.ESTIMATE) {
      text.append(Component.literal(" est").withStyle(ChatFormatting.DARK_GRAY));
    } else if (quote.source() == PriceBlend.Source.BASE) {
      text.append(Component.literal(" base").withStyle(ChatFormatting.DARK_GRAY));
    }
    if (quote.ageSeconds() >= OLD_NOTE) {
      text.append(
          Component.literal("  " + TrendChart.ago(quote.ageSeconds()) + " old")
              .withStyle(ChatFormatting.DARK_GRAY));
    }
    if (!Float.isNaN(change)) {
      if (change > 0.0005F) {
        text.append(Component.literal("  ▲ " + percent(change)).withStyle(ChatFormatting.GREEN));
      } else if (change < -0.0005F) {
        text.append(Component.literal("  ▼ " + percent(-change)).withStyle(ChatFormatting.RED));
      } else {
        text.append(Component.literal("  ▬ " + percent(0.0)).withStyle(ChatFormatting.GRAY));
      }
    }
    return text;
  }

  private final class Names implements TrendsHost {
    private final Map<String, Integer> handles = new HashMap<>();
    private final Map<String, String> titles = new HashMap<>();

    @Override
    public int handle(String id) {
      return handles.computeIfAbsent(
          id,
          key -> {
            Item item = item(key);
            return item == null ? -1 : Stacks.ofItem(new ItemStack(item));
          });
    }

    @Override
    public String title(String id) {
      return titles.computeIfAbsent(
          id,
          key -> {
            Item item = item(key);
            return item == null ? key : new ItemStack(item).getHoverName().getString();
          });
    }

    @Override
    public boolean watched(String id) {
      return module.watchlist.has(id);
    }

    @Override
    public void watch(String id) {
      module.watchlist.toggle(id);
    }

    @Override
    public int watchRevision() {
      return module.watchlist.revision();
    }

    @Override
    public long updated() {
      return Prices.updated();
    }

    @Override
    public long staleAfter() {
      return DonutSMP.this.staleAfter();
    }

    @Override
    public float flip(String id) {
      return auctions.flip(id);
    }

    @Override
    public String flipText(String id) {
      return auctions.flipText(id);
    }

    @Override
    public void search(String id) {
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null) {
        mc.player.connection.sendCommand("ah " + title(id));
      }
    }

    static Item item(String id) {
      Identifier identifier = Identifier.tryParse(id);
      return identifier == null
          ? null
          : BuiltInRegistries.ITEM.getOptional(identifier).orElse(null);
    }
  }

  public record Watched(String id, int handle, String title, String price, float change) {}

  private static final class Row {
    final Item item;
    final int count;
    final double auction;

    Row(Item item, int count) {
      this.item = item;
      this.count = count;
      this.auction = worth(Prices.auction(item), count);
    }
  }
}
