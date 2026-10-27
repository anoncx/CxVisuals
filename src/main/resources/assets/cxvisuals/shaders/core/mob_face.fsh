#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 texUv;
in vec2 local;
flat in float halfSize;
flat in float radius;

out vec4 fragColor;

float roundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    vec4 face = texture(Sampler0, texUv);
    vec3 color = mix(vertexColor.rgb * 0.35, face.rgb, face.a);

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
