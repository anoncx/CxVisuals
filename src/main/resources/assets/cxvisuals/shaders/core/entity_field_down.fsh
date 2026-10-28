#version 330
#extension GL_ARB_separate_shader_objects : require

#moj_import <cxvisuals:entity_block.glsl>

uniform sampler2D FieldSampler;

layout(location = 0) out vec4 fragColor;

void main() {
    vec2 size = vec2(textureSize(FieldSampler, 0));
    vec2 texel = 1.0 / size;
    vec2 full = size * exp2(round(log2(Screen.x / size.x)));
    vec2 low = Region.xy / full + 0.5 * texel;
    vec2 high = max(low, (Region.xy + Region.zw) / full - 0.5 * texel);
    vec2 uv = 2.0 * gl_FragCoord.xy * texel;
    vec4 sum = texture(FieldSampler, clamp(uv, low, high)) * 4.0;
    sum += texture(FieldSampler, clamp(uv + vec2(-texel.x, -texel.y), low, high));
    sum += texture(FieldSampler, clamp(uv + vec2(texel.x, -texel.y), low, high));
    sum += texture(FieldSampler, clamp(uv + vec2(-texel.x, texel.y), low, high));
    sum += texture(FieldSampler, clamp(uv + vec2(texel.x, texel.y), low, high));
    fragColor = sum * 0.125;
}
