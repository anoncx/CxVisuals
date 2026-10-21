package me.cxiy.cxvisuals.mc.modules.inventory;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.util.ArrayList;
import java.util.List;
import me.cxiy.cxvisuals.core.module.ClientSettings;
import me.cxiy.cxvisuals.gui.ExplorerPanel;
import me.cxiy.cxvisuals.gui.PreviewPanel;
import me.cxiy.cxvisuals.gui.RecipePanel;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.gui.Themes;
import me.cxiy.cxvisuals.gui.spatial.Spring;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.McKeyBinder;
import me.cxiy.cxvisuals.mc.gui.McCanvas;
import me.cxiy.cxvisuals.modules.inventory.Catalog;
import me.cxiy.cxvisuals.modules.inventory.Favorites;
import me.cxiy.cxvisuals.modules.inventory.Handles;
import me.cxiy.cxvisuals.modules.inventory.InventoryHost;
import me.cxiy.cxvisuals.modules.inventory.InventoryModule;
import me.cxiy.cxvisuals.modules.inventory.Recipe;
import me.cxiy.cxvisuals.modules.inventory.RecipeBook;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.saveddata.maps.MapId;

public final class Inventory implements InventoryHost {
  private static final float FROST = 22.0F;
  private static final float MARGIN = 20.0F;
  private static final int MIN_COLUMNS = 2;
  private static final float GUTTER = 14.0F;
  private static final float OPEN_AT = 0.3F;
  private static final float CELL_GUI = 18.0F;
  private static final float MAX_FRAME_SECONDS = 0.1F;
  private static final int TIPS_PER_TICK = 96;
  private static final int DIM = 0x8C000000;
  private static final float HOT_ZONE = 48.0F;
  private static final float TIP_SHIFT = 12.0F;
  private static final float TIP_EDGE = 4.0F;
  private static final float SIDE_CELL_SCALE = 0.62F;
  private static final float SIDE_CELL_FLOOR = 22.0F;
  private static final float CARD_GAP = 10.0F;
  private static final long LAYOUT_PRIME = 1_000_003L;
  private static final float SCORE_COLUMN = 10000.0F;
  private static final String ENDER_TITLE = "ender chest";
  private static final float PEEK = 18.0F;
  private static final float LIVE_AT = 0.75F;
  private static final float GRACE_SECONDS = 1.6F;
  private static final String HYPIXEL = "hypixel.net";
  private static final String SKYBLOCK_FOLDER = "cxvisuals";

  private static Inventory self;
  private static InventoryModule module;

  private final KeyMapping recipeKey;
  private final KeyMapping usageKey;
  private final KeyMapping favoriteKey;
  private final KeyMapping hideKey;
  private final KeyMapping searchKey;
  private final KeyMapping previewKey;
  private final Previews previews = new Previews();
  private final Maps maps = new Maps();
  private final PreviewPanel[] panels = new PreviewPanel[Previews.CARDS];
  private final Slot[] cardSlots = new Slot[Previews.CARDS];
  private final boolean[] cardMap = new boolean[Previews.CARDS];
  private final boolean[] cardSide = new boolean[Previews.CARDS];
  private final boolean[] cardOn = new boolean[Previews.CARDS];
  private final Handles handles = Stacks.handles();
  private final Catalog catalog = new Catalog(handles);
  private final Favorites favorites;
  private final ExplorerPanel explorer;
  private final RecipePanel recipes;
  private final McCanvas canvas = new McCanvas();
  private final Themes themes;
  private final Spring peek = Spring.critical(170.0F);

  private int[] vanilla = new int[0];
  private int[] sky = new int[0];
  private List<Recipe> vanillaRecipes = List.of();
  private List<Recipe> skyRecipes = List.of();
  private RecipeBook book = RecipeBook.EMPTY;
  private ClientLevel builtFor;
  private boolean skyRequested;
  private boolean skyPlaced;
  private boolean placed;
  private float lastX = -1.0F;
  private float lastY = -1.0F;
  private boolean live;
  private boolean previewing;
  private boolean cursorPeek;
  private int cardCount;
  private final float[] cardX = new float[Previews.CARDS];
  private final float[] cardY = new float[Previews.CARDS];
  private final float[] cardWide = new float[Previews.CARDS];
  private final float[] cardTall = new float[Previews.CARDS];
  private final boolean[] cardFits = new boolean[Previews.CARDS];
  private final float[] obstacleX0 = new float[Previews.CARDS + 4];
  private final float[] obstacleY0 = new float[Previews.CARDS + 4];
  private final float[] obstacleX1 = new float[Previews.CARDS + 4];
  private final float[] obstacleY1 = new float[Previews.CARDS + 4];
  private final float[] candX = new float[(Previews.CARDS + 4) * 2 + 2];
  private final float[] candY = new float[(Previews.CARDS + 4) * 2 + 2];
  private long layoutKey;
  private float restX;
  private float restShift;
  private float restY;
  private float restW;
  private float restH;
  private float grace;
  private int tipCursor;
  private long lastNanos;
  private float scale = 1.0F;
  private float pointer = 1.0F;
  private float cellSize = 36.0F;
  private AbstractContainerScreen<?> shown;
  private Slot hoveredSlot;
  private boolean recipeOpen;
  private int tooltipHandle = Handles.NONE;

  private Inventory(McKeyBinder keys, InventoryModule owner, ClientSettings client) {
    recipeKey = keys.mapping(InventoryModule.RECIPE_KEY);
    usageKey = keys.mapping(InventoryModule.USAGE_KEY);
    favoriteKey = keys.mapping(InventoryModule.FAVORITE_KEY);
    hideKey = keys.mapping(InventoryModule.HIDE_KEY);
    searchKey = keys.mapping(InventoryModule.SEARCH_KEY);
    previewKey = keys.mapping(InventoryModule.PREVIEW_KEY);
    for (int i = 0; i < panels.length; i++) {
      panels[i] = new PreviewPanel(client.motion::get);
    }
    favorites = new Favorites(owner.favorites, handles);
    explorer = new ExplorerPanel(owner, handles, catalog, favorites, this, client.motion::get);
    recipes = new RecipePanel(handles, this, client.motion::get);
    themes = new Themes(client);
  }

  public static InventoryModule create(McKeyBinder keys, ClientSettings client) {
    module = new InventoryModule(keys);
    self = new Inventory(keys, module, client);
    return module;
  }

  public static InventoryModule module() {
    return module;
  }

  public static void tick(Minecraft mc) {
    if (self != null && module.live()) {
      self.update(mc);
    }
  }

  public static void opened(AbstractContainerScreen<?> screen) {
    if (self != null) {
      self.shown = screen;
      self.recipeOpen = false;
      self.recipes.reset();
      self.explorer.open(module.side.get() == InventoryModule.Side.RIGHT);
      self.peek.snap(1.0F);
      self.grace = GRACE_SECONDS;
    }
  }

  public static boolean show(String key, boolean uses) {
    if (self == null || self.shown == null) {
      return false;
    }
    int handle = self.handles.find(key);
    if (handle == Handles.NONE) {
      return false;
    }
    self.open(handle, uses);
    return true;
  }

  public static void closed() {
    if (self != null) {
      self.shown = null;
      self.recipeOpen = false;
    }
  }

  public static boolean active(AbstractContainerScreen<?> screen) {
    return self != null && module.live() && self.eligible(screen);
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
    if (self != null && module.live() && self.eligible(screen)) {
      self.draw(screen, graphics, mouseX, mouseY, left, top, width, height, hovered);
    }
  }

  public static int occupied(AbstractContainerScreen<?> screen, float[] out) {
    return active(screen) ? self.collectOccupied(out) : 0;
  }

  private int collectOccupied(float[] out) {
    int used = 0;
    if (placed && restW > 0.0F && used + 4 <= out.length) {
      out[used++] = restX + restShift;
      out[used++] = restY;
      out[used++] = restX + restShift + restW;
      out[used++] = restY + restH;
    }
    for (int i = 0; i < cardCount && used + 4 <= out.length; i++) {
      if (cardSide[i] && cardOn[i] && cardFits[i]) {
        out[used++] = cardX[i];
        out[used++] = cardY[i];
        out[used++] = cardX[i] + cardWide[i];
        out[used++] = cardY[i] + cardTall[i];
      }
    }
    return used / 4;
  }

  public static boolean covers(AbstractContainerScreen<?> screen) {
    return active(screen) && self.covering();
  }

  public static boolean mouseClicked(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
    return active(screen) && self.click(event);
  }

  public static boolean mouseReleased(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
    return active(screen) && self.release(event);
  }

  public static boolean mouseDragged(AbstractContainerScreen<?> screen, MouseButtonEvent event) {
    return active(screen) && self.drag(event);
  }

  public static boolean mouseScrolled(
      AbstractContainerScreen<?> screen, double x, double y, double amount) {
    return active(screen) && self.scroll(x, y, amount);
  }

  public static boolean keyPressed(
      AbstractContainerScreen<?> screen, KeyEvent event, Slot hovered) {
    return active(screen) && self.key(event, hovered);
  }

  public static boolean charTyped(AbstractContainerScreen<?> screen, int codepoint) {
    return active(screen) && self.type(codepoint);
  }

  private int overCard(float mx, float my) {
    for (int i = 0; i < cardCount; i++) {
      if (panels[i].contains(mx, my)) {
        return i;
      }
    }
    return -1;
  }

  private boolean covering() {
    return recipeOpen
        || (placed && live && explorer.contains(lastX, lastY))
        || overCard(lastX, lastY) >= 0;
  }

  private boolean eligible(AbstractContainerScreen<?> screen) {
    if (screen instanceof CreativeModeInventoryScreen || Minecraft.getInstance().level == null) {
      return false;
    }
    return module.storage.get()
        || screen instanceof InventoryScreen
        || screen instanceof CraftingScreen;
  }

  private boolean onHypixel(Minecraft mc) {
    ServerData server = mc.getCurrentServer();
    return server != null
        && server.ip != null
        && server.ip.toLowerCase(java.util.Locale.ROOT).contains(HYPIXEL);
  }

  private void update(Minecraft mc) {
    ClientLevel level = mc.level;
    if (level == null) {
      builtFor = null;
      return;
    }
    if (builtFor != level) {
      build(level);
    }
    boolean wantsSky = module.wantsSkyBlock(onHypixel(mc));
    if (wantsSky && !skyRequested) {
      skyRequested = true;
      SkyBlock.load(
          FabricLoader.getInstance().getConfigDir().resolve(SKYBLOCK_FOLDER),
          module.refresh.get(),
          this::skyReady);
    }
    if (wantsSky != skyPlaced && (sky.length > 0 || !wantsSky)) {
      compose(wantsSky);
    }
    fillTips(mc, level);
    rememberEnder(mc);
  }

  private void rememberEnder(Minecraft mc) {
    if (mc.gui.screen() instanceof ContainerScreen screen
        && screen.getMenu() instanceof ChestMenu menu
        && screen.getTitle().getString().toLowerCase(java.util.Locale.ROOT).contains(ENDER_TITLE)) {
      previews.remember(menu, menu.getRowCount());
    }
  }

  private void build(ClientLevel level) {
    builtFor = level;
    maps.reset();
    try {
      vanilla = VanillaData.items(level);
      vanillaRecipes = VanillaData.recipes(level);
    } catch (RuntimeException e) {
      CxVisualsClient.LOG.error("Inventory data failed to build", e);
    }
    tipCursor = 0;
    compose(module.wantsSkyBlock(onHypixel(Minecraft.getInstance())));
    rebuildBook();
    favorites.refresh();
  }

  private void skyReady(int[] entries, List<Recipe> found) {
    sky = entries;
    skyRecipes = found;
    rebuildBook();
    compose(module.wantsSkyBlock(onHypixel(Minecraft.getInstance())));
    favorites.refresh();
  }

  private void compose(boolean withSky) {
    skyPlaced = withSky;
    boolean first = withSky && onHypixel(Minecraft.getInstance());
    int[] head = first ? sky : vanilla;
    int[] tail = withSky ? (first ? vanilla : sky) : new int[0];
    int[] merged = new int[head.length + tail.length];
    System.arraycopy(head, 0, merged, 0, head.length);
    System.arraycopy(tail, 0, merged, head.length, tail.length);
    catalog.replace(merged);
  }

  private void rebuildBook() {
    List<Recipe> all = new ArrayList<>(vanillaRecipes.size() + skyRecipes.size());
    all.addAll(vanillaRecipes);
    all.addAll(skyRecipes);
    book = new RecipeBook(all);
    recipes.book(book);
  }

  private void fillTips(Minecraft mc, ClientLevel level) {
    int size = handles.size();
    if (tipCursor >= size || mc.player == null) {
      return;
    }
    Item.TooltipContext context = Item.TooltipContext.of(level);
    int end = Math.min(size, tipCursor + TIPS_PER_TICK);
    for (; tipCursor < end; tipCursor++) {
      ItemStack stack = Stacks.get(tipCursor);
      if (stack.isEmpty() || !Stacks.plain(stack)) {
        continue;
      }
      StringBuilder text = new StringBuilder();
      List<Component> lines = stack.getTooltipLines(context, mc.player, TooltipFlag.NORMAL);
      for (int i = 1; i < lines.size(); i++) {
        text.append(lines.get(i).getString()).append(' ');
      }
      handles.describe(tipCursor, text.toString());
    }
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
    Window window = mc.getWindow();
    int guiScale = Math.max(1, window.getGuiScale());
    scale = McCanvas.interfaceScale(window.getHeight());
    pointer = guiScale / scale;
    cellSize = CELL_GUI * pointer;
    hoveredSlot = hovered;
    long now = System.nanoTime();
    float dt = lastNanos == 0L ? 0.0F : Math.min((now - lastNanos) / 1.0e9F, MAX_FRAME_SECONDS);
    lastNanos = now;
    tooltipHandle = Handles.NONE;
    grace = Math.max(0.0F, grace - dt);

    float screenWidth = window.getWidth() / scale;
    float screenHeight = window.getHeight() / scale;
    boolean listed = module.listing() && module.visible.get();
    placed = false;
    float mx = mouseX * pointer;
    float my = mouseY * pointer;
    lastX = mx;
    lastY = my;
    Theme theme = themes.get();
    restW = 0.0F;
    restH = 0.0F;
    boolean overlay = recipeOpen || !recipes.gone();
    boolean showCards = chooseCards(mc, hovered);
    previewing = cursorPeek;

    if (!listed && !overlay && !showCards && !anyCardVisible()) {
      return;
    }
    canvas.begin(graphics, scale, guiScale);
    if (listed) {
      boolean right = module.side.get() == InventoryModule.Side.RIGHT;
      float pad = ExplorerPanel.padding();
      float edge = right ? (left + width) * pointer : left * pointer;
      float room = (right ? screenWidth - edge : edge) - GUTTER - MARGIN;
      int fitting = (int) Math.floor((room - pad * 2.0F) / cellSize);
      boolean cramped = fitting < MIN_COLUMNS;
      int columns = Math.max(MIN_COLUMNS, Math.min(module.columns.get().intValue(), fitting));
      float panelWidth = Math.min(screenWidth - MARGIN * 2.0F, columns * cellSize + pad * 2.0F);
      float panelHeight =
          Math.min(
              screenHeight - MARGIN * 2.0F,
              module.rows.get().intValue() * cellSize + ExplorerPanel.chrome());
      float beside = right ? edge + GUTTER : edge - GUTTER - panelWidth;
      float x = cramped ? (right ? screenWidth - MARGIN - panelWidth : MARGIN) : beside;
      float middle = (top + height * 0.5F) * pointer;
      float y =
          Math.max(
              MARGIN, Math.min(screenHeight - MARGIN - panelHeight, middle - panelHeight * 0.5F));
      boolean inBand = my >= y - HOT_ZONE && my <= y + panelHeight + HOT_ZONE;
      boolean overEdge =
          inBand && (right ? mx >= screenWidth - PEEK - HOT_ZONE : mx <= PEEK + HOT_ZONE);
      boolean overPanel = inBand && mx >= x - HOT_ZONE && mx <= x + panelWidth + HOT_ZONE;
      boolean near = overEdge || (peek.value() > OPEN_AT && overPanel);
      boolean wanted =
          (!module.autoHide.get() && !cramped)
              || near
              || grace > 0.0F
              || explorer.typing()
              || recipeOpen;
      peek.step(dt, wanted ? 1.0F : 0.0F);
      float shown = Math.max(0.0F, Math.min(1.0F, peek.value()));
      float reach = right ? screenWidth - x - PEEK : x + panelWidth - PEEK;
      float hidden = reach * (1.0F - shown) * (right ? 1.0F : -1.0F);
      restX = x;
      restShift = hidden;
      restY = y;
      restW = panelWidth;
      restH = panelHeight;
      explorer.layout(x + hidden, y, panelWidth, panelHeight, cellSize);
      placed = true;
      live = shown > LIVE_AT;
      boolean covered = recipeOpen || !live;
      explorer.draw(canvas, theme, covered ? -1.0F : mx, covered ? -1.0F : my, dt);
    }
    if (showCards || anyCardVisible()) {
      drawCards(theme, mx, my, screenWidth, screenHeight, hovered, left, top, width, height, dt);
    }
    if (overlay) {
      canvas.roundRect(
          0.0F, 0.0F, screenWidth, screenHeight, 0.0F, Theme.withAlpha(DIM, recipes.phase()));
      recipes.layout(screenWidth, screenHeight, cellSize);
      recipes.draw(canvas, theme, recipeOpen ? mx : -1.0F, recipeOpen ? my : -1.0F, dt);
      if (recipes.closed()) {
        recipeOpen = false;
      }
    }
    canvas.end();
    if (tooltipHandle != Handles.NONE) {
      tooltip(graphics, mc, mouseX, mouseY, tooltipHandle);
    } else {
      cardTooltip(graphics, mc, mouseX, mouseY);
    }
  }

  private String labelOf(Previews.Card card) {
    if (card.copies <= 1) {
      return card.title;
    }
    if (card.labelCopies != card.copies || !card.labelTitle.equals(card.title)) {
      card.label = card.copies + "\u00d7 " + card.title;
      card.labelCopies = card.copies;
      card.labelTitle = card.title;
    }
    return card.label;
  }

  private boolean anyCardVisible() {
    for (PreviewPanel panel : panels) {
      if (panel.visible()) {
        return true;
      }
    }
    return false;
  }

  private void cardTooltip(GuiGraphicsExtractor graphics, Minecraft mc, int mouseX, int mouseY) {
    if (mc.player == null || mc.level == null) {
      return;
    }
    for (int i = 0; i < cardCount; i++) {
      if (panels[i].hovered() >= 0) {
        ItemStack inner = cardMap[i] ? maps.card(i).source : previews.at(i, panels[i].hovered());
        if (!inner.isEmpty()) {
          graphics.setComponentTooltipForNextFrame(
              mc.font,
              inner.getTooltipLines(
                  Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL),
              mouseX,
              mouseY);
        }
        return;
      }
    }
  }

  private void drawCards(
      Theme theme,
      float mx,
      float my,
      float screenWidth,
      float screenHeight,
      Slot hovered,
      int containerLeft,
      int containerTop,
      int containerWidth,
      int containerHeight,
      float dt) {
    boolean explorerRight = module.side.get() == InventoryModule.Side.RIGHT;
    float zoom = module.previewScale.get().floatValue() / 100.0F;
    float sideCell = Math.max(SIDE_CELL_FLOOR, cellSize * SIDE_CELL_SCALE) * zoom;
    float cursorCell = cellSize * zoom;
    long key =
        signature(
            screenWidth,
            screenHeight,
            containerLeft,
            containerTop,
            containerWidth,
            containerHeight,
            explorerRight,
            sideCell,
            cursorCell);
    for (int i = 0; i < cardCount; i++) {
      PreviewPanel panel = panels[i];
      float cell = cardSide[i] ? sideCell : cursorCell;
      if (cardMap[i]) {
        Maps.Card card = maps.card(i);
        panel.content(
            card.label(), card.note, Maps.GRID * Maps.GRID, Maps.GRID, 0, null, i, zoom, true);
      } else {
        Previews.Card card = previews.card(i);
        panel.content(
            labelOf(card),
            card.note,
            card.slots,
            card.columns,
            module.tint.get() ? card.tint : 0,
            card.counts,
            Previews.base(i),
            zoom);
      }
      cardWide[i] = panel.width(canvas, cell);
      cardTall[i] = panel.height(cell);
      key =
          key * LAYOUT_PRIME
              + Float.floatToIntBits(cardWide[i]) * LAYOUT_PRIME
              + Float.floatToIntBits(cardTall[i]);
    }
    if (key != layoutKey) {
      layoutKey = key;
      float k = pointer;
      arrange(
          screenWidth,
          screenHeight,
          explorerRight,
          containerLeft * k,
          containerTop * k,
          (containerLeft + containerWidth) * k,
          (containerTop + containerHeight) * k);
    }
    for (int i = 0; i < Previews.CARDS; i++) {
      PreviewPanel panel = panels[i];
      boolean live = i < cardCount;
      boolean onScreen = live;
      if (live) {
        if (cardSide[i]) {
          onScreen = cardFits[i];
          panel.place(cardX[i], cardY[i], sideCell, cardWide[i]);
        } else {
          float wide = cardWide[i];
          float tall = cardTall[i];
          float shift = TIP_SHIFT * pointer;
          float px = mx + shift + wide > screenWidth - TIP_EDGE ? mx - shift - wide : mx + shift;
          float py = Math.max(TIP_EDGE, Math.min(screenHeight - TIP_EDGE - tall, my - shift));
          panel.place(px, py, cursorCell, wide);
        }
      }
      boolean lit = live && cardSide[i] && hovered != null && cardSlots[i] == hovered;
      boolean interactive = live && cardSide[i] && !recipeOpen;
      panel.draw(
          canvas,
          theme,
          onScreen && live && cardOn[i] && !recipeOpen,
          lit,
          interactive ? mx : -1.0F,
          interactive ? my : -1.0F,
          dt);
    }
  }

  private long signature(
      float screenWidth,
      float screenHeight,
      int left,
      int top,
      int width,
      int height,
      boolean right,
      float sideCell,
      float cursorCell) {
    long key = cardCount;
    key = key * LAYOUT_PRIME + Float.floatToIntBits(screenWidth);
    key = key * LAYOUT_PRIME + Float.floatToIntBits(screenHeight);
    key = key * LAYOUT_PRIME + left * 31 + top * 17 + width * 13 + height * 7;
    key = key * LAYOUT_PRIME + (right ? 1 : 0);
    key = key * LAYOUT_PRIME + Float.floatToIntBits(sideCell);
    key = key * LAYOUT_PRIME + Float.floatToIntBits(cursorCell);
    key =
        key * LAYOUT_PRIME
            + Float.floatToIntBits(restX)
            + Float.floatToIntBits(restW) * 3
            + Float.floatToIntBits(restH) * 5;
    return key;
  }

  private void arrange(
      float screenWidth,
      float screenHeight,
      boolean explorerRight,
      float boxLeft,
      float boxTop,
      float boxRight,
      float boxBottom) {
    int obstacles = 0;
    float gap = CARD_GAP;
    float[] ox0 = obstacleX0;
    float[] oy0 = obstacleY0;
    float[] ox1 = obstacleX1;
    float[] oy1 = obstacleY1;
    float mirrorLeft = explorerRight ? boxLeft : screenWidth - boxRight;
    float mirrorRight = explorerRight ? boxRight : screenWidth - boxLeft;
    ox0[obstacles] = mirrorLeft - gap;
    oy0[obstacles] = boxTop - gap;
    ox1[obstacles] = mirrorRight + gap;
    oy1[obstacles++] = boxBottom + gap;
    if (restW > 0.0F) {
      float rl = explorerRight ? restX : screenWidth - restX - restW;
      ox0[obstacles] = rl - gap;
      oy0[obstacles] = restY - gap;
      ox1[obstacles] = rl + restW + gap;
      oy1[obstacles++] = restY + restH + gap;
    }
    float minX = MARGIN;
    float maxX = screenWidth - MARGIN;
    float minY = MARGIN;
    float maxY = screenHeight - MARGIN;
    for (int i = 0; i < cardCount; i++) {
      if (!cardSide[i]) {
        continue;
      }
      float wide = cardWide[i];
      float tall = cardTall[i];
      int xs = 0;
      int ys = 0;
      candX[xs++] = minX;
      candY[ys++] = maxY - tall;
      for (int o = 0; o < obstacles; o++) {
        candX[xs++] = ox1[o];
        candX[xs++] = ox0[o] - wide;
        candY[ys++] = oy0[o] - tall;
        candY[ys++] = oy1[o];
      }
      boolean found = false;
      float bestX = 0.0F;
      float bestY = 0.0F;
      float bestScore = Float.MAX_VALUE;
      for (int a = 0; a < xs; a++) {
        float cx = candX[a];
        if (cx < minX || cx + wide > maxX) {
          continue;
        }
        for (int b = 0; b < ys; b++) {
          float cy = candY[b];
          if (cy < minY || cy + tall > maxY) {
            continue;
          }
          float score = (cx - minX) * SCORE_COLUMN + (maxY - (cy + tall));
          if (score >= bestScore || hits(cx, cy, wide, tall, ox0, oy0, ox1, oy1, obstacles)) {
            continue;
          }
          bestScore = score;
          bestX = cx;
          bestY = cy;
          found = true;
        }
      }
      cardFits[i] = found;
      if (found) {
        ox0[obstacles] = bestX - gap * 0.5F;
        oy0[obstacles] = bestY - gap * 0.5F;
        ox1[obstacles] = bestX + wide + gap * 0.5F;
        oy1[obstacles++] = bestY + tall + gap * 0.5F;
        cardX[i] = explorerRight ? bestX : screenWidth - bestX - wide;
        cardY[i] = bestY;
      }
    }
  }

  private static boolean hits(
      float x,
      float y,
      float w,
      float h,
      float[] x0,
      float[] y0,
      float[] x1,
      float[] y1,
      int count) {
    for (int i = 0; i < count; i++) {
      if (x < x1[i] && x + w > x0[i] && y < y1[i] && y + h > y0[i]) {
        return true;
      }
    }
    return false;
  }

  private boolean chooseCards(Minecraft mc, Slot hovered) {
    cardCount = 0;
    cursorPeek = false;
    InventoryModule.Preview mode = module.preview.get();
    InventoryModule.Preview mapMode = module.mapPreview.get();
    if (mode == InventoryModule.Preview.OFF && mapMode == InventoryModule.Preview.OFF) {
      return false;
    }
    if (shown == null || recipeOpen) {
      return false;
    }
    boolean compact = module.compact.get();
    InventoryModule.Place place = module.place.get();
    InventoryModule.MapPlace mapPlace = module.mapPlace.get();
    if (mode != InventoryModule.Preview.OFF && place == InventoryModule.Place.SIDE
        || mapMode != InventoryModule.Preview.OFF && mapPlace == InventoryModule.MapPlace.SIDE) {
      for (Slot slot : shown.getMenu().slots) {
        ItemStack stack = slot.getItem();
        if (mode != InventoryModule.Preview.OFF
            && place == InventoryModule.Place.SIDE
            && Previews.candidate(stack)) {
          int twin = compact ? twinOf(stack) : -1;
          if (twin >= 0) {
            previews.card(twin).copies++;
          } else if (cardCount < Previews.CARDS && previews.resolve(cardCount, stack, compact)) {
            previews.card(cardCount).copies = 1;
            cardMap[cardCount] = false;
            cardSide[cardCount] = true;
            cardSlots[cardCount++] = slot;
          }
        }
        if (mapMode != InventoryModule.Preview.OFF
            && mapPlace == InventoryModule.MapPlace.SIDE
            && Maps.candidate(stack)) {
          int twin = compact ? mapTwinOf(stack) : -1;
          if (twin >= 0) {
            maps.card(twin).copies++;
          } else if (cardCount < Previews.CARDS && maps.resolve(cardCount, stack)) {
            maps.card(cardCount).copies = 1;
            cardMap[cardCount] = true;
            cardSide[cardCount] = true;
            cardSlots[cardCount++] = slot;
          }
        }
      }
    } else if (hovered != null && hovered.hasItem()) {
      if (mode != InventoryModule.Preview.OFF
          && place == InventoryModule.Place.CURSOR
          && previews.resolve(0, hovered.getItem(), compact)) {
        cardMap[0] = false;
        cardSide[0] = false;
        cardSlots[cardCount++] = hovered;
      } else if (mapMode != InventoryModule.Preview.OFF
          && mapPlace == InventoryModule.MapPlace.CURSOR
          && maps.resolve(0, hovered.getItem())) {
        cardMap[0] = true;
        cardSide[0] = false;
        cardSlots[cardCount++] = hovered;
      }
    }
    boolean keyDown = keyHeld(mc);
    boolean any = false;
    for (int i = 0; i < cardCount; i++) {
      InventoryModule.Preview gate = cardMap[i] ? mapMode : mode;
      boolean on =
          gate == InventoryModule.Preview.ALWAYS
              || (gate == InventoryModule.Preview.HOLD && keyDown);
      cardOn[i] = on;
      any |= on;
      cursorPeek |= on && !cardSide[i];
    }
    return any;
  }

  private int twinOf(ItemStack stack) {
    for (int i = 0; i < cardCount; i++) {
      if (ItemStack.isSameItemSameComponents(previews.card(i).source, stack)) {
        return i;
      }
    }
    return -1;
  }

  private int mapTwinOf(ItemStack stack) {
    MapId id = stack.get(DataComponents.MAP_ID);
    if (id == null) {
      return -1;
    }
    for (int i = 0; i < cardCount; i++) {
      if (cardMap[i] && maps.card(i).id == id.id()) {
        return i;
      }
    }
    return -1;
  }

  private boolean keyHeld(Minecraft mc) {
    int code = InputConstants.getKey(previewKey.saveString()).getValue();
    return code != InputConstants.UNKNOWN.getValue()
        && InputConstants.isKeyDown(mc.getWindow(), code);
  }

  public static boolean previewTooltip(
      AbstractContainerScreen<?> screen,
      GuiGraphicsExtractor graphics,
      int mouseX,
      int mouseY,
      Slot hovered) {
    return active(screen)
        && (self.tooltipCard(screen, graphics, mouseX, mouseY, hovered)
            || self.mapTooltipCard(screen, graphics, mouseX, mouseY, hovered));
  }

  private boolean tooltipCard(
      AbstractContainerScreen<?> screen,
      GuiGraphicsExtractor graphics,
      int mouseX,
      int mouseY,
      Slot hovered) {
    InventoryModule.Preview mode = module.preview.get();
    Minecraft mc = Minecraft.getInstance();
    if (mode == InventoryModule.Preview.OFF
        || module.place.get() != InventoryModule.Place.TOOLTIP
        || recipeOpen
        || hovered == null
        || !screen.getMenu().getCarried().isEmpty()
        || !hovered.hasItem()) {
      return false;
    }
    ItemStack stack = hovered.getItem();
    if (!previews.resolve(0, stack, module.compact.get())
        || (mode == InventoryModule.Preview.HOLD && !keyHeld(mc))) {
      return false;
    }
    ItemStack plain = stack.copy();
    plain.remove(DataComponents.CONTAINER);
    plain.remove(DataComponents.BUNDLE_CONTENTS);
    List<ClientTooltipComponent> parts = new ArrayList<>();
    for (Component line : Screen.getTooltipFromItem(mc, plain)) {
      parts.add(ClientTooltipComponent.create(line.getVisualOrderText()));
    }
    Previews.Card card = previews.card(0);
    parts.add(
        new ContainerTooltip(
            previews, card.slots, card.columns, module.tint.get() ? card.tint : 0));
    graphics.tooltip(
        mc.font,
        parts,
        mouseX,
        mouseY,
        DefaultTooltipPositioner.INSTANCE,
        stack.get(DataComponents.TOOLTIP_STYLE));
    return true;
  }

  private boolean mapTooltipCard(
      AbstractContainerScreen<?> screen,
      GuiGraphicsExtractor graphics,
      int mouseX,
      int mouseY,
      Slot hovered) {
    InventoryModule.Preview mode = module.mapPreview.get();
    Minecraft mc = Minecraft.getInstance();
    if (mode == InventoryModule.Preview.OFF
        || module.mapPlace.get() != InventoryModule.MapPlace.TOOLTIP
        || recipeOpen
        || hovered == null
        || !screen.getMenu().getCarried().isEmpty()
        || !hovered.hasItem()) {
      return false;
    }
    ItemStack stack = hovered.getItem();
    if (!maps.resolve(0, stack) || (mode == InventoryModule.Preview.HOLD && !keyHeld(mc))) {
      return false;
    }
    List<ClientTooltipComponent> parts = new ArrayList<>();
    for (Component line : Screen.getTooltipFromItem(mc, stack)) {
      parts.add(ClientTooltipComponent.create(line.getVisualOrderText()));
    }
    parts.add(new MapTooltip(maps.state(0)));
    graphics.tooltip(
        mc.font,
        parts,
        mouseX,
        mouseY,
        DefaultTooltipPositioner.INSTANCE,
        stack.get(DataComponents.TOOLTIP_STYLE));
    return true;
  }

  public static boolean hidesTooltip(AbstractContainerScreen<?> screen) {
    return active(screen) && (self.covering() || self.previewing);
  }

  public static boolean replacingMapFaces() {
    if (self == null || !module.live()) {
      return false;
    }
    if (module.mapPlace.get() != InventoryModule.MapPlace.TEXTURE) {
      return false;
    }
    if (module.mapPreview.get() == InventoryModule.Preview.ALWAYS) {
      return true;
    }
    return module.mapPreview.get() == InventoryModule.Preview.HOLD
        && self.keyHeld(Minecraft.getInstance());
  }

  public static boolean mapFace(GuiGraphicsExtractor graphics, ItemStack stack, int x, int y) {
    return self != null && self.maps.face(graphics, stack, x, y);
  }

  private void tooltip(
      GuiGraphicsExtractor graphics, Minecraft mc, int mouseX, int mouseY, int handle) {
    ItemStack stack = Stacks.get(handle);
    if (stack.isEmpty() || mc.player == null || mc.level == null) {
      return;
    }
    List<Component> lines =
        new ArrayList<>(
            stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, TooltipFlag.NORMAL));
    if (module.hints.get() && book.has(handle)) {
      lines.add(
          Component.literal(
                  recipeKey.getTranslatedKeyMessage().getString()
                      + " recipes   "
                      + usageKey.getTranslatedKeyMessage().getString()
                      + " uses   "
                      + favoriteKey.getTranslatedKeyMessage().getString()
                      + " favourite")
              .withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
    }
    graphics.setComponentTooltipForNextFrame(mc.font, lines, mouseX, mouseY);
  }

  @Override
  public void hover(int handle) {
    tooltipHandle = handle;
  }

  @Override
  public void recipes(int handle) {
    open(handle, false);
  }

  @Override
  public void uses(int handle) {
    open(handle, true);
  }

  private void open(int handle, boolean uses) {
    if (handle == Handles.NONE) {
      return;
    }
    recipeOpen = true;
    recipes.show(handle, uses);
  }

  @Override
  public boolean canGive() {
    Minecraft mc = Minecraft.getInstance();
    return module.cheat.get() && mc.player != null && mc.player.canUseGameMasterBlocks();
  }

  @Override
  public void give(int handle, boolean full) {
    Minecraft mc = Minecraft.getInstance();
    ItemStack stack = Stacks.get(handle);
    if (mc.player == null
        || stack.isEmpty()
        || !Stacks.plain(stack)
        || !stack.getComponentsPatch().isEmpty()) {
      return;
    }
    mc.player.connection.sendCommand(
        "give @s "
            + BuiltInRegistries.ITEM.getKey(stack.getItem())
            + " "
            + (full ? stack.getMaxStackSize() : 1));
  }

  @Override
  public boolean canFill(Recipe recipe) {
    return module.fill.get() && shown != null && Filler.supports(shown.getMenu(), recipe);
  }

  @Override
  public void fill(Recipe recipe) {
    if (shown != null) {
      Filler.fill(Minecraft.getInstance(), shown.getMenu(), recipe);
    }
  }

  private boolean click(MouseButtonEvent event) {
    float mx = (float) event.x() * pointer;
    float my = (float) event.y() * pointer;
    if (recipeOpen) {
      recipes.mouseDown(mx, my, event.button());
      return true;
    }
    int card = overCard(mx, my);
    if (card >= 0 && cardSide[card]) {
      takeFromPreview(card, event.button(), event.hasShiftDown());
      return true;
    }
    if (!placed || !live) {
      return false;
    }
    return explorer.mouseDown(mx, my, event.button(), event.hasShiftDown(), event.hasControlDown());
  }

  private void takeFromPreview(int card, int button, boolean shift) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.gameMode == null || mc.player == null || shown == null || cardSlots[card] == null) {
      return;
    }
    mc.gameMode.handleContainerInput(
        shown.getMenu().containerId,
        cardSlots[card].index,
        button,
        shift ? ContainerInput.QUICK_MOVE : ContainerInput.PICKUP,
        mc.player);
  }

  private boolean release(MouseButtonEvent event) {
    float mx = (float) event.x() * pointer;
    float my = (float) event.y() * pointer;
    if (recipeOpen) {
      recipes.mouseUp(mx, my);
      return true;
    }
    if (overCard(mx, my) >= 0) {
      return true;
    }
    if (!placed || !live) {
      return false;
    }
    explorer.mouseUp(mx, my);
    return explorer.contains(mx, my);
  }

  private boolean drag(MouseButtonEvent event) {
    if (recipeOpen) {
      return true;
    }
    if (!placed || !explorer.typing()) {
      return false;
    }
    explorer.mouseDrag((float) event.x() * pointer);
    return true;
  }

  private boolean scroll(double x, double y, double amount) {
    if (recipeOpen) {
      return recipes.scroll((float) amount);
    }
    return placed
        && live
        && explorer.scroll((float) x * pointer, (float) y * pointer, (float) amount);
  }

  private boolean key(KeyEvent event, Slot hovered) {
    if (recipeOpen) {
      if (recipeKey.matches(event) || usageKey.matches(event)) {
        int handle = recipes.hovered();
        if (handle != Handles.NONE) {
          open(handle, usageKey.matches(event));
        }
        return true;
      }
      recipes.keyDown(event.key());
      return true;
    }
    if (placed && explorer.typing()) {
      return explorer.keyDown(event.key(), event.hasControlDown(), event.hasShiftDown());
    }
    if (!module.listing()) {
      return false;
    }
    if (hideKey.matches(event)) {
      module.visible.set(!module.visible.get());
      return true;
    }
    if (!placed) {
      return false;
    }
    if (searchKey.matches(event) && event.hasControlDown()) {
      explorer.focusSearch();
      return true;
    }
    int handle = explorer.hovered();
    if (handle == Handles.NONE && hovered != null) {
      handle = Stacks.of(hovered.getItem());
    }
    if (handle == Handles.NONE) {
      return false;
    }
    if (recipeKey.matches(event)) {
      open(handle, false);
      return true;
    }
    if (usageKey.matches(event)) {
      open(handle, true);
      return true;
    }
    if (favoriteKey.matches(event)) {
      explorer.toggleFavorite(handle);
      return true;
    }
    return false;
  }

  private boolean type(int codepoint) {
    return !recipeOpen && placed && explorer.charTyped(codepoint);
  }
}
