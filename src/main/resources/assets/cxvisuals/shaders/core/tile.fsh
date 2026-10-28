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
layout(location = 2) flat in float halfSize;
layout(location = 3) flat in float radius;
layout(location = 4) flat in ivec2 cellInfo;

layout(location = 0) out vec4 fragColor;

float roundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

void main() {
    int columns = cellInfo.y & 255;
    int rows = cellInfo.y >> 8;
    vec2 grid = vec2(float(columns), float(rows));
    vec2 origin = vec2(float(cellInfo.x % columns), float(cellInfo.x / columns));
    vec2 texel = 0.5 / vec2(textureSize(Sampler0, 0)) * grid;
    vec2 inside = clamp(local, texel, 1.0 - texel);
    vec4 tile = texture(Sampler0, (origin + inside) / grid);

    vec2 p = (local - 0.5) * halfSize * 2.0;
    float d = roundBox(p, vec2(halfSize), min(radius, halfSize));
    float aa = max(fwidth(d), 0.0001);
    float coverage = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, d);

    vec4 result = vec4(tile.rgb, tile.a * coverage * vertexColor.a) * ColorModulator;
    if (result.a < 0.002) {
        discard;
    }
    fragColor = result;
}
