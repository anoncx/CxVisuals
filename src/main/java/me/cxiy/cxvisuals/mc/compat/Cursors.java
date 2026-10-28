package me.cxiy.cxvisuals.mc.compat;

import java.nio.ByteBuffer;
import org.lwjgl.sdl.SDLMouse;
import org.lwjgl.sdl.SDLPixels;
import org.lwjgl.sdl.SDLSurface;
import org.lwjgl.sdl.SDL_Surface;

public final class Cursors {
  private static final int BYTES_PER_PIXEL = 4;

  private Cursors() {}

  public static long create(ByteBuffer rgba, int size, int hotX, int hotY) {
    SDL_Surface surface =
        SDLSurface.SDL_CreateSurfaceFrom(
            size, size, SDLPixels.SDL_PIXELFORMAT_RGBA32, rgba, size * BYTES_PER_PIXEL);
    if (surface == null) {
      return 0L;
    }
    try {
      return SDLMouse.SDL_CreateColorCursor(surface, hotX, hotY);
    } finally {
      SDLSurface.SDL_DestroySurface(surface);
    }
  }

  public static void select(long cursor) {
    SDLMouse.SDL_SetCursor(cursor == 0L ? SDLMouse.SDL_GetDefaultCursor() : cursor);
  }

  public static void hidden(boolean hidden) {
    if (hidden) {
      SDLMouse.SDL_HideCursor();
    } else {
      SDLMouse.SDL_ShowCursor();
    }
  }

  public static boolean hidden() {
    return !SDLMouse.SDL_CursorVisible();
  }
}
