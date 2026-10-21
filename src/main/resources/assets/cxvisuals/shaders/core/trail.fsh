#version 330

layout(std140) uniform Trail {
    mat4 ViewProj;
    vec4 First;
    vec4 Second;
    vec4 Look;
    vec4 Cut;
};

in vec3 offset;
in vec2 local;
in float progress;
in float alpha;
in float aux;
in float tone;
flat in int kind;
flat in float seed;
flat in float extra;

out vec4 fragColor;

const int SMOKE = 0;
const int STREAK = 1;
const int ECHO = 2;
const int EMBERS = 3;
const int ARC = 4;
const int AURORA = 5;
const int GLITCH = 6;
const float TURN = 6.2831853;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x), mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float weight = 0.5;
    for (int i = 0; i < 3; i++) {
        value += weight * vnoise(p);
        p = p * 2.03 + 17.1;
        weight *= 0.5;
    }
    return value;
}

vec4 smoke(float time) {
    float turn = seed * TURN;
    vec2 q = mat2(cos(turn), sin(turn), -sin(turn), cos(turn)) * local;
    float r = length(q);
    float n = fbm(q * 1.6 + vec2(seed * 41.0, -progress * 1.1 - time * 0.12));
    float body = smoothstep(1.0, 0.2, r + (n - 0.5) * 0.85);
    float density = body * smoothstep(1.0, 0.0, r);
    vec3 colour = mix(First.rgb, Second.rgb, clamp(progress * 0.85 + aux * 0.3, 0.0, 1.0));
    float a = body * alpha * First.a * 0.7;
    vec3 lit = colour * (0.45 + 0.9 * n) * (0.6 + 0.4 * Look.x) * a + colour * density * density * 0.25 * alpha * First.a * Look.x;
    return vec4(lit, a * 0.55);
}

vec4 streak(float time) {
    float across = abs(local.y);
    float soft = 1.0 - smoothstep(0.0, 1.0, across);
    float hot = 1.0 - smoothstep(0.0, 0.4, across);
    float flow = 0.72 + 0.28 * sin(progress * 46.0 - time * 16.0 + aux * 5.0 + seed * TURN);
    vec3 colour = mix(First.rgb, Second.rgb, clamp(progress * 1.1 + aux * 0.35, 0.0, 1.0));
    float a = alpha * First.a;
    vec3 light = colour * soft * soft * (0.7 + 0.5 * Look.x) * flow + vec3(hot * hot * 0.6 * (1.0 - progress));
    return vec4(light * a, soft * 0.18 * a);
}

vec4 echo(float time) {
    float aspect = aux * 8.0;
    vec2 p = vec2(local.x * extra, local.y * extra * aspect);
    float radius = extra / 1.9;
    float arm = max(extra * aspect - extra, 0.0);
    float d = length(vec2(p.x, p.y - clamp(p.y, -arm, arm))) - radius;
    float aa = max(fwidth(d), 1.0e-4);
    float inside = 1.0 - smoothstep(-aa, aa, d);
    float rim = exp(-abs(d) / max(radius * 0.07, 1.0e-3));
    float halo = exp(-max(d, 0.0) / max(radius * 0.22 * (0.4 + 0.6 * Look.x) * Look.z, 1.0e-3)) * (1.0 - inside) * 0.5;
    float fres = smoothstep(-radius, 0.0, d);
    float scan = 0.7 + 0.3 * sin(p.y * 42.0 - time * 6.0 + seed * TURN);
    vec3 colour = mix(First.rgb, Second.rgb, tone);
    float a = alpha * First.a;
    vec3 light = colour * (inside * (0.16 + 0.55 * fres) * scan + rim * 0.9 + halo) + vec3(rim * 0.2);
    return vec4(light * a, inside * 0.1 * a);
}

vec4 ember(float time) {
    float r2 = dot(local, local);
    float core = exp(-r2 * 14.0);
    float halo = exp(-r2 * 3.2) * 0.5 * (1.0 - smoothstep(0.7, 1.0, sqrt(r2)));
    float flick = 0.7 + 0.3 * sin(time * 26.0 + seed * 91.0);
    vec3 colour = mix(Second.rgb, First.rgb, 1.0 - progress);
    vec3 hot = mix(colour, vec3(1.0), 0.65 * (1.0 - progress));
    float a = alpha * First.a * flick;
    return vec4((hot * core * 1.7 + colour * halo * Look.x) * a, 0.0);
}

vec4 bolt(float time) {
    float d = abs(local.y);
    float core = exp(-d * d * 22.0);
    float glow = exp(-d * 2.6) * 0.55 * Look.x;
    float shimmer = 0.85 + 0.15 * sin(local.x * 70.0 + time * 40.0 + seed * TURN);
    vec3 colour = mix(First.rgb, Second.rgb, clamp(aux * 0.6 + progress * 0.5, 0.0, 1.0));
    float a = alpha * First.a * shimmer;
    return vec4((mix(colour, vec3(1.0), core * 0.85) * core * 1.8 + colour * glow) * a, 0.0);
}

vec4 curtain(float time) {
    float u = local.x;
    float v = local.y;
    float ray = pow(vnoise(vec2(u * 38.0 + aux * 7.0 + seed * 13.0, time * 0.7)), 1.6);
    float body = pow(1.0 - v, 1.4) * smoothstep(0.0, 0.08, v);
    float hue = 0.5 + 0.5 * sin(u * 7.0 - time * 0.9 + v * 1.8 + aux * 2.0);
    vec3 colour = mix(First.rgb, Second.rgb, hue);
    float a = body * (0.25 + 1.1 * ray) * alpha * First.a;
    return vec4(colour * a * (0.6 + 0.6 * Look.x), 0.0);
}

vec4 pixel(float time) {
    float edge = max(abs(local.x), abs(local.y));
    float frame = smoothstep(0.55, 0.95, edge);
    vec3 colour = aux < 0.4 ? First.rgb : aux < 0.8 ? Second.rgb : vec3(1.0);
    float scan = tone > 0.5 ? 0.6 + 0.4 * step(0.5, fract(local.x * 6.0 + time * 3.0)) : 1.0;
    float a = alpha * First.a;
    vec3 light = colour * (0.65 + 0.7 * frame) * scan + vec3(frame * 0.15);
    return vec4(light * a, a * 0.6);
}

vec4 ring() {
    float r = length(local) * 1.3;
    float line = 0.09 * (1.0 - 0.6 * progress);
    float d = r - 1.0;
    float aa = max(fwidth(r), 1.0e-3);
    float core = 1.0 - smoothstep(line - aa, line + aa, abs(d));
    float glow = exp(-abs(d) / (0.04 + 0.16 * Look.x)) * 0.4 * (1.0 - smoothstep(1.0, 1.3, r));
    float inner = (1.0 - smoothstep(line * 0.7 - aa, line * 0.7 + aa, abs(r - 0.72))) * 0.45;
    float fill = smoothstep(1.0, 0.0, r) * 0.12 * (1.0 - progress);
    vec3 colour = mix(First.rgb, Second.rgb, tone);
    float a = alpha * First.a;
    vec3 light = colour * (core * 1.4 + glow + inner + fill) + vec3(core * 0.25);
    return vec4(light * a, core * 0.25 * a);
}

void main() {
    if (dot(offset, Cut.xyz) < Cut.w) {
        discard;
    }
    float time = Second.a * Look.y;
    vec4 colour;
    if (kind == SMOKE) {
        colour = smoke(time);
    } else if (kind == STREAK) {
        colour = streak(time);
    } else if (kind == ECHO) {
        colour = echo(time);
    } else if (kind == EMBERS) {
        colour = ember(time);
    } else if (kind == ARC) {
        colour = bolt(time);
    } else if (kind == AURORA) {
        colour = curtain(time);
    } else if (kind == GLITCH) {
        colour = pixel(time);
    } else {
        colour = ring();
    }
    if (max(colour.r, max(colour.g, max(colour.b, colour.a))) < 0.002) {
        discard;
    }
    fragColor = colour;
}
