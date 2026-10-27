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

out vec4 vertexColor;
out vec2 texUv;
out vec2 local;
flat out float halfSize;
flat out float radius;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    texUv = UV0;
    local = vec2(UV2);
    halfSize = float(UV1.x) / 16.0;
    radius = float(UV1.y) / 8.0;
}
