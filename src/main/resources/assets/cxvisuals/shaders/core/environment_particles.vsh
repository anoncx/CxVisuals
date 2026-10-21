#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

out vec4 tint;
out vec2 local;
flat out int shape;
flat out float detail;
flat out float seed;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    tint = Color;
    local = UV0;
    shape = UV2.x >> 8;
    detail = float(UV2.x & 255) / 255.0;
    seed = float(UV2.y) / 32767.0;
}
