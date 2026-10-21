package me.cxiy.cxvisuals.modules.donutsmp.auction;

public record Listing(String id, int count, double price, String seller, int slot) {
  public double unit() {
    return price / Math.max(1, count);
  }

  public int key() {
    return (seller.hashCode() * 31 + Double.hashCode(price)) * 31 + count;
  }
}
