#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};
layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 glyphUv;
layout(location = 2) flat in vec4 plate;

layout(location = 0) out vec4 fragColor;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);
const float FLIP = 0.47;
const float BAND = 0.012;
const float QUIET = 0.30;
const float TINT_FROM = 0.25;
const float TINT_FULL = 0.60;

void main() {
    float coverage = texture(Sampler0, glyphUv).a * vertexColor.a;
    if (coverage < 0.004) {
        discard;
    }
    vec3 behind = texture(Sampler1, gl_FragCoord.xy / max(ScreenSize, vec2(1.0))).rgb;
    behind = mix(behind, plate.rgb, plate.a);
    float bright = smoothstep(FLIP - BAND, FLIP + BAND, dot(behind, LUMA));

    vec3 ink = vertexColor.rgb;
    float high = max(ink.r, max(ink.g, ink.b));
    float low = min(ink.r, min(ink.g, ink.b));
    float saturation = high <= 0.0 ? 0.0 : (high - low) / high;
    float quiet = (1.0 - abs(dot(ink, LUMA) * 2.0 - 1.0)) * QUIET;
    float level = mix(1.0 - quiet, quiet, bright);
    vec3 color = mix(vec3(level), ink, smoothstep(TINT_FROM, TINT_FULL, saturation));

    fragColor = vec4(color, coverage) * ColorModulator;
}
