package me.cxiy.cxvisuals.modules.accounts;

import java.util.function.Consumer;

public interface Platform {
  enum Rejoin {
    NO,
    YES
  }

  String activeUuid();

  String activeName();

  boolean inGame();

  String server();

  void main(Runnable task);

  void open(String url);

  void switchTo(Hub.Ready ready, Rejoin rejoin, Consumer<String> done);
}
