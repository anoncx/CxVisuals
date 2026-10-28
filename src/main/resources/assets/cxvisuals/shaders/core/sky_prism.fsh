#version 330
#extension GL_ARB_separate_shader_objects : require

#include <cxvisuals:sky.glsl>

const float SCALE = 3.2;
const float LEVELS = 2.2;
const float SKEW = 1.4;

void main() {
    vec3 world;
    vec3 dir;
    if (!skyRay(world, dir)) {
        discard;
    }
    float time = Lens.y;
    vec3 a = dir * SCALE + time * 0.05;
    vec3 b = dir.yzx * SCALE * SKEW - time * 0.035;
    vec3 sa = sin(a);
    vec3 sb = sin(b);
    vec3 cb = cos(b);
    float field = dot(sa, cb);
    vec3 slope = (cos(a) * cb - SKEW * sa.zxy * sb.zxy) * SCALE * LEVELS;
    slope -= dir * dot(slope, dir);

    float gap = abs(fract(field * LEVELS) - 0.5) / max(length(slope), 1.0e-3);
    float thin = max(Lens.x * 1.2, 0.002);
    float line = 1.0 - smoothstep(thin * 0.5, thin * 1.5, gap);
    float halo = exp(-gap * 55.0);
    vec3 tone = mix(First.rgb, Second.rgb, 0.5 + 0.5 * sin(field * 1.3 + time * 0.1));

    vec3 colour = vec3(0.004, 0.005, 0.012) + tone * (0.05 + 0.015 * field);
    colour += tone * (line * 1.1 + halo * 0.2);
    colour += stars(dir, 0.6) * 0.6;
    finish(colour, world);
}
