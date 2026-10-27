#version 330

layout(std140) uniform MapTerrain {
    mat4 ViewProj;
    vec4 Eye;
    vec4 FogColor;
    vec4 Params;
    vec4 Extra;
};

uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

in vec2 local;
in float fogDistance;
flat in vec3 raw;
flat in int block;
flat in int face;
flat in float sideHeight;
flat in int water;

out vec4 fragColor;

const int EDGE = 128;
const ivec2 TOPS = ivec2(1920, 1920);
const ivec2 SIDES = ivec2(1792, 1920);
const ivec2 OVERLAYS = ivec2(1664, 1920);

uint word(vec4 texel, bool high) {
    vec4 b = floor(texel * 255.0 + 0.5);
    return high ? uint(b.b) | (uint(b.a) << 8u) : uint(b.r) | (uint(b.g) << 8u);
}

void main() {
    ivec2 table = face == 0 ? TOPS : SIDES;
    vec4 entry = texelFetch(Sampler1, table + ivec2(block % EDGE, block / EDGE), 0);
    uint lo = word(entry, false);
    uint hi = word(entry, true);
    vec3 color = raw;
    if ((hi & 0x2000u) != 0u) {
        float sprite = float(8u << ((lo >> 13u) & 7u));
        vec2 origin = vec2(float(lo & 0x1FFFu), float(hi & 0x1FFFu));
        vec2 atlas = vec2(textureSize(Sampler2, 0));
        vec2 cell = fract(local);
        vec2 uv = (origin + cell * sprite) / atlas;
        vec2 dx = dFdx(local) * sprite / atlas;
        vec2 dy = dFdy(local) * sprite / atlas;
        vec4 tex = textureGrad(Sampler2, uv, dx, dy);
        bool tinted = face == 0 || (hi & 0x4000u) != 0u;
        vec4 overEntry = texelFetch(Sampler1, OVERLAYS + ivec2(block % EDGE, block / EDGE), 0);
        uint olo = word(overEntry, false);
        uint ohi = word(overEntry, true);
        bool layered = face != 0 && (ohi & 0x2000u) != 0u;
        vec3 shade = vec3(1.0);
        if (tinted || layered) {
            vec4 topEntry = texelFetch(Sampler1, TOPS + ivec2(block % EDGE, block / EDGE), 0);
            uint tlo = word(topEntry, false);
            uint thi = word(topEntry, true);
            float topSprite = float(8u << ((tlo >> 13u) & 7u));
            vec2 topOrigin = vec2(float(tlo & 0x1FFFu), float(thi & 0x1FFFu));
            vec3 average = textureLod(Sampler2, (topOrigin + topSprite * 0.5) / atlas, 12.0).rgb;
            shade = min(raw / max(average, vec3(0.03)), vec3(2.6));
        }
        color = mix(raw * 0.85, tex.rgb * (tinted ? shade : vec3(1.0)), tex.a);
        if (layered) {
            float overSprite = float(8u << ((olo >> 13u) & 7u));
            vec2 overOrigin = vec2(float(olo & 0x1FFFu), float(ohi & 0x1FFFu));
            vec2 overUv = (overOrigin + cell * overSprite) / atlas;
            vec4 over = textureGrad(Sampler2, overUv, dx * overSprite / sprite, dy * overSprite / sprite);
            color = mix(color, over.rgb * shade, over.a);
        }
    }
    float light = face == 0 ? 1.0 : face == 1 ? 0.8 : 0.6;
    if (face != 0) {
        float base = sideHeight - local.y;
        light *= mix(0.72, 1.0, smoothstep(0.0, 1.2, base));
    }
    if (water == 1 && face == 0) {
        light *= 0.98;
    }
    color *= light;
    float fog = smoothstep(FogColor.w, Eye.w, fogDistance);
    color = mix(color, FogColor.rgb, fog);
    fragColor = vec4(color, 1.0);
}
