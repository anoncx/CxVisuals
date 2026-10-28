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
    vec3 p = s.anchor;
    float drift = noise(vec3(p.xy * 0.9, time * 0.18));
    vec3 colour = palette(0.5 + 0.5 * sin(p.y * 1.4 - time * 0.5 + drift * 2.0));
    float breathe = 0.9 + 0.1 * sin(time * 1.1);

    if (s.inside) {
        float rim = exp(s.edge / max(s.reach * 0.3, 1.0));
        emit(colour * rim * 0.7 * breathe, colour, (0.04 + rim * 0.12) * Knobs.y);
        return;
    }

    float u = s.edge / s.reach;
    float soft = exp(-u * 3.2) * (1.0 - smoothstep(0.55, 1.0, u));
    float core = exp(-u * 11.0) * 0.6;
    emit(colour * (soft * (0.85 + 0.3 * drift) + core) * breathe, colour, 0.0);
}
