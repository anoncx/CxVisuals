#version 330
#extension GL_ARB_separate_shader_objects : require

#moj_import <cxvisuals:sky.glsl>

const vec3 SUN = vec3(0.0, 0.0995, -0.995);
const vec3 WARM = vec3(1.0, 0.78, 0.5);

void main() {
    vec3 world;
    vec3 dir;
    if (!skyRay(world, dir)) {
        discard;
    }
    float time = Lens.y;
    float rise = max(world.y, 0.0);
    float toward = dot(world, SUN);
    float off = 1.0 - toward;
    float side = max(toward, 0.0);

    vec3 low = mix(First.rgb, WARM, 0.35 + 0.4 * side * side) * 1.9;
    vec3 mid = mix(Second.rgb, First.rgb, 0.3) * 0.9;
    vec3 high = Second.rgb * 0.1 + vec3(0.012, 0.016, 0.05);
    vec3 colour = mix(low, mid, smoothstep(0.0, 0.3, rise));
    colour = mix(colour, high, smoothstep(0.2, 0.85, rise));

    colour += stars(dir, 1.0) * smoothstep(0.35, 0.85, rise) * 0.6;
    colour += WARM * (exp(-off * 16.0) * 1.6 + exp(-off * 2.5) * 0.4);
    float disc = 1.0 - smoothstep(0.0029, 0.0034, off);
    colour = mix(colour, vec3(5.0, 4.2, 2.8), disc);

    float reach = 1.0 - smoothstep(0.02, 0.5, rise);
    if (reach > 0.0) {
        float wisp = noise(vec3(world.x * 2.2 + time * 0.012, rise * 19.0, world.z * 2.2));
        wisp += noise(vec3(world.x * 5.0 - time * 0.02, rise * 44.0, world.z * 5.0)) * 0.5;
        float cloud = smoothstep(0.78, 1.12, wisp) * reach * (1.0 - disc * 0.75);
        vec3 lit = mix(Second.rgb * 0.16, mix(First.rgb, WARM, 0.5) * 2.2, side * side * side);
        colour = mix(colour, lit, cloud * 0.85);
    }

    finish(colour, world);
}
