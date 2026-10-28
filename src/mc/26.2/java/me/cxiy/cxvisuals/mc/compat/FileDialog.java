package me.cxiy.cxvisuals.mc.compat;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

public final class FileDialog {
  private static final boolean MAC =
      System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

  private FileDialog() {}

  public static void open(
      String title,
      String description,
      List<String> extensions,
      Path start,
      Consumer<List<Path>> done) {
    if (MAC) {
      ask(title, description, extensions, start, done);
      return;
    }
    Thread thread =
        new Thread(() -> ask(title, description, extensions, start, done), "CxVisuals File Dialog");
    thread.setDaemon(true);
    thread.start();
  }

  private static void ask(
      String title,
      String description,
      List<String> extensions,
      Path start,
      Consumer<List<Path>> done) {
    String chosen;
    try (MemoryStack stack = MemoryStack.stackPush()) {
      PointerBuffer patterns = stack.mallocPointer(extensions.size());
      for (String extension : extensions) {
        patterns.put(stack.UTF8("*." + extension));
      }
      patterns.flip();
      String folder = start.toString();
      chosen =
          TinyFileDialogs.tinyfd_openFileDialog(
              title,
              folder.endsWith(File.separator) ? folder : folder + File.separator,
              patterns,
              description,
              true);
    }
    List<Path> files = new ArrayList<>();
    if (chosen != null) {
      for (String part : chosen.split("\\|")) {
        if (!part.isBlank()) {
          files.add(Path.of(part));
        }
      }
    }
    Minecraft.getInstance().execute(() -> done.accept(files));
  }
}
