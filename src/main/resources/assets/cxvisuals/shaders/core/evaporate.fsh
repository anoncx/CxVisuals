#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 layerUv;
in float erosion;
in float fade;

out vec4 fragColor;

const float BLOB = 46.0;
const float WARP = 2.2;
const float FRONT_REACH = 1.4;
const float FRONT_SOFT = 0.25;
const float VANISH_FROM = 0.5;
const float VANISH_TO = 0.92;

float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    vec4 layer = texture(Sampler0, layerUv);
    if (layer.a < 0.002) {
        discard;
    }
    float keep = 1.0;
    if (erosion > 0.0) {
        vec2 p = layerUv * vec2(textureSize(Sampler0, 0)) / BLOB;
        p += (vec2(vnoise(p * 1.9 + 3.1), vnoise(p * 1.9 + 7.7)) - 0.5) * WARP;
        float n = vnoise(p) * 0.62 + vnoise(p * 2.3 + 11.0) * 0.38;
        n = clamp((n - 0.2) / 0.6, 0.0, 1.0);
        float front = erosion * FRONT_REACH;
        keep = smoothstep(front - FRONT_SOFT, front, n) * (1.0 - smoothstep(VANISH_FROM, VANISH_TO, erosion));
    }
    vec4 result = layer * (keep * fade) * ColorModulator;
    if (result.a < 0.004) {
        discard;
    }
    fragColor = result;
}
