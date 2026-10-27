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
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec4 tint;
out vec2 texUv;
out vec2 maskPos;
out vec2 cloudPos;
flat out vec3 params;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    tint = Color;
    texUv = UV0;
    maskPos = vec2(UV1) / 2048.0;
    cloudPos = vec2(UV2) / 16.0;
    params = Normal;
}
