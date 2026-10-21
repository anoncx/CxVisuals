#version 330

layout(std140) uniform Death {
    mat4 Unproject;
    vec4 Origin;
    vec4 Floor;
    vec4 Lens;
    vec4 First;
    vec4 Second;
    vec4 Frame;
    vec4 Focus;
};

uniform sampler2D CorpseSampler;
uniform sampler2D CorpseDepthSampler;
uniform sampler2D SceneDepthSampler;

in vec2 texCoord;

out vec4 fragColor;

const int RITUAL = 0;
const int DISSOLVE = 1;
const int ASCEND = 2;
const int GLITCH = 3;
const int MATRIX = 4;
const int VOID = 5;
const int FALL_SEARCH = 40;

float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float hash3(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float noise(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(mix(hash3(i), hash3(i + vec3(1, 0, 0)), f.x), mix(hash3(i + vec3(0, 1, 0)), hash3(i + vec3(1, 1, 0)), f.x), f.y);
    float b = mix(mix(hash3(i + vec3(0, 0, 1)), hash3(i + vec3(1, 0, 1)), f.x), mix(hash3(i + vec3(0, 1, 1)), hash3(i + vec3(1, 1, 1)), f.x), f.y);
    return mix(a, b, f.z);
}

vec3 place(vec2 pixel, float depth) {
    vec4 point = Unproject * vec4(pixel / Lens.zw * 2.0 - 1.0, depth * Lens.x + Lens.y, 1.0);
    return point.xyz / point.w;
}

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

vec4 body(vec2 at) {
    return texelFetch(CorpseSampler, ivec2(at), 0);
}

bool hidden(vec2 at, vec2 pixel) {
    vec3 p = place(at, texelFetch(CorpseDepthSampler, ivec2(at), 0).r);
    vec3 wall = place(pixel, texelFetch(SceneDepthSampler, ivec2(pixel), 0).r);
    return dot(p, p) > dot(wall, wall) * 1.0004 + 0.0004;
}

vec4 dots(vec2 pixel, float t, float tall, float bottom) {
    float cell = max(4.0, floor(tall / 22.0));
    vec2 grid = floor(pixel / cell);
    float rows = max(tall / cell, 1.0);
    float bottomRow = floor(bottom / cell);
    float gravity = rows * 3.2;
    float best = 0.0;
    vec2 from = pixel;
    for (int k = 0; k < FALL_SEARCH; k++) {
        vec2 source = grid + vec2(0.0, float(k));
        float height = clamp((source.y - bottomRow) / rows, 0.0, 1.0);
        float age = max(t - (0.22 + height * 0.42 + hash(source) * 0.16), 0.0);
        float drop = 0.5 * gravity * age * age;
        if (drop + 1.0 < float(k)) {
            continue;
        }
        vec2 centre = source + 0.5;
        vec4 seen = body(centre * cell);
        if (seen.a < 0.01) {
            continue;
        }
        float life = 1.0 - smoothstep(rows * 0.15, rows * 0.4, drop);
        float drift = (hash(source + 7.0) - 0.5) * drop * 0.3;
        vec2 spot = (centre + vec2(drift, -drop)) * cell;
        float radius = cell * 0.5 * (0.62 + 0.38 * luma(seen.rgb)) * life;
        float cover = 1.0 - smoothstep(radius - 0.75, radius + 0.35, length(pixel - spot));
        if (cover > best) {
            best = cover;
            from = centre * cell;
        }
    }
    return vec4(from, best, 0.0);
}

void main() {
    vec2 pixel = gl_FragCoord.xy;
    int fate = int(Floor.w);
    float t = Floor.z;
    float time = Second.a;
    float intensity = First.a;
    float tall = max(Frame.y, 8.0);
    float bottom = Frame.w;

    if (fate == MATRIX) {
        float show = smoothstep(0.02, 0.2, t);
        vec4 found = dots(pixel, t, tall, bottom);
        vec4 plain = body(pixel);
        float plainCover = plain.a > 0.01 && !hidden(pixel, pixel) ? 1.0 - show : 0.0;
        float dotCover = found.z > 0.01 && !hidden(found.xy, pixel) ? found.z * show : 0.0;
        vec4 source = body(found.xy);
        vec3 bead = mix(vec3(1.0), First.rgb, 0.3) * (0.75 + 0.6 * luma(source.rgb));
        vec3 colour = plain.rgb * plainCover + bead * dotCover;
        float alpha = clamp(plainCover + dotCover, 0.0, 1.0);
        if (alpha < 0.01) {
            discard;
        }
        fragColor = vec4(colour, alpha);
        return;
    }

    vec2 at = pixel;
    float darken = 1.0;
    vec3 heat = vec3(0.0);
    if (fate == GLITCH) {
        float burst = smoothstep(0.0, 0.08, t) * (1.0 - smoothstep(0.38, 0.48, t));
        float band = floor((pixel.y - bottom) / tall * 26.0);
        float active = step(0.62, hash(vec2(band, floor(time * 11.0))));
        at.x += sin(band * 1.7 + time * 9.0) * tall * 0.045 * active * burst;
        float block = max(1.0, floor(tall / 34.0 * burst + 1.0));
        at = (floor(at / block) + 0.5) * block;
    } else if (fate == VOID) {
        vec2 centre = Focus.xy;
        float horizon = Focus.z;
        float pull = smoothstep(0.08, 0.8, t);
        vec2 d = pixel - centre;
        float r = length(d);
        if (r < horizon * 1.02 && horizon > 0.5) {
            discard;
        }
        float squeeze = max(1.0 - pull, 0.02);
        float reach = horizon + (r - horizon) / squeeze;
        float spin = pull * pull * 5.0 / (1.0 + max(r - horizon, 0.0) / max(tall * 0.12, 1.0));
        float angle = atan(d.y, d.x) - spin;
        at = centre + vec2(cos(angle), sin(angle)) * max(reach, 0.0);
        float near = 1.0 - smoothstep(horizon, horizon + tall * 0.35, r);
        darken = 1.0 - near * 0.85 * pull;
        heat = mix(Second.rgb, First.rgb, near) * near * pull * 1.4;
    }

    vec4 shade = body(at);
    if (shade.a < 0.01 || hidden(at, pixel)) {
        discard;
    }
    vec3 p = place(at, texelFetch(CorpseDepthSampler, ivec2(at), 0).r);
    vec3 local = p - Origin.xyz;
    float h = clamp(local.y / max(Origin.w, 0.1), 0.0, 1.0);
    vec3 colour = shade.rgb;
    float alpha = 1.0;
    vec3 glow = vec3(0.0);

    if (fate == RITUAL) {
        float above = p.y - Floor.x;
        if (above < 0.0) {
            discard;
        }
        float taint = smoothstep(0.15, 0.6, t);
        colour = mix(colour, colour * vec3(0.32, 0.06, 0.05), taint * 0.85);
        float seam = exp(-above * 9.0) * smoothstep(0.2, 0.3, t);
        glow = mix(Second.rgb, First.rgb, exp(-above * 22.0)) * seam * 1.6;
        colour += glow * 0.2;
    } else if (fate == DISSOLVE) {
        float n = clamp((noise(local * 7.5) * 0.7 + noise(local * 17.0) * 0.3 - 0.25) * 2.0, 0.0, 1.0);
        float keep = n - (t * 1.15 - 0.12 + (1.0 - h) * 0.12);
        if (keep < 0.0) {
            discard;
        }
        float edge = 1.0 - smoothstep(0.0, 0.09, keep);
        colour = mix(colour, colour * 0.25, edge * 0.6);
        glow = mix(Second.rgb, First.rgb, edge) * edge * edge * 2.2;
    } else if (fate == ASCEND) {
        float bleach = smoothstep(0.08, 0.6, t);
        vec3 light = mix(vec3(1.0, 0.97, 0.9), First.rgb, 0.2);
        colour = mix(colour, colour * 0.35 + light * (0.35 + 0.45 * luma(colour)), bleach * 0.85);
        glow = light * bleach * (0.08 + 0.18 * h);
        alpha = 1.0 - smoothstep(0.45, 0.92, t);
    } else if (fate == GLITCH) {
        float burst = smoothstep(0.0, 0.08, t) * (1.0 - smoothstep(0.38, 0.48, t));
        float split = tall * 0.012 * burst;
        colour = vec3(body(at + vec2(split, 0.0)).r, colour.g, body(at - vec2(split, 0.0)).b);
        float scan = 1.0 - 0.18 * burst * step(0.5, fract(pixel.y / 3.0));
        vec3 mono = mix(vec3(1.0), First.rgb, 0.3) * (0.4 + 0.8 * luma(colour));
        colour = mix(colour, mono, burst * 0.35) * scan;
        float crush = smoothstep(0.45, 0.78, t);
        vec3 beam = mix(vec3(1.0), First.rgb, 0.35);
        colour = mix(colour, beam * (0.55 + 0.9 * luma(colour)), crush * 0.7);
        glow = beam * crush * crush * 0.9;
    } else if (fate == VOID) {
        colour *= darken;
        glow = heat;
    }

    vec3 result = colour + glow * intensity;
    fragColor = vec4(result * alpha, alpha);
}
