package me.cxiy.cxvisuals.modules.hud.mirror.portal;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.modules.hud.media.MediaScripts;
import me.cxiy.cxvisuals.modules.hud.mirror.FrameStore;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorBackend;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorCapture;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorWindow;

public final class PortalBackend implements MirrorBackend {
  private static final String SCRIPT = "cxvisuals-mirror-portal.py";
  private static final String TOKEN = "portal.token";
  private static final String FRESH = "fresh";

  private final Path folder;
  private final Consumer<String> log;

  public PortalBackend(Path folder, Consumer<String> log) {
    this.folder = folder;
    this.log = log;
  }

  @Override
  public List<MirrorWindow> windows() {
    return List.of();
  }

  @Override
  public MirrorCapture open(MirrorWindow window, FrameStore store) {
    return null;
  }

  @Override
  public boolean chooser() {
    return true;
  }

  @Override
  public MirrorCapture choose(FrameStore store, boolean fresh) {
    try {
      Path script = MediaScripts.materialize(folder, SCRIPT, PortalScripts.CAST);
      List<String> command = new ArrayList<>(4);
      command.add("python3");
      command.add(script.toString());
      command.add(folder.resolve(TOKEN).toString());
      if (fresh) {
        command.add(FRESH);
      }
      return new PortalCapture(command, store, log);
    } catch (IOException e) {
      log.accept("Window capture unavailable: " + e.getMessage());
      return null;
    }
  }
}
