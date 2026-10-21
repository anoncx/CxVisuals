package me.cxiy.cxvisuals.modules.hud.mirror.win;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class Com {
  static final Linker LINKER = Linker.nativeLinker();
  static final StructLayout SIZE = MemoryLayout.structLayout(JAVA_INT, JAVA_INT);

  private static final Map<FunctionDescriptor, MethodHandle> HANDLES = new ConcurrentHashMap<>();
  private static final long POINTER = ADDRESS.byteSize();

  private Com() {}

  static FunctionDescriptor sig(MemoryLayout... arguments) {
    return FunctionDescriptor.of(JAVA_INT, prepend(arguments));
  }

  static FunctionDescriptor action(MemoryLayout... arguments) {
    return FunctionDescriptor.ofVoid(prepend(arguments));
  }

  private static MemoryLayout[] prepend(MemoryLayout[] arguments) {
    MemoryLayout[] all = new MemoryLayout[arguments.length + 1];
    all[0] = ADDRESS;
    System.arraycopy(arguments, 0, all, 1, arguments.length);
    return all;
  }

  static Object call(MemorySegment self, int slot, FunctionDescriptor signature, Object... args) {
    MemorySegment table =
        self.reinterpret(POINTER).get(ADDRESS, 0).reinterpret((slot + 1) * POINTER);
    MethodHandle handle = HANDLES.computeIfAbsent(signature, key -> LINKER.downcallHandle(key));
    Object[] full = new Object[args.length + 2];
    full[0] = table.getAtIndex(ADDRESS, slot);
    full[1] = self;
    System.arraycopy(args, 0, full, 2, args.length);
    try {
      return handle.invokeWithArguments(full);
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable e) {
      throw new IllegalStateException(e);
    }
  }

  static int hr(MemorySegment self, int slot, FunctionDescriptor signature, Object... args) {
    return (int) call(self, slot, signature, args);
  }

  static void check(int result, String what) {
    if (result < 0) {
      throw new IllegalStateException(what + " failed 0x" + Integer.toHexString(result));
    }
  }

  static MemorySegment guid(Arena arena, String text) {
    MemorySegment segment = arena.allocate(16);
    String[] parts = text.split("-");
    segment.set(JAVA_INT, 0, (int) Long.parseLong(parts[0], 16));
    segment.set(JAVA_SHORT, 4, (short) Integer.parseInt(parts[1], 16));
    segment.set(JAVA_SHORT, 6, (short) Integer.parseInt(parts[2], 16));
    String tail = parts[3] + parts[4];
    for (int i = 0; i < 8; i++) {
      segment.set(JAVA_BYTE, 8 + i, (byte) Integer.parseInt(tail.substring(i * 2, i * 2 + 2), 16));
    }
    return segment;
  }

  static MemorySegment query(MemorySegment out, MemorySegment self, MemorySegment iid) {
    check(hr(self, 0, sig(ADDRESS, ADDRESS), iid, out), "QueryInterface");
    return out.get(ADDRESS, 0);
  }

  static MemorySegment tryQuery(MemorySegment out, MemorySegment self, MemorySegment iid) {
    return hr(self, 0, sig(ADDRESS, ADDRESS), iid, out) < 0
        ? MemorySegment.NULL
        : out.get(ADDRESS, 0);
  }

  static void release(MemorySegment self) {
    if (self != null && self.address() != 0L) {
      call(self, 2, FunctionDescriptor.of(JAVA_INT, ADDRESS));
    }
  }

  static boolean none(MemorySegment pointer) {
    return pointer == null || pointer.address() == 0L;
  }
}
