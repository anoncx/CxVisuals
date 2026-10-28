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

uniform sampler2D Albedo;

layout(location = 0) in vec3 normal;
layout(location = 1) in vec2 uv;
layout(location = 2) in vec4 tint;

layout(location = 0) out vec4 fragColor;

void main() {
    vec4 albedo = texture(Albedo, uv) * tint;
    if (albedo.a < Params.y) {
        discard;
    }
    vec3 n = normalize(normal);
    if (!gl_FrontFacing) {
        n = -n;
    }
    float key = max(dot(n, normalize(Light.xyz)), 0.0);
    float sky = 0.5 + 0.5 * n.y;
    float rim = pow(1.0 - clamp(n.z, 0.0, 1.0), 3.0);
    vec3 lit = albedo.rgb * (Key.w * mix(0.55, 1.0, sky) + Key.rgb * key) + Rim.rgb * (rim * Rim.w);
    float alpha = clamp(albedo.a, 0.0, 1.0);
    fragColor = vec4(min(lit, vec3(1.0)) * alpha, alpha);
}
