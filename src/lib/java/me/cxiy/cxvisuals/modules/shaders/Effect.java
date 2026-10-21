package me.cxiy.cxvisuals.modules.shaders;

public enum Effect {
  PLASMA("entity_plasma"),
  AURA("entity_aura"),
  FROST("entity_frost"),
  OUTLINE("entity_outline"),
  HOLOGRAM("entity_hologram"),
  GLITCH("entity_glitch"),
  SIGNAL("entity_signal");

  private final String shader;

  Effect(String shader) {
    this.shader = shader;
  }

  public String shader() {
    return shader;
  }
}
