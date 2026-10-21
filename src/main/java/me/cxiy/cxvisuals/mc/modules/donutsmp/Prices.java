package me.cxiy.cxvisuals.mc.modules.donutsmp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import me.cxiy.cxvisuals.modules.donutsmp.PriceHistory;
import me.cxiy.cxvisuals.modules.donutsmp.auction.CrowdFeed;
import me.cxiy.cxvisuals.modules.donutsmp.auction.Market;
import me.cxiy.cxvisuals.modules.donutsmp.auction.PriceBlend;
import me.cxiy.cxvisuals.modules.donutsmp.auction.PriceBlend.Quote;
import me.cxiy.cxvisuals.modules.donutsmp.auction.Variant;
import me.cxiy.cxvisuals.modules.donutsmp.auction.VariantKey;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class Prices {
  static final double MISSING = -1.0;

  private static final long CACHE_SECONDS = 20L;
  private static final long MILLIS = 1000L;
  private static final double MOST_UNIT = 2.0e10;
  private static final Map<String, CrowdFeed.Price> COMMUNITY = new ConcurrentHashMap<>(1024);
  private static final Map<String, Quote> CACHE = new HashMap<>(256);
  private static final Market SESSION = new Market();

  private static volatile Market market = new Market();
  private static volatile int communityRevision;
  private static volatile long sighted;
  private static volatile long communityAt;
  private static int cachedRevision = -1;
  private static long cachedAt;

  private Prices() {}

  static void bind(Market book) {
    market = book;
    CACHE.clear();
  }

  static void sighted(boolean fresh) {
    if (fresh) {
      sighted = System.nanoTime();
    }
  }

  static long sighted() {
    return sighted;
  }

  static Market session() {
    return SESSION;
  }

  static int revision() {
    return market.revision() * 31 + communityRevision;
  }

  static long updated() {
    return Math.max(communityAt, market.latest());
  }

  static int community() {
    return COMMUNITY.size();
  }

  static void community(CrowdFeed.Answer answer, PriceHistory history) {
    long now = PriceHistory.now();
    for (CrowdFeed.Price price : answer.prices()) {
      if (price.price() < MOST_UNIT) {
        COMMUNITY.put(price.key(), price);
      }
      if (history != null && price.key().equals(price.id())) {
        history.record(price.id(), PriceHistory.COMMUNITY, now, (float) price.price());
      }
    }
    communityAt = now;
    communityRevision++;
  }

  static double auction(Item item) {
    return auction(BuiltInRegistries.ITEM.getKey(item).toString());
  }

  static double auction(String id) {
    return quote(id).price();
  }

  static double auction(ItemStack stack) {
    return quote(stack).price();
  }

  static Quote quote(String id) {
    long now = PriceHistory.now();
    int revision = revision();
    if (revision != cachedRevision || now - cachedAt > CACHE_SECONDS) {
      CACHE.clear();
      cachedRevision = revision;
      cachedAt = now;
    }
    Quote known = CACHE.get(id);
    if (known != null) {
      return known;
    }
    Quote quote = compute(id, now);
    CACHE.put(id, quote);
    return quote;
  }

  static Quote quote(ItemStack stack) {
    return quote(stack, 3);
  }

  static Quote quote(ItemStack stack, int depth) {
    if (stack == null || stack.isEmpty()) {
      return Quote.NONE;
    }
    String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    Variant variant = Variants.of(stack);
    String key = VariantKey.of(id, variant);
    Quote direct = quote(key);
    Quote base = quote(id);
    double basePrice = base.known() ? base.price() : MISSING;
    double intrinsic = MISSING;
    if (depth > 0 && !variant.contents().isEmpty()) {
      Contents.Worth worth = Contents.of(stack, depth);
      if (worth.reliable()) {
        intrinsic = worth.value() + Math.max(0.0, basePrice);
      }
    }
    if (direct.known() && direct.source() != PriceBlend.Source.BASE) {
      if (intrinsic > 0.0
          && (direct.price() < intrinsic * 0.5 || direct.price() > intrinsic * 3.0)) {
        return new Quote(intrinsic, PriceBlend.Source.ESTIMATE, 0L);
      }
      return direct;
    }
    if (intrinsic > 0.0) {
      return new Quote(intrinsic, PriceBlend.Source.ESTIMATE, 0L);
    }
    if (basePrice > 0.0
        && !variant.enchants().isEmpty()
        && !"minecraft:enchanted_book".equals(id)) {
      double books = 0.0;
      int known = 0;
      for (Variant.Enchant enchant : variant.enchants()) {
        Variant one = new Variant(List.of(enchant), "", List.of(), "", 0, "", List.of());
        Quote book = quote(VariantKey.of("minecraft:enchanted_book", one));
        if (book.known() && book.source() != PriceBlend.Source.BASE) {
          books += book.price();
          known++;
        }
      }
      if (known > 0) {
        return new Quote(basePrice + books, PriceBlend.Source.ESTIMATE, 0L);
      }
    }
    if (basePrice > 0.0) {
      return new Quote(basePrice, PriceBlend.Source.BASE, 0L);
    }
    return direct.known() ? direct : Quote.NONE;
  }

  private static Quote compute(String id, long now) {
    Market book = market;
    int seen = book.count(id, now);
    double median = seen > 0 ? book.median(id, now) : MISSING;
    long age = seen > 0 ? Math.max(0L, now - book.newest(id, now)) : 0L;
    CrowdFeed.Price crowd = COMMUNITY.get(id);
    boolean estimated = crowd != null && !"market".equals(crowd.basis());
    Quote quote =
        PriceBlend.blend(
            seen,
            median,
            age,
            crowd == null ? MISSING : crowd.price(),
            crowd == null ? 0 : crowd.reporters(),
            crowd == null ? 0L : Math.max(0L, now - crowd.at() / MILLIS),
            estimated);
    if (quote.known()
        && quote.source() == PriceBlend.Source.ESTIMATE
        && crowd != null
        && "base".equals(crowd.basis())) {
      return new Quote(quote.price(), PriceBlend.Source.BASE, quote.ageSeconds());
    }
    return quote;
  }
}
