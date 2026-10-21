#version 330

#moj_import <cxvisuals:entity_block.glsl>

uniform sampler2D FieldSampler;
uniform sampler2D LevelSampler;

out vec4 fragColor;

const float FIELD_MIX = 0.5;
const float BAND_LEVEL = 8.0;

void main() {
    vec2 size = vec2(textureSize(FieldSampler, 0));
    vec2 texel = 1.0 / size;
    float shrink = exp2(round(log2(Screen.x / size.x)));
    vec2 full = size * shrink;
    vec2 low = Region.xy / full + 0.5 * texel;
    vec2 high = max(low, (Region.xy + Region.zw) / full - 0.5 * texel);
    vec2 uv = 0.5 * gl_FragCoord.xy * texel;
    vec2 inner = texel * 0.5;
    vec4 sum = texture(FieldSampler, clamp(uv + vec2(-texel.x, 0.0), low, high));
    sum += texture(FieldSampler, clamp(uv + vec2(texel.x, 0.0), low, high));
    sum += texture(FieldSampler, clamp(uv + vec2(0.0, -texel.y), low, high));
    sum += texture(FieldSampler, clamp(uv + vec2(0.0, texel.y), low, high));
    sum += texture(FieldSampler, clamp(uv + vec2(-inner.x, -inner.y), low, high)) * 2.0;
    sum += texture(FieldSampler, clamp(uv + vec2(inner.x, -inner.y), low, high)) * 2.0;
    sum += texture(FieldSampler, clamp(uv + vec2(-inner.x, inner.y), low, high)) * 2.0;
    sum += texture(FieldSampler, clamp(uv + vec2(inner.x, inner.y), low, high)) * 2.0;
    vec4 wide = sum / 12.0;
    vec4 same = texelFetch(LevelSampler, ivec2(gl_FragCoord.xy), 0);
    vec4 mixed = mix(wide, same, FIELD_MIX);
    mixed.a = abs(shrink - BAND_LEVEL) < 0.5 ? same.r : wide.a;
    fragColor = mixed;
}
