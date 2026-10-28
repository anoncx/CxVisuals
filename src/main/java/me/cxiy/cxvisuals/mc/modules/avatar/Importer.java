package me.cxiy.cxvisuals.mc.modules.avatar;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.mc.compat.FileDialog;
import me.cxiy.cxvisuals.modules.avatar.format.ModelFormats;

final class Importer {
  private static final String TITLE = Lang.key("Import avatar");
  private static final String DESCRIPTION =
      Lang.key("3D models (GLB, glTF, OBJ, STL, PLY, Blockbench, VOX)");
  private static final AtomicBoolean OPEN = new AtomicBoolean();

  private Importer() {}

  static void open() {
    if (!OPEN.compareAndSet(false, true)) {
      return;
    }
    try {
      FileDialog.open(
          Lang.tr(TITLE),
          Lang.tr(DESCRIPTION),
          ModelFormats.EXTENSIONS,
          home(),
          files -> {
            OPEN.set(false);
            if (!files.isEmpty()) {
              Avatars.importFiles(files);
            }
          });
    } catch (RuntimeException | LinkageError e) {
      OPEN.set(false);
      CxVisualsClient.LOG.error(
          "The file dialog could not open, drop model files onto the window instead", e);
    }
  }

  private static Path home() {
    Path user = Path.of(System.getProperty("user.home", "."));
    Path downloads = user.resolve("Downloads");
    return Files.isDirectory(downloads) ? downloads : user;
  }
}
