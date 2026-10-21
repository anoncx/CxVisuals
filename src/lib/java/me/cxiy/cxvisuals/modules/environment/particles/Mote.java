package me.cxiy.cxvisuals.modules.environment.particles;

final class Mote {
  double x;
  double y;
  double z;
  float vx;
  float vy;
  float vz;
  float seed;
  float phase;
  float rate;
  float size;
  float drop;
  float hover;
  float age;
  float life;
  float spin;
  float tumble;
  float floor;
  double floorX = Double.NaN;
  double floorZ;
  float rest;
  boolean landed;
  int cellX;
  int cellY;
  int cellZ;
  float ledge;
  boolean buried;
  float sky = 1.0F;
  float lamp;
  double[] trail;
  int trailHead;
  float trailClock;
}
