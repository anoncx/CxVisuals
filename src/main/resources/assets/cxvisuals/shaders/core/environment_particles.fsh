#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec4 tint;
layout(location = 1) in vec2 local;
layout(location = 2) flat in int shape;
layout(location = 3) flat in float detail;
layout(location = 4) flat in float seed;

layout(location = 0) out vec4 fragColor;

const int GLOW = 0;
const int PETAL = 1;
const int FLAKE = 2;
const int SPARK = 3;
const int LANTERN = 4;
const int METEOR = 5;
const int HALO = 6;
const int WING = 7;
const int BUBBLE = 8;
const int RIPPLE = 9;
const int STREAK = 10;
const float TURN = 6.2831853;

float pixel;

float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

vec4 glow() {
    float r2 = dot(local, local);
    float r = sqrt(r2);
    float core = exp(-r2 * 90.0);
    float halo = exp(-r2 * 6.5) * 0.6 + exp(-r * 4.5) * 0.18;
    float rays = detail * (exp(-abs(local.x) * 48.0) + exp(-abs(local.y) * 48.0)) * exp(-r2 * 5.0) * 0.7;
    float edge = 1.0 - smoothstep(0.7, 1.0, r);
    vec3 hot = mix(tint.rgb, vec3(1.0), 0.7);
    return vec4((hot * core * 1.6 + tint.rgb * (halo + rays)) * edge * tint.a, 0.0);
}

vec4 halo() {
    float r2 = dot(local, local);
    float r = sqrt(r2);
    float light = (exp(-r2 * 5.0) * 0.75 + exp(-r * 3.2) * 0.35) * (1.0 - smoothstep(0.75, 1.0, r));
    return vec4(tint.rgb * light * tint.a, 0.0);
}

vec4 wing() {
    vec2 p = vec2(local.x * 0.5 + 0.5, local.y);
    vec2 fore = (p - vec2(0.48, 0.3)) / vec2(0.52, 0.64);
    vec2 hind = (p - vec2(0.36, -0.44)) / vec2(0.4, 0.5);
    float d = min(length(fore) - 1.0, length(hind) - 1.0) * 0.6;
    float aa = max(pixel, 1.0e-4) * 1.2;
    float cover = 1.0 - smoothstep(-aa, aa, d);
    vec3 base;
    vec3 trim;
    float spotted = 0.0;
    if (seed < 0.4) {
        base = vec3(1.0, 0.52, 0.1);
        trim = vec3(0.05, 0.035, 0.03);
        spotted = 1.0;
    } else if (seed < 0.68) {
        base = vec3(0.16, 0.55, 1.0);
        trim = vec3(0.02, 0.04, 0.14);
    } else if (seed < 0.86) {
        base = vec3(1.0, 0.9, 0.36);
        trim = vec3(0.3, 0.22, 0.06);
    } else {
        base = vec3(0.98, 0.97, 0.94);
        trim = vec3(0.16, 0.16, 0.2);
    }
    float border = smoothstep(-0.13, -0.05, d);
    float veins = smoothstep(0.9, 1.0, abs(cos(atan(p.y, p.x + 0.2) * 7.0))) * 0.4 * (1.0 - border) * smoothstep(0.15, 0.5, p.x);
    vec3 colour = mix(base * mix(0.72, 1.1, p.x), trim, max(border, veins));
    float spots = border * spotted * smoothstep(0.55, 0.9, sin(p.x * 30.0) * sin(p.y * 24.0));
    colour = mix(colour, vec3(1.0), spots * 0.85);
    colour += vec3(0.08, 0.2, 0.3) * step(0.4, seed) * step(seed, 0.68) * smoothstep(0.2, 0.8, p.x) * (1.0 - border);
    colour = mix(colour, trim, smoothstep(0.12, 0.0, p.x));
    float alpha = cover * tint.a;
    return vec4(colour * tint.rgb * alpha, alpha);
}

vec4 bubble() {
    float r = length(local);
    if (detail >= 0.5) {
        float burst = detail * 2.0 - 1.0;
        float band = exp(-pow((r - 0.55 - burst * 0.4) * 12.0, 2.0));
        float broken = step(0.35, fract(atan(local.y, local.x) * 1.9 + seed * 7.0));
        return vec4(vec3(0.92, 0.96, 1.0) * band * broken * (1.0 - burst) * tint.a, 0.0);
    }
    float aa = max(pixel, 1.0e-4);
    float cover = 1.0 - smoothstep(0.94 - aa, 0.94 + aa, r);
    float rim = smoothstep(0.5, 0.94, r);
    float film = r * 1.6 + local.y * 0.45 + local.x * 0.2 + seed * 3.0 + detail * 2.0;
    vec3 hue = 0.55 + 0.45 * cos(TURN * (film + vec3(0.0, 0.33, 0.67)));
    vec2 g = local - vec2(-0.34, 0.38);
    vec2 h = local - vec2(0.3, -0.42);
    float glint = exp(-dot(g, g) * 60.0) + exp(-dot(h, h) * 180.0) * 0.5;
    float alpha = cover * (0.05 + rim * rim * 0.5) * tint.a;
    return vec4(hue * tint.rgb * alpha + vec3(glint * cover * tint.a * 0.9), alpha);
}

vec4 ripple() {
    float r = length(local);
    float p = detail;
    float spread = 1.0 - pow(1.0 - p, 2.4);
    float fade = (1.0 - p) * (1.0 - p);
    vec2 dir = local / max(r, 1.0e-4);
    float lit = 0.5 + 0.5 * dot(dir, normalize(vec2(-0.55, 0.83)));
    float jitter = 0.9 + 0.2 * fract(seed * 7.31);
    float crest = 0.0;
    float trough = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float radius = spread * (0.92 - 0.27 * fi) * jitter;
        float base = (0.03 + 0.045 * p) * (1.0 + 0.35 * fi);
        float w = max(base, pixel * 1.8);
        float arrive = smoothstep(0.06 * fi, 0.2 + 0.06 * fi, p);
        float amp = (base / w) * (1.0 - 0.3 * fi) * arrive;
        float d = r - radius;
        float t = d + w * 1.7;
        crest += amp * exp(-d * d / (w * w));
        trough += amp * exp(-t * t / (w * w * 1.4));
    }
    float edge = 1.0 - smoothstep(0.78, 1.0, r);
    float flash = exp(-r * r * 260.0) * exp(-p * 14.0);
    float bright = min(crest, 1.4) * fade * (0.28 + 0.72 * lit * lit);
    float dark = min(trough, 1.4) * fade * (0.15 + 0.5 * (1.0 - lit));
    vec3 hot = mix(tint.rgb, vec3(1.0), 0.6);
    float cover = clamp(dark * 0.55, 0.0, 0.7) * edge * tint.a;
    vec3 light = (hot * bright * 0.95 + vec3(flash) * 0.9) * edge * tint.a;
    return vec4(light, cover);
}

vec4 streak() {
    float t = local.x * 0.5 + 0.5;
    float body = smoothstep(0.0, 0.6, t) * (1.0 - smoothstep(0.94, 1.0, t));
    float head = smoothstep(0.55, 1.0, t);
    float w = max(0.26, pixel);
    float across = exp(-local.y * local.y / (w * w)) * (1.0 - smoothstep(0.78, 1.0, abs(local.y)));
    float energy = clamp(0.42 / w, 0.0, 1.0);
    float a = across * body * mix(0.45, 1.0, head) * energy * mix(0.8, 1.0, detail) * tint.a * 1.5;
    vec3 hot = mix(tint.rgb, vec3(1.0), 0.4 * head);
    return vec4(hot * a, a * 0.5);
}

vec4 petal() {
    float t = local.x * 0.5 + 0.5;
    float v = local.y - t * t * (fract(seed * 2.9) - 0.5) * 0.4;
    float belly = mix(0.5, 0.66, fract(seed * 9.1));
    float bulge = (t - belly) / belly;
    float body = 0.97 * sqrt(max(0.0, 1.0 - bulge * bulge)) * mix(0.45, 1.0, smoothstep(0.0, 0.55, t));
    float notch = mix(0.02, 0.2, fract(seed * 5.3)) * exp(-v * v * 40.0);
    float d = max(abs(v) - body, t - (0.95 - notch));
    float aa = max(pixel, 1.0e-4) * (1.0 + detail * 5.0);
    float cover = 1.0 - smoothstep(-aa, aa, d);
    vec3 base = tint.rgb * 0.8;
    vec3 tip = min(tint.rgb * 1.22 + 0.04, vec3(1.0));
    vec3 colour = mix(base, tip, smoothstep(0.05, 0.85, t));
    colour *= 0.93 + 0.07 * cos(v * 9.0 + seed * 6.0);
    float alpha = cover * tint.a * 0.94;
    return vec4(colour * alpha, alpha);
}

vec4 flake() {
    float r = length(local);
    float soft = max(pixel * 1.5, mix(0.05, 0.45, detail));
    float disc = 1.0 - smoothstep(0.42 - soft, 0.42 + soft, r);
    float angle = atan(local.y, local.x) + seed * TURN;
    float arms = pow(abs(cos(angle * 3.0)), mix(10.0, 40.0, fract(seed * 4.7))) * (1.0 - smoothstep(0.3, 0.92, r));
    arms += step(0.5, fract(seed * 3.3)) * pow(abs(cos(angle * 3.0 + 0.5236)), 40.0) * smoothstep(0.2, 0.32, r) * (1.0 - smoothstep(0.42, 0.58, r));
    arms *= (1.0 - detail) * (1.0 - smoothstep(0.06, 0.2, pixel));
    float ring = detail * smoothstep(0.25, 0.55, r) * (1.0 - smoothstep(0.55, 0.8, r)) * 0.4;
    float alpha = clamp(disc * mix(1.0, 0.5, detail) + arms * 0.75 + ring, 0.0, 1.0) * tint.a;
    return vec4(tint.rgb * alpha, alpha * 0.92);
}

vec4 spark() {
    float t = local.x * 0.5 + 0.5;
    float v = local.y * (1.0 + detail * 2.5);
    float along = smoothstep(0.0, 0.75, t) * (1.0 - smoothstep(0.88, 1.0, t));
    float body = exp(-v * v * 9.0) * along;
    float core = exp(-v * v * 60.0) * smoothstep(0.35, 0.82, t) * (1.0 - smoothstep(0.86, 0.98, t));
    float bloom = detail * exp(-local.y * local.y * 7.0) * smoothstep(0.3, 0.8, t) * (1.0 - smoothstep(0.85, 1.0, t)) * 0.35;
    return vec4((tint.rgb * (body * 0.8 + bloom) + mix(tint.rgb, vec3(1.0), 0.65) * core * 1.4) * tint.a, 0.0);
}

float lanternBody(vec3 p) {
    vec2 q = vec2(length(p.xz), p.y);
    vec2 k1 = vec2(0.36, 0.44);
    vec2 k2 = vec2(0.11, 0.88);
    vec2 ca = vec2(q.x - min(q.x, q.y < 0.0 ? 0.25 : 0.36), abs(q.y) - 0.44);
    vec2 cb = q - k1 + k2 * clamp(dot(k1 - q, k2) / dot(k2, k2), 0.0, 1.0);
    float s = cb.x < 0.0 && ca.y < 0.0 ? -1.0 : 1.0;
    return s * sqrt(min(dot(ca, ca), dot(cb, cb))) - 0.07 - 0.025 * max(0.0, 1.0 - p.y * p.y * 4.0);
}

vec3 lanternNormal(vec3 p) {
    const vec2 e = vec2(0.002, -0.002);
    return normalize(e.xyy * lanternBody(p + e.xyy) + e.yyx * lanternBody(p + e.yyx) + e.yxy * lanternBody(p + e.yxy) + e.xxx * lanternBody(p + e.xxx));
}

vec4 lantern() {
    float s = detail * 2.0 - 1.0;
    float c = sqrt(max(1.0 - s * s, 0.0));
    vec3 ro = vec3(local.x, c * local.y - s * 1.2, s * local.y + c * 1.2);
    vec3 rd = vec3(0.0, s, -c);
    float b = dot(ro, rd);
    float gap = 0.52 - dot(ro, ro) + b * b;
    if (gap <= 0.0) {
        return vec4(0.0);
    }
    float aa = max(pixel, 1.0e-4) * 1.5;
    float span = sqrt(gap);
    float t = max(0.0, -b - span);
    float end = -b + span;
    float near = 1.0;
    vec3 p = ro + rd * t;
    vec3 closest = p;
    for (int i = 0; i < 48 && t < end; i++) {
        vec3 at = ro + rd * t;
        float d = lanternBody(at);
        if (d < near) {
            near = d;
            closest = at;
        }
        if (d < 0.0005) {
            break;
        }
        t += d * 0.9;
    }
    float cover = 1.0 - smoothstep(0.0, aa, near);
    if (cover <= 0.0) {
        return vec4(0.0);
    }
    p = closest;
    float spin = seed * TURN;
    float ring = length(p.xz);
    vec3 warm = tint.rgb;
    vec3 hot = min(warm * vec3(1.05, 1.3, 1.9) + 0.08, vec3(1.0));
    vec3 deep = warm * vec3(0.55, 0.2, 0.07);
    vec3 bamboo = vec3(0.2, 0.1, 0.04);
    vec3 colour;
    float alpha;
    if (p.y < -0.48 && ring < 0.25) {
        float r = ring / 0.25;
        vec2 xz = mat2(cos(spin), -sin(spin), sin(spin), cos(spin)) * p.xz;
        float box = max(abs(xz.x), abs(xz.y));
        float wire = min(abs(xz.x), abs(xz.y));
        colour = mix(hot * 1.1, warm * vec3(1.0, 0.8, 0.6), r * r);
        colour += vec3(1.0, 0.95, 0.8) * exp(-pow(max(box - 0.06, 0.0) * 22.0, 2.0)) * 0.9;
        colour = mix(colour, vec3(0.1, 0.05, 0.02), (1.0 - smoothstep(0.06 - aa * 0.5, 0.06 + aa * 0.5, box)) * 0.9);
        colour = mix(colour, bamboo, (1.0 - smoothstep(0.006, 0.006 + aa, wire)) * step(0.06, box) * 0.85);
        colour = mix(colour, bamboo, smoothstep(0.86, 0.96, r));
        alpha = 1.0;
    } else {
        vec3 n = lanternNormal(p);
        vec3 toward = p - vec3(0.0, -0.34, 0.0);
        float reach = dot(toward, toward);
        float through = max(dot(n, toward * inversesqrt(reach)), 0.0);
        float energy = (0.35 + 0.65 * through) / (1.0 + reach * 6.0);
        colour = mix(deep, warm, smoothstep(0.06, 0.5, energy));
        colour = mix(colour, hot, smoothstep(0.5, 1.0, energy));
        colour *= 0.7 + 0.3 * max(dot(n, -rd), 0.0);
        float angle = atan(p.z, p.x) - spin;
        float rib = abs(sin(angle * 3.0));
        float ribAa = 3.0 * aa / max(ring, 0.05);
        colour *= 1.0 - 0.32 * (1.0 - smoothstep(0.0, 0.03 + ribAa, rib)) * smoothstep(0.03, 0.12, ring);
        colour *= 0.97 + 0.03 * sin(angle * 17.0 + sin(p.y * 9.0 + angle * 2.0) * 1.5);
        vec3 w = vec3(0.0, -0.34, 0.0) - ro;
        float tc = dot(w, rd);
        colour += hot * exp(-(dot(w, w) - tc * tc) * 22.0) * 0.4;
        colour = mix(colour, bamboo + hot * 0.08, 1.0 - smoothstep(-0.47, -0.43, p.y));
        alpha = 0.94;
    }
    alpha *= cover * tint.a;
    return vec4(colour * alpha, alpha);
}

vec4 meteor() {
    float t = local.x * 0.5 + 0.5;
    float v = local.y;
    if (detail >= 0.5) {
        float after = detail * 2.0 - 1.0;
        float drift = sin(t * 3.14159) * sin(t * 6.0 + seed * 40.0 + after * 2.0) * after * 0.45;
        float w = mix(0.16, 0.34, after);
        float band = exp(-pow((v - drift) / w, 2.0));
        float body = smoothstep(0.0, 0.35, t) * (1.0 - smoothstep(0.85, 1.0, t)) * mix(0.55, 1.0, t);
        vec3 hue = mix(tint.rgb, vec3(0.6, 1.0, 0.8), 0.35);
        return vec4(hue * band * body * 0.6 * tint.a, 0.0);
    }
    float trail = pow(t, 1.8) * smoothstep(0.0, 0.1, t) * (1.0 - smoothstep(0.95, 1.0, t));
    float width = mix(0.16, 1.0, t);
    float across = exp(-v * v / (width * width * 0.14));
    float core = exp(-v * v / (width * width * 0.012)) * pow(t, 3.5) * (1.0 - smoothstep(0.95, 1.0, t));
    vec3 hue = mix(tint.rgb * vec3(1.0, 0.5, 0.35), tint.rgb, smoothstep(0.15, 0.75, t));
    float crumbs = step(0.84, hash(vec2(floor(t * 54.0), seed * 311.0))) * exp(-v * v * 40.0) * smoothstep(0.05, 0.5, t) * (1.0 - t);
    return vec4((hue * across * trail * 0.9 + vec3(core) * 1.2 + mix(hue, vec3(1.0), 0.5) * crumbs * 0.9) * tint.a, 0.0);
}

void main() {
    vec2 grain = fwidth(local);
    pixel = max(grain.x, grain.y);
    vec4 colour;
    if (shape == GLOW) {
        colour = glow();
    } else if (shape == PETAL) {
        colour = petal();
    } else if (shape == FLAKE) {
        colour = flake();
    } else if (shape == SPARK) {
        colour = spark();
    } else if (shape == LANTERN) {
        colour = lantern();
    } else if (shape == HALO) {
        colour = halo();
    } else if (shape == WING) {
        colour = wing();
    } else if (shape == BUBBLE) {
        colour = bubble();
    } else if (shape == RIPPLE) {
        colour = ripple();
    } else if (shape == STREAK) {
        colour = streak();
    } else {
        colour = meteor();
    }
    float light = max(colour.r, max(colour.g, colour.b));
    if (light < 0.002 && colour.a < 0.002) {
        discard;
    }
    colour.rgb = max(colour.rgb + (hash(gl_FragCoord.xy + seed * 97.0) - 0.5) / 255.0, 0.0);
    fragColor = colour;
}
