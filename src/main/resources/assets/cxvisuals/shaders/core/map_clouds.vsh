#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform MapTerrain {
    mat4 ViewProj;
    vec4 Eye;
    vec4 FogColor;
    vec4 Params;
    vec4 Extra;
    mat4 Inverse;
    vec4 Cloud;
    vec4 CloudBase;
    vec4 CloudTop;
};

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(location = 0) out vec2 ndc;

void main() {
    vec2 corner = vec2(float((gl_VertexIndex << 1) & 2), float(gl_VertexIndex & 2)) * 2.0 - 1.0;
    ndc = corner;
    gl_Position = vec4(corner, Extra.y > 0.5 ? 0.0 : -1.0, 1.0);
}
