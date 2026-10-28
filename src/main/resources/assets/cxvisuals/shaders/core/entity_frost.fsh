#version 330
#extension GL_ARB_separate_shader_objects : require

#include <cxvisuals:entity_block.glsl>
#include <cxvisuals:entity_fx.glsl>

uniform sampler2D SceneSampler;

vec3 cells(vec3 p) {
    vec3 base = floor(p);
    vec3 f = fract(p);
    float first = 8.0;
    float second = 8.0;
    float id = 0.0;
    for (int k = -1; k <= 1; k++) {
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                vec3 shift = vec3(i, j, k);
                vec3 site = hash33(base + shift);
                vec3 gap = shift + site - f;
                float span = dot(gap, gap);
                if (span < first) {
                    second = first;
                    first = span;
                    id = site.x;
                } else if (span < second) {
                    second = span;
                }
            }
        }
    }
    return vec3(sqrt(first), sqrt(second), id);
}

void main() {
    Surface s;
    if (!surface(s)) {
        discard;
    }
    float time = Lens.w;
    vec3 p = s.anchor;
    vec3 ice = mix(Second.rgb, vec3(1.0), 0.6);

    if (s.inside) {
        vec3 scene = texelFetch(SceneSampler, ivec2(gl_FragCoord.xy), 0).rgb;
        float luma = dot(scene, vec3(0.299, 0.587, 0.114));
        float depth = -s.edge / s.reach;
        float grow = smoothstep(1.9, 0.1, depth + (noise(p * 3.0) - 0.5) * 1.1);
        if (grow <= 0.0) {
            discard;
        }
        vec3 cell = cells(p * 6.5);
        float crack = 1.0 - smoothstep(0.0, 0.055, cell.y - cell.x);
        float twinkle = pow(max(0.0, sin(time * 2.3 + cell.z * 40.0)), 20.0) * step(0.72, cell.z) * smoothstep(0.2, 0.0, cell.x);
        float rim = exp(s.edge / max(s.reach * 0.14, 1.0));
        vec3 frozen = mix(scene, ice * (0.32 + 0.9 * luma), 0.66) * (0.84 + 0.3 * cell.z);
        frozen = mix(frozen, mix(First.rgb, vec3(1.0), 0.6), crack * 0.5);
        vec3 glow = First.rgb * (crack * grow * 0.3 + rim * 0.55) + vec3(1.0) * twinkle * grow * 2.6;
        emit(glow, frozen, min(0.96, grow) * min(1.0, Knobs.y));
        return;
    }

    float u = s.edge / s.reach;
    float mist = exp(-u * 3.6) * (0.2 + 0.24 * noise(p * 2.1 + vec3(0.0, time * 0.1, 0.0))) * (1.0 - smoothstep(0.7, 1.0, u));
    float contour = exp(-s.edge / 1.5) * 0.75;
    emit(First.rgb * (contour + mist), ice, 0.0);
}
