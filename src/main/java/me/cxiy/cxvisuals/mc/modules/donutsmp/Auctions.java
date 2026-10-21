package me.cxiy.cxvisuals.mc.modules.donutsmp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.gui.Canvas;
import me.cxiy.cxvisuals.gui.Depth;
import me.cxiy.cxvisuals.gui.FontStyle;
import me.cxiy.cxvisuals.gui.Theme;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.modules.hud.Hud;
import me.cxiy.cxvisuals.modules.donutsmp.DonutSMPModule;
import me.cxiy.cxvisuals.modules.donutsmp.Money;
import me.cxiy.cxvisuals.modules.donutsmp.PriceHistory;
import me.cxiy.cxvisuals.modules.donutsmp.TrendsHost;
import me.cxiy.cxvisuals.modules.donutsmp.auction.AuctionText;
import me.cxiy.cxvisuals.modules.donutsmp.auction.Listing;
import me.cxiy.cxvisuals.modules.donutsmp.auction.Market;
import me.cxiy.cxvisuals.modules.donutsmp.auction.PriceBlend;
import me.cxiy.cxvisuals.modules.donutsmp.auction.PriceBlend.Quote;
import me.cxiy.cxvisuals.modules.donutsmp.auction.Sanity;
import me.cxiy.cxvisuals.modules.donutsmp.auction.Size;
import me.cxiy.cxvisuals.modules.donutsmp.auction.Variant;
import me.cxiy.cxvisuals.modules.donutsmp.auction.VariantKey;
import me.cxiy.cxvisuals.modules.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

final class Auctions {
  private static final int ROWS = 6;
  private static final int SLOTS = ROWS * 9;
  private static final int LISTED = 5;
  private static final long SAVE_EVERY = 60L;
  private static final int DEMO_SIGHTINGS = 4;
  private static final long BLOCK_FLASH_NANOS = 900_000_000L;
  private static final long BLOCK_TOAST_NANOS = 2_500_000_000L;
  private static final float PULSE_SECONDS = 1.1F;
  private static final float PANEL_WIDTH = 204.0F;
  private static final float PANEL_HEAD = 30.0F;
  private static final float PANEL_ROW = 26.0F;
  private static final float PANEL_FOOT = 8.0F;
  private static final float PANEL_PAD = 11.0F;
  private static final float PANEL_ICON = 16.0F;
  private static final float EDGE = 12.0F;
  private static final float CHIME_PITCH = 1.5F;
  private static final float CHIME_VOLUME = 0.5F;
  private static final int GOOD = 0xFF3DDC5A;
  private static final int BEST = 0xFF7CFFA0;
  private static final int BAD = 0xFFFF5F57;

  record Seen(
      Listing listing,
      int handle,
      String title,
      double reference,
      float edge,
      double profit,
      boolean deal,
      boolean snipe,
      boolean over,
      Contents.Worth worth,
      String basis) {}

  private static final double MOST_UNIT = 2.0e10;
  private static final double MOST_FLIP = 5.0;

  private final DonutSMPModule module;
  private final PriceHistory history;
  private final TrendsHost names;
  private final Path file;
  private final Market market = new Market();
  private final Sanity sanity = new Sanity();
  private final Seen[] bySlot = new Seen[SLOTS];
  private final Map<ItemStack, Seen> byStack = new IdentityHashMap<>();
  private final List<Listing> found = new ArrayList<>(SLOTS);
  private final List<ItemStack> foundStacks = new ArrayList<>(SLOTS);
  private final List<Seen> best = new ArrayList<>(LISTED);
  private static final String[] SKIP_LABELS = {"odd listing", "suspicious price"};
  private final int[] skips = new int[SKIP_LABELS.length];
  private final Set<Integer> alerted = new HashSet<>();
  private long signature;
  private boolean active;
  private int blockedSlot = -1;
  private long blockedAt;
  private long toastAt;
  private long savedAt = PriceHistory.now();
  private long frame = System.nanoTime();
  private float pulse;

  Auctions(DonutSMPModule module, PriceHistory history, TrendsHost names, Path file) {
    this.module = module;
    this.history = history;
    this.names = names;
    this.file = file;
    try {
      market.load(file);
    } catch (IOException | RuntimeException e) {
      CxVisualsClient.LOG.warn("DonutSMP market unreadable: {}", e.toString());
    }
    Prices.bind(market);
  }

  void demo(String id, double unit) {
    long now = PriceHistory.now();
    for (int i = 0; i < DEMO_SIGHTINGS; i++) {
      market.observe(id, unit * (0.97 + 0.02 * i), id.hashCode() + i, now);
    }
  }

  Market market() {
    return market;
  }

  boolean active() {
    return active;
  }

  void clear() {
    market.reset();
    sanity.clear();
    persist();
  }

  void persist() {
    if (!market.dirty()) {
      return;
    }
    try {
      market.save(file);
    } catch (IOException e) {
      CxVisualsClient.LOG.warn("DonutSMP market not saved: {}", e.toString());
    }
  }

  void observe(AbstractContainerScreen<?> screen) {
    long now = System.nanoTime();
    pulse = (pulse + (now - frame) / 1.0e9F) % PULSE_SECONDS;
    frame = now;
    if (!(screen.getMenu() instanceof ChestMenu chest) || chest.getRowCount() != ROWS) {
      leave();
      return;
    }
    long next = settingsKey();
    for (int i = 0; i < SLOTS; i++) {
      ItemStack stack = chest.slots.get(i).getItem();
      next = (next * 31L + System.identityHashCode(stack)) * 31L + stack.getCount();
    }
    if (next == signature) {
      return;
    }
    signature = next;
    rebuild(screen, chest);
  }

  private long settingsKey() {
    long key = Prices.revision();
    key = key * 31L + Double.hashCode(module.guardPercent.get());
    key = key * 31L + Double.hashCode(module.guardLimit.get());
    key = key * 31L + Double.hashCode(module.dealPercent.get());
    key = key * 31L + Double.hashCode(module.snipePercent.get());
    key = key * 31L + Double.hashCode(module.snipeProfit.get());
    return key;
  }

  private void leave() {
    if (!active && signature == 0L) {
      return;
    }
    active = false;
    signature = 0L;
    reset();
    long now = PriceHistory.now();
    if (market.dirty() && now - savedAt >= SAVE_EVERY) {
      savedAt = now;
      Thread writer = new Thread(this::persist, "CxVisuals-DonutSMP-Market");
      writer.setDaemon(true);
      writer.start();
    }
  }

  private void reset() {
    Arrays.fill(bySlot, null);
    byStack.clear();
    best.clear();
  }

  private void rebuild(AbstractContainerScreen<?> screen, ChestMenu chest) {
    reset();
    found.clear();
    foundStacks.clear();
    boolean controls = false;
    String sample = "";
    for (int i = 0; i < SLOTS; i++) {
      ItemStack stack = chest.slots.get(i).getItem();
      if (stack.isEmpty()) {
        continue;
      }
      if (AuctionText.control(stack.getHoverName().getString())) {
        controls = true;
        continue;
      }
      ItemLore lore = stack.get(DataComponents.LORE);
      if (lore == null) {
        continue;
      }
      List<String> lines = new ArrayList<>(lore.lines().size());
      for (Component line : lore.lines()) {
        lines.add(line.getString());
      }
      Listing listing =
          AuctionText.read(
              BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),
              stack.getCount(),
              lines,
              i);
      if (listing != null) {
        found.add(listing);
        foundStacks.add(stack);
      } else if (sample.isEmpty()) {
        sample = AuctionText.sample(lines);
      }
    }
    boolean auction =
        controls || AuctionText.plain(screen.getTitle().getString()).contains("auction");
    if (found.isEmpty() && auction) {
      module.pageStatus.show(
          sample.isEmpty()
              ? "No prices on this page, the items carry no text"
              : "Could not find a price in: " + sample);
    }
    active = !found.isEmpty() && auction;
    if (!active) {
      return;
    }
    long now = PriceHistory.now();
    report(module.learn.get() && Server.donut(Minecraft.getInstance()) ? learn(now) : -1);
    float dealAt = module.dealPercent.get().floatValue() / 100.0F;
    float snipeAt = module.snipePercent.get().floatValue() / 100.0F;
    float guardAt = module.guardPercent.get().floatValue() / 100.0F;
    double limit = module.guardLimit.get();
    double least = module.snipeProfit.get();
    for (int i = 0; i < found.size(); i++) {
      Listing listing = found.get(i);
      ItemStack stack = foundStacks.get(i);
      Variant variant = Variants.of(stack);
      String key = VariantKey.of(listing.id(), variant);
      Contents.Worth worth = Contents.filled(stack) ? Contents.of(stack) : Contents.Worth.NONE;
      Size size = Size.of(listing.count(), stack.getMaxStackSize());
      double sized =
          worth.any() ? AuctionText.NONE : sizedReference(key, size, stack.getMaxStackSize());
      Quote quote = Prices.quote(stack);
      double reference =
          sized > 0.0 ? sized : quote.known() ? quote.price() : reference(listing.id());
      boolean known = reference > 0.0;
      float edge = known ? (float) ((listing.unit() - reference) / reference) : Float.NaN;
      double profit = known ? (reference - listing.unit()) * listing.count() : 0.0;
      boolean deal = known && edge <= -dealAt;
      boolean snipe = known && edge <= -snipeAt && profit >= least;
      boolean over = known && edge > guardAt || limit > 0.0 && listing.price() > limit;
      String basis =
          sized > 0.0
              ? size.label()
              : quote.source() == PriceBlend.Source.ESTIMATE
                  ? "estimate"
                  : quote.source() == PriceBlend.Source.BASE ? "base" : "";
      Seen seen =
          new Seen(
              listing,
              names.handle(listing.id()),
              names.title(listing.id()),
              reference,
              edge,
              profit,
              deal,
              snipe,
              over,
              worth,
              basis);
      bySlot[listing.slot()] = seen;
      byStack.put(stack, seen);
      if (deal) {
        best.add(seen);
      }
    }
    best.sort(Comparator.comparingDouble((Seen seen) -> seen.profit()).reversed());
    while (best.size() > LISTED) {
      best.remove(best.size() - 1);
    }
    if (module.sniper.get()) {
      alert();
    }
  }

  private static boolean genuine(Listing listing) {
    return listing.count() > 0 && listing.unit() > 0.0 && listing.unit() < MOST_UNIT;
  }

  private void report(int kept) {
    Minecraft mc = Minecraft.getInstance();
    String text;
    if (!module.learn.get()) {
      text = "Learning is off";
    } else if (kept < 0) {
      String address = Server.address(mc);
      text =
          "Not recorded: "
              + (address.isEmpty() ? "this is not a server" : address)
              + " is not "
              + Server.names();
    } else {
      text = "Read " + found.size() + ", recorded " + kept + skipped();
    }
    module.pageStatus.show(text);
  }

  private String skipped() {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < skips.length; i++) {
      if (skips[i] > 0) {
        out.append(out.length() == 0 ? ", skipped: " : ", ")
            .append(SKIP_LABELS[i])
            .append(' ')
            .append(skips[i]);
      }
    }
    return out.toString();
  }

  private int learn(long now) {
    Set<String> ids = new HashSet<>();
    int kept = 0;
    Arrays.fill(skips, 0);
    for (int i = 0; i < found.size(); i++) {
      Listing listing = found.get(i);
      if (!genuine(listing)) {
        skips[0]++;
        continue;
      }
      ItemStack taken = foundStacks.get(i);
      Variant variant = Variants.of(taken);
      String key = VariantKey.of(listing.id(), variant);
      int seller = listing.seller().isEmpty() ? 0 : listing.seller().hashCode() | 1;
      if (variant.plain() && !sanity.accept(key, listing.unit(), seller, trusted(key, now), now)) {
        skips[1]++;
        continue;
      }
      market.describe(key, variant);
      Prices.session().describe(key, variant);
      market.observe(key, listing.unit(), listing.key(), seller, now);
      if (Size.splits(taken.getMaxStackSize())) {
        market.observe(
            Size.of(listing.count(), taken.getMaxStackSize()).key(key),
            listing.unit(),
            listing.key(),
            seller,
            now);
      }
      Prices.session().observe(key, listing.unit(), listing.key(), seller, now);
      ids.add(listing.id());
      kept++;
    }
    Prices.sighted(kept > 0);
    if (!module.history.get()) {
      return kept;
    }
    for (String id : ids) {
      double price = Prices.auction(id);
      if (price > 0.0) {
        history.record(id, PriceHistory.AUCTION, now, (float) price);
      }
      if (market.count(id, now) > 0) {
        history.record(id, PriceHistory.SEEN, now, (float) market.median(id, now));
      }
    }
    return kept;
  }

  private double trusted(String key, long now) {
    if (market.count(key, now) >= Sanity.REFERENCE_SIGHTINGS) {
      return market.median(key, now);
    }
    Quote quote = Prices.quote(key);
    return quote.known() && quote.source() == PriceBlend.Source.COMMUNITY
        ? quote.price()
        : AuctionText.NONE;
  }

  private double sizedReference(String key, Size size, int maxStack) {
    if (!Size.splits(maxStack)) {
      return AuctionText.NONE;
    }
    long now = PriceHistory.now();
    String sized = size.key(key);
    return market.count(sized, now) >= Size.MIN_SAMPLE
        ? market.median(sized, now)
        : AuctionText.NONE;
  }

  private double reference(String id) {
    Quote quote = Prices.quote(id);
    return quote.known() ? quote.price() : AuctionText.NONE;
  }

  private void alert() {
    boolean chime = false;
    for (Seen seen : best) {
      if (!seen.snipe() || !alerted.add(seen.listing().key())) {
        continue;
      }
      chime = true;
      if (Hud.module() != null) {
        String body =
            "$" + Money.format(seen.listing().price()) + " · save $" + Money.format(seen.profit());
        Hud.module()
            .toasts
            .stack
            .push(
                new Friends.Toast(
                    "",
                    seen.title(),
                    body,
                    Friends.NO_ENTITY,
                    percent(seen.edge()),
                    seen.handle()));
      }
    }
    if (chime && module.snipeSound.get()) {
      Minecraft.getInstance()
          .getSoundManager()
          .play(
              SimpleSoundInstance.forUI(
                  SoundEvents.AMETHYST_BLOCK_CHIME, CHIME_PITCH, CHIME_VOLUME));
    }
  }

  private static String percent(float edge) {
    return String.format(Locale.ROOT, "%+.0f%%", edge * 100.0F);
  }

  boolean blocks(Slot slot, boolean override) {
    if (!active || !module.guard.get() || override || slot == null || slot.index >= SLOTS) {
      return false;
    }
    Seen seen = bySlot[slot.index];
    if (seen == null || !seen.over()) {
      return false;
    }
    long now = System.nanoTime();
    blockedSlot = slot.index;
    blockedAt = now;
    if (now - toastAt > BLOCK_TOAST_NANOS && Hud.module() != null) {
      toastAt = now;
      String reason =
          Float.isNaN(seen.edge()) || seen.edge() <= 0.0F
              ? "Over your price limit"
              : percent(seen.edge()) + " over market";
      Hud.module()
          .toasts
          .stack
          .push(
              new Friends.Toast(
                  "",
                  seen.title(),
                  reason + " · Shift-click to buy",
                  Friends.NO_ENTITY,
                  "BLOCKED",
                  seen.handle()));
    }
    return true;
  }

  boolean tint(GuiGraphicsExtractor graphics, Slot slot) {
    if (!active || slot.index >= SLOTS) {
      return false;
    }
    Seen seen = bySlot[slot.index];
    if (seen == null) {
      return true;
    }
    boolean flashing =
        slot.index == blockedSlot && System.nanoTime() - blockedAt < BLOCK_FLASH_NANOS;
    if (flashing) {
      SlotPaint.draw(graphics, slot, BAD, 0.55F, 1.0F, 1.5F);
    } else if (!module.deals.get()) {
      return true;
    } else if (seen.snipe()) {
      float wave = 0.5F + 0.5F * (float) Math.sin(pulse / PULSE_SECONDS * Math.PI * 2.0);
      SlotPaint.draw(graphics, slot, BEST, 0.3F + 0.25F * wave, 1.0F, 1.5F);
    } else if (seen.deal()) {
      SlotPaint.draw(graphics, slot, GOOD, 0.26F, 0.8F, 1.0F);
    } else if (seen.over()) {
      SlotPaint.draw(graphics, slot, BAD, 0.2F, 0.7F, 1.0F);
    }
    return true;
  }

  void describe(ItemStack stack, Consumer<Component> out) {
    Seen seen = byStack.get(stack);
    if (seen == null || !module.unitPrices.get()) {
      return;
    }
    Listing listing = seen.listing();
    if (listing.count() > 1) {
      out.accept(
          Component.literal("Each: ")
              .withStyle(ChatFormatting.GRAY)
              .append(
                  Component.literal("$" + Money.format(listing.unit()))
                      .withStyle(ChatFormatting.WHITE)));
    }
    Contents.Worth worth = seen.worth();
    if (worth.any()) {
      String note =
          worth.unpriced() > 0
              ? " · " + worth.unpriced() + " of " + worth.stacks() + " unpriced"
              : "";
      out.accept(
          Component.literal("Contents: ")
              .withStyle(ChatFormatting.GRAY)
              .append(
                  Component.literal("$" + Money.format(worth.value()))
                      .withStyle(ChatFormatting.WHITE))
              .append(Component.literal(note).withStyle(ChatFormatting.DARK_GRAY)));
    }
    if (!Float.isNaN(seen.edge())) {
      ChatFormatting tone =
          seen.edge() < -0.005F
              ? ChatFormatting.GREEN
              : seen.edge() > 0.005F ? ChatFormatting.RED : ChatFormatting.GRAY;
      String basis = seen.basis().isEmpty() ? "" : " (" + seen.basis() + ")";
      out.accept(
          Component.literal(worth.any() ? "Worth: " : "Market" + basis + ": ")
              .withStyle(ChatFormatting.GRAY)
              .append(
                  Component.literal("$" + Money.format(seen.reference()))
                      .withStyle(ChatFormatting.WHITE))
              .append(Component.literal("  " + percent(seen.edge())).withStyle(tone)));
    }
    if (seen.deal()) {
      out.accept(
          Component.literal("Deal · save $" + Money.format(seen.profit()))
              .withStyle(ChatFormatting.GREEN));
    } else if (seen.over() && module.guard.get()) {
      out.accept(
          Component.literal("Overpriced · Shift-click to buy").withStyle(ChatFormatting.RED));
    }
  }

  void panel(Canvas c, Theme t, float screenWidth, float boxLeft, float boxTop, float boxWidth) {
    if (!active || best.isEmpty() || !module.dealList.get()) {
      return;
    }
    float height = PANEL_HEAD + best.size() * PANEL_ROW + PANEL_FOOT;
    float x = boxLeft - EDGE - PANEL_WIDTH;
    if (x < EDGE) {
      x = boxLeft + boxWidth + EDGE;
      if (x + PANEL_WIDTH > screenWidth - EDGE) {
        return;
      }
    }
    float y = Math.max(EDGE, boxTop);
    float radius = Math.min(t.radius(), 16.0F);
    c.shadow(x, y, PANEL_WIDTH, height, radius, 20.0F, 7.0F, t.cardShadow());
    if (t.glass()) {
      c.glass(x, y, PANEL_WIDTH, height, radius, t.surface());
    } else {
      c.roundRect(x, y, PANEL_WIDTH, height, radius, t.surface());
    }
    c.rim(
        x,
        y,
        PANEL_WIDTH,
        height,
        radius,
        1.0F,
        Math.min(height, radius * 1.4F),
        Depth.rimColor(t));
    c.text(FontStyle.CAPTION, "DEALS", x + PANEL_PAD, y + 9.0F, t.grey());
    String count = Integer.toString(best.size());
    c.text(
        FontStyle.MONO,
        count,
        x + PANEL_WIDTH - PANEL_PAD - c.textWidth(FontStyle.MONO, count),
        y + 9.0F,
        t.ink2());
    c.roundRect(
        x + PANEL_PAD, y + PANEL_HEAD - 3.0F, PANEL_WIDTH - PANEL_PAD * 2.0F, 1.0F, 0.0F, t.hair());
    for (int i = 0; i < best.size(); i++) {
      Seen seen = best.get(i);
      float ry = y + PANEL_HEAD + i * PANEL_ROW;
      if (seen.snipe()) {
        c.roundRect(
            x + 5.0F,
            ry + 2.0F,
            PANEL_WIDTH - 10.0F,
            PANEL_ROW - 4.0F,
            9.0F,
            Theme.withAlpha(GOOD, 0.12F));
      }
      c.stack(seen.handle(), x + PANEL_PAD, ry + (PANEL_ROW - PANEL_ICON) * 0.5F, PANEL_ICON);
      String edge = percent(seen.edge());
      float edgeWidth = c.textWidth(FontStyle.CAPTION, edge);
      String price = "$" + Money.format(seen.listing().price());
      float priceWidth = c.textWidth(FontStyle.LABEL, price);
      float right = x + PANEL_WIDTH - PANEL_PAD;
      float lineY = ry + (PANEL_ROW - c.lineHeight(FontStyle.LABEL)) * 0.5F;
      c.text(
          FontStyle.CAPTION,
          edge,
          right - edgeWidth,
          ry + (PANEL_ROW - c.lineHeight(FontStyle.CAPTION)) * 0.5F,
          GOOD);
      c.text(FontStyle.LABEL, price, right - edgeWidth - 8.0F - priceWidth, lineY, t.ink2());
      float nameX = x + PANEL_PAD + PANEL_ICON + 8.0F;
      float room = right - edgeWidth - 8.0F - priceWidth - 8.0F - nameX;
      c.pushClip(nameX, ry, Math.max(0.0F, room), PANEL_ROW);
      c.text(FontStyle.LABEL, seen.title(), nameX, lineY, t.ink());
      c.popClip();
    }
  }

  float flip(String id) {
    long now = PriceHistory.now();
    double buy = buy(id, now);
    double target = target(id, now);
    double gain = buy > 0.0 ? (target - buy) / buy : Double.NaN;
    return gain > 0.0 && gain <= MOST_FLIP && target < MOST_UNIT ? (float) gain : Float.NaN;
  }

  String flipText(String id) {
    long now = PriceHistory.now();
    return "$" + Money.format(buy(id, now)) + " → $" + Money.format(target(id, now));
  }

  private double buy(String id, long now) {
    double low = market.low(id, now);
    return low > 0.0 ? low : Prices.auction(id);
  }

  private double target(String id, long now) {
    double resale = market.count(id, now) > 0 ? market.median(id, now) : AuctionText.NONE;
    return Math.max(resale, Prices.auction(id));
  }
}
