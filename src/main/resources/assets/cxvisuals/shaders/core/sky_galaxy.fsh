#version 330
#extension GL_ARB_separate_shader_objects : require

#moj_import <cxvisuals:sky.glsl>

const vec3 POLE = vec3(0.7505, 0.3502, 0.5604);
const vec3 HEART = vec3(-0.2802, 0.9367, -0.2092);

void main() {
    vec3 world;
    vec3 dir;
    if (!skyRay(world, dir)) {
        discard;
    }
    float time = Lens.y;
    float lat = dot(dir, POLE);
    float band = max(exp(-lat * lat * 9.0) - 0.03, 0.0) / 0.97;
    vec3 colour = vec3(0.006, 0.008, 0.02) + Second.rgb * band * 0.015;
    vec3 lights = stars(dir, 1.0 + band * 3.0);

    if (band > 0.0) {
        vec3 p = dir * 2.6;
        float cloud = fbm(p + vec3(0.0, time * 0.006, 0.0));
        float dust = fbm(p * 2.3 + vec3(7.3, 1.1, 4.7) + cloud * 0.9);
        float body = band * smoothstep(0.25, 0.85, cloud + band * 0.35);
        float spine = exp(-lat * lat * 40.0);
        float lanes = smoothstep(0.42, 0.7, dust) * spine;
        float heart = max(dot(dir, HEART), 0.0);
        heart *= heart;
        float core = exp(-lat * lat * 26.0) * heart * heart;
        vec3 gas = mix(Second.rgb, First.rgb, smoothstep(0.3, 0.8, cloud)) * body * 0.8;
        gas += mix(First.rgb, vec3(1.0), 0.6) * spine * (0.05 + cloud * 0.12);
        gas += vec3(1.0, 0.82, 0.62) * core * 0.8;
        colour += gas * (1.0 - lanes * 0.75);
        lights *= 1.0 - lanes * 0.6;
    }

    finish(colour + lights, world);
}
