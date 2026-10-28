#version 330
#extension GL_ARB_separate_shader_objects : require

#include <cxvisuals:sky.glsl>

const float RATE = 0.3;

float deck(vec2 p) {
    float sum = noise(p) * 0.5;
    p = vec2(p.x * 1.62 - p.y * 1.22, p.x * 1.22 + p.y * 1.62) + vec2(13.7, 4.1);
    sum += noise(p) * 0.25;
    p = vec2(p.x * 1.62 - p.y * 1.22, p.x * 1.22 + p.y * 1.62) + vec2(13.7, 4.1);
    sum += noise(p) * 0.125;
    return sum / 0.875;
}

void main() {
    vec3 world;
    vec3 dir;
    if (!skyRay(world, dir)) {
        discard;
    }
    float time = Lens.y;
    float rise = abs(world.y);
    vec2 p = world.xz / (rise + 0.14);
    float far = exp(-length(p) * 0.07);

    float mass = deck(p * 0.55 + vec2(time * 0.03, time * 0.012));
    float churn = deck(p * 1.3 + mass * 1.6 - vec2(time * 0.05, 0.0));
    float thick = smoothstep(0.3, 0.72, mass * 0.6 + churn * 0.4);

    vec3 dark = vec3(0.05, 0.056, 0.075) + Second.rgb * 0.02;
    vec3 pale = vec3(0.42, 0.45, 0.52) + Second.rgb * 0.16;
    vec3 colour = mix(pale, dark, thick);
    colour *= 0.5 + 0.7 * churn;

    float beat = time * RATE;
    float slot = floor(beat);
    float since = beat - slot;
    vec3 seed = hash33(vec3(slot, 7.3, 1.9));
    float strike = step(0.45, seed.z) * exp(-since * 7.0) * (0.55 + 0.45 * sin(since * 85.0 + seed.x * 6.0));
    float near = exp(-length(p - (seed.xy - 0.5) * 9.0) * 0.45);
    vec3 flash = mix(First.rgb, vec3(1.0), 0.55) * strike * (near * (1.2 + 3.0 * (1.0 - thick)) + 0.12);

    colour = mix(dark * 1.4, colour + flash, far);
    finish(colour, world);
}
