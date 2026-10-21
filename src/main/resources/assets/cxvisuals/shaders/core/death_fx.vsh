#version 330

layout(std140) uniform DeathFx {
    mat4 ViewProj;
    vec4 First;
    vec4 Second;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

out vec2 local;
out float progress;
out float alpha;
flat out int kind;
flat out float seed;
flat out float size;

void main() {
    gl_Position = ViewProj * vec4(Position, 1.0);
    local = UV0;
    progress = Color.r;
    alpha = Color.g;
    kind = UV2.x >> 12;
    seed = float(UV2.x & 4095) / 4095.0;
    size = float(UV2.y) / 1000.0;
}
