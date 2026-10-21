package me.cxiy.cxvisuals.mc.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix3x2fc;

final class GuiVertex {
  private GuiVertex() {}

  static VertexConsumer at(VertexConsumer consumer, Matrix3x2fc pose, float x, float y, float z) {
    if (z == 0.0F) {
      return consumer.addVertexWith2DPose(pose, x, y);
    }
    return consumer.addVertex(
        pose.m00() * x + pose.m10() * y + pose.m20(),
        pose.m01() * x + pose.m11() * y + pose.m21(),
        z);
  }
}
