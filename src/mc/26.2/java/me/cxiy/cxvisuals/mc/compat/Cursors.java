package me.cxiy.cxvisuals.mc.compat;

import java.nio.ByteBuffer;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWImage;

public final class Cursors {
  private Cursors() {}

  public static long create(ByteBuffer rgba, int size, int hotX, int hotY) {
    GLFWImage image = GLFWImage.malloc();
    try {
      image.set(size, size, rgba);
      return GLFW.glfwCreateCursor(image, hotX, hotY);
    } finally {
      image.free();
    }
  }

  public static void select(long cursor) {
    GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().handle(), cursor);
  }

  public static void hidden(boolean hidden) {
    GLFW.glfwSetInputMode(
        Minecraft.getInstance().getWindow().handle(),
        GLFW.GLFW_CURSOR,
        hidden ? GLFW.GLFW_CURSOR_HIDDEN : GLFW.GLFW_CURSOR_NORMAL);
  }

  public static boolean hidden() {
    return GLFW.glfwGetInputMode(Minecraft.getInstance().getWindow().handle(), GLFW.GLFW_CURSOR)
        == GLFW.GLFW_CURSOR_HIDDEN;
  }
}
