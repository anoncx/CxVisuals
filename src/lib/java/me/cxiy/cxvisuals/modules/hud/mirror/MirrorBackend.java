package me.cxiy.cxvisuals.modules.hud.mirror;

import java.util.List;

public interface MirrorBackend {
  List<MirrorWindow> windows();

  MirrorCapture open(MirrorWindow window, FrameStore store);

  default boolean chooser() {
    return false;
  }

  default MirrorCapture choose(FrameStore store, boolean fresh) {
    return null;
  }
}
