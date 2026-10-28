#version 330
#extension GL_ARB_separate_shader_objects : require

#include <cxvisuals:entity_block.glsl>
#include <cxvisuals:entity_fx.glsl>

uniform sampler2D SceneSampler;

const vec3 INK = vec3(1.0);
const vec3 ACCENT = vec3(0.843, 0.098, 0.129);

void main() {
    Surface s;
    if (!surface(s)) {
        discard;
    }
    float time = Lens.w;
    float pitch = clamp(s.scale * 0.055, 4.0, 9.0);
    vec2 grid = s.rigid * s.scale / pitch;
    vec2 cell = floor(grid);
    vec2 local = fract(grid) - 0.5;
    float span = length(local);
    float soft = 1.0 / pitch;
    float scan = pow(0.5 + 0.5 * sin(cell.y * 0.42 - time * 2.1), 6.0);
    float turn = atan(s.rigid.y, s.rigid.x) / 6.2831853 + 0.5;
    float lead = abs(fract(turn - time * 0.11) - 0.5);
    float marker = 1.0 - smoothstep(0.02, 0.045, lead);

    if (s.inside) {
        ivec2 centre = clamp(ivec2(gl_FragCoord.xy - local * pitch), ivec2(0), ivec2(Screen.xy) - 1);
        float tone = dot(texelFetch(SceneSampler, centre, 0).rgb, vec3(0.299, 0.587, 0.114));
        float size = 0.12 + 0.3 * tone;
        float point = 1.0 - smoothstep(size - soft, size + soft, span);
        float rim = exp(s.edge / 1.3);
        vec3 plate = vec3(0.035 + 0.1 * tone);
        vec3 glow = INK * (point * (0.42 + 0.75 * scan) + rim * 0.5);
        emit(glow, plate, 0.9 * min(1.0, Knobs.y));
        return;
    }

    float u = s.edge / s.reach;
    float line = clamp(s.edge + 0.5, 0.0, 1.0) * clamp(2.0 - s.edge, 0.0, 1.0);
    float size = 0.3 * (1.0 - u * 1.7);
    float halo = size <= 0.0 ? 0.0 : (1.0 - smoothstep(size - soft, size + soft, span)) * (1.0 - u) * (0.35 + 0.65 * scan);
    float flare = marker * exp(-u * 5.0);
    vec3 glow = INK * halo * (1.0 - line) + ACCENT * flare * 1.6;
    emit(glow, mix(INK, ACCENT, marker), line);
}
