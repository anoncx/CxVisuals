#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;

layout(location = 0) out vec4 tint;
layout(location = 1) out vec2 local;
layout(location = 2) flat out int shape;
layout(location = 3) flat out float detail;
layout(location = 4) flat out float seed;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    tint = Color;
    local = UV0;
    shape = UV2.x >> 8;
    detail = float(UV2.x & 255) / 255.0;
    seed = float(UV2.y) / 32767.0;
}
