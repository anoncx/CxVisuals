package me.cxiy.cxvisuals.modules.donutsmp.auction;

public final class PriceBlend {
  public enum Source {
    LOCAL,
    COMMUNITY,
    ESTIMATE,
    BASE,
    NONE
  }

  public record Quote(double price, Source source, long ageSeconds) {
    public static final Quote NONE = new Quote(AuctionText.NONE, Source.NONE, 0L);

    public boolean known() {
      return source != Source.NONE;
    }
  }

  public static final long LOCAL_FRESH = 3L * 3600L;
  public static final long LOCAL_STALE = 48L * 3600L;
  public static final long COMMUNITY_STALE = 24L * 3600L;

  private PriceBlend() {}

  public static Quote blend(
      int seen, double seenPrice, long seenAge, double crowdPrice, int reporters, long crowdAge) {
    return blend(seen, seenPrice, seenAge, crowdPrice, reporters, crowdAge, false);
  }

  public static Quote blend(
      int seen,
      double seenPrice,
      long seenAge,
      double crowdPrice,
      int reporters,
      long crowdAge,
      boolean estimated) {
    boolean local = seen > 0 && seenPrice > 0.0 && seenAge <= LOCAL_STALE;
    boolean crowd = crowdPrice > 0.0 && crowdAge <= COMMUNITY_STALE && (reporters > 0 || estimated);
    if (local && seenAge <= LOCAL_FRESH) {
      return new Quote(seenPrice, Source.LOCAL, seenAge);
    }
    if (crowd) {
      return new Quote(crowdPrice, reporters > 0 ? Source.COMMUNITY : Source.ESTIMATE, crowdAge);
    }
    if (local) {
      return new Quote(seenPrice, Source.LOCAL, seenAge);
    }
    return Quote.NONE;
  }
}
