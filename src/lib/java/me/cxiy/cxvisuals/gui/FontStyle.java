package me.cxiy.cxvisuals.gui;

public enum FontStyle {
  TITLE("geist-semibold.ttf", "geist-semibold.ttf", 24),
  ROW("geist-medium.ttf", "geist-semibold.ttf", 14),
  LABEL("geist-medium.ttf", "geist-semibold.ttf", 13),
  CAPTION("geist-medium.ttf", "geist-semibold.ttf", 12),
  SMALL("geist-regular.ttf", "geist-semibold.ttf", 12),
  MONO("geistmono-regular.ttf", "geistmono-regular.ttf", 12),
  DOTS("doto-black.ttf", "doto-black.ttf", 22),
  DISPLAY("geist-semibold.ttf", "geist-semibold.ttf", 30),
  HERO("doto-black.ttf", "doto-black.ttf", 52);

  public final String file;
  public final String bold;
  public final int size;

  FontStyle(String file, String bold, int size) {
    this.file = file;
    this.bold = bold;
    this.size = size;
  }
}
