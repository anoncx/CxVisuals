package me.cxiy.cxvisuals.modules.chat;

import java.util.Locale;

public final class Echoes {
  private static final long CHAT_NANOS = 4_000_000_000L;
  private static final long COMMAND_NANOS = 1_500_000_000L;

  private String body = "";
  private long chatAt;
  private boolean chatArmed;
  private long commandAt;
  private boolean commandArmed;

  public void chat(String message, long nanos) {
    body = message == null ? "" : message.strip().toLowerCase(Locale.ROOT);
    chatAt = nanos;
    chatArmed = !body.isEmpty();
  }

  public void command(String line, long nanos) {
    commandAt = nanos;
    commandArmed = true;
  }

  public boolean echoes(String plain, long nanos) {
    return chatArmed
        && nanos - chatAt <= CHAT_NANOS
        && plain.toLowerCase(Locale.ROOT).contains(body);
  }

  public boolean feedback(long nanos) {
    return commandArmed && nanos - commandAt <= COMMAND_NANOS;
  }
}
