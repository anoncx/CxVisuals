package me.cxiy.cxvisuals.modules.shaders;

public enum Sky {
  GALAXY("sky_galaxy"),
  PLANET("sky_planet"),
  AURORA("sky_aurora"),
  PRISM("sky_prism"),
  STORM("sky_storm"),
  SUNSET("sky_sunset");

  public enum Hours {
    ALWAYS,
    NIGHT
  }

  private final String shader;

  Sky(String shader) {
    this.shader = shader;
  }

  public String shader() {
    return shader;
  }
}
