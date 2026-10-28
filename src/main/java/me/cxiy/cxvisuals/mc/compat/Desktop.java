package me.cxiy.cxvisuals.mc.compat;

import com.mojang.blaze3d.Blaze3D;
import java.net.URI;
import java.nio.file.Path;

public final class Desktop {
  private Desktop() {}

  public static void openUri(String uri) {
    Blaze3D.openUri(URI.create(uri));
  }

  public static void openPath(Path path) {
    Blaze3D.openPath(path);
  }
}
