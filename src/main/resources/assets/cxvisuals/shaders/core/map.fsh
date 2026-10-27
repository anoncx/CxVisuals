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
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

in vec4 tint;
in vec2 texUv;
in vec2 maskPos;
in vec2 cloudPos;
flat in vec3 params;

out vec4 fragColor;

const int PERIOD = 256;
const int GRID = 16;
const int EDGE = 128;
const float DRIFT = 256.0;

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

float fbm(vec2 p, vec2 drift, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    float norm = 0.0;
    int period = PERIOD;
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

float roundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

vec2 uv2cell(vec2 texel) {
    vec2 slot = floor(texel / float(EDGE));
    return clamp(texel, slot * float(EDGE), (slot + 1.0) * float(EDGE) - 0.5);
}

vec4 sampleMap(vec2 uv) {
    vec2 size = vec2(textureSize(Sampler0, 0));
    vec2 cell = floor(uv * float(GRID));
    vec2 low = (cell * float(EDGE) + 0.5) / size;
    vec2 high = ((cell + 1.0) * float(EDGE) - 0.5) / size;
    vec2 texel = uv * size;
    vec2 seam = floor(texel + 0.5);
    vec2 spread = max(fwidth(texel), vec2(0.0001));
    texel = seam + clamp((texel - seam) / spread, -0.5, 0.5);
    vec3 crisp = texture(Sampler0, clamp(texel / size, low, high)).rgb;
    float soft = texture(Sampler0, clamp(uv, low, high)).a;
    return vec4(crisp, soft);
}

vec3 detailed(vec3 base, vec2 texel, vec2 dx, vec2 dy, float amount) {
    vec2 cell = floor(uv2cell(texel));
    vec4 id = texelFetch(Sampler1, ivec2(cell), 0);
    uint lo = uint(id.r * 255.0 + 0.5) | (uint(id.g * 255.0 + 0.5) << 8u);
    uint hi = uint(id.b * 255.0 + 0.5) | (uint(id.a * 255.0 + 0.5) << 8u);
    if ((hi & 0x2000u) == 0u) {
        return base;
    }
    float sprite = float(8u << ((lo >> 13u) & 7u));
    vec2 origin = vec2(float(lo & 0x1FFFu), float(hi & 0x1FFFu));
    vec2 atlas = vec2(textureSize(Sampler2, 0));
    vec2 local = fract(texel);
    vec2 uv = (origin + local * sprite) / atlas;
    vec4 tex = textureGrad(Sampler2, uv, dx * sprite / atlas, dy * sprite / atlas);
    vec3 average = textureLod(Sampler2, (origin + sprite * 0.5) / atlas, 12.0).rgb;
    vec3 tint = min(base / max(average, vec3(0.03)), vec3(2.6));
    vec3 textured = mix(base * 0.78, tex.rgb * tint, tex.a);
    return mix(base, textured, amount);
}

void main() {
    vec2 atlasTexel = texUv * vec2(textureSize(Sampler0, 0));
    vec2 texelDx = dFdx(atlasTexel);
    vec2 texelDy = dFdy(atlasTexel);
    float blockPixels = 1.0 / max(max(length(texelDx), length(texelDy)), 0.0001);
    vec4 map = sampleMap(texUv);
    float coverage = 1.0;
    float rim = 0.0;
    if (params.y > 0.5) {
        float d = roundBox(maskPos, vec2(1.0), clamp(params.x, 0.0, 1.0));
        float aa = max(fwidth(d), 0.0001);
        coverage = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, d);
        if (coverage <= 0.0) {
            discard;
        }
        rim = smoothstep(-0.2, 0.0, d);
    }

    vec3 color = map.rgb;
    float detail = smoothstep(3.0, 7.0, blockPixels);
    if (detail > 0.0 && map.a > 0.01) {
        color = detailed(color, atlasTexel, texelDx, texelDy, detail);
    }
    if (map.a < 0.995) {
        float t = fract(GameTime);
        vec2 p = cloudPos;
        vec2 warp = vec2(
            fbm(p, vec2(0.0, DRIFT) * t, 3),
            fbm(p + vec2(5.2, 1.3), vec2(DRIFT, 0.0) * t, 3)) - 0.5;
        vec2 q = p + warp * 0.55;
        float n = fbm(q, vec2(DRIFT, 0.0) * t, 5);
        float hidden = 1.0 - map.a;
        float edge = min(1.0, hidden * 3.0);
        float cover = hidden * 1.25 + (n - 0.52) * 1.25 * edge;
        float fog = smoothstep(0.36, 0.64, cover);
        color *= 1.0 - 0.22 * min(1.0, hidden * 5.0) * (1.0 - fog);
        if (fog > 0.0) {
            float toward = fbm(q - vec2(0.09, 0.09), vec2(DRIFT, 0.0) * t, 5);
            float light = clamp(0.5 + (toward - n) * 9.0, 0.0, 1.0);
            float puff = smoothstep(0.38, 0.78, n);
            vec3 base = tint.rgb;
            vec3 deep = base * 0.8;
            vec3 top = mix(base, vec3(1.0), 0.12);
            vec3 cloud = mix(deep, top, puff);
            if (params.z > 0.5) {
                cloud = mix(cloud * 0.9, cloud + vec3(0.07), light);
            } else {
                cloud = base;
            }
            vec2 grid = mod(gl_FragCoord.xy, 4.0) - 2.0;
            float grain = 1.0 - smoothstep(0.45, 0.95, length(grid));
            cloud += grain * 0.028 * fog;
            color = mix(color, cloud, fog);
        }
    }

    color *= 1.0 - rim * 0.2;
    vec4 result = vec4(color, coverage * tint.a) * ColorModulator;
    if (result.a < 0.002) {
        discard;
    }
    fragColor = result;
}
