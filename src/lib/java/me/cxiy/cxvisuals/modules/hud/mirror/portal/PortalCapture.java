package me.cxiy.cxvisuals.modules.hud.mirror.portal;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.modules.hud.mirror.FrameStore;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorCapture;

final class PortalCapture implements MirrorCapture {
  private static final int NOTE = 400;

  private final Process process;
  private final Thread reader;
  private volatile boolean ended;

  PortalCapture(List<String> command, FrameStore store, Consumer<String> log) throws IOException {
    this.process = new ProcessBuilder(command).start();
    this.reader =
        new Thread(
            () -> {
              try {
                PortalProtocol.pump(process.getInputStream(), store, log);
              } catch (IOException | RuntimeException e) {
                log.accept("Window capture stopped: " + e.getMessage());
              } finally {
                complain(process.getErrorStream(), log);
                ended = true;
              }
            },
            "CxVisuals window capture");
    reader.setDaemon(true);
    reader.start();
  }

  private static void complain(InputStream errors, Consumer<String> log) {
    try {
      byte[] text = errors.readNBytes(NOTE);
      if (text.length > 0) {
        log.accept("Window capture helper: " + new String(text, StandardCharsets.UTF_8).strip());
      }
    } catch (IOException e) {
      return;
    }
  }

  @Override
  public boolean alive() {
    return !ended && process.isAlive();
  }

  @Override
  public void close() {
    process.destroy();
  }
}
