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
out vec2 local;
flat out vec2 halfSize;
flat out float radius;
flat out float blurUnits;
flat out float fade;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    tint = Color;
    local = UV0;
    halfSize = vec2(UV1) / 8.0;
    radius = float(UV2.x) / 8.0;
    blurUnits = float(UV2.y) / 8.0;
    fade = Normal.x;
}
