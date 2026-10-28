#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform Swing {
    mat4 ViewProj;
    vec4 First;
    vec4 Second;
    vec4 Look;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec4 Color;
layout(location = 2) in vec2 UV0;
layout(location = 3) in ivec2 UV2;

layout(location = 0) out float across;
layout(location = 1) out float arc;
layout(location = 2) out float along;
layout(location = 3) out float alpha;
layout(location = 4) out float ends;
layout(location = 5) out float tipEnds;
layout(location = 6) flat out float seed;

void main() {
    gl_Position = ViewProj * vec4(Position, 1.0);
    across = UV0.x;
    arc = UV0.y;
    along = Color.r;
    alpha = Color.g;
    ends = Color.b;
    tipEnds = Color.a;
    seed = float(UV2.x) / 4095.0;
}
