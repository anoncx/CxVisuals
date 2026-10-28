#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform Circle {
    mat4 ViewProj;
    vec4 Rim[64];
    vec4 Look;
    vec4 First;
    vec4 Second;
    vec4 Cut;
};

layout(location = 0) in vec3 offset;
layout(location = 1) in vec2 local;
layout(location = 2) in float progress;
layout(location = 3) in float alpha;
layout(location = 4) flat in int kind;
layout(location = 5) flat in float seed;
layout(location = 6) flat in float radius;

layout(location = 0) out vec4 fragColor;

const int FLOOR = 0;
const int WALL = 1;
const int DOTS = 1;
const int GLYPH = 2;
const int RIPPLE = 3;
const int SONAR = 4;
const int PRISM = 5;
const float TURN = 6.2831853;
const float POINTS = 128.0;
const int SEARCH = 4;

vec2 rimPoint(int index) {
    int k = (index + 128) & 127;
    vec4 pair = Rim[k >> 1];
    return (k & 1) == 0 ? pair.xy : pair.zw;
}

float rimRadius(float turn) {
    float position = fract(turn) * POINTS;
    int base = int(floor(position));
    return mix(length(rimPoint(base)), length(rimPoint(base + 1)), position - float(base));
}

float rimDistance(vec2 p, int base) {
    float best = 1.0e9;
    vec2 a = rimPoint(base - SEARCH);
    for (int k = 1 - SEARCH; k <= SEARCH + 1; k++) {
        vec2 b = rimPoint(base + k);
        vec2 e = b - a;
        vec2 w = p - a;
        vec2 gap = w - e * clamp(dot(w, e) / max(dot(e, e), 1.0e-8), 0.0, 1.0);
        best = min(best, dot(gap, gap));
        a = b;
    }
    return sqrt(best);
}

vec3 tone(float turn) {
    return mix(First.rgb, Second.rgb, 0.5 + 0.5 * sin(turn * TURN + seed * TURN));
}

vec3 spectrum(float h) {
    return clamp(abs(fract(h + vec3(0.0, 0.6667, 0.3333)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

float band(float distance, float width, float aa) {
    return 1.0 - smoothstep(width - aa, width + aa, abs(distance));
}

float hash(float n) {
    return fract(sin(n * 91.3458) * 47453.5453);
}

vec4 floorRing() {
    float r = length(local);
    float turn = atan(local.y, local.x) / TURN;
    float rim = rimRadius(turn);
    float radial = r - rim;
    float near = rimDistance(local, int(floor(fract(turn) * POINTS)));
    float edge = mix(radial, radial < 0.0 ? -near : near, smoothstep(0.35, 0.6, r / rim));
    float aa = max(fwidth(r), 1.0e-4);
    float line = max(Look.y * (1.0 - 0.35 * progress) / radius, aa * 0.75);
    float reach = max(0.32 * Look.z / radius, 1.0e-3);
    float glow = exp(-abs(edge) / reach) * 0.55 * Look.z * (1.0 - smoothstep(reach * 2.4, reach * 3.4, abs(edge)));
    float inside = 1.0 - smoothstep(-aa, aa, edge);
    float depth = clamp(r / rim, 0.0, 1.0);
    vec3 colour = tone(turn);
    float time = Second.a;
    int style = int(Look.x);

    float core = band(edge, line, aa);
    vec3 light;
    if (style == DOTS) {
        float perimeter = TURN * rim;
        float count = clamp(floor(perimeter / (line * 4.2)), 12.0, 160.0);
        float cell = fract(turn * count + 0.5) - 0.5;
        float index = floor(turn * count + 0.5);
        float twinkle = 0.65 + 0.35 * sin(time * 5.0 + index * 1.7);
        float lit = step(hash(index + seed * 13.0), 0.35 + progress * 2.5);
        float bead = 1.0 - smoothstep(line * 1.15 - aa, line * 1.15 + aa, length(vec2(cell * perimeter / count, edge)));
        core = bead * lit * twinkle;
        light = colour * (core * 1.5 + glow * 0.55);
    } else if (style == GLYPH) {
        float segments = 8.0;
        float along = (abs(fract(turn * segments) - 0.5) - 0.36) * TURN * rim / segments;
        float bar = 1.0 - smoothstep(line * 1.3 - aa, line * 1.3 + aa, length(vec2(max(along, 0.0), edge)));
        float chase = pow(fract(turn - fract(progress * 1.4 + seed)), 3.0);
        float lane = 1.0 - smoothstep(0.0, line * 6.0, max(along, 0.0));
        core = bar * (0.55 + 0.9 * chase);
        light = colour * (core * 1.4 + glow * (0.4 + chase) * lane);
    } else if (style == RIPPLE) {
        core = 0.0;
        for (int k = 0; k < 3; k++) {
            float lag = float(k) * (0.14 + 0.1 * progress);
            core += band(edge + lag, line * (1.0 - float(k) * 0.25), aa) * (1.0 - float(k) * 0.3);
        }
        light = colour * (core * 1.3 + glow * 0.8);
    } else if (style == SONAR) {
        float fill = inside * (0.06 + 0.4 * depth * depth * depth);
        float wedge = pow(fract(turn - fract(progress * 1.25 + seed)), 10.0) * inside * depth * 0.9;
        light = colour * (core * 1.3 + fill + wedge + glow * 0.6);
    } else if (style == PRISM) {
        float split = line * (1.4 + 2.0 * progress);
        vec3 rgb = vec3(band(edge - split, line * 0.7, aa), band(edge, line * 0.7, aa), band(edge + split, line * 0.7, aa));
        vec3 sheen = mix(spectrum(turn + time * 0.1), colour, 0.35);
        light = rgb * 1.4 * mix(vec3(1.0), sheen, 0.5) + sheen * glow;
        core = max(rgb.r, max(rgb.g, rgb.b));
    } else {
        float hot = band(edge, line * 0.35, aa);
        light = colour * (core * 1.2 + glow) + vec3(hot * 0.5);
    }

    float pool = inside * First.a * (0.25 + 0.75 * depth * depth) * 0.45;
    light += mix(First.rgb, Second.rgb, 0.5) * pool;
    float fade = alpha * Look.w;
    return vec4(light * fade, clamp(core * 0.45, 0.0, 0.6) * fade);
}

vec4 wallLight() {
    float v = local.y;
    float u = local.x;
    float time = Second.a;
    int style = int(Look.x);
    float body = pow(1.0 - v, 1.8) * 0.5 + exp(-v * 14.0) * 0.7;
    float pattern;
    if (style == DOTS) {
        vec2 cell = fract(vec2(u * 96.0, v * 9.0 - progress * 4.0)) - 0.5;
        pattern = smoothstep(0.32, 0.18, length(cell)) * 1.6;
    } else if (style == GLYPH) {
        pattern = step(0.14, abs(fract(u * 8.0) - 0.5)) * (0.7 + 0.6 * pow(fract(u - progress * 1.4 - seed), 3.0));
    } else if (style == RIPPLE) {
        pattern = 0.6 + 0.4 * sin(v * 22.0 - progress * 26.0);
    } else if (style == SONAR) {
        pattern = 0.4 + 1.2 * pow(fract(u - progress * 1.25 - seed), 8.0);
    } else if (style == PRISM) {
        pattern = 1.0;
    } else {
        pattern = 0.8 + 0.2 * sin(v * 50.0 - time * 8.0);
    }
    vec3 colour = style == PRISM ? mix(spectrum(v * 0.8 + u + time * 0.05), tone(u), 0.4) : tone(u);
    return vec4(colour * body * pattern * alpha * Look.w * 0.7, 0.0);
}

vec4 spark() {
    float r2 = dot(local, local);
    float core = exp(-r2 * 14.0);
    float halo = exp(-r2 * 3.5) * 0.45 * (1.0 - smoothstep(0.7, 1.0, sqrt(r2)));
    vec3 colour = mix(First.rgb, Second.rgb, seed);
    return vec4((mix(colour, vec3(1.0), 0.65) * core * 1.4 + colour * halo) * alpha * Look.w, 0.0);
}

void main() {
    if (dot(offset, Cut.xyz) < Cut.w) {
        discard;
    }
    vec4 colour = kind == FLOOR ? floorRing() : kind == WALL ? wallLight() : spark();
    if (max(colour.r, max(colour.g, max(colour.b, colour.a))) < 0.002) {
        discard;
    }
    fragColor = colour;
}
