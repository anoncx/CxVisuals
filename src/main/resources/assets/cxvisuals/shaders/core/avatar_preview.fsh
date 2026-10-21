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

uniform sampler2D Albedo;

in vec3 normal;
in vec2 uv;
in vec4 tint;

out vec4 fragColor;

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
