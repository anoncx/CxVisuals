package me.cxiy.cxvisuals.menu.model;

import me.cxiy.cxvisuals.gui.StyledText;

public final class Item {
  public static final int NO_SIGNAL = -2;
  public static final int BUSY = -1;
  public static final int BARS = 5;

  public String title = "";
  public StyledText first = StyledText.EMPTY;
  public StyledText second = StyledText.EMPTY;
  public String trailing = "";
  public String note = "";
  public byte[] icon;
  public Object stamp;
  public int signal = NO_SIGNAL;
  public boolean header;
  public boolean flagged;
}
