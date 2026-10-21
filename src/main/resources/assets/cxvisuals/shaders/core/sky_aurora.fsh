#version 330

#moj_import <cxvisuals:sky.glsl>

const int LAYERS = 8;
const float SPACING = 0.8;
const float DEPTH = 1.7;
const float WIDTH = 0.42;
const float PI = 3.14159265;

float streaks(float x) {
    float cell = floor(x);
    float f = x - cell;
    return mix(hash12(vec2(cell, 3.7)), hash12(vec2(cell + 1.0, 3.7)), f * f * (3.0 - 2.0 * f));
}

void main() {
    vec3 world;
    vec3 dir;
    if (!skyRay(world, dir)) {
        discard;
    }
    float time = Lens.y;
    float rise = world.y;
    vec3 colour = mix(vec3(0.004, 0.012, 0.028), vec3(0.001, 0.004, 0.014), clamp(rise, 0.0, 1.0));
    colour += First.rgb * 0.03 * exp(-max(rise, 0.0) * 5.0);
    colour += stars(dir, 1.0);

    if (rise > 0.02) {
        float jitter = hash12(gl_FragCoord.xy);
        vec3 glow = vec3(0.0);
        for (int i = 0; i < LAYERS; i++) {
            float layer = (float(i) + 0.35 + jitter * 0.3) / float(LAYERS);
            float height = 1.0 + layer * DEPTH;
            vec2 p = world.xz * (height / rise);
            float sway = sin(p.x * 0.21 + time * 0.03) * 1.5 + sin(p.x * 0.47 - time * 0.021 + p.y * 0.13) * 0.7;
            float fold = (p.y + sway) * SPACING;
            float each = floor(fold / PI + 0.5);
            float off = fold - each * PI;
            float wide = max(WIDTH, abs(p.y) * SPACING * DEPTH / (float(LAYERS) * height));
            float ridge = exp(-off * off / (wide * wide)) * (WIDTH / wide);
            float alive = smoothstep(0.15, 0.75, 0.5 + 0.5 * sin(p.x * 0.11 + each * 2.4 + time * 0.05));
            float far = exp(-length(p) * 0.07);
            float rays = streaks(p.x * 2.6 + sway * 2.0 + time * 0.12);
            rays = mix(0.55, 0.25 + 0.75 * rays * rays, far);
            glow += mix(First.rgb, Second.rgb, smoothstep(0.0, 0.7, layer)) * ridge * alive * rays * far * exp(-layer * 1.6);
        }
        colour += glow * 1.9 * smoothstep(0.04, 0.26, rise);
    }

    finish(colour, world);
}
