package me.cxiy.cxvisuals.mc.compat;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import org.lwjgl.sdl.SDLDialog;
import org.lwjgl.sdl.SDLProperties;
import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

public final class FileDialog {
  private FileDialog() {}

  public static void open(
      String title,
      String description,
      List<String> extensions,
      Path start,
      Consumer<List<Path>> done) {
    ByteBuffer label = MemoryUtil.memUTF8(description);
    ByteBuffer pattern = MemoryUtil.memUTF8(String.join(";", extensions));
    SDL_DialogFileFilter.Buffer filters = SDL_DialogFileFilter.malloc(1);
    filters.get(0).set(label, pattern);
    SDL_DialogFileCallback[] callback = new SDL_DialogFileCallback[1];
    callback[0] =
        SDL_DialogFileCallback.create(
            (userdata, fileList, filter) -> {
              List<Path> chosen = read(fileList);
              MemoryUtil.memFree(label);
              MemoryUtil.memFree(pattern);
              filters.free();
              callback[0].free();
              Minecraft.getInstance().execute(() -> done.accept(chosen));
            });
    int props = SDLProperties.SDL_CreateProperties();
    try {
      SDLProperties.SDL_SetPointerProperty(
          props,
          SDLDialog.SDL_PROP_FILE_DIALOG_WINDOW_POINTER,
          Minecraft.getInstance().getWindow().handle());
      SDLProperties.SDL_SetPointerProperty(
          props, SDLDialog.SDL_PROP_FILE_DIALOG_FILTERS_POINTER, filters.address());
      SDLProperties.SDL_SetNumberProperty(
          props, SDLDialog.SDL_PROP_FILE_DIALOG_NFILTERS_NUMBER, 1L);
      SDLProperties.SDL_SetStringProperty(
          props, SDLDialog.SDL_PROP_FILE_DIALOG_TITLE_STRING, title);
      SDLProperties.SDL_SetStringProperty(
          props, SDLDialog.SDL_PROP_FILE_DIALOG_LOCATION_STRING, start.toString());
      SDLProperties.SDL_SetBooleanProperty(
          props, SDLDialog.SDL_PROP_FILE_DIALOG_MANY_BOOLEAN, true);
      SDLDialog.SDL_ShowFileDialogWithProperties(
          SDLDialog.SDL_FILEDIALOG_OPENFILE, callback[0], 0L, props);
    } finally {
      SDLProperties.SDL_DestroyProperties(props);
    }
  }

  private static List<Path> read(long fileList) {
    List<Path> files = new ArrayList<>();
    if (fileList == 0L) {
      return files;
    }
    for (int i = 0; ; i++) {
      long entry = MemoryUtil.memGetAddress(fileList + (long) i * Long.BYTES);
      if (entry == 0L) {
        break;
      }
      files.add(Path.of(MemoryUtil.memUTF8(entry)));
    }
    return files;
  }
}
