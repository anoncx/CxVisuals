#version 330
#extension GL_ARB_separate_shader_objects : require

#moj_import <cxvisuals:entity_block.glsl>

uniform sampler2D MaskSampler;
uniform sampler2D MaskDepthSampler;

layout(location = 0) out vec4 fragColor;

void main() {
    ivec2 base = ivec2(gl_FragCoord.xy) * 2;
    ivec2 low = ivec2(Region.xy);
    ivec2 high = ivec2(Region.xy + Region.zw) - 1;
    float cover = 0.0;
    float ranged = 0.0;
    for (int i = 0; i < 4; i++) {
        ivec2 at = clamp(base + ivec2(i & 1, i >> 1), low, high);
        if (texelFetch(MaskSampler, at, 0).a > 0.0) {
            cover += 0.25;
            ranged += 0.25 * max(viewDistance(vec2(at) + 0.5, texelFetch(MaskDepthSampler, at, 0).r), 0.001);
        }
    }
    fragColor = vec4(cover, ranged, 1.0 - cover, 0.0);
}
