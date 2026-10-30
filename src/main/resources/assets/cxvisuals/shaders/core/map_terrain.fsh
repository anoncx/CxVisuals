#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform MapTerrain {
    mat4 ViewProj;
    vec4 Eye;
    vec4 FogColor;
    vec4 Params;
    vec4 Extra;
    mat4 Inverse;
    vec4 Cloud;
    vec4 CloudBase;
    vec4 CloudTop;
};

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) in vec2 local;
layout(location = 1) in float fogDistance;
layout(location = 2) flat in vec3 raw;
layout(location = 3) flat in int block;
layout(location = 4) flat in int topBlock;
layout(location = 5) flat in int face;
layout(location = 6) flat in int side;
layout(location = 7) flat in int layer;
layout(location = 8) flat in float sideHeight;
layout(location = 9) flat in int water;
layout(location = 10) flat in int groundWet;
layout(location = 11) flat in float skirt;
layout(location = 12) in float relY;

layout(location = 0) out vec4 fragColor;

const int EDGE = 128;
const ivec2 TOPS = ivec2(1920, 1920);
const ivec2 SIDES = ivec2(1792, 1920);
const ivec2 OVERLAYS = ivec2(1280, 1920);
const float SHELTER = 0.62;
const float FADE_TOP = -4.0;
const float FADE_BOTTOM = -26.0;
const vec3 WATER_TINT = vec3(0.247, 0.463, 0.894);

uint word(vec4 texel, bool high) {
    vec4 b = floor(texel * 255.0 + 0.5);
    return high ? uint(b.b) | (uint(b.a) << 8u) : uint(b.r) | (uint(b.g) << 8u);
}

vec4 entry(ivec2 table, int id) {
    return texelFetch(Sampler1, table + ivec2(id % EDGE, id / EDGE), 0);
}

float spriteSize(uint lo) {
    return float(8u << ((lo >> 13u) & 7u));
}

vec2 spriteOrigin(uint lo, uint hi) {
    return vec2(float(lo & 0x1FFFu), float(hi & 0x1FFFu));
}

void main() {
    vec2 atlas = vec2(textureSize(Sampler2, 0));
    vec4 topEntry = entry(TOPS, topBlock);
    uint tlo = word(topEntry, false);
    uint thi = word(topEntry, true);
    vec3 topAverage = textureLod(
        Sampler2, (spriteOrigin(tlo, thi) + spriteSize(tlo) * 0.5) / atlas, 12.0).rgb;
    vec3 ratio = min(raw / max(topAverage, vec3(0.03)), vec3(2.6));

    vec4 own = entry(face == 0 ? TOPS : SIDES - ivec2(side * EDGE, 0), block);
    uint lo = word(own, false);
    uint hi = word(own, true);
    vec3 color = raw;
    if ((hi & 0x2000u) != 0u) {
        float sprite = spriteSize(lo);
        vec2 origin = spriteOrigin(lo, hi);
        vec2 cell = fract(local);
        vec2 uv = (origin + cell * sprite) / atlas;
        vec2 dx = dFdx(local) * sprite / atlas;
        vec2 dy = dFdy(local) * sprite / atlas;
        vec4 tex = textureGrad(Sampler2, uv, dx, dy);
        bool tinted = (hi & 0x4000u) != 0u || (layer == 0 && face == 0);
        vec3 tint = !tinted ? vec3(1.0)
            : layer == 1 && groundWet == 1 ? WATER_TINT : ratio;
        vec3 average = layer == 0
            ? raw
            : textureLod(Sampler2, (origin + sprite * 0.5) / atlas, 12.0).rgb * tint;
        color = mix(average * 0.85, tex.rgb * tint, tex.a);
        if (face != 0) {
            vec4 over = entry(OVERLAYS, block);
            uint olo = word(over, false);
            uint ohi = word(over, true);
            if ((ohi & 0x2000u) != 0u) {
                float overSprite = spriteSize(olo);
                vec2 overUv = (spriteOrigin(olo, ohi) + cell * overSprite) / atlas;
                vec4 layerTex = textureGrad(
                    Sampler2, overUv, dx * overSprite / sprite, dy * overSprite / sprite);
                color = mix(color, layerTex.rgb * ratio, layerTex.a);
            }
        }
    }
    float light = face == 0 ? 1.0 : face == 1 ? 0.8 : 0.6;
    if (face != 0) {
        float base = sideHeight - local.y;
        light *= mix(0.72, 1.0, smoothstep(0.0, 1.2, base));
    }
    if (layer == 1) {
        light *= SHELTER;
    }
    color *= light;
    if (skirt > 0.5) {
        float down = smoothstep(0.0, sideHeight, sideHeight - local.y);
        color = mix(color, FogColor.rgb, down * down * 0.92);
    }
    if (face != 0) {
        float reach = smoothstep(3.0, 9.0, sideHeight);
        float sink = clamp((FADE_TOP - relY) / (FADE_TOP - FADE_BOTTOM), 0.0, 1.0);
        vec3 cloud = mix(CloudBase.rgb, CloudTop.rgb, 0.5) * 0.88;
        color = mix(color, cloud, sink * sink * (3.0 - 2.0 * sink) * reach);
    }
    float fog = smoothstep(FogColor.w, Eye.w, fogDistance);
    color = mix(color, FogColor.rgb, fog);
    fragColor = vec4(color, 1.0);
}
