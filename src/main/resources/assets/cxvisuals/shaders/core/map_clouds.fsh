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

layout(location = 0) in vec2 ndc;

layout(location = 0) out vec4 fragColor;

const int PERIOD = 256;

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

float fbm(vec2 p, int octaves) {
    float sum = 0.0;
    float amp = 0.5;
    float norm = 0.0;
    int period = PERIOD;
    for (int k = 0; k < octaves; k++) {
        sum += amp * noise(p, period);
        norm += amp;
        p *= 2.0;
        period *= 2;
        amp *= 0.5;
    }
    return sum / norm;
}

void main() {
    vec4 nearPoint = Inverse * vec4(ndc, 1.0, 1.0);
    vec4 farPoint = Inverse * vec4(ndc, Extra.y > 0.5 ? 0.0 : -1.0, 1.0);
    vec3 a = nearPoint.xyz / nearPoint.w;
    vec3 b = farPoint.xyz / farPoint.w;
    vec3 ray = b - a;
    if (ray.y >= -0.0001) {
        discard;
    }
    float t = (Cloud.z - a.y) / ray.y;
    if (t <= 0.0) {
        discard;
    }
    vec3 hit = a + ray * t;
    vec2 p = (hit.xz + Cloud.xy) / Cloud.w;
    vec2 warp = vec2(fbm(p, 3), fbm(p + vec2(5.0, 1.0), 3)) - 0.5;
    vec2 q = p + warp * 0.6;
    float n = fbm(q, 5);
    float toward = fbm(q - vec2(0.08, 0.08), 5);
    float light = clamp(0.5 + (toward - n) * 8.0, 0.0, 1.0);
    float puff = smoothstep(0.32, 0.78, n);
    vec3 color = mix(CloudBase.rgb, CloudTop.rgb, puff);
    color = mix(color * 0.88, color + vec3(0.05), light);
    float distance = length(hit - Eye.xyz);
    float fade = 1.0 - smoothstep(FogColor.w * 1.4, Eye.w * 1.6, distance);
    float edge = smoothstep(-0.02, -0.12, ray.y / length(ray));
    float alpha = fade * edge * mix(0.82, 1.0, puff);
    color = mix(FogColor.rgb, color, fade);
    if (alpha < 0.004) {
        discard;
    }
    fragColor = vec4(color, alpha);
}
