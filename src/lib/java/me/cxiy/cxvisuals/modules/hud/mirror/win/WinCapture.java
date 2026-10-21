package me.cxiy.cxvisuals.modules.hud.mirror.win;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.nio.ByteBuffer;
import java.util.function.Consumer;
import java.util.concurrent.locks.LockSupport;
import me.cxiy.cxvisuals.modules.hud.mirror.FrameStore;
import me.cxiy.cxvisuals.modules.hud.mirror.MirrorCapture;
import me.cxiy.cxvisuals.modules.hud.mirror.Reduce;

final class WinCapture implements MirrorCapture {
  private static final String ITEM_CLASS = "Windows.Graphics.Capture.GraphicsCaptureItem";
  private static final String POOL_CLASS = "Windows.Graphics.Capture.Direct3D11CaptureFramePool";
  private static final String IID_DXGI_DEVICE = "54EC77FA-1377-44E6-8C32-88FD5F44C84C";
  private static final String IID_DIRECT3D_DEVICE = "A37624AB-8D5F-4650-9D3E-9EAE3D9BC670";
  private static final String IID_INTEROP = "3628E81B-3CAC-4C60-B7F4-23CE0E0C3356";
  private static final String IID_ITEM = "79C3F95B-31F7-4EC2-A464-632EF5D30760";
  private static final String IID_POOL_STATICS = "589B103F-6BBC-5DF5-A991-02E28B3B66D5";
  private static final String IID_SESSION_CURSOR = "2C39AE40-7D2E-5044-804E-8B6799D4CF9E";
  private static final String IID_SESSION_BORDER = "F2CDD966-22AE-5EA1-9596-3A289344C3BE";
  private static final String IID_ACCESS = "A9B3D012-3DF2-4EE3-B8D1-8695F457D3C1";
  private static final String IID_TEXTURE = "6F15AAF2-D208-4E89-9AB4-489535D34F9C";
  private static final String IID_CLOSABLE = "30D5A829-7FA4-4026-83BB-D75BAE4EA99E";

  private static final int FORMAT_BGRA = 87;
  private static final int BUFFERS = 2;
  private static final int FALLBACK_WIDTH = 1280;
  private static final int FALLBACK_HEIGHT = 720;
  private static final long CHECK_NANOS = 100_000_000L;
  private static final long POLL_NANOS = 1_000_000L;
  private static final long JOIN_MILLIS = 800L;

  private static final int USAGE_STAGING = 3;
  private static final int BIND_SAMPLED_TARGET = 0x28;
  private static final int CPU_READ = 0x20000;
  private static final int MISC_MIPS = 0x1;
  private static final int MAP_READ = 1;

  private static final int SLOT_CREATE_FOR_WINDOW = 3;
  private static final int SLOT_POOL_CREATE = 6;
  private static final int SLOT_POOL_RECREATE = 6;
  private static final int SLOT_POOL_NEXT = 7;
  private static final int SLOT_POOL_SESSION = 10;
  private static final int SLOT_SESSION_START = 6;
  private static final int SLOT_SETTER = 7;
  private static final int SLOT_FRAME_SURFACE = 6;
  private static final int SLOT_FRAME_SIZE = 8;
  private static final int SLOT_ACCESS_GET = 3;
  private static final int SLOT_CLOSE = 6;
  private static final int SLOT_DEVICE_TEXTURE = 5;
  private static final int SLOT_DEVICE_VIEW = 7;
  private static final int SLOT_CONTEXT_MAP = 14;
  private static final int SLOT_CONTEXT_UNMAP = 15;
  private static final int SLOT_CONTEXT_COPY = 46;
  private static final int SLOT_CONTEXT_MIPS = 54;

  private static final FunctionDescriptor NONE = Com.sig();
  private static final FunctionDescriptor ONE = Com.sig(ADDRESS);
  private static final FunctionDescriptor TWO = Com.sig(ADDRESS, ADDRESS);
  private static final FunctionDescriptor THREE = Com.sig(ADDRESS, ADDRESS, ADDRESS);
  private static final FunctionDescriptor CREATE =
      Com.sig(ADDRESS, JAVA_INT, JAVA_INT, Com.SIZE, ADDRESS);
  private static final FunctionDescriptor RECREATE =
      Com.sig(ADDRESS, JAVA_INT, JAVA_INT, Com.SIZE);
  private static final FunctionDescriptor FLAG = Com.sig(JAVA_BYTE);
  private static final FunctionDescriptor MAP =
      Com.sig(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS);
  private static final FunctionDescriptor UNMAP = Com.action(ADDRESS, JAVA_INT);
  private static final FunctionDescriptor MIPS = Com.action(ADDRESS);
  private static final FunctionDescriptor COPY =
      Com.action(ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, ADDRESS, JAVA_INT, ADDRESS);

  private final MemorySegment window;
  private final FrameStore store;
  private final Consumer<String> log;
  private final Thread thread;
  private volatile boolean running = true;
  private volatile boolean ended;

  private MemorySegment device = MemorySegment.NULL;
  private MemorySegment context = MemorySegment.NULL;
  private MemorySegment direct = MemorySegment.NULL;
  private MemorySegment item = MemorySegment.NULL;
  private MemorySegment pool = MemorySegment.NULL;
  private MemorySegment session = MemorySegment.NULL;
  private MemorySegment staging = MemorySegment.NULL;
  private MemorySegment mips = MemorySegment.NULL;
  private MemorySegment view = MemorySegment.NULL;

  private MemorySegment out;
  private MemorySegment sizeBuffer;
  private MemorySegment poolSize;
  private MemorySegment description;
  private MemorySegment box;
  private MemorySegment mapped;
  private MemorySegment iidDirect;
  private MemorySegment iidAccess;
  private MemorySegment iidTexture;
  private MemorySegment iidClosable;
  private int poolWidth;
  private int poolHeight;
  private int gpuWidth;
  private int gpuHeight;
  private int gpuLevel = -1;

  WinCapture(long handle, FrameStore store, Consumer<String> log) {
    this.window = Win32.handle(handle);
    this.store = store;
    this.log = log;
    this.thread = new Thread(this::run, "CxVisuals window capture");
    thread.setDaemon(true);
    thread.start();
  }

  @Override
  public boolean alive() {
    return !ended;
  }

  @Override
  public void close() {
    running = false;
    try {
      thread.join(JOIN_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  private void run() {
    try (Arena arena = Arena.ofConfined()) {
      Win32.initialize();
      try {
        start(arena);
        pump();
      } finally {
        teardown();
        Win32.uninitialize();
      }
    } catch (RuntimeException | LinkageError e) {
      log.accept("Window capture stopped: " + e.getMessage());
    } finally {
      ended = true;
    }
  }

  private void start(Arena arena) {
    out = arena.allocate(ADDRESS);
    sizeBuffer = arena.allocate(Com.SIZE);
    poolSize = arena.allocate(Com.SIZE);
    description = arena.allocate(44);
    box = arena.allocate(24);
    mapped = arena.allocate(16);
    iidDirect = Com.guid(arena, IID_DIRECT3D_DEVICE);
    iidAccess = Com.guid(arena, IID_ACCESS);
    iidTexture = Com.guid(arena, IID_TEXTURE);
    iidClosable = Com.guid(arena, IID_CLOSABLE);

    MemorySegment deviceSlot = arena.allocate(ADDRESS);
    MemorySegment contextSlot = arena.allocate(ADDRESS);
    Win32.createDevice(deviceSlot, contextSlot);
    device = deviceSlot.get(ADDRESS, 0);
    context = contextSlot.get(ADDRESS, 0);

    MemorySegment dxgi = Com.query(out, device, Com.guid(arena, IID_DXGI_DEVICE));
    MemorySegment wrapped = arena.allocate(ADDRESS);
    try {
      Win32.wrapDevice(dxgi, wrapped);
    } finally {
      Com.release(dxgi);
    }
    MemorySegment inspectable = wrapped.get(ADDRESS, 0);
    try {
      direct = Com.query(out, inspectable, iidDirect);
    } finally {
      Com.release(inspectable);
    }

    MemorySegment interop = Win32.factory(arena, ITEM_CLASS, Com.guid(arena, IID_INTEROP));
    try {
      Com.check(
          Com.hr(interop, SLOT_CREATE_FOR_WINDOW, THREE, window, Com.guid(arena, IID_ITEM), out),
          "CreateForWindow");
      item = out.get(ADDRESS, 0);
    } finally {
      Com.release(interop);
    }

    int[] extent = Win32.extent(window, arena);
    poolWidth = extent[0] > 0 && !Win32.minimized(window) ? extent[0] : FALLBACK_WIDTH;
    poolHeight = extent[1] > 0 && !Win32.minimized(window) ? extent[1] : FALLBACK_HEIGHT;
    poolSize.set(JAVA_INT, 0, poolWidth);
    poolSize.set(JAVA_INT, 4, poolHeight);
    MemorySegment statics = Win32.factory(arena, POOL_CLASS, Com.guid(arena, IID_POOL_STATICS));
    try {
      Com.check(
          Com.hr(statics, SLOT_POOL_CREATE, CREATE, direct, FORMAT_BGRA, BUFFERS, poolSize, out),
          "CreateFreeThreaded");
      pool = out.get(ADDRESS, 0);
    } finally {
      Com.release(statics);
    }

    Com.check(Com.hr(pool, SLOT_POOL_SESSION, TWO, item, out), "CreateCaptureSession");
    session = out.get(ADDRESS, 0);
    optional(session, Com.guid(arena, IID_SESSION_CURSOR));
    optional(session, Com.guid(arena, IID_SESSION_BORDER));
    Com.check(Com.hr(session, SLOT_SESSION_START, NONE), "StartCapture");
    store.status(FrameStore.Status.WAITING);
  }

  private void optional(MemorySegment target, MemorySegment iid) {
    MemorySegment face = Com.tryQuery(out, target, iid);
    if (!Com.none(face)) {
      Com.hr(face, SLOT_SETTER, FLAG, (byte) 0);
      Com.release(face);
    }
  }

  private void pump() {
    long checked = 0L;
    while (running) {
      long now = System.nanoTime();
      if (now - checked > CHECK_NANOS) {
        checked = now;
        if (!Win32.exists(window)) {
          return;
        }
        boolean iconic = Win32.minimized(window);
        if (iconic) {
          store.status(FrameStore.Status.PAUSED);
        } else if (store.status() == FrameStore.Status.PAUSED) {
          store.status(store.has() ? FrameStore.Status.LIVE : FrameStore.Status.WAITING);
        }
      }
      MemorySegment frame = newest();
      if (frame == null) {
        LockSupport.parkNanos(POLL_NANOS);
        continue;
      }
      try {
        read(frame);
      } finally {
        discard(frame);
      }
    }
  }

  private MemorySegment newest() {
    MemorySegment latest = null;
    while (true) {
      Com.check(Com.hr(pool, SLOT_POOL_NEXT, ONE, out), "TryGetNextFrame");
      MemorySegment next = out.get(ADDRESS, 0);
      if (Com.none(next)) {
        return latest;
      }
      if (latest != null) {
        discard(latest);
      }
      latest = next;
    }
  }

  private void discard(MemorySegment frame) {
    MemorySegment closable = Com.tryQuery(out, frame, iidClosable);
    if (!Com.none(closable)) {
      Com.hr(closable, SLOT_CLOSE, NONE);
      Com.release(closable);
    }
    Com.release(frame);
  }

  private void read(MemorySegment frame) {
    Com.check(Com.hr(frame, SLOT_FRAME_SIZE, ONE, sizeBuffer), "ContentSize");
    int width = sizeBuffer.get(JAVA_INT, 0);
    int height = sizeBuffer.get(JAVA_INT, 4);
    if (width <= 0 || height <= 0) {
      return;
    }
    if (width != poolWidth || height != poolHeight) {
      poolWidth = width;
      poolHeight = height;
      poolSize.set(JAVA_INT, 0, width);
      poolSize.set(JAVA_INT, 4, height);
      Com.check(
          Com.hr(pool, SLOT_POOL_RECREATE, RECREATE, direct, FORMAT_BGRA, BUFFERS, poolSize),
          "Recreate");
    }
    Com.check(Com.hr(frame, SLOT_FRAME_SURFACE, ONE, out), "Surface");
    MemorySegment surface = out.get(ADDRESS, 0);
    MemorySegment access = MemorySegment.NULL;
    MemorySegment texture = MemorySegment.NULL;
    try {
      access = Com.query(out, surface, iidAccess);
      Com.check(Com.hr(access, SLOT_ACCESS_GET, TWO, iidTexture, out), "GetInterface");
      texture = out.get(ADDRESS, 0);
      transfer(texture, width, height);
    } finally {
      Com.release(texture);
      Com.release(access);
      Com.release(surface);
    }
  }

  private void transfer(MemorySegment texture, int width, int height) {
    int level = Reduce.level(width, height, store.wanted());
    prepare(width, height, level);
    box.set(JAVA_INT, 0, 0);
    box.set(JAVA_INT, 4, 0);
    box.set(JAVA_INT, 8, 0);
    box.set(JAVA_INT, 12, width);
    box.set(JAVA_INT, 16, height);
    box.set(JAVA_INT, 20, 1);
    if (level == 0) {
      Com.call(context, SLOT_CONTEXT_COPY, COPY, staging, 0, 0, 0, 0, texture, 0, box);
    } else {
      Com.call(context, SLOT_CONTEXT_COPY, COPY, mips, 0, 0, 0, 0, texture, 0, box);
      Com.call(context, SLOT_CONTEXT_MIPS, MIPS, view);
      Com.call(
          context, SLOT_CONTEXT_COPY, COPY, staging, 0, 0, 0, 0, mips, level, MemorySegment.NULL);
    }
    int outWidth = Reduce.size(width, level);
    int outHeight = Reduce.size(height, level);
    Com.check(Com.hr(context, SLOT_CONTEXT_MAP, MAP, staging, 0, MAP_READ, 0, mapped), "Map");
    try {
      int pitch = mapped.get(JAVA_INT, 8);
      MemorySegment source = mapped.get(ADDRESS, 0).reinterpret((long) pitch * outHeight);
      ByteBuffer target = store.begin(outWidth, outHeight);
      MemorySegment sink = MemorySegment.ofBuffer(target);
      long row = outWidth * 4L;
      if (pitch == row) {
        MemorySegment.copy(source, 0L, sink, 0L, row * outHeight);
      } else {
        for (int y = 0; y < outHeight; y++) {
          MemorySegment.copy(source, (long) y * pitch, sink, y * row, row);
        }
      }
    } finally {
      Com.call(context, SLOT_CONTEXT_UNMAP, UNMAP, staging, 0);
    }
    store.publish();
  }

  private void prepare(int width, int height, int level) {
    if (width == gpuWidth && height == gpuHeight && level == gpuLevel) {
      return;
    }
    free();
    gpuWidth = width;
    gpuHeight = height;
    gpuLevel = level;
    int outWidth = Reduce.size(width, level);
    int outHeight = Reduce.size(height, level);
    describe(outWidth, outHeight, 1, USAGE_STAGING, 0, CPU_READ, 0);
    Com.check(
        Com.hr(device, SLOT_DEVICE_TEXTURE, THREE, description, MemorySegment.NULL, out),
        "CreateTexture2D");
    staging = out.get(ADDRESS, 0);
    if (level > 0) {
      describe(width, height, level + 1, 0, BIND_SAMPLED_TARGET, 0, MISC_MIPS);
      Com.check(
          Com.hr(device, SLOT_DEVICE_TEXTURE, THREE, description, MemorySegment.NULL, out),
          "CreateTexture2D");
      mips = out.get(ADDRESS, 0);
      Com.check(
          Com.hr(device, SLOT_DEVICE_VIEW, THREE, mips, MemorySegment.NULL, out),
          "CreateShaderResourceView");
      view = out.get(ADDRESS, 0);
    }
  }

  private void describe(
      int width, int height, int levels, int usage, int bind, int cpu, int misc) {
    description.set(JAVA_INT, 0, width);
    description.set(JAVA_INT, 4, height);
    description.set(JAVA_INT, 8, levels);
    description.set(JAVA_INT, 12, 1);
    description.set(JAVA_INT, 16, FORMAT_BGRA);
    description.set(JAVA_INT, 20, 1);
    description.set(JAVA_INT, 24, 0);
    description.set(JAVA_INT, 28, usage);
    description.set(JAVA_INT, 32, bind);
    description.set(JAVA_INT, 36, cpu);
    description.set(JAVA_INT, 40, misc);
  }

  private void free() {
    Com.release(view);
    Com.release(mips);
    Com.release(staging);
    view = MemorySegment.NULL;
    mips = MemorySegment.NULL;
    staging = MemorySegment.NULL;
  }

  private void teardown() {
    try {
      closeObject(session);
      closeObject(pool);
    } catch (RuntimeException e) {
      log.accept("Window capture cleanup: " + e.getMessage());
    }
    free();
    Com.release(session);
    Com.release(pool);
    Com.release(item);
    Com.release(direct);
    Com.release(context);
    Com.release(device);
  }

  private void closeObject(MemorySegment target) {
    if (Com.none(target)) {
      return;
    }
    MemorySegment closable = Com.tryQuery(out, target, iidClosable);
    if (!Com.none(closable)) {
      Com.hr(closable, SLOT_CLOSE, NONE);
      Com.release(closable);
    }
  }
}
