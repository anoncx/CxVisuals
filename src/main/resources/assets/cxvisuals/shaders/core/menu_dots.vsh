#version 330
#extension GL_ARB_separate_shader_objects : require

layout(location = 0) in vec3 Position;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
