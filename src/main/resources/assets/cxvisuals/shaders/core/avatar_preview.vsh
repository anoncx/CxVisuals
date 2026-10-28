#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform AvatarPreview {
    mat4 Mvp;
    mat4 Model;
    vec4 Light;
    vec4 Key;
    vec4 Rim;
    vec4 Params;
    mat4 Palette[128];
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec3 Normal;
layout(location = 2) in vec2 UV0;
layout(location = 3) in vec4 Color;
layout(location = 4) in uvec4 Joints;
layout(location = 5) in vec4 Weights;

layout(location = 0) out vec3 normal;
layout(location = 1) out vec2 uv;
layout(location = 2) out vec4 tint;

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
