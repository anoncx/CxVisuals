package me.cxiy.cxvisuals.modules.hud.mirror;

import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.modules.hud.mirror.portal.PortalBackend;
import me.cxiy.cxvisuals.modules.hud.mirror.win.WinBackend;
import me.cxiy.cxvisuals.modules.hud.mirror.x11.X11Backend;

public final class MirrorBackends {
  private static final String WAYLAND = "wayland";

  private MirrorBackends() {}

  public static MirrorBackend platform(Path folder, Consumer<String> log) {
    String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
    if (os.contains("win")) {
      return new WinBackend(log);
    }
    if (os.contains("nux") || os.contains("nix") || os.contains("bsd")) {
      return wayland() ? new PortalBackend(folder, log) : new X11Backend(log);
    }
    return null;
  }

  private static boolean wayland() {
    String session = System.getenv("XDG_SESSION_TYPE");
    if (session != null) {
      return session.equalsIgnoreCase(WAYLAND);
    }
    return System.getenv("WAYLAND_DISPLAY") != null && System.getenv("DISPLAY") == null;
  }
}
