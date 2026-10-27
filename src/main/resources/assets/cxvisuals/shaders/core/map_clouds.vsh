#version 330

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

in vec3 Position;
in vec2 UV0;

out vec2 ndc;

void main() {
    vec2 corner = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2)) * 2.0 - 1.0;
    ndc = corner;
    gl_Position = vec4(corner, Extra.y > 0.5 ? 0.0 : -1.0, 1.0);
}
