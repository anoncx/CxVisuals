#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 local;
flat in float halfSize;
flat in float radius;

out vec4 fragColor;

const vec2 FACE = vec2(8.0, 8.0);
const vec2 HAT = vec2(40.0, 8.0);
const float CELLS = 8.0;

float roundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    ivec2 size = textureSize(Sampler0, 0);
    vec2 sheet = vec2(64.0, 64.0 * float(size.y) / float(max(size.x, 1)));
    vec2 cell = clamp(local, 0.0, 0.9999) * CELLS;
    vec4 skin = texture(Sampler0, (FACE + cell) / sheet);
    vec4 hat = texture(Sampler0, (HAT + cell) / sheet);
    vec3 color = mix(skin.rgb, hat.rgb, hat.a);

    vec2 p = (local - 0.5) * halfSize * 2.0;
    float d = roundBox(p, vec2(halfSize), min(radius, halfSize));
    float aa = max(fwidth(d), 0.0001);
    float coverage = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, d);

    vec4 result = vec4(color, coverage * vertexColor.a) * ColorModulator;
    if (result.a < 0.002) {
        discard;
    }
    fragColor = result;
}
