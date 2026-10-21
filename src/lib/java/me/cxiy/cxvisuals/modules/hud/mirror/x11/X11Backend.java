package me.cxiy.cxvisuals.modules.hud.mirror.x11;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import me.cxiy.cxvisuals.modules.hud.mirror.FrameStore;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorBackend;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorCapture;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorWindow;

public final class X11Backend implements MirrorBackend {
  private static final int LIST_WORDS = 4096;
  private static final int TITLE_WORDS = 256;
  private static final int STATE_WORDS = 64;

  private final Consumer<String> log;

  public X11Backend(Consumer<String> log) {
    this.log = log;
  }

  @Override
  public List<MirrorWindow> windows() {
    List<MirrorWindow> found = new ArrayList<>();
    MemorySegment connection = Xcb.connect();
    try (Arena arena = Arena.ofConfined()) {
      int root = Xcb.root(connection, arena);
      int clients = Xcb.atom(connection, arena, "_NET_CLIENT_LIST");
      int name = Xcb.atom(connection, arena, "_NET_WM_NAME");
      int utf8 = Xcb.atom(connection, arena, "UTF8_STRING");
      int pid = Xcb.atom(connection, arena, "_NET_WM_PID");
      int state = Xcb.atom(connection, arena, "_NET_WM_STATE");
      int hidden = Xcb.atom(connection, arena, "_NET_WM_STATE_HIDDEN");
      long own = ProcessHandle.current().pid();
      MemorySegment list =
          Xcb.property(connection, arena, root, clients, Xcb.ATOM_WINDOW, LIST_WORDS);
      if (list.address() == 0L) {
        return found;
      }
      try {
        int count = Xcb.length(list) / 4;
        MemorySegment ids = Xcb.value(list).reinterpret(count * 4L);
        for (int i = count - 1; i >= 0; i--) {
          int window = ids.get(JAVA_INT, i * 4L);
          int owner = cardinal(connection, arena, window, pid);
          if (owner == own) {
            continue;
          }
          String title = text(connection, arena, window, name, utf8);
          if (title.isEmpty()) {
            title = text(connection, arena, window, Xcb.ATOM_WM_NAME, Xcb.ATOM_STRING);
          }
          if (title.isEmpty()) {
            continue;
          }
          found.add(
              new MirrorWindow(
                  application(owner),
                  title,
                  window & 0xFFFFFFFFL,
                  hidden(connection, arena, window, state, hidden)));
        }
      } finally {
        Xcb.free(list);
      }
    } finally {
      Xcb.disconnect(connection);
    }
    return found;
  }

  @Override
  public MirrorCapture open(MirrorWindow window, FrameStore store) {
    return new X11Capture((int) window.handle(), store, log);
  }

  private static int cardinal(
      MemorySegment connection, Arena arena, int window, int property) {
    MemorySegment reply = Xcb.property(connection, arena, window, property, Xcb.ATOM_CARDINAL, 1);
    if (reply.address() == 0L) {
      return -1;
    }
    try {
      return Xcb.length(reply) >= 4 ? Xcb.value(reply).reinterpret(4).get(JAVA_INT, 0) : -1;
    } finally {
      Xcb.free(reply);
    }
  }

  private static String text(
      MemorySegment connection, Arena arena, int window, int property, int type) {
    MemorySegment reply = Xcb.property(connection, arena, window, property, type, TITLE_WORDS);
    if (reply.address() == 0L) {
      return "";
    }
    try {
      int length = Xcb.length(reply);
      if (length <= 0) {
        return "";
      }
      byte[] bytes = Xcb.value(reply).reinterpret(length).toArray(JAVA_BYTE);
      return new String(bytes, StandardCharsets.UTF_8).strip();
    } finally {
      Xcb.free(reply);
    }
  }

  private static boolean hidden(
      MemorySegment connection, Arena arena, int window, int property, int target) {
    MemorySegment reply =
        Xcb.property(connection, arena, window, property, Xcb.ATOM_ATOM, STATE_WORDS);
    if (reply.address() == 0L) {
      return false;
    }
    try {
      int count = Xcb.length(reply) / 4;
      MemorySegment atoms = Xcb.value(reply).reinterpret(count * 4L);
      for (int i = 0; i < count; i++) {
        if (atoms.get(JAVA_INT, i * 4L) == target) {
          return true;
        }
      }
      return false;
    } finally {
      Xcb.free(reply);
    }
  }

  private static String application(int pid) {
    if (pid <= 0) {
      return "";
    }
    try {
      return Files.readString(Path.of("/proc", Integer.toString(pid), "comm")).strip();
    } catch (IOException | RuntimeException e) {
      return "";
    }
  }
}
