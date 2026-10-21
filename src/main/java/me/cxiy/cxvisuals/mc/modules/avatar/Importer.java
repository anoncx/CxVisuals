package me.cxiy.cxvisuals.mc.modules.avatar;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import me.cxiy.cxvisuals.core.i18n.Lang;
import me.cxiy.cxvisuals.mc.CxVisualsClient;
import me.cxiy.cxvisuals.modules.avatar.format.ModelFormats;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

final class Importer {
  private static final String TITLE = Lang.key("Import avatar");
  private static final String DESCRIPTION =
      Lang.key("3D models (GLB, glTF, OBJ, STL, PLY, Blockbench, VOX)");
  private static final AtomicBoolean OPEN = new AtomicBoolean();
  private static final boolean MAC =
      System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

  private Importer() {}

  static void open() {
    if (!OPEN.compareAndSet(false, true)) {
      return;
    }
    if (MAC) {
      ask();
      return;
    }
    Thread thread = new Thread(Importer::ask, "CxVisuals File Dialog");
    thread.setDaemon(true);
    thread.start();
  }

  private static void ask() {
    try {
      String chosen;
      try (MemoryStack stack = MemoryStack.stackPush()) {
        PointerBuffer patterns = stack.mallocPointer(ModelFormats.EXTENSIONS.size());
        for (String extension : ModelFormats.EXTENSIONS) {
          patterns.put(stack.UTF8("*." + extension));
        }
        patterns.flip();
        chosen =
            TinyFileDialogs.tinyfd_openFileDialog(
                Lang.tr(TITLE), home(), patterns, Lang.tr(DESCRIPTION), true);
      }
      if (chosen == null || chosen.isBlank()) {
        return;
      }
      List<Path> files = new ArrayList<>();
      for (String part : chosen.split("\\|")) {
        if (!part.isBlank()) {
          files.add(Path.of(part));
        }
      }
      Minecraft.getInstance().execute(() -> Avatars.importFiles(files));
    } catch (RuntimeException | LinkageError e) {
      CxVisualsClient.LOG.error(
          "The file dialog could not open, drop model files onto the window instead", e);
    } finally {
      OPEN.set(false);
    }
  }

  private static String home() {
    String user = System.getProperty("user.home", ".");
    Path downloads = Path.of(user, "Downloads");
    String folder = Files.isDirectory(downloads) ? downloads.toString() : user;
    return folder.endsWith(File.separator) ? folder : folder + File.separator;
  }
}
