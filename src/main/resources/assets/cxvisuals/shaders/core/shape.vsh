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
layout(location = 3) in ivec2 UV1;
layout(location = 4) in ivec2 UV2;
layout(location = 5) in vec3 Normal;

layout(location = 0) out vec4 vertexColor;
layout(location = 1) out vec2 local;
layout(location = 2) flat out vec2 halfSize;
layout(location = 3) flat out float radius;
layout(location = 4) flat out float softness;
layout(location = 5) flat out float border;
layout(location = 6) flat out float fade;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    local = UV0;
    halfSize = vec2(UV1) / 8.0;
    radius = float(UV2.x) / 8.0;
    softness = float(UV2.y) / 8.0;
    border = Normal.x * 16.0;
    fade = Normal.y * 96.0;
}
