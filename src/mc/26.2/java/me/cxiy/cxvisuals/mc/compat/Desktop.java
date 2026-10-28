package me.cxiy.cxvisuals.mc.compat;

import java.nio.file.Path;
import net.minecraft.util.Util;

public final class Desktop {
  private Desktop() {}

  public static void openUri(String uri) {
    Util.getPlatform().openUri(uri);
  }

  public static void openPath(Path path) {
    Util.getPlatform().openFile(path.toFile());
  }
}
