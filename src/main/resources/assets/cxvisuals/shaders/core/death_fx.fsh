#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DeathFx {
    mat4 ViewProj;
    vec4 First;
    vec4 Second;
};

layout(location = 0) in vec2 local;
layout(location = 1) in float progress;
layout(location = 2) in float alpha;
layout(location = 3) flat in int kind;
layout(location = 4) flat in float seed;
layout(location = 5) flat in float size;

layout(location = 0) out vec4 fragColor;

const int SIGIL = 0;
const int EMBER = 1;
const int BEAM = 2;
const int HALO = 3;
const int CORE = 4;
const int FLECK = 5;
const int SHARD = 6;
const float TURN = 6.2831853;

float band(float distance, float width, float aa) {
    return 1.0 - smoothstep(width - aa, width + aa, abs(distance));
}

float segment(vec2 p, vec2 a, vec2 b) {
    vec2 e = b - a;
    vec2 w = p - a;
    return length(w - e * clamp(dot(w, e) / dot(e, e), 0.0, 1.0));
}

float hash(float n) {
    return fract(sin(n * 91.3458) * 47453.5453);
}

vec2 corner(int i, float radius, float spin) {
    float angle = float(i) * TURN / 5.0 + spin + TURN * 0.25;
    return vec2(cos(angle), sin(angle)) * radius;
}

vec4 sigil() {
    float r = length(local);
    float aa = max(fwidth(r), 1.0e-4) * 1.2;
    float spin = progress * 0.9;
    float angle = atan(local.y, local.x);
    float sweep = smoothstep(0.0, 0.2, progress);
    float reveal = sweep >= 1.0 ? 1.0 : 1.0 - smoothstep(sweep - 0.03, sweep, fract((angle - spin) / TURN));
    float lines = band(r - 0.86, 0.012, aa) + band(r - 0.74, 0.008, aa) + band(r - 0.22, 0.006, aa) * 0.8;
    float runes = 0.0;
    if (r > 0.75 && r < 0.85) {
        float slot = floor((angle - spin * 0.5) / TURN * 36.0);
        float cell = fract((angle - spin * 0.5) / TURN * 36.0);
        float glyph = step(0.35, hash(slot + seed * 17.0));
        float stroke = band(cell - 0.5, 0.08, fwidth(cell)) * band(r - 0.8, 0.03 * glyph + 0.005, aa);
        runes = stroke * glyph + band(r - 0.8, 0.004, aa) * (1.0 - glyph) * 0.5;
    }
    float star = 0.0;
    for (int i = 0; i < 5; i++) {
        star += band(segment(local, corner(i, 0.74, spin), corner(i + 2, 0.74, spin)), 0.007, aa);
    }
    float shape = clamp(lines + runes + star, 0.0, 1.0) * reveal;
    float pulse = 0.85 + 0.15 * sin(Second.a * 6.0 + r * 10.0);
    float pool = (1.0 - smoothstep(0.0, 0.86, r)) * 0.22 * smoothstep(0.1, 0.4, progress);
    vec3 hot = mix(Second.rgb, First.rgb, shape);
    vec3 light = hot * shape * 1.6 * pulse + Second.rgb * pool + Second.rgb * exp(-abs(r - 0.86) * 18.0) * 0.25;
    return vec4(light * alpha * First.a, 0.0);
}

vec4 ember() {
    float r2 = dot(local, local);
    float core = exp(-r2 * 12.0);
    float halo = exp(-r2 * 3.0) * 0.4 * (1.0 - smoothstep(0.7, 1.0, sqrt(r2)));
    vec3 colour = mix(First.rgb, Second.rgb, seed);
    return vec4((mix(colour, vec3(1.0), 0.5) * core * 1.3 + colour * halo) * alpha * First.a, 0.0);
}

vec4 beam() {
    float across = exp(-local.x * local.x * 5.0);
    float core = exp(-local.x * local.x * 40.0);
    float fade = pow(1.0 - local.y, 0.7) * smoothstep(0.0, 0.02, local.y);
    float ripple = 0.85 + 0.15 * sin(local.y * 60.0 - Second.a * 5.0);
    vec3 colour = mix(vec3(1.0), First.rgb, 0.35);
    return vec4(colour * (across * 0.35 + core * 0.6) * fade * ripple * alpha * First.a, 0.0);
}

vec4 halo() {
    float r = length(local);
    float aa = max(fwidth(r), 1.0e-4);
    float ring = band(r - 0.78, 0.05, aa);
    float glow = exp(-abs(r - 0.78) * 9.0) * 0.5;
    vec3 colour = mix(vec3(1.0, 0.95, 0.8), First.rgb, 0.3);
    return vec4(colour * (ring * 1.4 + glow) * alpha * First.a, 0.0);
}

vec4 core() {
    float r = length(local);
    float aa = max(fwidth(r), 1.0e-4);
    float open = smoothstep(0.42, 0.72, progress);
    float shut = smoothstep(0.86, 0.93, progress);
    float hole = 0.5 * open * (1.0 - shut);
    float angle = atan(local.y, local.x);
    float swirl = 0.6 + 0.4 * sin(angle * 3.0 - Second.a * 7.0 + r * 18.0);
    float disk = 1.0 - smoothstep(hole - aa, hole + aa, r);
    float ring = exp(-abs(r - hole * 1.18) * 30.0) * swirl * open * (1.0 - shut);
    float haze = exp(-max(r - hole, 0.0) * 6.0) * 0.35 * open * (1.0 - shut);
    float burst = smoothstep(0.92, 0.94, progress) * (1.0 - smoothstep(0.94, 1.0, progress));
    float wave = band(r - (progress - 0.92) * 9.0, 0.04, aa) * burst;
    vec3 light = First.rgb * (ring * 1.8 + haze) + mix(vec3(1.0), First.rgb, 0.4) * wave * 1.6;
    float cover = disk * 0.97;
    return vec4(light * (1.0 - cover) * First.a, cover);
}

vec4 fleck() {
    vec2 q = abs(local);
    float box = 1.0 - smoothstep(0.55, 0.75, max(q.x, q.y));
    float glow = exp(-dot(local, local) * 2.5) * 0.4;
    vec3 colour = mix(First.rgb, Second.rgb, progress);
    return vec4(colour * (box * 1.2 + glow) * alpha * First.a, 0.0);
}

vec4 shard() {
    vec2 q = abs(local);
    float box = 1.0 - smoothstep(0.82, 0.9, max(q.x, q.y));
    int channel = int(seed * 3.0);
    vec3 colour = channel == 0 ? vec3(1.0, 0.25, 0.3) : channel == 1 ? vec3(0.3, 1.0, 0.6) : vec3(0.35, 0.55, 1.0);
    colour = mix(colour, First.rgb, 0.35);
    return vec4(colour * box * alpha * First.a, box * alpha * 0.3);
}

void main() {
    vec4 colour;
    if (kind == SIGIL) {
        colour = sigil();
    } else if (kind == EMBER) {
        colour = ember();
    } else if (kind == BEAM) {
        colour = beam();
    } else if (kind == HALO) {
        colour = halo();
    } else if (kind == CORE) {
        colour = core();
    } else if (kind == FLECK) {
        colour = fleck();
    } else {
        colour = shard();
    }
    if (max(colour.r, max(colour.g, max(colour.b, colour.a))) < 0.002) {
        discard;
    }
    fragColor = colour;
}
