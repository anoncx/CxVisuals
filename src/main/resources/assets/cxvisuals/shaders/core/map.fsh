#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
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
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

layout(location = 0) in vec4 tint;
layout(location = 1) in vec2 texUv;
layout(location = 2) in vec2 maskPos;
layout(location = 3) in vec2 cloudPos;
layout(location = 4) flat in vec3 params;

layout(location = 0) out vec4 fragColor;

const int PERIOD = 256;
const int GRID = 16;
const int EDGE = 128;
const int TABLE = 1920;

float hash(ivec2 c) {
    uint h = uint(c.x) * 374761393u + uint(c.y) * 668265263u;
    h = (h ^ (h >> 13u)) * 1274126177u;
    return float(h ^ (h >> 16u)) * (1.0 / 4294967295.0);
}

float noise(vec2 p, int period) {
    vec2 i = floor(p);
    vec2 f = p - i;
    ivec2 c = ivec2(i);
    int m = period - 1;
    float a = hash(c & m);
    float b = hash((c + ivec2(1, 0)) & m);
    float d = hash((c + ivec2(0, 1)) & m);
    float e = hash((c + ivec2(1, 1)) & m);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(a, b, u.x), mix(d, e, u.x), u.y);
}

float fbm(vec2 p, vec2 drift, int period, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    float norm = 0.0;
    for (int k = 0; k < octaves; k++) {
        sum += amp * noise(p + drift, period);
        norm += amp;
        p *= 2.0;
        drift *= 2.0;
        period *= 2;
        amp *= 0.5;
    }
    return sum / norm;
}

vec2 cloud(vec2 p, float t, int period) {
    float drift = float(period) * t;
    vec2 warp = vec2(
        fbm(p, vec2(0.0, drift), period, 3),
        fbm(p + vec2(5.0, 1.0), vec2(drift, 0.0), period, 3)) - 0.5;
    vec2 q = p + warp * 0.55;
    float n = fbm(q, vec2(drift, 0.0), period, 5);
    float toward = fbm(q - vec2(0.09), vec2(drift, 0.0), period, 5);
    return vec2(n, toward);
}

float roundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

uint word(vec4 texel, bool high) {
    vec4 b = floor(texel * 255.0 + 0.5);
    return high ? uint(b.b) | (uint(b.a) << 8u) : uint(b.r) | (uint(b.g) << 8u);
}

int heightAt(ivec2 cell, ivec2 lowCell, int fallback) {
    vec4 id = texelFetch(Sampler1, clamp(cell, lowCell, lowCell + ivec2(EDGE - 1)), 0);
    return (word(id, false) & 0x3FFFu) == 0u ? fallback : int(word(id, true) & 0x1FFu);
}

vec3 detailed(vec3 base, vec2 texel, ivec2 lowCell, vec2 dx, vec2 dy, float amount) {
    ivec2 cell = clamp(ivec2(floor(texel)), lowCell, lowCell + ivec2(EDGE - 1));
    vec4 id = texelFetch(Sampler1, cell, 0);
    uint block = word(id, false) & 0x3FFFu;
    if (block == 0u) {
        return base;
    }
    uint hi = word(id, true);
    int h = int(hi & 0x1FFu);
    vec2 local = fract(texel);
    vec3 surface = base;
    vec4 entry = texelFetch(Sampler1, ivec2(TABLE + int(block) % EDGE, TABLE + int(block) / EDGE), 0);
    uint elo = word(entry, false);
    uint ehi = word(entry, true);
    if ((ehi & 0x2000u) != 0u) {
        float sprite = float(8u << ((elo >> 13u) & 7u));
        vec2 origin = vec2(float(elo & 0x1FFFu), float(ehi & 0x1FFFu));
        vec2 atlas = vec2(textureSize(Sampler2, 0));
        vec2 uv = (origin + local * sprite) / atlas;
        vec4 tex = textureGrad(Sampler2, uv, dx * sprite / atlas, dy * sprite / atlas);
        vec3 average = textureLod(Sampler2, (origin + sprite * 0.5) / atlas, 12.0).rgb;
        vec3 tinted = tex.rgb * min(base / max(average, vec3(0.03)), vec3(2.6));
        surface = mix(base * 0.86, tinted, tex.a);
    }
    if ((hi & 0x200u) == 0u) {
        int hN = heightAt(cell + ivec2(0, -1), lowCell, h);
        int hW = heightAt(cell + ivec2(-1, 0), lowCell, h);
        int hS = heightAt(cell + ivec2(0, 1), lowCell, h);
        int hE = heightAt(cell + ivec2(1, 0), lowCell, h);
        float castN = clamp(float(hN - h) / 3.0, 0.0, 1.0) * (1.0 - smoothstep(0.0, 0.6, local.y));
        float castW = clamp(float(hW - h) / 3.0, 0.0, 1.0) * (1.0 - smoothstep(0.0, 0.6, local.x));
        float lit = (h > hN ? 1.0 - smoothstep(0.0, 0.16, local.y) : 0.0)
            + (h > hW ? 1.0 - smoothstep(0.0, 0.16, local.x) : 0.0);
        float side = (h > hS ? 1.0 - smoothstep(0.0, 0.2, 1.0 - local.y) : 0.0)
            + (h > hE ? 1.0 - smoothstep(0.0, 0.2, 1.0 - local.x) : 0.0);
        surface *= 1.0 - 0.42 * max(castN, castW) + 0.14 * min(lit, 1.0) - 0.24 * min(side, 1.0);
    }
    return mix(base, surface, amount);
}

void main() {
    vec2 atlasSize = vec2(textureSize(Sampler0, 0));
    vec2 atlasTexel = texUv * atlasSize;
    vec2 texelDx = dFdx(atlasTexel);
    vec2 texelDy = dFdy(atlasTexel);
    vec2 spread = max(fwidth(atlasTexel), vec2(0.0001));
    float blockPixels = 1.0 / max(max(length(texelDx), length(texelDy)), 0.0001);
    vec2 slot = floor(clamp(texUv, vec2(0.0), vec2(0.99999)) * float(GRID));
    ivec2 lowCell = ivec2(slot) * EDGE;
    vec2 low = (slot * float(EDGE) + 0.5) / atlasSize;
    vec2 high = ((slot + 1.0) * float(EDGE) - 0.5) / atlasSize;
    vec2 seam = floor(atlasTexel + 0.5);
    vec2 crispTexel = seam + clamp((atlasTexel - seam) / spread, -0.5, 0.5);
    vec3 crisp = texture(Sampler0, clamp(crispTexel / atlasSize, low, high)).rgb;
    float explored = texture(Sampler0, clamp(texUv, low, high)).a;

    float coverage = 1.0;
    float rim = 0.0;
    if (params.y != 0.0) {
        vec2 box = params.y > 0.0 ? vec2(1.0, params.y) : vec2(-params.y, 1.0);
        float r = clamp(params.x, 0.0, min(box.x, box.y));
        float d = roundBox(maskPos, box, r);
        float aa = max(fwidth(d), 0.0001);
        coverage = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, d);
        if (coverage <= 0.0) {
            discard;
        }
        rim = smoothstep(-0.2, 0.0, d);
    }

    vec3 color = crisp;
    float detail = smoothstep(3.0, 7.0, blockPixels);
    if (detail > 0.0 && explored > 0.01) {
        color = detailed(color, atlasTexel, lowCell, texelDx, texelDy, detail);
    }
    if (explored < 0.995) {
        float t = fract(GameTime);
        float blend = clamp(0.3 - log2(blockPixels), 0.0, 1.0);
        vec2 near = cloud(cloudPos, t, PERIOD);
        vec2 shape = blend > 0.001 ? mix(near, cloud(cloudPos * 0.5, t, PERIOD / 2), blend) : near;
        float n = shape.x;
        float hidden = 1.0 - explored;
        float edge = min(1.0, hidden * 3.0);
        float cover = hidden * 1.25 + (n - 0.52) * 1.25 * edge;
        float fog = smoothstep(0.36, 0.64, cover);
        color *= 1.0 - 0.22 * min(1.0, hidden * 5.0) * (1.0 - fog);
        if (fog > 0.0) {
            float light = clamp(0.5 + (shape.y - n) * 9.0, 0.0, 1.0);
            float puff = smoothstep(0.38, 0.78, n);
            vec3 base = tint.rgb;
            vec3 cloudColor = mix(base * 0.8, mix(base, vec3(1.0), 0.12), puff);
            if (params.z > 0.5) {
                cloudColor = mix(cloudColor * 0.9, cloudColor + vec3(0.07), light);
            } else {
                cloudColor = base;
            }
            vec2 grid = mod(gl_FragCoord.xy, 4.0) - 2.0;
            float grain = 1.0 - smoothstep(0.45, 0.95, length(grid));
            cloudColor += grain * 0.028 * fog;
            color = mix(color, cloudColor, fog);
        }
    }

    color *= 1.0 - rim * 0.2;
    vec4 result = vec4(color, coverage * tint.a) * ColorModulator;
    if (result.a < 0.002) {
        discard;
    }
    fragColor = result;
}
