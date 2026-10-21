package me.cxiy.cxvisuals.modules.hud.mirror.x11;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.StructLayout;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

final class Xcb {
  static final int ATOM_ATOM = 4;
  static final int ATOM_CARDINAL = 6;
  static final int ATOM_STRING = 31;
  static final int ATOM_WINDOW = 33;
  static final int ATOM_WM_NAME = 39;
  static final int MAP_VIEWABLE = 2;

  private static final int FORMAT_ZPIXMAP = 2;
  private static final Linker LINKER = Linker.nativeLinker();
  private static final Arena GLOBAL = Arena.global();
  private static final SymbolLookup XCB = SymbolLookup.libraryLookup("libxcb.so.1", GLOBAL);
  private static final SymbolLookup COMPOSITE =
      SymbolLookup.libraryLookup("libxcb-composite.so.0", GLOBAL);
  private static final StructLayout COOKIE = MemoryLayout.structLayout(JAVA_INT);
  private static final StructLayout ITERATOR =
      MemoryLayout.structLayout(ADDRESS, JAVA_INT, JAVA_INT);

  private static final MethodHandle CONNECT =
      bind(XCB, "xcb_connect", FunctionDescriptor.of(ADDRESS, ADDRESS, ADDRESS));
  private static final MethodHandle DISCONNECT =
      bind(XCB, "xcb_disconnect", FunctionDescriptor.ofVoid(ADDRESS));
  private static final MethodHandle ERROR =
      bind(XCB, "xcb_connection_has_error", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle SETUP =
      bind(XCB, "xcb_get_setup", FunctionDescriptor.of(ADDRESS, ADDRESS));
  private static final MethodHandle ROOTS =
      bind(XCB, "xcb_setup_roots_iterator", FunctionDescriptor.of(ITERATOR, ADDRESS));
  private static final MethodHandle INTERN =
      bind(
          XCB,
          "xcb_intern_atom",
          FunctionDescriptor.of(COOKIE, ADDRESS, JAVA_BYTE, JAVA_SHORT, ADDRESS));
  private static final MethodHandle INTERN_REPLY =
      bind(
          XCB,
          "xcb_intern_atom_reply",
          FunctionDescriptor.of(ADDRESS, ADDRESS, COOKIE, ADDRESS));
  private static final MethodHandle PROPERTY =
      bind(
          XCB,
          "xcb_get_property",
          FunctionDescriptor.of(
              COOKIE, ADDRESS, JAVA_BYTE, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT));
  private static final MethodHandle PROPERTY_REPLY =
      bind(
          XCB,
          "xcb_get_property_reply",
          FunctionDescriptor.of(ADDRESS, ADDRESS, COOKIE, ADDRESS));
  private static final MethodHandle PROPERTY_VALUE =
      bind(XCB, "xcb_get_property_value", FunctionDescriptor.of(ADDRESS, ADDRESS));
  private static final MethodHandle PROPERTY_LENGTH =
      bind(XCB, "xcb_get_property_value_length", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle GEOMETRY =
      bind(XCB, "xcb_get_geometry", FunctionDescriptor.of(COOKIE, ADDRESS, JAVA_INT));
  private static final MethodHandle GEOMETRY_REPLY =
      bind(
          XCB,
          "xcb_get_geometry_reply",
          FunctionDescriptor.of(ADDRESS, ADDRESS, COOKIE, ADDRESS));
  private static final MethodHandle ATTRIBUTES =
      bind(XCB, "xcb_get_window_attributes", FunctionDescriptor.of(COOKIE, ADDRESS, JAVA_INT));
  private static final MethodHandle ATTRIBUTES_REPLY =
      bind(
          XCB,
          "xcb_get_window_attributes_reply",
          FunctionDescriptor.of(ADDRESS, ADDRESS, COOKIE, ADDRESS));
  private static final MethodHandle IMAGE =
      bind(
          XCB,
          "xcb_get_image",
          FunctionDescriptor.of(
              COOKIE,
              ADDRESS,
              JAVA_BYTE,
              JAVA_INT,
              JAVA_SHORT,
              JAVA_SHORT,
              JAVA_SHORT,
              JAVA_SHORT,
              JAVA_INT));
  private static final MethodHandle IMAGE_REPLY =
      bind(
          XCB,
          "xcb_get_image_reply",
          FunctionDescriptor.of(ADDRESS, ADDRESS, COOKIE, ADDRESS));
  private static final MethodHandle IMAGE_DATA =
      bind(XCB, "xcb_get_image_data", FunctionDescriptor.of(ADDRESS, ADDRESS));
  private static final MethodHandle IMAGE_LENGTH =
      bind(XCB, "xcb_get_image_data_length", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle GENERATE =
      bind(XCB, "xcb_generate_id", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle FREE_PIXMAP =
      bind(XCB, "xcb_free_pixmap", FunctionDescriptor.of(COOKIE, ADDRESS, JAVA_INT));
  private static final MethodHandle CHECK =
      bind(XCB, "xcb_request_check", FunctionDescriptor.of(ADDRESS, ADDRESS, COOKIE));
  private static final MethodHandle FLUSH =
      bind(XCB, "xcb_flush", FunctionDescriptor.of(JAVA_INT, ADDRESS));
  private static final MethodHandle QUERY_VERSION =
      bind(
          COMPOSITE,
          "xcb_composite_query_version",
          FunctionDescriptor.of(COOKIE, ADDRESS, JAVA_INT, JAVA_INT));
  private static final MethodHandle QUERY_VERSION_REPLY =
      bind(
          COMPOSITE,
          "xcb_composite_query_version_reply",
          FunctionDescriptor.of(ADDRESS, ADDRESS, COOKIE, ADDRESS));
  private static final MethodHandle REDIRECT =
      bind(
          COMPOSITE,
          "xcb_composite_redirect_window_checked",
          FunctionDescriptor.of(COOKIE, ADDRESS, JAVA_INT, JAVA_BYTE));
  private static final MethodHandle NAME_PIXMAP =
      bind(
          COMPOSITE,
          "xcb_composite_name_window_pixmap_checked",
          FunctionDescriptor.of(COOKIE, ADDRESS, JAVA_INT, JAVA_INT));
  private static final MethodHandle FREE =
      LINKER.downcallHandle(
          LINKER.defaultLookup().find("free").orElseThrow(), FunctionDescriptor.ofVoid(ADDRESS));

  private Xcb() {}

  private static MethodHandle bind(SymbolLookup library, String name, FunctionDescriptor shape) {
    return LINKER.downcallHandle(library.find(name).orElseThrow(), shape);
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

  private static MemorySegment cookie(MethodHandle handle, SegmentAllocator arena, Object... args) {
    Object[] full = new Object[args.length + 1];
    full[0] = arena;
    System.arraycopy(args, 0, full, 1, args.length);
    return (MemorySegment) invoke(handle, full);
  }

  static MemorySegment connect() {
    MemorySegment connection =
        (MemorySegment) invoke(CONNECT, MemorySegment.NULL, MemorySegment.NULL);
    if ((int) invoke(ERROR, connection) != 0) {
      invoke(DISCONNECT, connection);
      throw new IllegalStateException("no X server");
    }
    return connection;
  }

  static void disconnect(MemorySegment connection) {
    invoke(DISCONNECT, connection);
  }

  static int root(MemorySegment connection, Arena arena) {
    MemorySegment setup = (MemorySegment) invoke(SETUP, connection);
    MemorySegment iterator = (MemorySegment) invoke(ROOTS, arena, setup);
    return iterator.get(ADDRESS, 0).reinterpret(8).get(JAVA_INT, 0);
  }

  static int atom(MemorySegment connection, Arena arena, String name) {
    byte[] text = name.getBytes(StandardCharsets.US_ASCII);
    MemorySegment chars = arena.allocate(text.length + 1L);
    MemorySegment.copy(text, 0, chars, JAVA_BYTE, 0, text.length);
    MemorySegment sent = cookie(INTERN, arena, connection, (byte) 0, (short) text.length, chars);
    MemorySegment reply =
        (MemorySegment) invoke(INTERN_REPLY, connection, sent, MemorySegment.NULL);
    if (reply.address() == 0L) {
      return 0;
    }
    int atom = reply.reinterpret(12).get(JAVA_INT, 8);
    free(reply);
    return atom;
  }

  static MemorySegment property(
      MemorySegment connection, Arena arena, int window, int property, int type, int words) {
    MemorySegment sent =
        cookie(PROPERTY, arena, connection, (byte) 0, window, property, type, 0, words);
    return (MemorySegment) invoke(PROPERTY_REPLY, connection, sent, MemorySegment.NULL);
  }

  static MemorySegment value(MemorySegment reply) {
    return (MemorySegment) invoke(PROPERTY_VALUE, reply);
  }

  static int length(MemorySegment reply) {
    return (int) invoke(PROPERTY_LENGTH, reply);
  }

  static int[] geometry(MemorySegment connection, Arena arena, int drawable) {
    MemorySegment sent = cookie(GEOMETRY, arena, connection, drawable);
    MemorySegment reply =
        (MemorySegment) invoke(GEOMETRY_REPLY, connection, sent, MemorySegment.NULL);
    if (reply.address() == 0L) {
      return null;
    }
    MemorySegment view = reply.reinterpret(24);
    int[] size = {view.get(JAVA_SHORT, 16) & 0xFFFF, view.get(JAVA_SHORT, 18) & 0xFFFF};
    free(reply);
    return size;
  }

  static int mapState(MemorySegment connection, Arena arena, int window) {
    MemorySegment sent = cookie(ATTRIBUTES, arena, connection, window);
    MemorySegment reply =
        (MemorySegment) invoke(ATTRIBUTES_REPLY, connection, sent, MemorySegment.NULL);
    if (reply.address() == 0L) {
      return -1;
    }
    int state = reply.reinterpret(28).get(JAVA_BYTE, 26) & 0xFF;
    free(reply);
    return state;
  }

  static MemorySegment image(
      MemorySegment connection, Arena arena, int drawable, int width, int height) {
    MemorySegment sent =
        cookie(
            IMAGE,
            arena,
            connection,
            (byte) FORMAT_ZPIXMAP,
            drawable,
            (short) 0,
            (short) 0,
            (short) width,
            (short) height,
            -1);
    return (MemorySegment) invoke(IMAGE_REPLY, connection, sent, MemorySegment.NULL);
  }

  static MemorySegment imageData(MemorySegment reply) {
    return (MemorySegment) invoke(IMAGE_DATA, reply);
  }

  static int imageLength(MemorySegment reply) {
    return (int) invoke(IMAGE_LENGTH, reply);
  }

  static int generate(MemorySegment connection) {
    return (int) invoke(GENERATE, connection);
  }

  static void freePixmap(MemorySegment connection, Arena arena, int pixmap) {
    cookie(FREE_PIXMAP, arena, connection, pixmap);
  }

  static boolean composite(MemorySegment connection, Arena arena) {
    MemorySegment sent = cookie(QUERY_VERSION, arena, connection, 0, 4);
    MemorySegment reply =
        (MemorySegment) invoke(QUERY_VERSION_REPLY, connection, sent, MemorySegment.NULL);
    if (reply.address() == 0L) {
      return false;
    }
    free(reply);
    return true;
  }

  static boolean redirect(MemorySegment connection, Arena arena, int window) {
    return clean(connection, cookie(REDIRECT, arena, connection, window, (byte) 0));
  }

  static boolean name(MemorySegment connection, Arena arena, int window, int pixmap) {
    return clean(connection, cookie(NAME_PIXMAP, arena, connection, window, pixmap));
  }

  private static boolean clean(MemorySegment connection, MemorySegment sent) {
    MemorySegment error = (MemorySegment) invoke(CHECK, connection, sent);
    if (error.address() == 0L) {
      return true;
    }
    free(error);
    return false;
  }

  static void flush(MemorySegment connection) {
    invoke(FLUSH, connection);
  }

  static void free(MemorySegment pointer) {
    if (pointer.address() != 0L) {
      invoke(FREE, pointer);
    }
  }
}
