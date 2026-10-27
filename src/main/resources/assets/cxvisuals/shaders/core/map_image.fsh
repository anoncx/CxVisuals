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
flat in vec2 halfSize;
flat in float radius;

out vec4 fragColor;

float roundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec4 frame = texture(Sampler0, clamp(vec2(local.x, 1.0 - local.y), 0.0, 1.0));

    vec2 p = (local - 0.5) * halfSize * 2.0;
    float d = roundBox(p, halfSize, min(radius, min(halfSize.x, halfSize.y)));
    float aa = max(fwidth(d), 0.0001);
    float coverage = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, d);

    vec4 result = vec4(frame.rgb * vertexColor.rgb, frame.a * coverage * vertexColor.a) * ColorModulator;
    if (result.a < 0.002) {
        discard;
    }
    fragColor = result;
}
