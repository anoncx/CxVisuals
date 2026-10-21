#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

uniform sampler2D Sampler0;

in vec4 tint;
in vec2 local;
flat in vec2 halfSize;
flat in float radius;
flat in float blurUnits;
flat in float fade;

out vec4 fragColor;

const float LENS_A = 0.7;
const float LENS_B = 2.3;
const float LENS_C = 5.2;
const float LENS_D = 6.9;
const float LENS_POWER = 3.0;
const float LENS_BAND = 46.0;
const float LENS_BEND = 30.0;
const float CLARITY = 0.35;
const float SHEEN = 0.04;
const float GRAIN = 0.015;
const float CONTRAST = 1.08;
const float GLOW = 0.30;
const float GLOW_BAND = 12.0;
const float SPECULAR = 0.70;
const float SPECULAR_TIGHT = 3.0;
const float BEVEL = 0.38;
const float BEVEL_BAND = 3.5;
const float DEEP_FROM = 12.0;
const int DEEP_RINGS = 5;
const int DEEP_TAPS = 12;

float roundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

vec3 backdrop(vec2 uv) {
    return texture(Sampler0, clamp(uv, 0.0, 1.0)).rgb;
}

vec3 frosted(vec2 uv, float radiusPx) {
    vec2 unit = vec2(radiusPx) / max(ScreenSize, vec2(1.0));
    vec3 sum = backdrop(uv);
    float total = 1.0;
    for (int ring = 1; ring <= 2; ring++) {
        float scale = float(ring) / 2.0;
        float weight = 1.0 - scale * 0.45;
        for (int i = 0; i < 8; i++) {
            float angle = (float(i) + float(ring) * 0.5) * 0.7853981634;
            sum += backdrop(uv + vec2(cos(angle), sin(angle)) * unit * scale) * weight;
            total += weight;
        }
    }
    return sum / total;
}

vec3 deep(vec2 uv, float radiusPx) {
    vec2 unit = vec2(radiusPx) / max(ScreenSize, vec2(1.0));
    vec3 sum = backdrop(uv);
    float total = 1.0;
    for (int ring = 1; ring <= DEEP_RINGS; ring++) {
        float scale = float(ring) / float(DEEP_RINGS);
        float weight = 1.0 - scale * 0.6;
        for (int i = 0; i < DEEP_TAPS; i++) {
            float angle = (float(i) + float(ring) * 0.37) * 6.2831853072 / float(DEEP_TAPS);
            sum += backdrop(uv + vec2(cos(angle), sin(angle)) * unit * scale) * weight;
            total += weight;
        }
    }
    return sum / total;
}

void main() {
    float r = min(radius, min(halfSize.x, halfSize.y));
    float d = roundBox(local, halfSize, r);
    float aa = max(fwidth(d), 0.0001);
    float coverage = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, d);
    if (coverage * fade < 0.002) {
        discard;
    }

    float pixelsPerUnit = 1.0 / max(fwidth(local.x), 0.0001);
    float inside = max(-d, 0.0);
    float depth = clamp(inside / LENS_BAND, 0.0, 1.0);
    float profile = 1.0 - LENS_B * pow(LENS_C * 2.718281828459045, -LENS_D * depth - LENS_A);
    float squeeze = pow(clamp(profile, 0.0, 1.0), LENS_POWER);

    vec2 slope = vec2(dFdx(d), dFdy(d));
    vec2 outward = slope / max(length(slope), 0.00001);
    vec2 bent = gl_FragCoord.xy - outward * (1.0 - squeeze) * LENS_BEND * pixelsPerUnit;
    vec2 uv = bent / max(ScreenSize, vec2(1.0));

    bool heavy = blurUnits > DEEP_FROM;
    vec3 glass = heavy ? deep(uv, blurUnits * pixelsPerUnit) : mix(frosted(uv, blurUnits * pixelsPerUnit), backdrop(uv), CLARITY);
    float luma = dot(glass, vec3(0.2126, 0.7152, 0.0722));
    glass = mix(glass, mix(vec3(1.0), vec3(0.0), smoothstep(0.35, 0.65, luma)), SHEEN);
    glass = mix(glass, tint.rgb, tint.a);
    glass += (hash21(gl_FragCoord.xy) - 0.5) * GRAIN;
    glass = clamp((glass - 0.5) * CONTRAST + 0.5, 0.0, 1.0);

    float up = -local.y / max(length(local), 0.0001);
    float edgeBand = smoothstep(GLOW_BAND, 0.0, inside);
    float arc = pow(max(up, 0.0), SPECULAR_TIGHT);
    glass *= 1.0 + up * GLOW * edgeBand;
    glass += arc * SPECULAR * edgeBand * edgeBand;
    glass += smoothstep(BEVEL_BAND, 0.0, inside) * BEVEL * (0.35 + 0.65 * arc);

    fragColor = vec4(clamp(glass, 0.0, 1.0), coverage * fade) * ColorModulator;
}
