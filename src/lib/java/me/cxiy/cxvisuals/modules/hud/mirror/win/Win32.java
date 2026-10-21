package me.cxiy.cxvisuals.modules.hud.mirror.win;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorWindow;

final class Win32 {
  private static final Arena GLOBAL = Arena.global();
  private static final SymbolLookup USER32 = SymbolLookup.libraryLookup("user32", GLOBAL);
  private static final SymbolLookup KERNEL32 = SymbolLookup.libraryLookup("kernel32", GLOBAL);
  private static final SymbolLookup DWM = SymbolLookup.libraryLookup("dwmapi", GLOBAL);
  private static final SymbolLookup COMBASE = SymbolLookup.libraryLookup("combase", GLOBAL);
  private static final SymbolLookup D3D11 = SymbolLookup.libraryLookup("d3d11", GLOBAL);

  private static final MethodHandle TOP =
      bind(USER32, "GetTopWindow", FunctionDescriptor.of(ADDRESS, ADDRESS));
  private static final MethodHandle NEXT =
      bind(USER32, "GetWindow", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
  private static final MethodHandle VISIBLE =
      bind(USER32, "IsWindowVisible", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle ICONIC =
      bind(USER32, "IsIconic", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle EXISTS =
      bind(USER32, "IsWindow", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle TEXT =
      bind(USER32, "GetWindowTextW", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT));
  private static final MethodHandle PROCESS_OF =
      bind(
          USER32,
          "GetWindowThreadProcessId",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
  private static final MethodHandle STYLE =
      bind(USER32, "GetWindowLongPtrW", FunctionDescriptor.of(JAVA_LONG, ADDRESS, JAVA_INT));
  private static final MethodHandle RECT =
      bind(USER32, "GetWindowRect", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
  private static final MethodHandle CLOAKED =
      bind(
          DWM,
          "DwmGetWindowAttribute",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, JAVA_INT));
  private static final MethodHandle OPEN_PROCESS =
      bind(KERNEL32, "OpenProcess", FunctionDescriptor.of(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT));
  private static final MethodHandle IMAGE_NAME =
      bind(
          KERNEL32,
          "QueryFullProcessImageNameW",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
  private static final MethodHandle CLOSE =
      bind(KERNEL32, "CloseHandle", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle OWN_PROCESS =
      bind(KERNEL32, "GetCurrentProcessId", FunctionDescriptor.of(JAVA_INT));
  private static final MethodHandle RO_INITIALIZE =
      bind(COMBASE, "RoInitialize", FunctionDescriptor.of(JAVA_INT, JAVA_INT));
  private static final MethodHandle RO_UNINITIALIZE =
      bind(COMBASE, "RoUninitialize", FunctionDescriptor.ofVoid());
  private static final MethodHandle RO_FACTORY =
      bind(
          COMBASE,
          "RoGetActivationFactory",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
  private static final MethodHandle STRING_CREATE =
      bind(
          COMBASE,
          "WindowsCreateString",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
  private static final MethodHandle STRING_DELETE =
      bind(COMBASE, "WindowsDeleteString", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle CREATE_DEVICE =
      bind(
          D3D11,
          "D3D11CreateDevice",
          FunctionDescriptor.of(
              JAVA_INT,
              ADDRESS,
              JAVA_INT,
              ADDRESS,
              JAVA_INT,
              ADDRESS,
              JAVA_INT,
              JAVA_INT,
              ADDRESS,
              ADDRESS,
              ADDRESS));
  private static final MethodHandle WRAP_DEVICE =
      bind(
          D3D11,
          "CreateDirect3D11DeviceFromDXGIDevice",
          FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));

  private static final int NEXT_SIBLING = 2;
  private static final int OWNER = 4;
  private static final int EXTENDED_STYLE = -20;
  private static final long TOOL_WINDOW = 0x80L;
  private static final long APP_WINDOW = 0x40000L;
  private static final int CLOAKED_ATTRIBUTE = 14;
  private static final int QUERY_LIMITED = 0x1000;
  private static final int TITLE_CHARS = 512;
  private static final int PATH_CHARS = 1024;
  private static final int DEVICE_BGRA = 0x20;
  private static final int DRIVER_HARDWARE = 1;
  private static final int SDK_VERSION = 7;
  private static final int MULTITHREADED = 1;
  private static final int MIN_EDGE = 64;

  private Win32() {}

  private static MethodHandle bind(SymbolLookup library, String name, FunctionDescriptor shape) {
    return Com.LINKER.downcallHandle(library.find(name).orElseThrow(), shape);
  }

  static List<MirrorWindow> windows() {
    List<MirrorWindow> found = new ArrayList<>();
    try (Arena arena = Arena.ofConfined()) {
      MemorySegment title = arena.allocate(TITLE_CHARS * 2L);
      MemorySegment pid = arena.allocate(JAVA_INT);
      MemorySegment rect = arena.allocate(16);
      MemorySegment flag = arena.allocate(JAVA_INT);
      MemorySegment path = arena.allocate(PATH_CHARS * 2L);
      MemorySegment size = arena.allocate(JAVA_INT);
      int own = (int) invoke(OWN_PROCESS);
      MemorySegment window = (MemorySegment) invoke(TOP, MemorySegment.NULL);
      for (int guard = 0; window.address() != 0L && guard < 4096; guard++) {
        MemorySegment current = window;
        window = (MemorySegment) invoke(NEXT, current, NEXT_SIBLING);
        if ((int) invoke(VISIBLE, current) == 0) {
          continue;
        }
        long style = (long) invoke(STYLE, current, EXTENDED_STYLE);
        boolean listed = (style & APP_WINDOW) != 0L;
        if ((style & TOOL_WINDOW) != 0L && !listed) {
          continue;
        }
        if (!listed && ((MemorySegment) invoke(NEXT, current, OWNER)).address() != 0L) {
          continue;
        }
        flag.set(JAVA_INT, 0, 0);
        invoke(CLOAKED, current, CLOAKED_ATTRIBUTE, flag, 4);
        if (flag.get(JAVA_INT, 0) != 0) {
          continue;
        }
        int length = (int) invoke(TEXT, current, title, TITLE_CHARS);
        if (length <= 0) {
          continue;
        }
        invoke(PROCESS_OF, current, pid);
        int owner = pid.get(JAVA_INT, 0);
        if (owner == own) {
          continue;
        }
        boolean minimized = (int) invoke(ICONIC, current) != 0;
        invoke(RECT, current, rect);
        int width = rect.get(JAVA_INT, 8) - rect.get(JAVA_INT, 0);
        int height = rect.get(JAVA_INT, 12) - rect.get(JAVA_INT, 4);
        if (!minimized && (width < MIN_EDGE || height < MIN_EDGE)) {
          continue;
        }
        String app = application(owner, path, size);
        String name =
            new String(title.asSlice(0, length * 2L).toArray(JAVA_BYTE), StandardCharsets.UTF_16LE);
        if (app.equals("explorer") && name.equals("Program Manager")) {
          continue;
        }
        found.add(new MirrorWindow(app, name, current.address(), minimized));
      }
    }
    return found;
  }

  private static String application(int pid, MemorySegment path, MemorySegment size) {
    MemorySegment process = (MemorySegment) invoke(OPEN_PROCESS, QUERY_LIMITED, 0, pid);
    if (process.address() == 0L) {
      return "";
    }
    try {
      size.set(JAVA_INT, 0, PATH_CHARS);
      if ((int) invoke(IMAGE_NAME, process, 0, path, size) == 0) {
        return "";
      }
      int chars = size.get(JAVA_INT, 0);
      String full =
          new String(path.asSlice(0, chars * 2L).toArray(JAVA_BYTE), StandardCharsets.UTF_16LE);
      String file = full.substring(Math.max(full.lastIndexOf('\\'), full.lastIndexOf('/')) + 1);
      return file.toLowerCase(Locale.ROOT).endsWith(".exe")
          ? file.substring(0, file.length() - 4)
          : file;
    } finally {
      invoke(CLOSE, process);
    }
  }

  static MemorySegment handle(long address) {
    return MemorySegment.ofAddress(address);
  }

  static boolean exists(MemorySegment window) {
    return (int) invoke(EXISTS, window) != 0;
  }

  static boolean minimized(MemorySegment window) {
    return (int) invoke(ICONIC, window) != 0;
  }

  static int[] extent(MemorySegment window, Arena arena) {
    MemorySegment rect = arena.allocate(16);
    invoke(RECT, window, rect);
    return new int[] {
      rect.get(JAVA_INT, 8) - rect.get(JAVA_INT, 0), rect.get(JAVA_INT, 12) - rect.get(JAVA_INT, 4)
    };
  }

  static void initialize() {
    invoke(RO_INITIALIZE, MULTITHREADED);
  }

  static void uninitialize() {
    invoke(RO_UNINITIALIZE);
  }

  static MemorySegment factory(Arena arena, String name, MemorySegment iid) {
    byte[] text = name.getBytes(StandardCharsets.UTF_16LE);
    MemorySegment chars = arena.allocate(text.length + 2L);
    MemorySegment.copy(text, 0, chars, JAVA_BYTE, 0, text.length);
    MemorySegment string = arena.allocate(ADDRESS);
    Com.check((int) invoke(STRING_CREATE, chars, name.length(), string), "WindowsCreateString");
    try {
      MemorySegment out = arena.allocate(ADDRESS);
      Com.check((int) invoke(RO_FACTORY, string.get(ADDRESS, 0), iid, out), name);
      return out.get(ADDRESS, 0);
    } finally {
      invoke(STRING_DELETE, string.get(ADDRESS, 0));
    }
  }

  static void createDevice(MemorySegment device, MemorySegment context) {
    Com.check(
        (int)
            invoke(
                CREATE_DEVICE,
                MemorySegment.NULL,
                DRIVER_HARDWARE,
                MemorySegment.NULL,
                DEVICE_BGRA,
                MemorySegment.NULL,
                0,
                SDK_VERSION,
                device,
                MemorySegment.NULL,
                context),
        "D3D11CreateDevice");
  }

  static void wrapDevice(MemorySegment dxgi, MemorySegment out) {
    Com.check((int) invoke(WRAP_DEVICE, dxgi, out), "CreateDirect3D11DeviceFromDXGIDevice");
  }

  private static Object invoke(MethodHandle handle, Object... args) {
    try {
      return handle.invokeWithArguments(args);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new IllegalStateException(e);
    }
  }
}
