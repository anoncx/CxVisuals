package me.cxiy.cxvisuals.modules.avatar.format;

public final class ImportError extends Exception {
  public ImportError(String userMessage) {
    super(userMessage, null, false, false);
  }
}
