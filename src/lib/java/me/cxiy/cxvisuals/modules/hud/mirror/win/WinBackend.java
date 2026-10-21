package me.cxiy.cxvisuals.modules.hud.mirror.win;

import java.util.List;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.modules.hud.mirror.FrameStore;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorBackend;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorCapture;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorWindow;

public final class WinBackend implements MirrorBackend {
  private final Consumer<String> log;

  public WinBackend(Consumer<String> log) {
    this.log = log;
  }

  @Override
  public List<MirrorWindow> windows() {
    return Win32.windows();
  }

  @Override
  public MirrorCapture open(MirrorWindow window, FrameStore store) {
    return new WinCapture(window.handle(), store, log);
  }
}
