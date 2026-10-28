#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform Circle {
    mat4 ViewProj;
    vec4 Rim[64];
    vec4 Look;
    vec4 First;
    vec4 Second;
    vec4 Cut;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;

layout(location = 0) out vec3 offset;
layout(location = 1) out vec2 local;
layout(location = 2) out float progress;
layout(location = 3) out float alpha;
layout(location = 4) flat out int kind;
layout(location = 5) flat out float seed;
layout(location = 6) flat out float radius;

void main() {
    gl_Position = ViewProj * vec4(Position, 1.0);
    offset = Position;
    local = UV0;
    progress = Color.r;
    alpha = Color.g;
    kind = UV2.x >> 12;
    seed = float(UV2.x & 4095) / 4095.0;
    radius = float(UV2.y) / 1000.0;
}
