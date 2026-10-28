#version 330
#extension GL_ARB_separate_shader_objects : require

#include <cxvisuals:entity_block.glsl>
#include <cxvisuals:entity_fx.glsl>

void main() {
    Surface s;
    if (!surface(s)) {
        discard;
    }
    float time = Lens.w;
    float width = clamp(s.reach * 0.075, 1.5, 5.0);
    float wave = 0.5 + 0.5 * sin(s.anchor.y * 2.4 + s.anchor.x * 0.9 + s.anchor.z * 0.7 + time * 0.8);
    vec3 colour = palette(wave);
    float angle = atan(s.normal.y, s.normal.x);
    float sweep = pow(max(0.0, sin(s.anchor.y * 3.1 + angle - time * 2.2)), 18.0);

    if (s.inside) {
        float depth = -s.edge;
        float rim = exp(-depth / max(s.reach * 0.16, 1.0));
        emit(colour * rim * 0.55, colour, (0.08 + rim * 0.14) * Knobs.y);
        return;
    }

    float line = clamp(s.edge + 0.5, 0.0, 1.0) * clamp(width - s.edge + 0.5, 0.0, 1.0);
    float halo = exp(-max(s.edge - width, 0.0) / max(s.reach * 0.22, 1.0)) * (1.0 - smoothstep(0.6, 1.0, s.edge / s.reach));
    vec3 glow = colour * halo * 0.42 * (1.0 - line) + vec3(1.0) * sweep * line * 1.4;
    emit(glow, colour, line);
}
