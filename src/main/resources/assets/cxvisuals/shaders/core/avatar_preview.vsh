#version 330

layout(std140) uniform AvatarPreview {
    mat4 Mvp;
    mat4 Model;
    vec4 Light;
    vec4 Key;
    vec4 Rim;
    vec4 Params;
    mat4 Palette[128];
};

in vec3 Position;
in vec3 Normal;
in vec2 UV0;
in vec4 Color;
in uvec4 Joints;
in vec4 Weights;

out vec3 normal;
out vec2 uv;
out vec4 tint;

void main() {
    vec4 position = vec4(Position, 1.0);
    vec3 direction = Normal;
    if (Params.x > 0.5) {
        float total = Weights.x + Weights.y + Weights.z + Weights.w;
        if (total > 0.0) {
            mat4 skin = (Palette[Joints.x] * Weights.x
                + Palette[Joints.y] * Weights.y
                + Palette[Joints.z] * Weights.z
                + Palette[Joints.w] * Weights.w) / total;
            position = skin * position;
            direction = mat3(skin) * direction;
        }
    }
    gl_Position = Mvp * position;
    normal = mat3(Model) * direction;
    uv = UV0;
    tint = Color;
}
