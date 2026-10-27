#version 330

layout(std140) uniform Swing {
    mat4 ViewProj;
    vec4 First;
    vec4 Second;
    vec4 Look;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

out float across;
out float arc;
out float along;
out float alpha;
out float ends;
out float tipEnds;
flat out float seed;

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
