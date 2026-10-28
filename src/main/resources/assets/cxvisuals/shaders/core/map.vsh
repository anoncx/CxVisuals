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

layout(location = 0) out vec4 tint;
layout(location = 1) out vec2 texUv;
layout(location = 2) out vec2 maskPos;
layout(location = 3) out vec2 cloudPos;
layout(location = 4) flat out vec3 params;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    tint = Color;
    texUv = UV0;
    maskPos = vec2(UV1) / 2048.0;
    cloudPos = vec2(UV2) / 16.0;
    params = Normal;
}
