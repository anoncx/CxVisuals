package me.cxiy.cxvisuals.modules.idlecamera.camera;

public interface Shot {
  CameraPose sample(ShotContext ctx, double t);
}
