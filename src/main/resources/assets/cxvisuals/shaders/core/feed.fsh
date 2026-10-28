#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

uniform sampler2D Sampler0;

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 local;
layout(location = 2) flat in vec2 halfSize;
layout(location = 3) flat in float radius;

layout(location = 0) out vec4 fragColor;

float roundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec3 frame = texture(Sampler0, clamp(local, 0.0, 1.0)).bgr;

    vec2 p = (local - 0.5) * halfSize * 2.0;
    float d = roundBox(p, halfSize, min(radius, min(halfSize.x, halfSize.y)));
    float aa = max(fwidth(d), 0.0001);
    float coverage = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, d);

    vec4 result = vec4(frame * vertexColor.rgb, coverage * vertexColor.a) * ColorModulator;
    if (result.a < 0.002) {
        discard;
    }
    fragColor = result;
}
