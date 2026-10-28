package me.cxiy.cxvisuals.mc.modules.shaders;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import me.cxiy.cxvisuals.mc.compat.Features;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

public final class Silhouettes {
  private final MaskStorage storage = new MaskStorage();
  private final MaskTee tee = new MaskTee();
  private final Matrix4f view = new Matrix4f();
  private GpuBufferSlice projection;
  private ProjectionType projectionType;
  private int picked;
  private boolean lensDue;
  private boolean ready;

  public int picked() {
    return picked;
  }

  public boolean ready() {
    return ready;
  }

  boolean placed() {
    return storage.placed;
  }

  Vector3f first() {
    return storage.first;
  }

  Matrix4f view() {
    return view;
  }

  public void submit(
      EntityRenderDispatcher dispatcher,
      EntityRenderState state,
      CameraRenderState camera,
      double x,
      double y,
      double z,
      PoseStack poseStack) {
    dispatcher.submit(state, camera, x, y, z, poseStack, storage);
    picked++;
  }

  public void lens() {
    view.set(RenderSystem.getModelViewStack());
    projection = RenderSystem.getProjectionMatrixBuffer();
    projectionType = RenderSystem.getProjectionType();
  }

  public SubmitNodeCollector tee(SubmitNodeCollector real) {
    lensDue = true;
    picked++;
    return tee.over(real, storage);
  }

  public void drawn() {
    if (lensDue) {
      lensDue = false;
      lens();
      ready = true;
    }
  }

  public void drop() {
    storage.getSubmitsPerOrder().clear();
    storage.rewind();
    picked = 0;
    lensDue = false;
    ready = false;
  }

  public void stencil(GameRenderer renderer, RenderTarget target) {
    GpuBufferSlice lens = RenderSystem.getProjectionMatrixBuffer();
    ProjectionType lensType = RenderSystem.getProjectionType();
    Matrix4fStack stack = RenderSystem.getModelViewStack();
    stack.pushMatrix().set(view);
    RenderSystem.setProjectionMatrix(projection, projectionType);
    try {
      Features.outline(renderer, storage, target);
    } finally {
      RenderSystem.setProjectionMatrix(lens, lensType);
      stack.popMatrix();
    }
  }
}
